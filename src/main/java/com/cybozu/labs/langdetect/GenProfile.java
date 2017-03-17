package com.cybozu.labs.langdetect;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nonnull;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.cybozu.labs.langdetect.util.LangProfile;
import com.cybozu.labs.langdetect.util.TagExtractor;
import com.helger.commons.io.stream.NonBlockingBufferedReader;

/**
 * Load Wikipedia's abstract XML as corpus and generate its language profile in
 * JSON format.
 *
 * @author Nakatani Shuyo
 */
public class GenProfile
{
  private GenProfile ()
  {}

  @Nonnull
  private static InputStream _getIS (@Nonnull final File file) throws IOException
  {
    InputStream is = new FileInputStream (file);
    if (file.getName ().endsWith (".gz"))
      is = new GZIPInputStream (is);
    return is;
  }

  /**
   * Load Wikipedia abstract database file and generate its language profile
   *
   * @param lang
   *        target language name
   * @param file
   *        target database file path
   * @return Language profile instance
   * @throws LangDetectException
   *         in IO error etc
   */
  public static LangProfile loadFromWikipediaAbstract (final String lang, final File file) throws LangDetectException
  {
    final LangProfile profile = new LangProfile (lang);

    try (final NonBlockingBufferedReader br = new NonBlockingBufferedReader (new InputStreamReader (_getIS (file),
                                                                                                    StandardCharsets.UTF_8)))
    {
      final TagExtractor tagextractor = new TagExtractor ("abstract", 100);

      XMLStreamReader reader = null;
      try
      {
        final XMLInputFactory factory = XMLInputFactory.newInstance ();
        reader = factory.createXMLStreamReader (br);
        while (reader.hasNext ())
        {
          switch (reader.next ())
          {
            case XMLStreamConstants.START_ELEMENT:
              tagextractor.setTag (reader.getName ().toString ());
              break;
            case XMLStreamConstants.CHARACTERS:
              tagextractor.add (reader.getText ());
              break;
            case XMLStreamConstants.END_ELEMENT:
              final String text = tagextractor.closeTag ();
              if (text != null)
                profile.update (text);
              break;
          }
        }
      }
      catch (final XMLStreamException e)
      {
        throw new LangDetectException (ELangDetectErrorCode.TrainDataFormatError,
                                       "Training database file '" + file.getName () + "' is an invalid XML.");
      }
      finally
      {
        try
        {
          if (reader != null)
            reader.close ();
        }
        catch (final XMLStreamException e)
        {}
      }
      System.out.println (lang + ":" + tagextractor.count ());

    }
    catch (final IOException e)
    {
      throw new LangDetectException (ELangDetectErrorCode.CantOpenTrainData,
                                     "Can't open training database file '" + file.getName () + "'");
    }
    return profile;
  }

  /**
   * Load text file with UTF-8 and generate its language profile
   *
   * @param lang
   *        target language name
   * @param file
   *        target file path
   * @return Language profile instance
   * @throws LangDetectException
   *         in case of IO error
   */
  public static LangProfile loadFromText (final String lang, final File file) throws LangDetectException
  {
    final LangProfile profile = new LangProfile (lang);

    try (NonBlockingBufferedReader is = new NonBlockingBufferedReader (new InputStreamReader (new FileInputStream (file),
                                                                                              StandardCharsets.UTF_8)))
    {
      int count = 0;
      while (is.ready ())
      {
        final String line = is.readLine ();
        profile.update (line);
        ++count;
      }

      System.out.println (lang + ":" + count);
    }
    catch (final IOException e)
    {
      throw new LangDetectException (ELangDetectErrorCode.CantOpenTrainData,
                                     "Can't open training database file '" + file.getName () + "'");
    }
    return profile;
  }
}
