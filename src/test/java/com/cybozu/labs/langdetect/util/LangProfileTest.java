/**
 *
 */
package com.cybozu.labs.langdetect.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Nakatani Shuyo
 */
public class LangProfileTest
{
  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#LangProfile()}.
   */
  @Test
  public final void testLangProfile ()
  {
    final LangProfile profile = new LangProfile ();
    assertEquals (profile.m_sName, null);
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#LangProfile(java.lang.String)}.
   */
  @Test
  public final void testLangProfileStringInt ()
  {
    final LangProfile profile = new LangProfile ("en");
    assertEquals (profile.m_sName, "en");
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#add(java.lang.String)}.
   */
  @Test
  public final void testAdd ()
  {
    final LangProfile profile = new LangProfile ("en");
    profile.add ("a");
    assertEquals (profile.m_aFreq.get ("a").intValue (), 1);
    profile.add ("a");
    assertEquals (profile.m_aFreq.get ("a").intValue (), 2);
    profile.omitLessFreq ();
  }

  /**
   * Illegal call test for {@link LangProfile#add(String)}
   */
  @Test
  public final void testAddIllegally1 ()
  {
    // Illegal ( available for only JSONIC ) but ignore
    final LangProfile profile = new LangProfile ();
    profile.add ("a"); // ignore
    assertEquals (profile.m_aFreq.get ("a"), null); // ignored
  }

  /**
   * Illegal call test for {@link LangProfile#add(String)}
   */
  @Test
  public final void testAddIllegally2 ()
  {
    final LangProfile profile = new LangProfile ("en");
    profile.add ("a");
    profile.add (""); // Illegal (string's length of parameter must be between 1
                      // and 3) but ignore
    profile.add ("abcd"); // as well
    assertEquals (profile.m_aFreq.get ("a").intValue (), 1);
    assertEquals (profile.m_aFreq.get (""), null); // ignored
    assertEquals (profile.m_aFreq.get ("abcd"), null); // ignored

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
        profile.add (g);
      }
    profile.add ("\u3050");

    assertEquals (profile.m_aFreq.get ("a").intValue (), 5);
    assertEquals (profile.m_aFreq.get ("\u3042").intValue (), 5);
    assertEquals (profile.m_aFreq.get ("\u3050").intValue (), 1);
    profile.omitLessFreq ();
    assertEquals (profile.m_aFreq.get ("a"), null); // omitted
    assertEquals (profile.m_aFreq.get ("\u3042").intValue (), 5);
    assertEquals (profile.m_aFreq.get ("\u3050"), null); // omitted

    final String s = profile.getAsJson ().getAsJsonString ();
    assertEquals (s, LangProfile.createFromJson (profile.getAsJson ()).getAsJson ().getAsJsonString ());
  }

  /**
   * Illegal call test for
   * {@link com.cybozu.labs.langdetect.util.LangProfile#omitLessFreq()}.
   */
  @Test
  public final void testOmitLessFreqIllegally ()
  {
    final LangProfile profile = new LangProfile ();
    profile.omitLessFreq (); // ignore
  }

}
