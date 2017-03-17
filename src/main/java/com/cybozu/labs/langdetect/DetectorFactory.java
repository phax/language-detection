package com.cybozu.labs.langdetect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.cybozu.labs.langdetect.util.LangProfile;
import com.cybozu.labs.langdetect.util.NGram;
import com.helger.json.IJson;
import com.helger.json.serialize.JsonReader;

/**
 * Language Detector Factory Class This class manages an initialization and
 * constructions of {@link Detector}. Before using language detection library,
 * load profiles with {@link DetectorFactory#loadProfile(String)} method and set
 * initialization parameters. When the language detection, construct Detector
 * instance via {@link DetectorFactory#create()}. See also {@link Detector}'s
 * sample code.
 * <ul>
 * <li>4x faster improvement based on Elmer Garduno's code. Thanks!</li>
 * </ul>
 *
 * @see Detector
 * @author Nakatani Shuyo
 */
public class DetectorFactory
{
  private final Map <String, double []> m_aWordLangProbMap = new HashMap<> ();
  private final List <String> m_aLanglist = new ArrayList<> ();
  private Long m_aSeed;

  private DetectorFactory ()
  {}

  private static DetectorFactory s_aInstance = new DetectorFactory ();

  Map <String, double []> getWordLangProbMap ()
  {
    return m_aWordLangProbMap;
  }

  Long getSeed ()
  {
    return m_aSeed;
  }

  /**
   * Load profiles from specified directory. This method must be called once
   * before language detection.
   *
   * @param profileDirectory
   *        profile directory path
   * @throws LangDetectException
   *         Can't open profiles(error code =
   *         {@link ELangDetectErrorCode#FileLoadError}) or profile's format is
   *         wrong (error code = {@link ELangDetectErrorCode#FormatError})
   */
  public static void loadProfile (final String profileDirectory) throws LangDetectException
  {
    loadProfile (new File (profileDirectory));
  }

  /**
   * Load profiles from specified directory. This method must be called once
   * before language detection.
   *
   * @param profileDirectory
   *        profile directory path
   * @throws LangDetectException
   *         Can't open profiles(error code =
   *         {@link ELangDetectErrorCode#FileLoadError}) or profile's format is
   *         wrong (error code = {@link ELangDetectErrorCode#FormatError})
   */
  public static void loadProfile (final File profileDirectory) throws LangDetectException
  {
    final File [] listFiles = profileDirectory.listFiles ();
    if (listFiles == null)
      throw new LangDetectException (ELangDetectErrorCode.NeedLoadProfileError,
                                     "Not found profile: " + profileDirectory);

    final int langsize = listFiles.length;
    int index = 0;
    for (final File file : listFiles)
    {
      if (file.getName ().startsWith (".") || !file.isFile ())
        continue;

      final IJson aJson = JsonReader.readFromFile (file);
      if (aJson == null || !aJson.isObject ())
        throw new LangDetectException (ELangDetectErrorCode.FormatError,
                                       "profile format error in '" + file.getName () + "'");

      final LangProfile profile = LangProfile.createFromJson (aJson.getAsObject ());
      addProfile (profile, index, langsize);
      ++index;
    }
  }

  /**
   * Load profiles from specified directory. This method must be called once
   * before language detection.
   *
   * @param json_profiles
   *        profile string list
   * @throws LangDetectException
   *         Can't open profiles(error code =
   *         {@link ELangDetectErrorCode#FileLoadError}) or profile's format is
   *         wrong (error code = {@link ELangDetectErrorCode#FormatError})
   */
  public static void loadProfile (final List <String> json_profiles) throws LangDetectException
  {
    int index = 0;
    final int langsize = json_profiles.size ();
    if (langsize < 2)
      throw new LangDetectException (ELangDetectErrorCode.NeedLoadProfileError, "Need more than 2 profiles");

    for (final String json : json_profiles)
    {
      final IJson aJson = JsonReader.readFromString (json);
      if (aJson == null || !aJson.isObject ())
        throw new LangDetectException (ELangDetectErrorCode.FormatError, "profile format error");

      final LangProfile profile = LangProfile.createFromJson (aJson.getAsObject ());
      addProfile (profile, index, langsize);
      ++index;
    }
  }

  /**
   * @param aProfile
   * @param nLangsize
   * @param nIndex
   * @throws LangDetectException
   */
  static void addProfile (@Nonnull final LangProfile aProfile,
                          final int nIndex,
                          final int nLangsize) throws LangDetectException
  {
    final String sLang = aProfile.getName ();
    if (sLang == null)
      throw new LangDetectException (ELangDetectErrorCode.FormatError, "no language present");

    if (s_aInstance.m_aLanglist.contains (sLang))
      throw new LangDetectException (ELangDetectErrorCode.DuplicateLangError, "duplicate the same language profile");

    s_aInstance.m_aLanglist.add (sLang);
    for (final String word : aProfile.getAllGrams ())
    {
      final int nLength = word.length ();
      if (nLength >= 1 && nLength <= NGram.N_GRAM)
      {
        final double prob = (double) aProfile.getFrequency (word) / aProfile.getNWord (nLength - 1);

        final double [] aLangProb = s_aInstance.m_aWordLangProbMap.computeIfAbsent (word, k -> new double [nLangsize]);
        aLangProb[nIndex] = prob;
      }
    }
  }

  /**
   * Clear loaded language profiles (reinitialization to be available)
   */
  static public void clear ()
  {
    s_aInstance.m_aLanglist.clear ();
    s_aInstance.m_aWordLangProbMap.clear ();
  }

  /**
   * Construct Detector instance
   *
   * @return Detector instance
   * @throws LangDetectException
   *         if no language is present
   */
  static public Detector create () throws LangDetectException
  {
    return _createDetector ();
  }

  /**
   * Construct Detector instance with smoothing parameter
   *
   * @param alpha
   *        smoothing parameter (default value = 0.5)
   * @return Detector instance
   * @throws LangDetectException
   *         if no language is contained
   */
  public static Detector create (final double alpha) throws LangDetectException
  {
    final Detector detector = _createDetector ();
    detector.setAlpha (alpha);
    return detector;
  }

  private static Detector _createDetector () throws LangDetectException
  {
    if (s_aInstance.m_aLanglist.isEmpty ())
      throw new LangDetectException (ELangDetectErrorCode.NeedLoadProfileError, "need to load profiles");
    final Detector detector = new Detector (s_aInstance);
    return detector;
  }

  public static void setSeed (final long seed)
  {
    s_aInstance.m_aSeed = Long.valueOf (seed);
  }

  public static final List <String> getLangList ()
  {
    return Collections.unmodifiableList (s_aInstance.m_aLanglist);
  }
}
