package com.cybozu.labs.langdetect;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * {@link Language} is to store the detected language.
 * {@link Detector#getProbabilities()} returns an {@link ArrayList} of
 * {@link Language}s.
 *
 * @see Detector#getProbabilities()
 * @author Nakatani Shuyo
 */
public class Language implements Serializable
{
  private final String m_sLang;
  private final double m_dProb;

  public Language (final String lang, final double prob)
  {
    m_sLang = lang;
    m_dProb = prob;
  }

  public String getLanguage ()
  {
    return m_sLang;
  }

  public double getProbability ()
  {
    return m_dProb;
  }

  @Override
  public String toString ()
  {
    if (m_sLang == null)
      return "";
    return m_sLang + ":" + m_dProb;
  }
}
