package com.cybozu.labs.langdetect.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.mutable.MutableInt;
import com.helger.commons.regex.RegExHelper;
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

  private final String m_sName;
  private final Map <String, MutableInt> m_aFreq = new HashMap<> ();
  private final int [] m_aNWords = new int [NGram.N_GRAM];

  /**
   * Normal Constructor
   *
   * @param sName
   *        language name
   */
  public LangProfile (@Nonnull @Nonempty final String sName)
  {
    ValueEnforcer.notEmpty (sName, "Name");
    m_sName = sName;
  }

  @Nonnull
  @Nonempty
  public String getName ()
  {
    return m_sName;
  }

  @Nonnull
  public Set <String> getAllGrams ()
  {
    return m_aFreq.keySet ();
  }

  @Nullable
  public MutableInt getFrequencyObj (final String sGram)
  {
    return m_aFreq.get (sGram);
  }

  public int getFrequency (final String sGram)
  {
    return getFrequencyObj (sGram).intValue ();
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
  public void addNGram (@Nonnull @Nonempty final String gram)
  {
    ValueEnforcer.notEmpty (gram, "Gram");

    final int len = gram.length ();
    if (len > NGram.N_GRAM)
      throw new IllegalArgumentException ("Maximum gram length is " + NGram.N_GRAM);

    m_aNWords[len - 1]++;
    m_aFreq.computeIfAbsent (gram, k -> new MutableInt (0)).inc ();
  }

  /**
   * Merge two language profiles together
   *
   * @param other
   *        other LangPorfile
   */
  public void merge (@Nonnull final LangProfile other)
  {
    if (!m_sName.equals (other.m_sName))
      throw new IllegalStateException ("Cannot merge " + m_sName + " with " + other.getName ());

    for (int i = 0; i < m_aNWords.length; i++)
      m_aNWords[i] += other.m_aNWords[i];

    for (final Map.Entry <String, MutableInt> aEntry : other.m_aFreq.entrySet ())
    {
      final String key = aEntry.getKey ();
      final MutableInt aOtherCount = aEntry.getValue ();
      final MutableInt aMy = m_aFreq.get (key);
      if (aMy != null)
        aMy.inc (aOtherCount);
      else
        m_aFreq.put (key, aOtherCount);
    }
  }

  /**
   * Eliminate below less frequency n-grams and noise Latin alphabets
   */
  public void omitLessFreq ()
  {
    int nThreshold = m_aNWords[0] / LESS_FREQ_RATIO;
    if (nThreshold < MINIMUM_FREQ)
      nThreshold = MINIMUM_FREQ;

    int nRoman = 0;
    // Must iterate on copy!
    for (final Map.Entry <String, MutableInt> aEntry : new HashSet<> (m_aFreq.entrySet ()))
    {
      final String sKey = aEntry.getKey ();
      final int nCount = aEntry.getValue ().intValue ();
      if (nCount <= nThreshold)
      {
        m_aNWords[sKey.length () - 1] -= nCount;
        m_aFreq.remove (sKey);
      }
      else
      {
        if (RegExHelper.stringMatchesPattern ("^[A-Za-z]$", sKey))
        {
          nRoman += nCount;
        }
      }
    }

    // roman check
    if (nRoman < m_aNWords[0] / 3)
    {
      // Must iterate on copy!
      for (final Map.Entry <String, MutableInt> aEntry : new HashSet<> (m_aFreq.entrySet ()))
      {
        final String sKey = aEntry.getKey ();
        if (RegExHelper.stringMatchesPattern (".*[A-Za-z].*", sKey))
        {
          m_aNWords[sKey.length () - 1] -= aEntry.getValue ().intValue ();
          m_aFreq.remove (sKey);
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
  public void update (@Nullable final String sText)
  {
    if (sText == null)
      return;

    final String sNormalizedText = NGram.normalize_vi (sText);
    final NGram aNGram = new NGram ();
    for (final char c : sNormalizedText.toCharArray ())
    {
      aNGram.addChar (c);
      for (int n = 1; n <= NGram.N_GRAM; ++n)
      {
        final String sGram = aNGram.get (n);
        if (sGram != null)
          addNGram (sGram);
      }
    }
  }

  @Nonnull
  public static LangProfile createFromJson (@Nonnull final IJsonObject aJson)
  {
    final LangProfile ret = new LangProfile (aJson.getAsString ("name"));
    int i = 0;
    for (final IJson aValue : aJson.getAsArray ("n_words"))
      ret.m_aNWords[i++] = aValue.getAsValue ().getAsInt ();
    for (final Map.Entry <String, IJson> aEntry : aJson.getAsObject ("freq"))
      ret.m_aFreq.put (aEntry.getKey (), new MutableInt (aEntry.getValue ().getAsValue ().getAsInt ()));
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
