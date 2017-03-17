package com.carrotsearch.labs.langid;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import com.carrotsearch.hppc.ByteStack;
import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.carrotsearch.hppc.IntOpenHashSet;

public class DumpFeatures
{
  static Model m = Model.defaultModel ();
  static ByteStack seq = ByteStack.newInstance ();
  static IntOpenHashSet visited = IntOpenHashSet.newInstance ();

  static class State
  {
    int state;
    int depth;
    ByteStack seq = ByteStack.newInstance ();
  }

  public static void main (final String [] args) throws Exception
  {
    final ArrayDeque <State> deque = new ArrayDeque <> ();
    {
      final State s = new State ();
      s.state = 0;
      s.depth = 0;
      deque.push (s);
    }

    final IntObjectOpenHashMap <String> featureMap = IntObjectOpenHashMap.newInstance ();
    final StringBuilder b = new StringBuilder ();
    while (!deque.isEmpty ())
    {
      final State s = deque.removeFirst ();
      if (visited.contains (s.state))
      {
        continue;
      }

      if (m.m_aDsaOutput[s.state] != null)
      {
        b.setLength (0);
        b.append (s.depth + " # " + s.seq.size () + " ");
        b.append (new String (s.seq.toArray (), "UTF-8"));

        for (final int fi : m.m_aDsaOutput[s.state])
        {
          b.append (" " + fi);
        }

        featureMap.put (s.state, b.toString ());
        System.out.println (s.state + " >  " + b);
      }

      visited.add (s.state);
      if (s.depth < 10)
      {
        for (int i = 0; i <= 0xFF; i++)
        {
          final int newState = m.m_aDsa[(s.state << 8) + i];
          final State ns = new State ();
          ns.state = newState;
          ns.depth = s.depth + 1;
          ns.seq = s.seq.clone ();
          ns.seq.push ((byte) i);
          deque.addLast (ns);
        }
      }
    }

    System.out.println (visited.size () + " " + m.m_aDsaOutput.length);

    final String in = "Salsa Caliente! salsa caliente, latin music, latin dance, salsa, salsa music, latin-american music, spanish music ... 2002 Cammy Award! SALSA CALIENTE! BEST WORLD BEAT BAND ... Proudly Sponsored By. Salsa Caliente! performs your favorite Latin/Spanish dance music ...";
    final ByteBuffer buffer = ByteBuffer.wrap (in.getBytes (StandardCharsets.UTF_8));

    // Update predictions (without an intermediate statecount as in the
    // original)
    short state = 0;
    final int [] [] tk_output = m.m_aDsaOutput;
    final short [] tk_nextmove = m.m_aDsa;

    while (buffer.hasRemaining ())
    {
      state = tk_nextmove[(state << 8) + (buffer.get () & 0xff)];

      final int [] is = tk_output[state];
      if (is != null)
      {
        System.out.println (" > " + featureMap.get (state));
      }
    }

    final LangIdV3 langid = new LangIdV3 ();
    langid.reset ();
    langid.append (in);
    for (final DetectedLanguage d : langid.rank (true))
    {
      System.out.println ("$ " + d);
    }
    System.out.println (langid.classify (true));
  }
}
