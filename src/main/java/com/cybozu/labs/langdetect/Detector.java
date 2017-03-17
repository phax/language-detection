package com.cybozu.labs.langdetect;

import java.io.IOException;
import java.io.Reader;
import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import com.cybozu.labs.langdetect.util.NGram;

/**
 * {@link Detector} class is to detect language from specified text. Its
 * instance is able to be constructed via the factory class
 * {@link DetectorFactory}.
 * <p>
 * After appending a target text to the {@link Detector} instance with
 * {@link #append(Reader)} or {@link #append(String)}, the detector provides the
 * language detection results for target text via {@link #detect()} or
 * {@link #getProbabilities()}. {@link #detect()} method returns a single
 * language name which has the highest probability. {@link #getProbabilities()}
 * methods returns a list of multiple languages and their probabilities.
 * <p>
 * The detector has some parameters for language detection. See
 * {@link #setAlpha(double)}, {@link #setMaxTextLength(int)} and
 * {@link #setPriorMap(Map)}.
 *
 * <pre>
 * import java.util.ArrayList;
 * import com.cybozu.labs.langdetect.Detector;
 * import com.cybozu.labs.langdetect.DetectorFactory;
 * import com.cybozu.labs.langdetect.Language;
 *
 * class LangDetectSample
 * {
 *   public void init (String profileDirectory) throws LangDetectException
 *   {
 *     DetectorFactory.loadProfile (profileDirectory);
 *   }
 *
 *   public String detect (String text) throws LangDetectException
 *   {
 *     Detector detector = DetectorFactory.create ();
 *     detector.append (text);
 *     return detector.detect ();
 *   }
 *
 *   public List <Language> detectLangs (String text) throws LangDetectException
 *   {
 *     Detector detector = DetectorFactory.create ();
 *     detector.append (text);
 *     return detector.getProbabilities ();
 *   }
 * }
 * </pre>
 * <ul>
 * <li>4x faster improvement based on Elmer Garduno's code. Thanks!</li>
 * </ul>
 *
 * @author Nakatani Shuyo
 * @see DetectorFactory
 */
public class Detector
{
  private static final double ALPHA_DEFAULT = 0.5;
  private static final double ALPHA_WIDTH = 0.05;

  private static final int ITERATION_LIMIT = 1000;
  private static final double PROB_THRESHOLD = 0.1;
  private static final double CONV_THRESHOLD = 0.99999;
  private static final int BASE_FREQ = 10000;
  private static final String UNKNOWN_LANG = "unknown";

  private static final Pattern URL_REGEX = Pattern.compile ("https?://[-_.?&~;+=/#0-9A-Za-z]{1,2076}");
  private static final Pattern MAIL_REGEX = Pattern.compile ("[-_.0-9A-Za-z]{1,64}@[-_0-9A-Za-z]{1,255}[-_.0-9A-Za-z]{1,255}");

  private final Map <String, double []> m_aWordLangProbMap;
  private final List <String> m_aLanglist;

  private StringBuilder m_aText = new StringBuilder ();
  private double [] m_aLangProb;

  private double m_dAlpha = ALPHA_DEFAULT;
  private int m_nNTrial = 7;
  private int m_nMaxTextLength = 10000;
  private double [] m_aPriorMap;
  private boolean m_bVerbose = false;
  private final Long m_aSeed;

  /**
   * Constructor. Detector instance can be constructed via
   * {@link DetectorFactory#create()}.
   *
   * @param aFactory
   *        {@link DetectorFactory} instance (only DetectorFactory inside)
   */
  public Detector (@Nonnull final DetectorFactory aFactory)
  {
    m_aWordLangProbMap = aFactory.getWordLangProbMap ();
    m_aLanglist = DetectorFactory.getLangList ();
    m_aSeed = aFactory.getSeed ();
  }

  /**
   * Set Verbose Mode(use for debug).
   */
  public void setVerbose ()
  {
    m_bVerbose = true;
  }

  /**
   * Set smoothing parameter. The default value is 0.5(i.e. Expected Likelihood
   * Estimate).
   *
   * @param alpha
   *        the smoothing parameter
   */
  public void setAlpha (final double alpha)
  {
    m_dAlpha = alpha;
  }

  /**
   * Set number of trials variable
   *
   * @param n_trial
   *        number of trials
   */
  public void setTrials (final int n_trial)
  {
    m_nNTrial = n_trial;
  }

  /**
   * Set prior information about language probabilities.
   *
   * @param priorMap
   *        the priorMap to set
   * @throws LangDetectException
   *         in case of negative priority
   */
  public void setPriorMap (final Map <String, Double> priorMap) throws LangDetectException
  {
    m_aPriorMap = new double [m_aLanglist.size ()];
    double sump = 0;
    for (int i = 0; i < m_aPriorMap.length; ++i)
    {
      final String lang = m_aLanglist.get (i);
      final Double aP = priorMap.get (lang);
      if (aP != null)
      {
        final double p = aP.doubleValue ();
        if (p < 0)
          throw new LangDetectException (ELangDetectErrorCode.InitParamError,
                                         "Prior probability must be non-negative.");
        m_aPriorMap[i] = p;
        sump += p;
      }
    }
    if (sump <= 0)
      throw new LangDetectException (ELangDetectErrorCode.InitParamError,
                                     "More one of prior probability must be non-zero.");
    for (int i = 0; i < m_aPriorMap.length; ++i)
      m_aPriorMap[i] /= sump;
  }

  /**
   * Specify max size of target text to use for language detection. The default
   * value is 10000(10KB).
   *
   * @param max_text_length
   *        the max_text_length to set
   */
  public void setMaxTextLength (final int max_text_length)
  {
    m_nMaxTextLength = max_text_length;
  }

  /**
   * Append the target text for language detection. This method read the text
   * from specified input reader. If the total size of target text exceeds the
   * limit size specified by {@link Detector#setMaxTextLength(int)}, the rest is
   * cut down.
   *
   * @param reader
   *        the input reader (BufferedReader as usual)
   * @throws IOException
   *         Can't read the reader.
   */
  public void append (final Reader reader) throws IOException
  {
    final char [] buf = new char [m_nMaxTextLength / 2];
    while (m_aText.length () < m_nMaxTextLength && reader.ready ())
    {
      final int length = reader.read (buf);
      append (new String (buf, 0, length));
    }
  }

  /**
   * Append the target text for language detection. If the total size of target
   * text exceeds the limit size specified by
   * {@link Detector#setMaxTextLength(int)}, the rest is cut down.
   *
   * @param sText
   *        the target text to append
   */
  public void append (final String sText)
  {
    String text = URL_REGEX.matcher (sText).replaceAll (" ");
    text = MAIL_REGEX.matcher (text).replaceAll (" ");
    text = NGram.normalize_vi (text);
    char pre = 0;
    for (int i = 0; i < text.length () && i < m_nMaxTextLength; ++i)
    {
      final char c = text.charAt (i);
      if (c != ' ' || pre != ' ')
        m_aText.append (c);
      pre = c;
    }
  }

  /**
   * Cleaning text to detect (eliminate URL, e-mail address and Latin sentence
   * if it is not written in Latin alphabet)
   */
  private void _cleaningText ()
  {
    int latinCount = 0, nonLatinCount = 0;
    for (int i = 0; i < m_aText.length (); ++i)
    {
      final char c = m_aText.charAt (i);
      if (c <= 'z' && c >= 'A')
      {
        ++latinCount;
      }
      else
        if (c >= '\u0300' && UnicodeBlock.of (c) != UnicodeBlock.LATIN_EXTENDED_ADDITIONAL)
        {
          ++nonLatinCount;
        }
    }
    if (latinCount * 2 < nonLatinCount)
    {
      final StringBuilder textWithoutLatin = new StringBuilder ();
      for (int i = 0; i < m_aText.length (); ++i)
      {
        final char c = m_aText.charAt (i);
        if (c > 'z' || c < 'A')
          textWithoutLatin.append (c);
      }
      m_aText = textWithoutLatin;
    }

  }

  /**
   * Detect language of the target text and return the language name which has
   * the highest probability.
   *
   * @return detected language name which has most probability.
   * @throws LangDetectException
   *         code = ErrorCode.CantDetectError : Can't detect because of no valid
   *         features in text
   */
  public String detect () throws LangDetectException
  {
    final List <Language> probabilities = getProbabilities ();
    if (probabilities.size () > 0)
      return probabilities.get (0).getLanguage ();
    return UNKNOWN_LANG;
  }

  /**
   * Get language candidates which have high probabilities
   *
   * @return possible languages list (whose probabilities are over
   *         PROB_THRESHOLD, ordered by probabilities descendently
   * @throws LangDetectException
   *         code = ErrorCode.CantDetectError : Can't detect because of no valid
   *         features in text
   */
  public List <Language> getProbabilities () throws LangDetectException
  {
    if (m_aLangProb == null)
      _detectBlock ();

    final List <Language> list = _sortProbability (m_aLangProb);
    return list;
  }

  /**
   * @throws LangDetectException
   */
  private void _detectBlock () throws LangDetectException
  {
    _cleaningText ();
    final List <String> ngrams = _extractNGrams ();
    if (ngrams.size () == 0)
      throw new LangDetectException (ELangDetectErrorCode.CantDetectError, "no features in text");

    m_aLangProb = new double [m_aLanglist.size ()];

    final Random rand = new Random ();
    if (m_aSeed != null)
      rand.setSeed (m_aSeed.longValue ());
    for (int t = 0; t < m_nNTrial; ++t)
    {
      final double [] prob = _initProbability ();
      final double alpha = m_dAlpha + rand.nextGaussian () * ALPHA_WIDTH;

      for (int i = 0;; ++i)
      {
        final int r = rand.nextInt (ngrams.size ());
        _updateLangProb (prob, ngrams.get (r), alpha);
        if (i % 5 == 0)
        {
          if (_normalizeProb (prob) > CONV_THRESHOLD || i >= ITERATION_LIMIT)
            break;
          if (m_bVerbose)
            System.out.println ("> " + _sortProbability (prob));
        }
      }
      for (int j = 0; j < m_aLangProb.length; ++j)
        m_aLangProb[j] += prob[j] / m_nNTrial;
      if (m_bVerbose)
        System.out.println ("==> " + _sortProbability (prob));
    }
  }

  /**
   * Initialize the map of language probabilities. If there is the specified
   * prior map, use it as initial map.
   *
   * @return initialized map of language probabilities
   */
  private double [] _initProbability ()
  {
    final double [] prob = new double [m_aLanglist.size ()];
    if (m_aPriorMap != null)
    {
      for (int i = 0; i < prob.length; ++i)
        prob[i] = m_aPriorMap[i];
    }
    else
    {
      for (int i = 0; i < prob.length; ++i)
        prob[i] = 1.0 / m_aLanglist.size ();
    }
    return prob;
  }

  /**
   * Extract n-grams from target text
   *
   * @return n-grams list
   */
  private List <String> _extractNGrams ()
  {
    final List <String> list = new ArrayList<> ();
    final NGram ngram = new NGram ();
    for (int i = 0; i < m_aText.length (); ++i)
    {
      ngram.addChar (m_aText.charAt (i));
      for (int n = 1; n <= NGram.N_GRAM; ++n)
      {
        final String w = ngram.get (n);
        if (w != null && m_aWordLangProbMap.containsKey (w))
          list.add (w);
      }
    }
    return list;
  }

  /**
   * update language probabilities with N-gram string(N=1,2,3)
   *
   * @param word
   *        N-gram string
   */
  private boolean _updateLangProb (final double [] prob, final String word, final double alpha)
  {
    if (word == null || !m_aWordLangProbMap.containsKey (word))
      return false;

    final double [] langProbMap = m_aWordLangProbMap.get (word);
    if (m_bVerbose)
      System.out.println (word + "(" + _unicodeEncode (word) + "):" + _wordProbToString (langProbMap));

    final double weight = alpha / BASE_FREQ;
    for (int i = 0; i < prob.length; ++i)
    {
      prob[i] *= weight + langProbMap[i];
    }
    return true;
  }

  private String _wordProbToString (final double [] prob)
  {
    try (final Formatter formatter = new Formatter ())
    {
      for (int j = 0; j < prob.length; ++j)
      {
        final double p = prob[j];
        if (p >= 0.00001)
          formatter.format (" %s:%.5f", m_aLanglist.get (j), Double.valueOf (p));
      }
      return formatter.toString ();
    }
  }

  /**
   * normalize probabilities and check convergence by the maximum probability
   *
   * @return maximum of probabilities
   */
  static private double _normalizeProb (final double [] prob)
  {
    double maxp = 0, sump = 0;
    for (final double aElement : prob)
      sump += aElement;
    for (int i = 0; i < prob.length; ++i)
    {
      final double p = prob[i] / sump;
      if (maxp < p)
        maxp = p;
      prob[i] = p;
    }
    return maxp;
  }

  /**
   * @param prob
   *        HashMap
   * @return lanugage candidates order by probabilities descendently
   */
  private List <Language> _sortProbability (final double [] prob)
  {
    final List <Language> list = new ArrayList<> ();
    for (int j = 0; j < prob.length; ++j)
    {
      final double p = prob[j];
      if (p > PROB_THRESHOLD)
      {
        for (int i = 0; i <= list.size (); ++i)
        {
          if (i == list.size () || list.get (i).getProbability () < p)
          {
            list.add (i, new Language (m_aLanglist.get (j), p));
            break;
          }
        }
      }
    }
    return list;
  }

  /**
   * unicode encoding (for verbose mode)
   *
   * @param word
   * @return
   */
  private static String _unicodeEncode (final String word)
  {
    final StringBuilder buf = new StringBuilder (word.length ());
    for (final char ch : word.toCharArray ())
    {
      if (ch >= '\u0080')
      {
        String st = Integer.toHexString (0x10000 + ch);
        while (st.length () < 4)
          st = "0" + st;
        buf.append ("\\u").append (st.subSequence (1, 5));
      }
      else
      {
        buf.append (ch);
      }
    }
    return buf.toString ();
  }

}
