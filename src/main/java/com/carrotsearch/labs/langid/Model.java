package com.carrotsearch.labs.langid;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.carrotsearch.labs.lzma.LzmaDecoder;

/**
 * Data model for {@link LangIdV3}.
 *
 * @see #defaultModel()
 */
public final class Model
{
  /** The default model, initialized lazily (once). */
  private static Model defaultModel;

  /**
   * Language classes.
   */
  final String [] m_aLangClasses;

  /**
   * Flattened matrix of per-language feature probabilities.
   *
   * <pre>
   * [featureIndex][langIndex]
   * where
   * index = {@link #numClasses} * langIndex + featureIndex
   * </pre>
   */
  final float [] nb_ptc;

  /**
   * Conditional init per-language probabilities (?).
   */
  final float [] nb_pc;

  /**
   * State machine for walking byte n-grams.
   */
  final short [] m_aDsa;

  /**
   * An output (may be null) associated with each state.
   */
  final int [] [] m_aDsaOutput;

  /** Number of classes (languages). */
  final int numClasses;

  /** Number of features (total). */
  final int numFeatures;

  /*
   * Create a new model.
   */
  Model (final String [] langClasses,
         final float [] ptc,
         final float [] pc,
         final short [] dsa,
         final int [] [] dsaOutput)
  {
    this.m_aLangClasses = langClasses;
    this.nb_ptc = ptc;
    this.nb_pc = pc;
    this.m_aDsa = dsa;
    this.m_aDsaOutput = dsaOutput;

    assert nb_pc.length == langClasses.length;
    this.numClasses = langClasses.length;
    this.numFeatures = nb_ptc.length / numClasses;
  }

  /*
   * Read a model from an external data stream.
   */
  public static Model readExternal (final ObjectInput in) throws IOException, ClassNotFoundException
  {
    final String [] langClasses = (String []) in.readObject ();
    final float [] nb_ptc = (float []) in.readObject ();
    final float [] nb_pc = (float []) in.readObject ();
    final short [] dsa = (short []) in.readObject ();
    final int [] [] dsaOutput = (int [] []) in.readObject ();
    return new Model (langClasses, nb_ptc, nb_pc, dsa, dsaOutput);
  }

  void writeExternal (final ObjectOutput out) throws IOException
  {
    out.writeObject (m_aLangClasses);
    out.writeObject (nb_ptc);
    out.writeObject (nb_pc);
    out.writeObject (m_aDsa);
    out.writeObject (m_aDsaOutput);
  }

  /*
   * Return a copy of this model trimmed to detect only a subset of languages.
   */
  public static Model detectOnly (final Set <String> langCodes)
  {
    final Model source = defaultModel ();

    final Set <String> newClasses = new LinkedHashSet<> (Arrays.asList (source.m_aLangClasses));
    newClasses.retainAll (langCodes);
    if (newClasses.size () < 2)
    {
      throw new IllegalArgumentException ("A model must contain at least two languages.");
    }

    // Limit the set of supported languages (fewer languages = tighter loops and
    // faster execution).
    final String [] trimmed_nb_classes = newClasses.toArray (new String [newClasses.size ()]);
    final float [] trimmed_nb_pc = new float [newClasses.size ()];
    final float [] trimmed_nb_ptc = new float [newClasses.size () * source.numFeatures];
    for (int i = 0, j = 0; i < source.numClasses; i++)
    {
      if (newClasses.contains (source.m_aLangClasses[i]))
      {
        trimmed_nb_pc[j] = source.nb_pc[i];
        for (int f = 0; f < source.numFeatures; f++)
        {
          final int iFrom = source.numFeatures * i + f;
          final int iTo = source.numFeatures * j + f;
          trimmed_nb_ptc[iTo] = source.nb_ptc[iFrom];
        }
        j++;
      }
    }

    return new Model (trimmed_nb_classes, trimmed_nb_ptc, trimmed_nb_pc, source.m_aDsa, source.m_aDsaOutput);
  }

  /**
   * @return a set of detected languages.
   */
  public Set <String> getDetectedLanguages ()
  {
    return Collections.unmodifiableSet (new LinkedHashSet<> (Arrays.asList (m_aLangClasses)));
  }

  /**
   * @return the default model with a full set of detected languages.
   */
  public static synchronized Model defaultModel ()
  {
    if (defaultModel != null)
    {
      return defaultModel;
    }

    DataInputStream is = null;
    try
    {
      final ByteArrayOutputStream os = new ByteArrayOutputStream ();
      is = new DataInputStream (new BufferedInputStream (Model.class.getResourceAsStream ("langid.lzma")));

      final byte [] streamProperties = new byte [5];
      is.readFully (streamProperties);

      final LzmaDecoder decoder = new LzmaDecoder ();
      if (!decoder.setDecoderProperties (streamProperties))
        throw new IOException ("Incorrect stream properties.");

      final byte [] streamSize = new byte [8];
      is.readFully (streamSize);

      long streamSizeLong = 0;
      for (int i = 8; --i >= 0;)
      {
        streamSizeLong <<= 8;
        streamSizeLong |= streamSize[i] & 0xFF;
      }

      if (!decoder.code (is, os, streamSizeLong))
      {
        throw new IOException ("Error in data stream");
      }

      os.flush ();

      return Model.readExternal (new ObjectInputStream (new ByteArrayInputStream (os.toByteArray ())));
    }
    catch (final Exception e)
    {
      throw new RuntimeException ("Default model not available.", e);
    }
    finally
    {
      if (is != null)
      {
        try
        {
          is.close ();
        }
        catch (final IOException e)
        {
          // Ignore, nothing to do.
        }
      }
    }
  }
}
