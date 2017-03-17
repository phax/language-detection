package com.cybozu.labs.langdetect.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.helger.json.IJson;
import com.helger.json.IJsonObject;
import com.helger.json.JsonArray;
import com.helger.json.JsonObject;

/**
 * {@link LangProfile} is a Language Profile Class. Users don't use this class
 * directly.
 *
 * @author Nakatani Shuyo
 */
public class LangProfile
{
  private static final int MINIMUM_FREQ = 2;
  private static final int LESS_FREQ_RATIO = 100000;

  String m_sName;
  final Map <String, Integer> m_aFreq = new HashMap<> ();
  final int [] m_aNWords = new int [NGram.N_GRAM];

  /**
   * Constructor for JSONIC
   */
  public LangProfile ()
  {}

  /**
   * Normal Constructor
   *
   * @param sName
   *        language name
   */
  public LangProfile (final String sName)
  {
    m_sName = sName;
  }

  public String getName ()
  {
    return m_sName;
  }

  public Set <String> getAllGrams ()
  {
    return m_aFreq.keySet ();
  }

  public Integer getFrequency (final String sGram)
  {
    return m_aFreq.get (sGram);
  }

  public int getNWord (final int i)
  {
    return m_aNWords[i];
  }

  /**
   * Add n-gram to profile
   *
   * @param gram
   *        n-gram to add
   */
  public void add (final String gram)
  {
    if (m_sName == null || gram == null)
      return; // Illegal

    final int len = gram.length ();
    if (len < 1 || len > NGram.N_GRAM)
      return; // Illegal

    ++m_aNWords[len - 1];
    final Integer aOld = m_aFreq.get (gram);
    if (aOld != null)
      m_aFreq.put (gram, Integer.valueOf (aOld.intValue () + 1));
    else
      m_aFreq.put (gram, Integer.valueOf (1));
  }

  /**
   * Merge two language profiles together
   *
   * @param other
   *        other LangPorfile
   */
  public void merge (final LangProfile other)
  {
    if (!m_sName.equals (other.m_sName))
      return; // Illegal

    for (int i = 0; i < m_aNWords.length; i++)
      m_aNWords[i] += other.m_aNWords[i];

    for (final String key : other.m_aFreq.keySet ())
    {
      final Integer aMy = m_aFreq.get (key);
      if (aMy != null)
        m_aFreq.put (key, Integer.valueOf (aMy.intValue () + other.m_aFreq.get (key).intValue ()));
      else
        m_aFreq.put (key, other.m_aFreq.get (key));
    }
  }

  /**
   * Eliminate below less frequency n-grams and noise Latin alphabets
   */
  public void omitLessFreq ()
  {
    if (m_sName == null)
      return; // Illegal

    int threshold = m_aNWords[0] / LESS_FREQ_RATIO;
    if (threshold < MINIMUM_FREQ)
      threshold = MINIMUM_FREQ;

    int roman = 0;
    // Must iterate on copy!
    for (final Map.Entry <String, Integer> aEntry : new HashSet<> (m_aFreq.entrySet ()))
    {
      final String key = aEntry.getKey ();
      final int count = aEntry.getValue ().intValue ();
      if (count <= threshold)
      {
        m_aNWords[key.length () - 1] -= count;
        m_aFreq.remove (key);
      }
      else
      {
        if (key.matches ("^[A-Za-z]$"))
        {
          roman += count;
        }
      }
    }

    // roman check
    if (roman < m_aNWords[0] / 3)
    {
      // Must iterate on copy!
      for (final Map.Entry <String, Integer> aEntry : new HashSet<> (m_aFreq.entrySet ()))
      {
        final String key = aEntry.getKey ();
        if (key.matches (".*[A-Za-z].*"))
        {
          m_aNWords[key.length () - 1] -= aEntry.getValue ().intValue ();
          m_aFreq.remove (key);
        }
      }
    }
  }

  /**
   * Update the language profile with (fragmented) text. Extract n-grams from
   * text and add their frequency into the profile.
   *
   * @param sText
   *        (fragmented) text to extract n-grams
   */
  public void update (final String sText)
  {
    if (sText == null)
      return;

    final String sNormalizedText = NGram.normalize_vi (sText);
    final NGram gram = new NGram ();
    for (final char c : sNormalizedText.toCharArray ())
    {
      gram.addChar (c);
      for (int n = 1; n <= NGram.N_GRAM; ++n)
        add (gram.get (n));
    }
  }

  @Nonnull
  public static LangProfile createFromJson (@Nonnull final IJsonObject aJson)
  {
    final LangProfile ret = new LangProfile ();
    ret.m_sName = aJson.getAsString ("name");
    int i = 0;
    for (final IJson aValue : aJson.getAsArray ("n_words"))
      ret.m_aNWords[i++] = aValue.getAsValue ().getAsInt ();
    for (final Map.Entry <String, IJson> aEntry : aJson.getAsObject ("freq"))
      ret.m_aFreq.put (aEntry.getKey (), Integer.valueOf (aEntry.getValue ().getAsValue ().getAsInt ()));
    return ret;
  }

  @Nonnull
  public IJsonObject getAsJson ()
  {
    final IJsonObject ret = new JsonObject ();
    ret.add ("name", m_sName);
    ret.add ("n_words", new JsonArray ().addAll (m_aNWords));
    ret.add ("freq", new JsonObject (m_aFreq.size ()).addAll (m_aFreq));
    return ret;
  }
}
