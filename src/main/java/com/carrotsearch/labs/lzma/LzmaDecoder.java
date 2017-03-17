package com.carrotsearch.labs.lzma;

import java.io.IOException;

import javax.annotation.Nonnull;

public class LzmaDecoder
{
  private static class LenDecoder
  {
    private final short [] m_Choice = new short [2];
    private final LzmaBitTreeDecoder [] m_LowCoder = new LzmaBitTreeDecoder [LzmaBase.kNumPosStatesMax];
    private final LzmaBitTreeDecoder [] m_MidCoder = new LzmaBitTreeDecoder [LzmaBase.kNumPosStatesMax];
    private final LzmaBitTreeDecoder m_HighCoder = new LzmaBitTreeDecoder (LzmaBase.kNumHighLenBits);
    private int m_NumPosStates = 0;

    public void create (final int numPosStates)
    {
      for (; m_NumPosStates < numPosStates; m_NumPosStates++)
      {
        m_LowCoder[m_NumPosStates] = new LzmaBitTreeDecoder (LzmaBase.kNumLowLenBits);
        m_MidCoder[m_NumPosStates] = new LzmaBitTreeDecoder (LzmaBase.kNumMidLenBits);
      }
    }

    public void init ()
    {
      LzmaBitDecoder.initBitModels (m_Choice);
      for (int posState = 0; posState < m_NumPosStates; posState++)
      {
        m_LowCoder[posState].init ();
        m_MidCoder[posState].init ();
      }
      m_HighCoder.init ();
    }

    public int decode (final LzmaBitDecoder rangeDecoder, final int posState) throws IOException
    {
      if (rangeDecoder.decodeBit (m_Choice, 0) == 0)
        return m_LowCoder[posState].decode (rangeDecoder);
      int symbol = LzmaBase.kNumLowLenSymbols;
      if (rangeDecoder.decodeBit (m_Choice, 1) == 0)
        symbol += m_MidCoder[posState].decode (rangeDecoder);
      else
        symbol += LzmaBase.kNumMidLenSymbols + m_HighCoder.decode (rangeDecoder);
      return symbol;
    }
  }

  private static class LiteralDecoder
  {
    private static class Decoder2
    {
      private final short [] m_Decoders = new short [0x300];

      public void init ()
      {
        LzmaBitDecoder.initBitModels (m_Decoders);
      }

      public byte decodeNormal (final LzmaBitDecoder rangeDecoder) throws IOException
      {
        int symbol = 1;
        do
          symbol = (symbol << 1) | rangeDecoder.decodeBit (m_Decoders, symbol);
        while (symbol < 0x100);
        return (byte) symbol;
      }

      public byte decodeWithMatchByte (final LzmaBitDecoder rangeDecoder, byte matchByte) throws IOException
      {
        int symbol = 1;
        do
        {
          final int matchBit = (matchByte >> 7) & 1;
          matchByte <<= 1;
          final int bit = rangeDecoder.decodeBit (m_Decoders, ((1 + matchBit) << 8) + symbol);
          symbol = (symbol << 1) | bit;
          if (matchBit != bit)
          {
            while (symbol < 0x100)
              symbol = (symbol << 1) | rangeDecoder.decodeBit (m_Decoders, symbol);
            break;
          }
        } while (symbol < 0x100);
        return (byte) symbol;
      }
    }

    Decoder2 [] m_Coders;
    int m_NumPrevBits;
    int m_NumPosBits;
    int m_PosMask;

    public void Create (final int numPosBits, final int numPrevBits)
    {
      if (m_Coders != null && m_NumPrevBits == numPrevBits && m_NumPosBits == numPosBits)
        return;
      m_NumPosBits = numPosBits;
      m_PosMask = (1 << numPosBits) - 1;
      m_NumPrevBits = numPrevBits;
      final int numStates = 1 << (m_NumPrevBits + m_NumPosBits);
      m_Coders = new Decoder2 [numStates];
      for (int i = 0; i < numStates; i++)
        m_Coders[i] = new Decoder2 ();
    }

    public void init ()
    {
      final int numStates = 1 << (m_NumPrevBits + m_NumPosBits);
      for (int i = 0; i < numStates; i++)
        m_Coders[i].init ();
    }

    private Decoder2 getDecoder (final int pos, final byte prevByte)
    {
      return m_Coders[((pos & m_PosMask) << m_NumPrevBits) + ((prevByte & 0xFF) >>> (8 - m_NumPrevBits))];
    }
  }

  final LzmaOutWindow m_OutWindow = new LzmaOutWindow ();
  final LzmaBitDecoder m_RangeDecoder = new LzmaBitDecoder ();

  final short [] m_IsMatchDecoders = new short [LzmaBase.kNumStates << LzmaBase.kNumPosStatesBitsMax];
  final short [] m_IsRepDecoders = new short [LzmaBase.kNumStates];
  final short [] m_IsRepG0Decoders = new short [LzmaBase.kNumStates];
  final short [] m_IsRepG1Decoders = new short [LzmaBase.kNumStates];
  final short [] m_IsRepG2Decoders = new short [LzmaBase.kNumStates];
  final short [] m_IsRep0LongDecoders = new short [LzmaBase.kNumStates << LzmaBase.kNumPosStatesBitsMax];

  final LzmaBitTreeDecoder [] m_PosSlotDecoder = new LzmaBitTreeDecoder [LzmaBase.kNumLenToPosStates];
  final short [] m_PosDecoders = new short [LzmaBase.kNumFullDistances - LzmaBase.kEndPosModelIndex];

  final LzmaBitTreeDecoder m_PosAlignDecoder = new LzmaBitTreeDecoder (LzmaBase.kNumAlignBits);

  final LenDecoder m_LenDecoder = new LenDecoder ();
  final LenDecoder m_RepLenDecoder = new LenDecoder ();

  final LiteralDecoder m_LiteralDecoder = new LiteralDecoder ();

  int m_DictionarySize = -1;
  int m_DictionarySizeCheck = -1;

  int m_PosStateMask;

  public LzmaDecoder ()
  {
    for (int i = 0; i < LzmaBase.kNumLenToPosStates; i++)
      m_PosSlotDecoder[i] = new LzmaBitTreeDecoder (LzmaBase.kNumPosSlotBits);
  }

  private boolean _setDictionarySize (final int dictionarySize)
  {
    if (dictionarySize < 0)
      return false;
    if (m_DictionarySize != dictionarySize)
    {
      m_DictionarySize = dictionarySize;
      m_DictionarySizeCheck = Math.max (m_DictionarySize, 1);
      m_OutWindow.create (Math.max (m_DictionarySizeCheck, (1 << 12)));
    }
    return true;
  }

  private boolean _setLcLpPb (final int lc, final int lp, final int pb)
  {
    if (lc > LzmaBase.kNumLitContextBitsMax || lp > 4 || pb > LzmaBase.kNumPosStatesBitsMax)
      return false;
    m_LiteralDecoder.Create (lp, lc);
    final int numPosStates = 1 << pb;
    m_LenDecoder.create (numPosStates);
    m_RepLenDecoder.create (numPosStates);
    m_PosStateMask = numPosStates - 1;
    return true;
  }

  private void _init () throws IOException
  {
    m_OutWindow.init (false);

    LzmaBitDecoder.initBitModels (m_IsMatchDecoders);
    LzmaBitDecoder.initBitModels (m_IsRep0LongDecoders);
    LzmaBitDecoder.initBitModels (m_IsRepDecoders);
    LzmaBitDecoder.initBitModels (m_IsRepG0Decoders);
    LzmaBitDecoder.initBitModels (m_IsRepG1Decoders);
    LzmaBitDecoder.initBitModels (m_IsRepG2Decoders);
    LzmaBitDecoder.initBitModels (m_PosDecoders);

    m_LiteralDecoder.init ();
    int i;
    for (i = 0; i < LzmaBase.kNumLenToPosStates; i++)
      m_PosSlotDecoder[i].init ();
    m_LenDecoder.init ();
    m_RepLenDecoder.init ();
    m_PosAlignDecoder.init ();
    m_RangeDecoder.init ();
  }

  public boolean code (final java.io.InputStream inStream,
                       final java.io.OutputStream outStream,
                       final long outSize) throws IOException
  {
    m_RangeDecoder.setStream (inStream);
    m_OutWindow.setStream (outStream);
    _init ();

    int state = LzmaBase.stateInit ();
    int rep0 = 0, rep1 = 0, rep2 = 0, rep3 = 0;

    long nowPos64 = 0;
    byte prevByte = 0;
    while (outSize < 0 || nowPos64 < outSize)
    {
      final int posState = (int) nowPos64 & m_PosStateMask;
      if (m_RangeDecoder.decodeBit (m_IsMatchDecoders, (state << LzmaBase.kNumPosStatesBitsMax) + posState) == 0)
      {
        final LiteralDecoder.Decoder2 decoder2 = m_LiteralDecoder.getDecoder ((int) nowPos64, prevByte);
        if (!LzmaBase.stateIsCharState (state))
          prevByte = decoder2.decodeWithMatchByte (m_RangeDecoder, m_OutWindow.getByte (rep0));
        else
          prevByte = decoder2.decodeNormal (m_RangeDecoder);
        m_OutWindow.putByte (prevByte);
        state = LzmaBase.stateUpdateChar (state);
        nowPos64++;
      }
      else
      {
        int len;
        if (m_RangeDecoder.decodeBit (m_IsRepDecoders, state) == 1)
        {
          len = 0;
          if (m_RangeDecoder.decodeBit (m_IsRepG0Decoders, state) == 0)
          {
            if (m_RangeDecoder.decodeBit (m_IsRep0LongDecoders,
                                          (state << LzmaBase.kNumPosStatesBitsMax) + posState) == 0)
            {
              state = LzmaBase.stateUpdateShortRep (state);
              len = 1;
            }
          }
          else
          {
            int distance;
            if (m_RangeDecoder.decodeBit (m_IsRepG1Decoders, state) == 0)
              distance = rep1;
            else
            {
              if (m_RangeDecoder.decodeBit (m_IsRepG2Decoders, state) == 0)
                distance = rep2;
              else
              {
                distance = rep3;
                rep3 = rep2;
              }
              rep2 = rep1;
            }
            rep1 = rep0;
            rep0 = distance;
          }
          if (len == 0)
          {
            len = m_RepLenDecoder.decode (m_RangeDecoder, posState) + LzmaBase.kMatchMinLen;
            state = LzmaBase.stateUpdateRep (state);
          }
        }
        else
        {
          rep3 = rep2;
          rep2 = rep1;
          rep1 = rep0;
          len = LzmaBase.kMatchMinLen + m_LenDecoder.decode (m_RangeDecoder, posState);
          state = LzmaBase.stateUpdateMatch (state);
          final int posSlot = m_PosSlotDecoder[LzmaBase.setLenToPosState (len)].decode (m_RangeDecoder);
          if (posSlot >= LzmaBase.kStartPosModelIndex)
          {
            final int numDirectBits = (posSlot >> 1) - 1;
            rep0 = ((2 | (posSlot & 1)) << numDirectBits);
            if (posSlot < LzmaBase.kEndPosModelIndex)
              rep0 += LzmaBitTreeDecoder.reverseDecode (m_PosDecoders,
                                                        rep0 - posSlot - 1,
                                                        m_RangeDecoder,
                                                        numDirectBits);
            else
            {
              rep0 += (m_RangeDecoder.decodeDirectBits (numDirectBits -
                                                        LzmaBase.kNumAlignBits) << LzmaBase.kNumAlignBits);
              rep0 += m_PosAlignDecoder.reverseDecode (m_RangeDecoder);
              if (rep0 < 0)
              {
                if (rep0 == -1)
                  break;
                return false;
              }
            }
          }
          else
            rep0 = posSlot;
        }
        if (rep0 >= nowPos64 || rep0 >= m_DictionarySizeCheck)
        {
          // m_OutWindow.Flush();
          return false;
        }
        m_OutWindow.copyBlock (rep0, len);
        nowPos64 += len;
        prevByte = m_OutWindow.getByte (0);
      }
    }
    m_OutWindow.flush ();
    m_OutWindow.releaseStream ();
    m_RangeDecoder.releaseStream ();
    return true;
  }

  public boolean setDecoderProperties (@Nonnull final byte [] properties)
  {
    if (properties.length < 5)
      return false;
    final int val = properties[0] & 0xFF;
    final int lc = val % 9;
    final int remainder = val / 9;
    final int lp = remainder % 5;
    final int pb = remainder / 5;
    int dictionarySize = 0;
    for (int i = 0; i < 4; i++)
      dictionarySize += ((properties[1 + i]) & 0xFF) << (i * 8);
    if (!_setLcLpPb (lc, lp, pb))
      return false;
    return _setDictionarySize (dictionarySize);
  }
}
