# Spotify Auth Library

[![Maven Central](https://img.shields.io/maven-central/v/com.spotify.android/auth.svg)](https://search.maven.org/search?q=g:com.spotify.android)

# This repository is now a part of [spotify/android-sdk](https://github.com/spotify/android-sdk). Please post new issues there!

This library is responsible for fetching the authorization code/access token
that can subsequently be used to play music or in requests to the [Spotify Web API](https://developer.spotify.com/web-api/).

# Integrating the library into your project

To add this library to your project add the reference to its `build.gradle` file:

```gradle
implementation "com.spotify.android:auth:<version>"
```

Since April 2021 we'll be publishing the library on MavenCentral instead of JCenter. Therefore to be able to get the library dependency, you should add MavenCentral into repositories block:
```gradle
repositories {
    mavenCentral()
    ...
}
```

To learn more see the [Authentication Guide](https://developer.spotify.com/technologies/spotify-android-sdk/android-sdk-authentication-guide/)
and the [API reference](https://spotify.github.io/android-sdk/auth-lib/docs/index.html).

The following entries are merged into your manifest when you add the library:

```xml
<uses-permission android:name="android.permission.INTERNET"/>

<activity
    android:name="com.spotify.sdk.android.auth.LoginActivity"
    android:theme="@android:style/Theme.Translucent.NoTitleBar">
</activity>
```

# Sample Code

Checkout [the sample project](auth-sample).

# Contributing

You are welcome to contribute to this project. Please make sure that:
* New code is test covered
* Features and APIs are well documented
* `./gradlew check` must succeed

## Code of conduct
This project adheres to the [Open Code of Conduct][code-of-conduct]. By participating, you are expected to honor this code.

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md

