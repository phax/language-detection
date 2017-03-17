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
public class Command
{
  /** smoothing default parameter (ELE) */
  private static final double DEFAULT_ALPHA = 0.5;

  /** for Command line easy parser */
  private final Map <String, String> opt_with_value = new HashMap<> ();
  private final Map <String, String> values = new HashMap<> ();
  private final Set <String> opt_without_value = new HashSet<> ();
  private final List <String> arglist = new ArrayList<> ();

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
      if (opt_with_value.containsKey (args[i]))
      {
        final String key = opt_with_value.get (args[i]);
        values.put (key, args[i + 1]);
        ++i;
      }
      else
        if (args[i].startsWith ("-"))
        {
          opt_without_value.add (args[i]);
        }
        else
        {
          arglist.add (args[i]);
        }
    }
  }

  private void _addOpt (final String opt, final String key, final String value)
  {
    opt_with_value.put (opt, key);
    values.put (key, value);
  }

  private String _getString (final String key)
  {
    return values.get (key);
  }

  private Long _getLong (final String key)
  {
    final String value = _getString (key);
    if (value == null)
      return null;
    try
    {
      return Long.valueOf (value);
    }
    catch (final NumberFormatException e)
    {
      return null;
    }
  }

  private double _getDouble (final String key, final double defaultValue)
  {
    try
    {
      return Double.parseDouble (_getString (key));
    }
    catch (final NumberFormatException e)
    {
      return defaultValue;
    }
  }

  private boolean _hasOpt (final String opt)
  {
    return opt_without_value.contains (opt);
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
   *
   * @return false if load success
   */
  private boolean _loadProfile ()
  {
    final String profileDirectory = _getString ("directory") + "/";
    try
    {
      DetectorFactory.loadProfile (profileDirectory);
      final Long seed = _getLong ("seed");
      if (seed != null)
        DetectorFactory.setSeed (seed.longValue ());
      return false;
    }
    catch (final LangDetectException e)
    {
      System.err.println ("ERROR: " + e.getMessage ());
      return true;
    }
  }

  /**
   * Generate Language Profile from Wikipedia Abstract Database File
   *
   * <pre>
   * usage: --genprofile -d [abstracts directory] [language names]
   * </pre>
   */
  public void generateProfile ()
  {
    final File directory = new File (_getString ("directory"));
    for (final String lang : arglist)
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
        if (_getString ("update") != null)
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

        final File profile_path = new File (_getString ("directory") + "/profiles/" + lang);
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
    if (arglist.size () != 1)
    {
      System.err.println ("Need to specify text file path");
      return;
    }
    final File file = new File (arglist.get (0));
    if (!file.exists ())
    {
      System.err.println ("Need to specify existing text file path");
      return;
    }

    final String lang = _getString ("lang");
    if (lang == null)
    {
      System.err.println ("Need to specify langage code(-l)");
      return;
    }

    try
    {
      final LangProfile profile = GenProfile.loadFromText (lang, file);
      final File profile_path = new File (lang);
      if (_getString ("update") != null)
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
    if (arglist.size () != 1)
    {
      System.err.println ("Need to specify profile file path");
      return;
    }

    try
    {
      final File profile_path = new File (arglist.get (0));
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
  public void detectLang ()
  {
    if (_loadProfile ())
      return;
    for (final String filename : arglist)
    {
      try (final BufferedReader is = new BufferedReader (new InputStreamReader (new FileInputStream (filename),
                                                                                StandardCharsets.UTF_8)))
      {
        final Detector detector = DetectorFactory.create (_getDouble ("alpha", DEFAULT_ALPHA));
        if (_hasOpt ("--debug"))
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
  public void batchTest ()
  {
    if (_loadProfile ())
      return;
    final HashMap <String, ArrayList <String>> result = new HashMap<> ();
    for (final String filename : arglist)
    {
      try (BufferedReader is = new BufferedReader (new InputStreamReader (new FileInputStream (filename),
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

          final Detector detector = DetectorFactory.create (_getDouble ("alpha", DEFAULT_ALPHA));
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
          if (_hasOpt ("--debug"))
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
    final Command command = new Command ();
    command._addOpt ("-d", "directory", "./");
    command._addOpt ("-a", "alpha", "" + DEFAULT_ALPHA);
    command._addOpt ("-s", "seed", null);
    command._addOpt ("-l", "lang", null);
    command._addOpt ("-u", "update", null);
    command._parse (args);

    if (command._hasOpt ("--genprofile"))
    {
      command.generateProfile ();
    }
    else
      if (command._hasOpt ("--genprofile-text"))
      {
        command._generateProfileFromText ();
      }
      else
        if (command._hasOpt ("--detectlang"))
        {
          command.detectLang ();
        }
        else
          if (command._hasOpt ("--batchtest"))
          {
            command.batchTest ();
          }
          else
            if (command._hasOpt ("--trim-profile"))
            {
              command._cleanupProfile ();
            }
  }

}
