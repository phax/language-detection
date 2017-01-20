# language-detection
[![Build Status](https://travis-ci.org/kirasystems/language-detection.svg?branch=master)](https://travis-ci.org/kirasystems/aging-session)

This is a language detection library implemented in plain Java. (aliases: language identification, language guessing)

[![Clojars Project](http://clojars.org/kirasystems/langdetect/latest-version.svg)](http://clojars.org/kirasystems/langdetect)

## New CLI options (January 2017)
`-u <text>` will cause the profile being learned to be added to an existing profile (should one exist). Useful for incrementally building language profiles without requiring a giant text file.

`--trim-profile <profile>` used to provide trim existing profile (filename provided) to remove any extraneous low frequency terms from the language model to improve accuracy and reduce model size. 
