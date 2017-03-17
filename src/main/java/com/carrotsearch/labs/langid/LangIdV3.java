package com.carrotsearch.labs.langid;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO: sub-sampling for stable detection and quicker termination?
// TODO: add a classify method operating directly on a byte[] or a byte buffer.
// TODO: add classify returning all predictions.

/**
 * Performs text language identification.
 * <p>
 * An adaptation of the algorithm (including vast chunks of the implementation)
 * described in
 * <a href="http://www.aclweb.org/anthology-new/P/P12/P12-3005.pdf">
 * http://www.aclweb.org/anthology-new/P/P12/P12-3005.pdf</a>.
 * <p>
 * Data structures and most of the code has been changed to reflect Java's
 * specific performance characteristics.
 * <p>
 * See performance notes in {@link #classify(CharSequence, boolean)}.
 * <p>
 * <strong>Thread safety:</strong> an instance of this class is <b>not</b> safe
 * for use by multiple threads at the same time. There are data buffers that are
 * reused internally (allocated statically for performance reasons). Model data
 * can be safely shared though so it's trivial to create a thread-local factory
 * of language identifiers.
 *
 * @see "https://github.com/saffsd/langid.py"
 */
public final class LangIdV3 implements ILangIdClassifier
{
  /** Data model for the classifier. */
  private final Model m_aModel;

  // Reusable feature vector.
  private final DoubleLinkedCountingSet m_aFV;

  // Scratch data.
  private final float [] m_aScratchPdc;

  // UTF16 to UTF8 encoder.
  private final CharsetEncoder m_aEncoder;

  // Scratch data.
  private final ByteBuffer m_aScratchUtf8 = ByteBuffer.allocate (1024 *
                                                                 4 /* 4 kB */);

  // Reusable rank list.
  private final List <DetectedLanguage> m_aRankList;
  private final List <DetectedLanguage> m_aRankListView;

  /**
   * Create a language identifier with the default model (full set of
   * languages).
   *
   * @see Model#detectOnly(java.util.Set)
   * @see Model#defaultModel()
   */
  public LangIdV3 ()
  {
    this (Model.defaultModel ());
  }

  /*
   * Create a language identifier with a restricted model (set of languages).
   */
  public LangIdV3 (final Model model)
  {
    this.m_aModel = model;

    this.m_aFV = new DoubleLinkedCountingSet (model.numFeatures, model.numFeatures);
    this.m_aScratchPdc = new float [model.numClasses];

    this.m_aRankList = new ArrayList<> ();
    for (final String langCode : model.m_aLangClasses)
    {
      m_aRankList.add (new DetectedLanguage (langCode, 0));
    }
    this.m_aRankListView = Collections.unmodifiableList (m_aRankList);

    this.m_aEncoder = StandardCharsets.UTF_8.newEncoder ()
                                            .onMalformedInput (CodingErrorAction.IGNORE)
                                            .onUnmappableCharacter (CodingErrorAction.IGNORE);
  }

  public Model getModel ()
  {
    return m_aModel;
  }

  /*
   *
   */
  @Override
  public DetectedLanguage classify (final CharSequence str, final boolean normalizeConfidence)
  {
    // Compute the features and apply NB
    reset ();
    append (str);
    return classify (normalizeConfidence);
  }

  /*
   *
   */
  @Override
  public void reset ()
  {
    m_aFV.clear ();
  }

  /*
   *
   */
  @Override
  public void append (final CharSequence str)
  {
    m_aEncoder.reset ();
    final CharBuffer chbuf = CharBuffer.wrap (str);
    CoderResult result;
    do
    {
      m_aScratchUtf8.clear ();
      result = m_aEncoder.encode (chbuf, m_aScratchUtf8, true);
      m_aScratchUtf8.flip ();

      append (m_aScratchUtf8);
    } while (result.isOverflow ());
  }

  /*
   *
   */
  @Override
  public void append (final ByteBuffer buffer)
  {
    // Update predictions (without an intermediate statecount as in the
    // original)
    short state = 0;
    final int [] [] tk_output = m_aModel.m_aDsaOutput;
    final short [] tk_nextmove = m_aModel.m_aDsa;

    while (buffer.hasRemaining ())
    {
      final byte b = buffer.get ();
      state = tk_nextmove[(state << 8) + (b & 0xff)];

      final int [] is = tk_output[state];
      if (is != null)
      {
        for (final int feature : is)
        {
          m_aFV.increment (feature);
        }
      }
    }
  }

  /*
   *
   */
  @Override
  public void append (final byte [] array, final int start, final int length)
  {
    // Update predictions (without an intermediate statecount as in the
    // original)
    short state = 0;
    final int [] [] tk_output = m_aModel.m_aDsaOutput;
    final short [] tk_nextmove = m_aModel.m_aDsa;

    for (int i = start, max = start + length; i < max; i++)
    {
      final byte b = array[i];
      state = tk_nextmove[(state << 8) + (b & 0xff)];

      final int [] is = tk_output[state];
      if (is != null)
      {
        for (final int feature : is)
        {
          m_aFV.increment (feature);
        }
      }
    }
  }

  /*
   *
   */
  @Override
  public DetectedLanguage classify (final boolean normalizeConfidence)
  {
    final float [] probs = naiveBayesClassConfidence ();

    // Search for argmax(language certainty)
    int c = 0;
    float max = probs[c];
    for (int i = 1; i < probs.length; i++)
    {
      if (probs[i] > max)
      {
        c = i;
        max = probs[i];
      }
    }

    if (normalizeConfidence)
    {
      max = normalizeConfidenceAsProbability (probs, c);
    }

    return new DetectedLanguage (m_aModel.m_aLangClasses[c], max);
  }

  /*
   *
   */
  @Override
  public List <DetectedLanguage> rank (final boolean normalizeConfidence)
  {
    final float [] probs = naiveBayesClassConfidence ();

    for (int c = m_aModel.numClasses; --c >= 0;)
    {
      final float confidence = normalizeConfidence ? normalizeConfidenceAsProbability (probs, c) : probs[c];

      m_aRankList.get (c).confidence = confidence;
    }

    return m_aRankListView;
  }

  /**
   * Normalize confidence to 0..1 interval.
   */
  private float normalizeConfidenceAsProbability (final float [] probs, final int clazzIndex)
  {
    // Renormalize log-probs into a proper distribution
    float s = 0;
    final float v = probs[clazzIndex];
    for (final float aProb : probs)
    {
      s += Math.exp (aProb - v);
    }
    return 1 / s;
  }

  /*
   * Compute naive bayes class confidence values.
   */
  private float [] naiveBayesClassConfidence ()
  {
    // Reuse scratch and initialize with nb_pc
    final float [] pdc = this.m_aScratchPdc;
    System.arraycopy (m_aModel.nb_pc, 0, pdc, 0, pdc.length);

    // Compute the partial log-probability of the document given each class.
    final int numClasses = m_aModel.numClasses;
    final int numFeatures = m_aModel.numFeatures;
    final int [] dense = this.m_aFV.dense;
    final int [] counts = this.m_aFV.counts;
    final int nz = this.m_aFV.elementsCount;
    final float [] nb_ptc = m_aModel.nb_ptc;
    for (int i = 0, fi = 0; i < numClasses; i++, fi += numFeatures)
    {
      float v = 0;
      for (int j = 0; j < nz; j++)
      {
        final int index = dense[j];
        v += counts[j] * nb_ptc[fi + index];
      }
      pdc[i] += v;
    }

    return pdc;
  }
}
