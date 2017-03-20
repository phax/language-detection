package com.carrotsearch.labs.langid;

/**
 * Mutable {@link DetectedLanguage} so that we can reuse instances if needed.
 */
public final class DetectedLanguage implements Cloneable
{
  public String m_sLangCode;
  public float m_fConfidence;

  public DetectedLanguage (final String lang, final float confidence)
  {
    this.m_sLangCode = lang;
    this.m_fConfidence = confidence;
  }

  public String getLangCode ()
  {
    return m_sLangCode;
  }

  public double getConfidence ()
  {
    return m_fConfidence;
  }

  @Override
  protected DetectedLanguage clone ()
  {
    return new DetectedLanguage (m_sLangCode, m_fConfidence);
  }

  @Override
  public int hashCode ()
  {
    throw new UnsupportedOperationException ();
  }

  @Override
  public boolean equals (final Object other)
  {
    if (other != null && other instanceof DetectedLanguage)
    {
      final DetectedLanguage d = (DetectedLanguage) other;
      return Float.compare (this.m_fConfidence, d.m_fConfidence) == 0 && equals (m_sLangCode, d.m_sLangCode);
    }
    return false;
  }

  private static boolean equals (final String a, final String b)
  {
    return a == b || (a != null && a.equals (b));
  }

  @Override
  public String toString ()
  {
    return "[" + m_sLangCode + ", conf.: " + m_fConfidence + "]";
  }
}
