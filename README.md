lyml
====

Convert YML files from localeapp into Java, Android, iOS and Windows Phone String resources.

[![Build Status](https://travis-ci.org/bhurling/lyml.svg?branch=master)](https://travis-ci.org/bhurling/lyml)

[Localeapp](http://www.localeapp.com/) is a great tool to manage the translations for your app. Unfortunately, they only support output in YML format.

This small program fetches the translations for one or more projects via their API. It then automatically creates the corresponding resource files for Java, Android, iOS and Windows Phone.

Usage
-----

Build via gradle and run through ```java -jar```:

```
./gradlew assemble
cd build/libs && java -jar lyml.jar <api-token> [<api-token>...]
```
