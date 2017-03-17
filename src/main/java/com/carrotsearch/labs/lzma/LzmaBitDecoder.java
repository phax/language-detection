package com.carrotsearch.labs.lzma;

import java.io.IOException;
import java.io.InputStream;

final class LzmaBitDecoder
{
  private static final int kTopMask = ~((1 << 24) - 1);

  private static final int kNumBitModelTotalBits = 11;
  private static final int kBitModelTotal = (1 << kNumBitModelTotalBits);
  private static final int kNumMoveBits = 5;

  private int m_nRange;
  private int m_nCode;

  private InputStream m_aIS;

  public final void setStream (final InputStream stream)
  {
    m_aIS = stream;
  }

  public final void releaseStream ()
  {
    m_aIS = null;
  }

  public final void init () throws IOException
  {
    m_nCode = 0;
    m_nRange = -1;
    for (int i = 0; i < 5; i++)
      m_nCode = (m_nCode << 8) | m_aIS.read ();
  }

  public final int decodeDirectBits (final int numTotalBits) throws IOException
  {
    int result = 0;
    for (int i = numTotalBits; i != 0; i--)
    {
      m_nRange >>>= 1;
      final int t = ((m_nCode - m_nRange) >>> 31);
      m_nCode -= m_nRange & (t - 1);
      result = (result << 1) | (1 - t);

      if ((m_nRange & kTopMask) == 0)
      {
        m_nCode = (m_nCode << 8) | m_aIS.read ();
        m_nRange <<= 8;
      }
    }
    return result;
  }

  public int decodeBit (final short [] probs, final int index) throws IOException
  {
    final int prob = probs[index];
    final int newBound = (m_nRange >>> kNumBitModelTotalBits) * prob;
    if ((m_nCode ^ 0x80000000) < (newBound ^ 0x80000000))
    {
      m_nRange = newBound;
      probs[index] = (short) (prob + ((kBitModelTotal - prob) >>> kNumMoveBits));
      if ((m_nRange & kTopMask) == 0)
      {
        m_nCode = (m_nCode << 8) | m_aIS.read ();
        m_nRange <<= 8;
      }
      return 0;
    }

    m_nRange -= newBound;
    m_nCode -= newBound;
    probs[index] = (short) (prob - ((prob) >>> kNumMoveBits));
    if ((m_nRange & kTopMask) == 0)
    {
      m_nCode = (m_nCode << 8) | m_aIS.read ();
      m_nRange <<= 8;
    }
    return 1;
  }

  public static void initBitModels (final short [] probs)
  {
    for (int i = 0; i < probs.length; i++)
      probs[i] = (kBitModelTotal >>> 1);
  }
}
