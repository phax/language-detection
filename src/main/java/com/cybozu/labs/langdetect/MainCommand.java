package com.cybozu.labs.langdetect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cybozu.labs.langdetect.util.LangProfile;
import com.helger.commons.io.EAppend;
import com.helger.commons.io.file.FileHelper;
import com.helger.commons.io.stream.NonBlockingBufferedReader;
import com.helger.commons.string.StringParser;
import com.helger.json.IJson;
import com.helger.json.serialize.JsonReader;
import com.helger.json.serialize.JsonWriter;

/**
 * LangDetect Command Line Interface
 * <p>
 * This is a command line interface of Language Detection Library "LangDetect".
 *
 * @author Nakatani Shuyo
 */
public class MainCommand
{
  /** smoothing default parameter (ELE) */
  private static final double DEFAULT_ALPHA = 0.5;

  /** for Command line easy parser */
  private final Map <String, String> m_aCmdOptWithValue = new HashMap<> ();
  private final Map <String, String> m_aCmdValues = new HashMap<> ();
  private final Set <String> m_aCmdOptWithoutValue = new HashSet<> ();
  private final List <String> m_aCmdArgs = new ArrayList<> ();

  private void _addOpt (final String opt, final String key, final String value)
  {
    m_aCmdOptWithValue.put (opt, key);
    if (value != null)
      m_aCmdValues.put (key, value);
  }

  /**
   * Command line easy parser
   *
   * @param args
   *        command line arguments
   */
  private void _parse (final String [] args)
  {
    for (int i = 0; i < args.length; ++i)
    {
      if (m_aCmdOptWithValue.containsKey (args[i]))
      {
        final String key = m_aCmdOptWithValue.get (args[i]);
        m_aCmdValues.put (key, args[i + 1]);
        ++i;
      }
      else
        if (args[i].startsWith ("-"))
        {
          m_aCmdOptWithoutValue.add (args[i]);
        }
        else
        {
          m_aCmdArgs.add (args[i]);
        }
    }
  }

  private String _getCmdValueAsString (final String key)
  {
    return m_aCmdValues.get (key);
  }

  private Long _getCmdValueAsLong (final String key)
  {
    return StringParser.parseLongObj (_getCmdValueAsString (key));
  }

  private double _getCmdValueAsDouble (final String key, final double defaultValue)
  {
    return StringParser.parseDouble (_getCmdValueAsString (key), defaultValue);
  }

  private boolean _hasOptWithoutValue (final String opt)
  {
    return m_aCmdOptWithoutValue.contains (opt);
  }

  /**
   * File search (easy glob)
   *
   * @param directory
   *        directory path
   * @param pattern
   *        searching file pattern with regular representation
   * @return matched file
   */
  private static File _searchFile (final File directory, final String pattern)
  {
    for (final File file : directory.listFiles ())
    {
      if (file.getName ().matches (pattern))
        return file;
    }
    return null;
  }

  /**
   * load profiles
   */
  private void _loadProfile ()
  {
    final String profileDirectory = _getCmdValueAsString ("directory") + "/";
    try
    {
      DetectorFactory.loadProfile (profileDirectory);
      final Long seed = _getCmdValueAsLong ("seed");
      if (seed != null)
        DetectorFactory.setSeed (seed.longValue ());
    }
    catch (final LangDetectException e)
    {
      System.err.println ("ERROR: " + e.getMessage ());
      throw new IllegalStateException (e);
    }
  }

  /**
   * Generate Language Profile from Wikipedia Abstract Database File
   *
   * <pre>
   * usage: --genprofile -d [abstracts directory] [language names]
   * </pre>
   */
  private void _generateProfile ()
  {
    final File directory = new File (_getCmdValueAsString ("directory"));
    for (final String lang : m_aCmdArgs)
    {
      final File file = _searchFile (directory, lang + "wiki-.*-abstract\\.xml.*");
      if (file == null)
      {
        System.err.println ("Not Found abstract xml : lang = " + lang);
        continue;
      }

      try
      {
        final LangProfile profile = GenProfile.loadFromWikipediaAbstract (lang, file);
        if (_getCmdValueAsString ("update") != null)
        {
          final IJson aJson = JsonReader.readFromFile (file);
          if (aJson == null || !aJson.isObject ())
            throw new LangDetectException (ELangDetectErrorCode.FileLoadError, "Failed to parse JSON from " + file);

          final LangProfile old_profile = LangProfile.createFromJson (aJson.getAsObject ());
          profile.merge (old_profile);
        }
        else
        {
          profile.omitLessFreq ();
        }

        final File profile_path = new File (_getCmdValueAsString ("directory") + "/profiles/" + lang);
        new JsonWriter ().writeToWriter (profile.getAsJson (),
                                         FileHelper.getBufferedWriter (profile_path,
                                                                       EAppend.TRUNCATE,
                                                                       StandardCharsets.UTF_8));
      }
      catch (final IOException | LangDetectException e)
      {
        e.printStackTrace ();
      }
    }
  }

  /**
   * Generate Language Profile from Text File
   *
   * <pre>
   * usage: --genprofile-text -l [language code] [text file path]
   * </pre>
   */
  private void _generateProfileFromText ()
  {
    if (m_aCmdArgs.size () != 1)
    {
      System.err.println ("Need to specify text file path");
      return;
    }
    final File file = new File (m_aCmdArgs.get (0));
    if (!file.exists ())
    {
      System.err.println ("Need to specify existing text file path");
      return;
    }

    final String lang = _getCmdValueAsString ("lang");
    if (lang == null)
    {
      System.err.println ("Need to specify langage code(-l)");
      return;
    }

    try
    {
      final LangProfile profile = GenProfile.loadFromText (lang, file);
      final File profile_path = new File (lang);
      if (_getCmdValueAsString ("update") != null)
      {
        final IJson aJson = JsonReader.readFromFile (profile_path);
        if (aJson == null || !aJson.isObject ())
          throw new LangDetectException (ELangDetectErrorCode.FileLoadError,
                                         "Failed to parse JSON from " + profile_path);

        final LangProfile old_profile = LangProfile.createFromJson (aJson.getAsObject ());
        profile.merge (old_profile);
      }
      else
      {
        profile.omitLessFreq ();
      }

      new JsonWriter ().writeToWriter (profile.getAsJson (),
                                       FileHelper.getBufferedWriter (profile_path,
                                                                     EAppend.TRUNCATE,
                                                                     StandardCharsets.UTF_8));
    }
    catch (final IOException | LangDetectException e)
    {
      e.printStackTrace ();
    }
  }

  /**
   * Omit less frequent terms from language profile
   *
   * <pre>
   * usage: --trim-profile [profile file path]
   * </pre>
   */
  private void _cleanupProfile ()
  {
    if (m_aCmdArgs.size () != 1)
    {
      System.err.println ("Need to specify profile file path");
      return;
    }

    try
    {
      final File profile_path = new File (m_aCmdArgs.get (0));
      final IJson aJson = JsonReader.readFromFile (profile_path);
      if (aJson == null || !aJson.isObject ())
        throw new LangDetectException (ELangDetectErrorCode.FileLoadError, "Failed to parse JSON from " + profile_path);

      final LangProfile profile = LangProfile.createFromJson (aJson.getAsObject ());
      profile.omitLessFreq ();

      new JsonWriter ().writeToWriter (profile.getAsJson (),
                                       FileHelper.getBufferedWriter (profile_path,
                                                                     EAppend.TRUNCATE,
                                                                     StandardCharsets.UTF_8));
    }
    catch (final IOException | LangDetectException e)
    {
      e.printStackTrace ();
    }
  }

  /**
   * Language detection test for each file (--detectlang option)
   *
   * <pre>
   * usage: --detectlang -d [profile directory] -a [alpha] -s [seed] [test file(s)]
   * </pre>
   */
  private void _detectLang ()
  {
    _loadProfile ();
    for (final String filename : m_aCmdArgs)
    {
      try (final BufferedReader is = new BufferedReader (new InputStreamReader (new FileInputStream (filename),
                                                                                StandardCharsets.UTF_8)))
      {
        final Detector detector = DetectorFactory.create (_getCmdValueAsDouble ("alpha", DEFAULT_ALPHA));
        if (_hasOptWithoutValue ("--debug"))
          detector.setVerbose ();
        detector.append (is);
        System.out.println (filename + ":" + detector.getProbabilities ());
      }
      catch (final IOException | LangDetectException e)
      {
        e.printStackTrace ();
      }
    }
  }

  /**
   * Batch Test of Language Detection (--batchtest option)
   *
   * <pre>
   * usage: --batchtest -d [profile directory] -a [alpha] -s [seed] [test data(s)]
   * </pre>
   *
   * The format of test data(s):
   *
   * <pre>
   *   [correct language name]\t[text body for test]\n
   * </pre>
   */
  private void _batchTest ()
  {
    _loadProfile ();
    final Map <String, ArrayList <String>> result = new HashMap<> ();
    for (final String filename : m_aCmdArgs)
    {
      try (NonBlockingBufferedReader is = new NonBlockingBufferedReader (new InputStreamReader (new FileInputStream (filename),
                                                                                                StandardCharsets.UTF_8)))
      {
        while (is.ready ())
        {
          final String line = is.readLine ();
          final int idx = line.indexOf ('\t');
          if (idx <= 0)
            continue;
          final String correctLang = line.substring (0, idx);
          final String text = line.substring (idx + 1);

          final Detector detector = DetectorFactory.create (_getCmdValueAsDouble ("alpha", DEFAULT_ALPHA));
          detector.append (text);
          String lang = "";
          try
          {
            lang = detector.detect ();
          }
          catch (final Exception e)
          {
            e.printStackTrace ();
          }
          if (!result.containsKey (correctLang))
            result.put (correctLang, new ArrayList <String> ());
          result.get (correctLang).add (lang);
          if (_hasOptWithoutValue ("--debug"))
            System.out.println (correctLang +
                                "," +
                                lang +
                                "," +
                                (text.length () > 100 ? text.substring (0, 100) : text));
        }

      }
      catch (final IOException | LangDetectException e)
      {
        e.printStackTrace ();
      }

      final List <String> langlist = new ArrayList<> (result.keySet ());
      Collections.sort (langlist);

      int totalCount = 0, totalCorrect = 0;
      for (final String lang : langlist)
      {
        final Map <String, Integer> resultCount = new HashMap<> ();
        int count = 0;
        final ArrayList <String> list = result.get (lang);
        for (final String detectedLang : list)
        {
          ++count;
          final Integer aOld = resultCount.get (detectedLang);
          if (aOld != null)
          {
            resultCount.put (detectedLang, Integer.valueOf (aOld.intValue () + 1));
          }
          else
          {
            resultCount.put (detectedLang, Integer.valueOf (1));
          }
        }
        final Integer aCorrect = resultCount.get (lang);
        final int correct = aCorrect != null ? aCorrect.intValue () : 0;
        final double rate = correct / (double) count;
        System.out.println (String.format (lang +
                                           " (" +
                                           correct +
                                           "/" +
                                           count +
                                           "=%.2f): " +
                                           resultCount,
                                           Double.valueOf (rate)));
        totalCorrect += correct;
        totalCount += count;
      }
      System.out.println (String.format ("total: " +
                                         totalCorrect +
                                         "/" +
                                         totalCount +
                                         " = %.3f",
                                         Double.valueOf (totalCorrect / (double) totalCount)));
    }
  }

  /**
   * Command Line Interface
   *
   * @param args
   *        command line arguments
   */
  public static void main (final String [] args)
  {
    final MainCommand command = new MainCommand ();
    command._addOpt ("-d", "directory", "./");
    command._addOpt ("-a", "alpha", Double.toString (DEFAULT_ALPHA));
    command._addOpt ("-s", "seed", null);
    command._addOpt ("-l", "lang", null);
    command._addOpt ("-u", "update", null);
    command._parse (args);

    if (command._hasOptWithoutValue ("--genprofile"))
    {
      command._generateProfile ();
    }
    else
      if (command._hasOptWithoutValue ("--genprofile-text"))
      {
        command._generateProfileFromText ();
      }
      else
        if (command._hasOptWithoutValue ("--detectlang"))
        {
          command._detectLang ();
        }
        else
          if (command._hasOptWithoutValue ("--batchtest"))
          {
            command._batchTest ();
          }
          else
            if (command._hasOptWithoutValue ("--trim-profile"))
            {
              command._cleanupProfile ();
            }
            else
            {
              System.err.println ("Command missing!");
              System.err.println ("  --genprofile");
              System.err.println ("  --genprofile-text");
              System.err.println ("  --detectlang");
              System.err.println ("  --batchtest");
              System.err.println ("  --trim-profile");
            }
  }

}
