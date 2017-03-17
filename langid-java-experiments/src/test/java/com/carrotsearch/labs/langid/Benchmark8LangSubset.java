package com.carrotsearch.labs.langid;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.google.common.collect.Sets;

/**
 * Run classification quality benchmark.
 */
public class Benchmark8LangSubset
{
  public static void main (final String [] args) throws IOException
  {
    final Random rnd = new Random (0xdeadbeef);

    // Test data.
    final List <ObjectObjectCursor <String, String>> euroParl21 = EuroParlData.europarl21 ();
    Collections.shuffle (euroParl21, rnd);

    // Classifiers to test.
    @SuppressWarnings ("unchecked")
    final IClassifier <String, String> [] classifiers = new IClassifier [] { new LangIdV3ClassifierAdapter (new LangIdV3 (Model.detectOnly (Sets.newHashSet ("en",
                                                                                                                                                             "de",
                                                                                                                                                             "it",
                                                                                                                                                             "pl",
                                                                                                                                                             "pt",
                                                                                                                                                             "fr",
                                                                                                                                                             "se",
                                                                                                                                                             "no")))),
                                                                             new LangIdV3ClassifierAdapter (new LangIdV3 ()), };

    for (final IClassifier <String, String> classifier : classifiers)
    {
      System.out.println ("--> " + classifier.getName ());
      EvaluateQuality.run (euroParl21, classifier);
    }
  }
}
