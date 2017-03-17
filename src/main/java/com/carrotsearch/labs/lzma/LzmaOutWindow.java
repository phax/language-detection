// LZ.OutWindow

package com.carrotsearch.labs.lzma;

import java.io.IOException;
import java.io.OutputStream;

final class LzmaOutWindow
{
  private byte [] m_aBuffer;
  private int m_nPos;
  private int m_nWindowSize = 0;
  private int m_nStreamPos;
  private OutputStream m_aOS;

  public void create (final int windowSize)
  {
    if (m_aBuffer == null || m_nWindowSize != windowSize)
      m_aBuffer = new byte [windowSize];
    m_nWindowSize = windowSize;
    m_nPos = 0;
    m_nStreamPos = 0;
  }

  public void setStream (final OutputStream stream) throws IOException
  {
    releaseStream ();
    m_aOS = stream;
  }

  public void releaseStream () throws IOException
  {
    flush ();
    m_aOS = null;
  }

  public void init (final boolean solid)
  {
    if (!solid)
    {
      m_nStreamPos = 0;
      m_nPos = 0;
    }
  }

  public void flush () throws IOException
  {
    final int size = m_nPos - m_nStreamPos;
    if (size == 0)
      return;
    m_aOS.write (m_aBuffer, m_nStreamPos, size);
    if (m_nPos >= m_nWindowSize)
      m_nPos = 0;
    m_nStreamPos = m_nPos;
  }

  public void copyBlock (final int distance, final int nLen) throws IOException
  {
    int pos = m_nPos - distance - 1;
    if (pos < 0)
      pos += m_nWindowSize;
    for (int len = nLen; len != 0; len--)
    {
      if (pos >= m_nWindowSize)
        pos = 0;
      m_aBuffer[m_nPos++] = m_aBuffer[pos++];
      if (m_nPos >= m_nWindowSize)
        flush ();
    }
  }

  public void putByte (final byte b) throws IOException
  {
    m_aBuffer[m_nPos++] = b;
    if (m_nPos >= m_nWindowSize)
      flush ();
  }

  public byte getByte (final int distance)
  {
    int pos = m_nPos - distance - 1;
    if (pos < 0)
      pos += m_nWindowSize;
    return m_aBuffer[pos];
  }
}
