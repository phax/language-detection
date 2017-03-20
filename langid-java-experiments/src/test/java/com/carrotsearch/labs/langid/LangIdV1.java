package com.carrotsearch.labs.langid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// http://www.aclweb.org/anthology-new/P/P12/P12-3005.pdf

// TODO: sub-sampling for stable detection and quicker termination?

public class LangIdV1
{
  private static Model defaultModel;

  public final static class Model
  {
    /**
     * Matrix [lang][feature], where index = nb_pc.length * lang + feature
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
    public Map <Integer, int []> tk_output;

    public int numFeats ()
    {
      return nb_ptc.length / numClasses ();
    }

    public int numClasses ()
    {
      return nb_pc.length;
    }
  }

  private final Model model;

  public LangIdV1 ()
  {
    this (defaultModel ());
  }

  public LangIdV1 (final Model model)
  {
    this.model = model;
  }

  private int [] instance2fv (final CharSequence str)
  {
    // TODO: reuse this per-thread?
    final int [] arr = new int [model.numFeats ()];

    // TODO: reuse a smaller buffer for decoding from a string.
    final byte [] utf8 = str.toString ().getBytes (Charset.forName ("UTF-8"));

    // Update predictions (without an intermediate statecount as in the
    // original)
    int state = 0;
    for (final byte b : utf8)
    {
      state = model.tk_nextmove[(state << 8) + (b & 0xff)];

      final int [] is = model.tk_output.get (state);
      if (is != null)
      {
        for (final int i : is)
        {
          arr[i]++;
        }
      }
    }

    return arr;
  }

  public DetectedLanguage classify (final CharSequence str)
  {
    final int [] fv = instance2fv (str);
    double [] probs = nb_classprobs (fv);

    final boolean normProbs = true;
    if (normProbs)
    {
      probs = norm_probs (probs);
    }

    // argmax.
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

    // TODO: Do we allow some notion of minimum confidence?
    return new DetectedLanguage (model.nb_classes[c], (float) max);
  }

  private double [] norm_probs (final double [] nb_classprobs)
  {
    /*
     * Renormalize log-probs into a proper distribution (sum 1) The technique
     * for dealing with underflow is described in
     * http://jblevins.org/log/log-sum-exp Ignore overflow when computing the
     * exponential. Large values in the exp produce a result of inf, which does
     * not affect the correctness of the calculation (as 1/x->0 as x->inf). On
     * Linux this does not actually trigger a warning, but on Windows this
     * causes a RuntimeWarning, so we explicitly suppress it.
     */

    // pd = (1/np.exp(pd[None,:] - pd[:,None]).sum(1))

    // TODO: allocate these once and reuse?
    final double [] pd = new double [nb_classprobs.length];
    final double [] tmp = new double [pd.length];

    for (int i = 0; i < pd.length; i++)
    {
      final double v = nb_classprobs[i];
      for (int j = 0; j < tmp.length; j++)
      {
        tmp[j] = Math.exp (nb_classprobs[j] - v);
      }

      // Sum up.
      double s = 0;
      for (final double aElement : tmp)
      {
        s += aElement;
      }
      pd[i] = 1 / s;
    }

    System.arraycopy (pd, 0, nb_classprobs, 0, pd.length);
    return nb_classprobs;
  }

  private double [] nb_classprobs (final int [] fv)
  {
    // Compute the partial log-probability of the document given each class.
    final int classes = model.numClasses ();

    // TODO: reuse this?
    final double [] pdc = new double [classes];

    // pdc = np.dot(fv,model.nb_ptc)
    for (int i = 0; i < classes; i++)
    {
      double v = 0;
      for (int j = 0; j < fv.length; j++)
      {
        // TODO: transpose nb_ptc to gain linear access on dot product.
        v += fv[j] * model.nb_ptc[i + j * classes];
      }
      pdc[i] += v;
    }

    // compute the partial log-probability of the document in each class
    // pdc + self.nb_pc;
    for (int i = 0; i < model.nb_pc.length; i++)
    {
      pdc[i] += model.nb_pc[i];
    }

    return pdc;
  }

  public static synchronized Model defaultModel ()
  {
    if (defaultModel == null)
    {
      try
      {
        defaultModel = loadModel (LangIdV1.class.getResourceAsStream ("/langid.model.txt"));
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
        model.tk_output = tk_output;
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
    final LangIdV1 langid = new LangIdV1 ();
    langid.classify ("dawid weiss");

    assert langid.model.numFeats () == 7480;
  }
}
