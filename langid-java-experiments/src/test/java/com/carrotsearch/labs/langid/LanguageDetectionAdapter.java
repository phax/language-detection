package com.carrotsearch.labs.langid;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.google.common.io.Resources;

public class LanguageDetectionAdapter implements IClassifier <String, String>
{

  static
  {
    /*
     * Initialize baseline.
     */
    final String languages[] = { "af",
                                 "ar",
                                 "bg",
                                 "bn",
                                 "cs",
                                 "da",
                                 "de",
                                 "el",
                                 "en",
                                 "es",
                                 "et",
                                 "fa",
                                 "fi",
                                 "fr",
                                 "gu",
                                 "he",
                                 "hi",
                                 "hr",
                                 "hu",
                                 "id",
                                 "it",
                                 "ja",
                                 "kn",
                                 "ko",
                                 "lt",
                                 "lv",
                                 "mk",
                                 "ml",
                                 "mr",
                                 "ne",
                                 "nl",
                                 "no",
                                 "pa",
                                 "pl",
                                 "pt",
                                 "ro",
                                 "ru",
                                 "sk",
                                 "sl",
                                 "so",
                                 "sq",
                                 "sv",
                                 "sw",
                                 "ta",
                                 "te",
                                 "th",
                                 "tl",
                                 "tr",
                                 "uk",
                                 "ur",
                                 "vi",
                                 "zh-cn",
                                 "zh-tw" };

    try
    {
      final List <String> profileData = new ArrayList <> ();
      for (final String language : languages)
      {
        final URL langData = Resources.getResource (com.cybozu.labs.langdetect.DetectorFactory.class,
                                                    "/profiles/" + language);
        profileData.add (Resources.toString (langData, StandardCharsets.UTF_8));
      }
      com.cybozu.labs.langdetect.DetectorFactory.loadProfile (profileData);
      com.cybozu.labs.langdetect.DetectorFactory.setSeed (0);
    }
    catch (final Exception e)
    {
      throw new RuntimeException (e);
    }

    try
    {
      final List <String> profileData = new ArrayList <> ();
      for (final String language : languages)
      {
        final URL langData = Resources.getResource (DetectorFactory.class, "/profiles/" + language);
        profileData.add (Resources.toString (langData, StandardCharsets.UTF_8));
      }
      DetectorFactory.clear ();
      DetectorFactory.loadProfile (profileData);
      DetectorFactory.setSeed (0);
    }
    catch (final Exception e)
    {
      throw new RuntimeException (e);
    }
  }

  @Override
  public String classify (final String data)
  {
    try
    {
      final Detector d = DetectorFactory.create ();
      d.append (data);
      return d.detect ();
    }
    catch (final LangDetectException e)
    {
      throw new RuntimeException (e);
    }
  }

  @Override
  public String getName ()
  {
    return "languagedetect";
  }
}
