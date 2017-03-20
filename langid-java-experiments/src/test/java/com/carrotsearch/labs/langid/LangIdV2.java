package com.carrotsearch.labs.langid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// TODO: sub-sampling for stable detection and quicker termination?

/**
 * An adaptation of the algorithm (and implementation) described in
 * http://www.aclweb.org/anthology-new/P/P12/P12-3005.pdf Data structures have
 * been changed to reflect Java's specific performance characteristics.
 */
public class LangIdV2
{
  private static Model defaultModel;

  public final static class Model
  {
    /**
     * Matrix [feature][lang], where index = classes * lang_index + feature
     */
    public double [] nb_ptc;
    public double [] nb_pc;
    public String [] nb_classes;
    public int [] tk_nextmove;

    // TODO: encode tk_output as a int[] vector of pointers to another int[]
    // array
    // with [length, elem, elem, elem]... This is not super-tight but will
    // decrease hashing
    // costs and will simplify deserialization.
    public int [] [] tk_output;

    public int numFeats ()
    {
      return nb_ptc.length / numClasses ();
    }

    public int numClasses ()
    {
      return nb_pc.length;
    }
  }

  // transposed model.nb_ptc so dot() has linear access pattern in memory.
  private final double [] nb_ptc_t;
  private final Model model;

  // Reusable feature vector.
  // TODO: should we replace it with double-linked counting map (faster clear()
  // and non-zero element iteration).
  private final int [] scratchFv;
  private final double [] scratchPdc;
  private final int [] scratchNonZeros;

  public LangIdV2 ()
  {
    this (defaultModel ());
  }

  public LangIdV2 (final Model model)
  {
    this.model = model;
    this.nb_ptc_t = transpose (model.nb_ptc, model.numClasses (), model.numFeats ());

    this.scratchFv = new int [model.numFeats ()];
    this.scratchNonZeros = new int [model.numFeats ()];

    this.scratchPdc = new double [model.numClasses ()];
  }

  /**
   * Return transposed matrix.
   */
  static double [] transpose (final double [] nb_ptc, final int cols, final int rows)
  {
    final double [] t = new double [nb_ptc.length];
    for (int r = 0; r < rows; r++)
    {
      for (int c = 0; c < cols; c++)
      {
        t[c * rows + r] = nb_ptc[r * cols + c];
      }
    }
    return t;
  }

  private int [] instanceToFeatureVector (final CharSequence str)
  {
    Arrays.fill (scratchFv, 0);

    // TODO: reuse a smaller buffer for decoding from a string.
    final byte [] utf8 = str.toString ().getBytes (Charset.forName ("UTF-8"));

    // Update predictions (without an intermediate statecount as in the
    // original)
    int state = 0;
    final int [] [] tk_output = model.tk_output;
    final int [] tk_nextmove = model.tk_nextmove;
    for (final byte b : utf8)
    {
      state = tk_nextmove[(state << 8) + (b & 0xff)];

      final int [] is = tk_output[state];
      if (is != null)
      {
        for (final int i : is)
        {
          scratchFv[i]++;
        }
      }
    }

    return scratchFv;
  }

  public DetectedLanguage classify (final CharSequence str, final boolean normalizeConfidence)
  {
    final int [] fv = instanceToFeatureVector (str);
    final double [] probs = naiveBayesClassprobs (fv);

    // Search for argmax(language certainty)
    int c = 0;
    double max = probs[c];
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

    return new DetectedLanguage (model.nb_classes[c], (float) max);
  }

  private double normalizeConfidenceAsProbability (final double [] nb_classprobs, final int clazzIndex)
  {
    // Renormalize log-probs into a proper distribution
    double s = 0;
    final double v = nb_classprobs[clazzIndex];
    for (final double aNb_classprob : nb_classprobs)
    {
      s += Math.exp (aNb_classprob - v);
    }
    return 1 / s;
  }

  private double [] naiveBayesClassprobs (final int [] fv)
  {
    // Compute the partial log-probability of the document given each class.
    final int classes = model.numClasses ();
    final int feats = model.numFeats ();

    final double [] pdc = this.scratchPdc;
    System.arraycopy (model.nb_pc, 0, pdc, 0, pdc.length);

    // TODO: calculate non-zero components of the fv; then iterate over these
    // for classes only.
    final int [] nonZeros = this.scratchNonZeros;
    int nz = 0;
    for (int i = 0; i < fv.length; i++)
    {
      if (fv[i] != 0)
      {
        nonZeros[nz++] = i;
      }
    }

    final double [] nb_ptc_t = this.nb_ptc_t;
    for (int i = 0, fi = 0; i < classes; i++, fi += feats)
    {
      double v = 0;
      for (int j = 0; j < nz; j++)
      {
        final int index = nonZeros[j];
        v += fv[index] * nb_ptc_t[fi + index];
      }
      pdc[i] += v;
    }

    return pdc;
  }

  public static synchronized Model defaultModel ()
  {
    if (defaultModel == null)
    {
      try
      {
        defaultModel = loadModel (LangIdV2.class.getResourceAsStream ("/langid.model.txt"));
      }
      catch (final IOException e)
      {
        throw new RuntimeException ("Default model not available.", e);
      }
    }
    return defaultModel;
  }

  private static Model loadModel (final InputStream modelData) throws IOException
  {
    final Model model = new Model ();

    final BufferedReader reader = new BufferedReader (new InputStreamReader (modelData, Charset.forName ("UTF-8")));

    String line;
    while ((line = reader.readLine ()) != null)
    {
      final int eqIndex = line.indexOf ("=");
      if (eqIndex < 0)
        continue;
      final String key = line.substring (0, eqIndex);
      final String val = line.substring (eqIndex + 1);
      if (key.equals ("nb_ptc"))
      {
        model.nb_ptc = arrayOfDoubles (val);
      }
      if (key.equals ("nb_pc"))
      {
        model.nb_pc = arrayOfDoubles (val);
      }
      if (key.equals ("nb_classes"))
      {
        model.nb_classes = val.split ("[\\,\\s]+");
      }
      if (key.equals ("tk_nextmove"))
      {
        model.tk_nextmove = arrayOfInts (val);
      }
      if (key.equals ("tk_output"))
      {
        final String [] kvPairs = val.split ("[\\;]");
        final Map <Integer, int []> tk_output = new HashMap <> ();
        for (final String aKvPair : kvPairs)
        {
          final int colonIndex = aKvPair.indexOf (":");
          final int index = Integer.parseInt (aKvPair.substring (0, colonIndex).trim ());
          final int [] vals = arrayOfInts (aKvPair.substring (colonIndex + 1).replaceAll ("[\\(\\)]", ""));
          tk_output.put (index, vals);
        }

        int maxKey = -1;
        for (final int i : tk_output.keySet ())
        {
          maxKey = Math.max (maxKey, i);
        }
        model.tk_output = new int [maxKey + 1] [];
        for (final Map.Entry <Integer, int []> e : tk_output.entrySet ())
        {
          final int [] value = e.getValue ();
          if (value.length != 0)
          {
            model.tk_output[e.getKey ()] = value;
          }
        }
      }
    }

    return model;
  }

  private static double [] arrayOfDoubles (final String val)
  {
    final String [] vals = val.split ("[\\,\\s]+");
    final double [] res = new double [vals.length];
    for (int i = 0; i < vals.length; i++)
    {
      res[i] = Double.parseDouble (vals[i]);
    }
    return res;
  }

  private static int [] arrayOfInts (final String val)
  {
    String [] vals = val.trim ().split ("[\\,\\s]+");
    if (vals.length > 0 && vals[vals.length - 1].equals (""))
    {
      vals = Arrays.copyOf (vals, vals.length - 1);
    }
    final int [] res = new int [vals.length];
    for (int i = 0; i < vals.length; i++)
    {
      try
      {
        res[i] = Integer.parseInt (vals[i]);
      }
      catch (final NumberFormatException e)
      {
        throw e;
      }
    }
    return res;
  }

  public static void main (final String [] args)
  {
    final LangIdV2 langid = new LangIdV2 ();
    langid.classify ("dawid weiss", true);

    assert langid.model.numFeats () == 7480;
  }
}
