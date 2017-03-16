package com.cybozu.labs.langdetect;

/**
 * @author Nakatani Shuyo
 */
public enum ELangDetectErrorCode
{
  NoTextError,
  FormatError,
  FileLoadError,
  DuplicateLangError,
  NeedLoadProfileError,
  CantDetectError,
  CantOpenTrainData,
  TrainDataFormatError,
  InitParamError
}
