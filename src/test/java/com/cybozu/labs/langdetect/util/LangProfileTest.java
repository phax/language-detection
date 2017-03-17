/**
 *
 */
package com.cybozu.labs.langdetect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * @author Nakatani Shuyo
 */
public class LangProfileTest
{
  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#LangProfile(java.lang.String)}.
   */
  @Test
  public final void testLangProfileStringInt ()
  {
    final LangProfile profile = new LangProfile ("en");
    assertEquals ("en", profile.getName ());
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#addNGram(java.lang.String)}.
   */
  @Test
  public final void testAdd ()
  {
    final LangProfile profile = new LangProfile ("en");
    profile.addNGram ("a");
    assertEquals (1, profile.getFrequency ("a"));
    profile.addNGram ("a");
    assertEquals (2, profile.getFrequency ("a"));
    profile.omitLessFreq ();
  }

  /**
   * Illegal call test for {@link LangProfile#addNGram(String)}
   */
  @Test
  public final void testAddIllegally2 ()
  {
    final LangProfile profile = new LangProfile ("en");
    profile.addNGram ("a");
    // Illegal (string's length of parameter must be between 1 and 3) but ignore
    try
    {
      profile.addNGram ("");
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {}
    try
    {
      profile.addNGram ("abcd"); // as well
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {}
    assertEquals (profile.getFrequency ("a"), 1);
    assertEquals (profile.getFrequencyObj (""), null); // ignored
    assertEquals (profile.getFrequencyObj ("abcd"), null); // ignored

  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#omitLessFreq()}.
   */
  @Test
  public final void testOmitLessFreq ()
  {
    final LangProfile profile = new LangProfile ("en");
    final String [] grams = "a b c \u3042 \u3044 \u3046 \u3048 \u304a \u304b \u304c \u304d \u304e \u304f".split (" ");
    for (int i = 0; i < 5; ++i)
      for (final String g : grams)
      {
        profile.addNGram (g);
      }
    profile.addNGram ("\u3050");

    assertEquals (profile.getFrequency ("a"), 5);
    assertEquals (profile.getFrequency ("\u3042"), 5);
    assertEquals (profile.getFrequency ("\u3050"), 1);
    profile.omitLessFreq ();
    assertEquals (profile.getFrequencyObj ("a"), null); // omitted
    assertEquals (profile.getFrequency ("\u3042"), 5);
    assertEquals (profile.getFrequencyObj ("\u3050"), null); // omitted

    final String s = profile.getAsJson ().getAsJsonString ();
    assertEquals (s, LangProfile.createFromJson (profile.getAsJson ()).getAsJson ().getAsJsonString ());
  }
}
