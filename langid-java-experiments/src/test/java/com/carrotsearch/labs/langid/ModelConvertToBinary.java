package com.carrotsearch.labs.langid;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.helger.commons.io.file.SimpleFileIO;

/**
 * Read a model from txt file and persist it to binary format.
 */
public final class ModelConvertToBinary
{
  private static Model loadModel (final InputStream modelData) throws IOException
  {
    final BufferedReader reader = new BufferedReader (new InputStreamReader (modelData, Charset.forName ("UTF-8")));

    float [] nb_ptc = null;
    float [] nb_pc = null;
    String [] nb_classes = null;
    short [] tk_nextmove = null;
    int [] [] tk_output = null;

    String line;
    while ((line = reader.readLine ()) != null)
    {
      final int eqIndex = line.indexOf ("=");
      if (eqIndex < 0)
        continue;
      final String key = line.substring (0, eqIndex);
      final String val = line.substring (eqIndex + 1);
      if (key.equals ("nb_ptc"))
      {
        nb_ptc = arrayOfFloats (val);
      }
      if (key.equals ("nb_pc"))
      {
        nb_pc = arrayOfFloats (val);
      }
      if (key.equals ("nb_classes"))
      {
        nb_classes = val.split ("[\\,\\s]+");
      }
      if (key.equals ("tk_nextmove"))
      {
        final int [] tmp = arrayOfInts (val);
        final short [] tmp2 = new short [tmp.length];
        for (int i = 0; i < tmp.length; i++)
        {
          if (tmp[i] > Short.MAX_VALUE)
            throw new RuntimeException ();
          tmp2[i] = (short) tmp[i];
        }
        tk_nextmove = tmp2;
      }
      if (key.equals ("tk_output"))
      {
        final String [] kvPairs = val.split ("[\\;]");
        final Map <Integer, int []> tmp = new HashMap <> ();
        for (final String aKvPair : kvPairs)
        {
          final int colonIndex = aKvPair.indexOf (":");
          final int index = Integer.parseInt (aKvPair.substring (0, colonIndex).trim ());
          final int [] vals = arrayOfInts (aKvPair.substring (colonIndex + 1).replaceAll ("[\\(\\)]", ""));
          tmp.put (index, vals);
        }

        int maxKey = -1;
        for (final int i : tmp.keySet ())
        {
          maxKey = Math.max (maxKey, i);
        }
        tk_output = new int [maxKey + 1] [];
        for (final Map.Entry <Integer, int []> e : tmp.entrySet ())
        {
          final int [] value = e.getValue ();
          if (value.length != 0)
          {
            tk_output[e.getKey ()] = value;
          }
        }
      }
    }

    // transpose model.nb_ptc so dot() has linear access pattern in memory.
    nb_ptc = MatrixOps.transpose (nb_ptc, nb_pc.length, nb_ptc.length / nb_pc.length);

    return new Model (nb_classes, nb_ptc, nb_pc, tk_nextmove, tk_output);
  }

  private static float [] arrayOfFloats (final String val)
  {
    final String [] vals = val.split ("[\\,\\s]+");
    final float [] res = new float [vals.length];
    for (int i = 0; i < vals.length; i++)
    {
      res[i] = Float.parseFloat (vals[i]);
    }
    return res;
  }

  private static int [] arrayOfInts (final String val)
  {
    String [] vals = val.trim ().split ("[\\,\\s]+");
    if (vals.length > 0 && vals[vals.length - 1].equals (""))
    {
      vals = Arrays.copyOf (vals, vals.length - 1);
    }
    final int [] res = new int [vals.length];
    for (int i = 0; i < vals.length; i++)
    {
      try
      {
        res[i] = Integer.parseInt (vals[i]);
      }
      catch (final NumberFormatException e)
      {
        throw e;
      }
    }
    return res;
  }

  public static void main (final String [] args) throws Exception
  {
    final Model model = ModelConvertToBinary.loadModel (ModelConvertToBinary.class.getResourceAsStream ("/langid.model.txt"));
    final ByteArrayOutputStream baos = new ByteArrayOutputStream ();
    final ObjectOutputStream oos = new ObjectOutputStream (baos);
    model.writeExternal (oos);

    SimpleFileIO.writeFile (new File ("langid.model"), baos.toByteArray ());
  }
}
