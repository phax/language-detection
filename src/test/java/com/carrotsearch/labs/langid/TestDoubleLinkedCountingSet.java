package com.carrotsearch.labs.langid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.helger.commons.mutable.MutableInt;
import com.helger.commons.random.RandomHelper;

public class TestDoubleLinkedCountingSet
{
  @Test
  public void testSimple ()
  {
    final DoubleLinkedCountingSet s = new DoubleLinkedCountingSet (10, 5);
    assertEquals (0, s.elementsCount);
    s.increment (3);
    assertEquals (1, s.elementsCount);
    assertEquals (1, s.counts[0]);
    assertEquals (3, s.dense[0]);
    s.increment (10);
    assertEquals (2, s.elementsCount);
    assertEquals (1, s.counts[1]);
    assertEquals (10, s.dense[1]);
    s.increment (3);
    assertEquals (2, s.elementsCount);
    assertEquals (2, s.counts[0]);
  }

  @Test
  public void testRandomized ()
  {
    final int maxValue = RandomHelper.getRandom ().nextInt (1000);
    final int maxValues = RandomHelper.getRandom ().nextInt (maxValue);

    final int [] values = new int [maxValues];
    for (int i = 0; i < values.length; i++)
    {
      values[i] = RandomHelper.getRandom ().nextInt (maxValue);
    }

    final DoubleLinkedCountingSet s = new DoubleLinkedCountingSet (maxValue, maxValues);
    final Map <Integer, MutableInt> ref = new HashMap<> ();
    for (int i = 0; i < maxValues * 10; i++)
    {
      final int r = values[RandomHelper.getRandom ().nextInt (values.length - 1)];
      ref.computeIfAbsent (Integer.valueOf (r), x -> new MutableInt (0)).inc ();
      s.increment (r);
    }

    final Map <Integer, MutableInt> result = new HashMap<> ();
    for (int i = 0; i < s.elementsCount; i++)
    {
      final Integer k = Integer.valueOf (s.dense[i]);
      final int v = s.counts[i];
      assertTrue (!result.containsKey (k));
      result.put (k, new MutableInt (v));
    }

    assertEquals (ref, result);
  }
}
