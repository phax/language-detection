package com.carrotsearch.labs.langid;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import javax.annotation.Nonnull;

import org.junit.Test;

import com.carrotsearch.labs.langid.DetectedLanguage;
import com.carrotsearch.labs.langid.LangIdV3;
import com.helger.commons.random.RandomHelper;

public class TestLangIdV3
{
  @Test
  public void testSanity ()
  {
    final LangIdV3 langid = new LangIdV3 ();

    for (final String [] langString : new String [] [] { { "en", "Mike McCandless rocks the boat." },
                                                         { "pl", "W Szczebrzeszynie chrząszcz brzmi w trzcinie" },
                                                         { "it",
                                                           "Piano italiano per la crescita: negoziato in Europa sugli investimenti «virtuosi»" } })
    {
      final DetectedLanguage result = langid.classify (langString[1], true);
      assertEquals (langString[0], result.m_sLangCode);
    }
  }

  private static int randomIntBetween (@Nonnull final Random r, final int nMinIncl, final int nMaxIncl)
  {
    final int ret = nMinIncl + r.nextInt (nMaxIncl + 1 - nMinIncl);
    assert ret >= nMinIncl && ret <= nMaxIncl;
    return ret;
  }

  public static String ofCodeUnitsLength (final int minCodeUnits, final int maxCodeUnits)
  {
    final Random r = RandomHelper.getRandom ();
    final int length = randomIntBetween (r, minCodeUnits, maxCodeUnits);
    final char [] chars = new char [length];
    for (int i = 0; i < chars.length;)
    {
      final int t = randomIntBetween (r, 0, 4);
      if (t == 0 && i < length - 1)
      {
        // Make a surrogate pair
        chars[i++] = (char) randomIntBetween (r, 0xd800, 0xdbff); // high
        chars[i++] = (char) randomIntBetween (r, 0xdc00, 0xdfff); // low
      }
      else
        if (t <= 1)
        {
          chars[i++] = (char) randomIntBetween (r, 0, 0x007f);
        }
        else
          if (t == 2)
          {
            chars[i++] = (char) randomIntBetween (r, 0x80, 0x07ff);
          }
          else
            if (t == 3)
            {
              chars[i++] = (char) randomIntBetween (r, 0x800, 0xd7ff);
            }
            else
              if (t == 4)
              {
                chars[i++] = (char) randomIntBetween (r, 0xe000, 0xffff);
              }
    }
    return new String (chars);
  }

  /**
   * Make sure all ways of getting the prediction yield the same result.
   */
  @Test
  public void testAppendMethods ()
  {
    final Random r = RandomHelper.getRandom ();
    final LangIdV3 v1 = new LangIdV3 ();

    for (int i = 0; i < 1000; i++)
    {
      final String in = ofCodeUnitsLength (1, 300);
      final boolean normalizeConfidence = RandomHelper.getRandom ().nextBoolean ();

      v1.reset ();
      v1.append (in);
      final DetectedLanguage c1 = v1.classify (normalizeConfidence).clone ();

      v1.reset ();
      assertEquals (c1, v1.classify (in, normalizeConfidence));

      v1.reset ();
      v1.append (ByteBuffer.wrap (in.getBytes (StandardCharsets.UTF_8)));
      assertEquals (c1, v1.classify (normalizeConfidence));

      final byte [] bytes = in.getBytes (StandardCharsets.UTF_8);
      final int pad = randomIntBetween (r, 0, 100);
      final int shift = randomIntBetween (r, 0, pad);
      final byte [] bytesShifted = new byte [bytes.length + pad];
      System.arraycopy (bytes, 0, bytesShifted, shift, bytes.length);

      v1.reset ();
      v1.append (bytesShifted, shift, bytes.length);
      assertEquals (c1, v1.classify (normalizeConfidence));
    }
  }
}
