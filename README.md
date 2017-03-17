# language-detection
[![Build Status](https://travis-ci.org/phax/language-detection.svg?branch=master)](https://travis-ci.org/phax/language-detection)

This is a language detection library implemented in plain Java (aliases: language identification, language guessing). Forked from https://github.com/kirasystems/language-detection which in turn is a fork from https://github.com/shuyo/language-detection

This version uses a Maven POM to build and contains some speed and coding styleguide improvements.

## New CLI options (January 2017)
`-u <text>` will cause the profile being learned to be added to an existing profile (should one exist). Useful for incrementally building language profiles without requiring a giant text file.

`--trim-profile <profile>` used to provide trim existing profile (filename provided) to remove any extraneous low frequency terms from the language model to improve accuracy and reduce model size. 
