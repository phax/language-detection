package com.cybozu.labs.langdetect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cybozu.labs.langdetect.util.LangProfile;
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
  final Map <String, double []> wordLangProbMap = new HashMap<> ();
  final List <String> langlist = new ArrayList<> ();
  Long seed;

  private DetectorFactory ()
  {}

  private static DetectorFactory instance_ = new DetectorFactory ();

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
   * @param profile
   * @param langsize
   * @param index
   * @throws LangDetectException
   */
  static void addProfile (final LangProfile profile, final int index, final int langsize) throws LangDetectException
  {
    final String lang = profile.getName ();
    if (instance_.langlist.contains (lang))
      throw new LangDetectException (ELangDetectErrorCode.DuplicateLangError, "duplicate the same language profile");

    instance_.langlist.add (lang);
    for (final String word : profile.getAllGrams ())
    {
      if (!instance_.wordLangProbMap.containsKey (word))
      {
        instance_.wordLangProbMap.put (word, new double [langsize]);
      }
      final int length = word.length ();
      if (length >= 1 && length <= 3)
      {
        final double prob = profile.getFrequency (word).doubleValue () / profile.getNWord (length - 1);
        instance_.wordLangProbMap.get (word)[index] = prob;
      }
    }
  }

  /**
   * Clear loaded language profiles (reinitialization to be available)
   */
  static public void clear ()
  {
    instance_.langlist.clear ();
    instance_.wordLangProbMap.clear ();
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
    if (instance_.langlist.isEmpty ())
      throw new LangDetectException (ELangDetectErrorCode.NeedLoadProfileError, "need to load profiles");
    final Detector detector = new Detector (instance_);
    return detector;
  }

  public static void setSeed (final long seed)
  {
    instance_.seed = Long.valueOf (seed);
  }

  public static final List <String> getLangList ()
  {
    return Collections.unmodifiableList (instance_.langlist);
  }
}
