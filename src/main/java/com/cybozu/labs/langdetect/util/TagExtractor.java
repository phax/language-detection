package com.cybozu.labs.langdetect.util;

import com.helger.commons.annotation.VisibleForTesting;
import com.helger.commons.equals.EqualsHelper;

/**
 * {@link TagExtractor} is a class which extracts inner texts of specified tag.
 * Users don't use this class directly.
 *
 * @author Nakatani Shuyo
 */
public class TagExtractor
{
  private final String m_sTarget;
  private final int m_nThreshold;
  private StringBuilder m_aBuf;
  private String m_sTag;
  private int m_nCount;

  public TagExtractor (final String tag, final int threshold)
  {
    m_sTarget = tag;
    m_nThreshold = threshold;
    m_nCount = 0;
    clear ();
  }

  @VisibleForTesting
  String getTarget ()
  {
    return m_sTarget;
  }

  @VisibleForTesting
  int getThreshold ()
  {
    return m_nThreshold;
  }

  @VisibleForTesting
  String getBuf ()
  {
    return m_aBuf.toString ();
  }

  @VisibleForTesting
  String getTag ()
  {
    return m_sTag;
  }

  public void setTag (final String tag)
  {
    m_sTag = tag;
  }

  public int count ()
  {
    return m_nCount;
  }

  public void clear ()
  {
    m_aBuf = new StringBuilder ();
    m_sTag = null;
  }

  public void add (final String sLine)
  {
    if (EqualsHelper.equals (m_sTag, m_sTarget) && sLine != null)
    {
      m_aBuf.append (sLine);
    }
  }

  public String closeTag ()
  {
    String ret = null;
    if (EqualsHelper.equals (m_sTag, m_sTarget) && m_aBuf.length () > m_nThreshold)
    {
      ret = m_aBuf.toString ();
      ++m_nCount;
    }
    clear ();
    return ret;
  }
}
