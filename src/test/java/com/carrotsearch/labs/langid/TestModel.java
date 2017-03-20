package com.carrotsearch.labs.langid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Set;

import org.junit.Test;

import com.carrotsearch.labs.langid.DetectedLanguage;
import com.carrotsearch.labs.langid.LangIdV3;
import com.carrotsearch.labs.langid.Model;
import com.helger.commons.collection.ext.CommonsHashSet;

public class TestModel
{
  @Test
  public void testModelCopy ()
  {
    final Model d = Model.defaultModel ();
    final Model n = Model.detectOnly (d.getDetectedLanguages ());

    assertArrayEquals (n.m_aLangClasses, d.m_aLangClasses);
    assertArrayEquals (n.nb_pc, d.nb_pc, 0.0f);
    assertArrayEquals (n.nb_ptc, d.nb_ptc, 0.0f);
    assertEquals (n.numClasses, d.numClasses);
    assertEquals (n.numFeatures, d.numFeatures);
    assertArrayEquals (n.m_aDsa, d.m_aDsa);

    assertEquals (n.m_aDsaOutput.length, d.m_aDsaOutput.length);
    for (int i = 0; i < n.m_aDsaOutput.length; i++)
    {
      assertArrayEquals (n.m_aDsaOutput[i], d.m_aDsaOutput[i]);
    }
  }

  @Test
  public void testSameResultWithTrimmedLanguages ()
  {
    final Set <String> allowed = new CommonsHashSet<> ("en", "de", "es", "fr", "it", "pl");
    final LangIdV3 v1 = new LangIdV3 ();
    final LangIdV3 v2 = new LangIdV3 (Model.detectOnly (allowed));

    for (int i = 0; i < 10000; i++)
    {
      final String in = TestLangIdV3.ofCodeUnitsLength (1, 300);
      final DetectedLanguage c1 = v1.classify (in, true);
      final DetectedLanguage c2 = v2.classify (in, true);

      if (allowed.contains (c1.m_sLangCode))
      {
        assertEquals (c1.m_sLangCode, c2.m_sLangCode);
      }
    }
  }
}
