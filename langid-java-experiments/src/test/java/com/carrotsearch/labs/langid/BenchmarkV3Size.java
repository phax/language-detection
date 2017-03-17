package com.carrotsearch.labs.langid;

import com.carrotsearch.sizeof.RamUsageEstimator;

public class BenchmarkV3Size
{
  public static void main (final String [] args)
  {
    final long s = System.currentTimeMillis ();
    final LangIdV3 classifier = new LangIdV3 ();
    final long e = System.currentTimeMillis ();
    System.out.println ("Load time: " + (e - s) / 1000.0d);
    System.out.println ("Total: " + RamUsageEstimator.humanSizeOf (classifier));
    System.out.println ("m: " + RamUsageEstimator.humanSizeOf (classifier.getModel ()));
    System.out.println ("m.nb_pc: " + RamUsageEstimator.humanSizeOf (classifier.getModel ().nb_pc));
    System.out.println ("m.nb_ptc: " + RamUsageEstimator.humanSizeOf (classifier.getModel ().nb_ptc));
    System.out.println ("m.tk_nextmove: " + RamUsageEstimator.humanSizeOf (classifier.getModel ().m_aDsa));
    System.out.println ("m.tk_output: " + RamUsageEstimator.humanSizeOf (classifier.getModel ().m_aDsaOutput));
  }
}
