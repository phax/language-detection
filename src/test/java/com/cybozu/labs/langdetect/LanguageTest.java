package com.cybozu.labs.langdetect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * @author Nakatani Shuyo
 */
public final class LanguageTest
{
  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.Language#Language(java.lang.String, double)}.
   */
  @Test
  public final void testLanguage ()
  {
    final Language lang = new Language (null, 0);
    assertNull (lang.getLanguage ());
    assertEquals (lang.getProbability (), 0.0, 0.0001);
    assertEquals (lang.toString (), "");

    final Language lang2 = new Language ("en", 1.0);
    assertEquals (lang2.getLanguage (), "en");
    assertEquals (lang2.getProbability (), 1.0, 0.0001);
    assertEquals (lang2.toString (), "en:1.0");
  }
}
