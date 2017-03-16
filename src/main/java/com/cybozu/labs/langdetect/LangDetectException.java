package com.cybozu.labs.langdetect;

/**
 * @author Nakatani Shuyo
 */
public class LangDetectException extends Exception
{
  private final ELangDetectErrorCode m_eCode;

  /**
   * @param code
   *        error code
   * @param message
   *        message text
   */
  public LangDetectException (final ELangDetectErrorCode code, final String message)
  {
    super (message);
    m_eCode = code;
  }

  /**
   * @return the error code
   */
  public ELangDetectErrorCode getCode ()
  {
    return m_eCode;
  }
}
