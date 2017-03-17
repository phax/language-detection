package com.carrotsearch.labs.lzma;

import java.io.IOException;

class LzmaBitTreeDecoder
{
  private final short [] m_aModels;
  private final int m_nNumBitLevels;

  public LzmaBitTreeDecoder (final int numBitLevels)
  {
    m_nNumBitLevels = numBitLevels;
    m_aModels = new short [1 << numBitLevels];
  }

  public void init ()
  {
    LzmaBitDecoder.initBitModels (m_aModels);
  }

  public int decode (final LzmaBitDecoder rangeDecoder) throws IOException
  {
    int m = 1;
    for (int bitIndex = m_nNumBitLevels; bitIndex != 0; bitIndex--)
      m = (m << 1) + rangeDecoder.decodeBit (m_aModels, m);
    return m - (1 << m_nNumBitLevels);
  }

  public int reverseDecode (final LzmaBitDecoder rangeDecoder) throws IOException
  {
    // [ph]
    if (true)
      return reverseDecode (m_aModels, 0, rangeDecoder, m_nNumBitLevels);

    int m = 1;
    int symbol = 0;
    for (int bitIndex = 0; bitIndex < m_nNumBitLevels; bitIndex++)
    {
      final int bit = rangeDecoder.decodeBit (m_aModels, m);
      m <<= 1;
      m += bit;
      symbol |= (bit << bitIndex);
    }
    return symbol;
  }

  public static int reverseDecode (final short [] Models,
                                   final int startIndex,
                                   final LzmaBitDecoder rangeDecoder,
                                   final int NumBitLevels) throws IOException
  {
    int m = 1;
    int symbol = 0;
    for (int bitIndex = 0; bitIndex < NumBitLevels; bitIndex++)
    {
      final int bit = rangeDecoder.decodeBit (Models, startIndex + m);
      m <<= 1;
      m += bit;
      symbol |= (bit << bitIndex);
    }
    return symbol;
  }
}
