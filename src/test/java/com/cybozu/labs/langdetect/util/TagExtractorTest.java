/**
 *
 */
package com.cybozu.labs.langdetect.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Nakatani Shuyo
 */
public class TagExtractorTest
{
  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.TagExtractor#TagExtractor(java.lang.String, int)}.
   */
  @Test
  public final void testTagExtractor ()
  {
    final TagExtractor extractor = new TagExtractor (null, 0);
    assertEquals (extractor.getTarget (), null);
    assertEquals (extractor.getThreshold (), 0);

    final TagExtractor extractor2 = new TagExtractor ("abstract", 10);
    assertEquals (extractor2.getTarget (), "abstract");
    assertEquals (extractor2.getThreshold (), 10);
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.TagExtractor#setTag(java.lang.String)}.
   */
  @Test
  public final void testSetTag ()
  {
    final TagExtractor extractor = new TagExtractor (null, 0);
    extractor.setTag ("");
    assertEquals (extractor.getTag (), "");
    extractor.setTag (null);
    assertEquals (extractor.getTag (), null);
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.TagExtractor#add(java.lang.String)}.
   */
  @Test
  public final void testAdd ()
  {
    final TagExtractor extractor = new TagExtractor (null, 0);
    extractor.add ("");
    extractor.add (null); // ignore
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.TagExtractor#closeTag()}.
   */
  @Test
  public final void testCloseTag ()
  {
    final TagExtractor extractor = new TagExtractor (null, 0);
    extractor.closeTag (); // ignore
  }

  /**
   * Scenario Test of extracting &lt;abstract&gt; tag from Wikipedia database.
   */
  @Test
  public final void testNormalScenario ()
  {
    final TagExtractor extractor = new TagExtractor ("abstract", 10);
    assertEquals (extractor.count (), 0);

    final LangProfile profile = new LangProfile ("en");

    // normal
    extractor.setTag ("abstract");
    extractor.add ("This is a sample text.");
    profile.update (extractor.closeTag ());
    assertEquals (extractor.count (), 1);
    assertEquals (profile.getNWord (0), 17); // Thisisasampletext
    assertEquals (profile.getNWord (1), 22); // _T, Th, hi, ...
    assertEquals (profile.getNWord (2), 17); // _Th, Thi, his, ...

    // too short
    extractor.setTag ("abstract");
    extractor.add ("sample");
    profile.update (extractor.closeTag ());
    assertEquals (extractor.count (), 1);

    // other tags
    extractor.setTag ("div");
    extractor.add ("This is a sample text which is enough long.");
    profile.update (extractor.closeTag ());
    assertEquals (extractor.count (), 1);
  }

  /**
   * Test method for
   * {@link com.cybozu.labs.langdetect.util.TagExtractor#clear()}.
   */
  @Test
  public final void testClear ()
  {
    final TagExtractor extractor = new TagExtractor ("abstract", 10);
    extractor.setTag ("abstract");
    extractor.add ("This is a sample text.");
    assertEquals (extractor.getBuf (), "This is a sample text.");
    assertEquals (extractor.getTag (), "abstract");
    extractor.clear ();
    assertEquals (extractor.getBuf (), "");
    assertEquals (extractor.getTag (), null);
  }

}
