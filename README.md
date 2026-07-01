# Spotify Auth Library

[![Maven Central](https://img.shields.io/maven-central/v/com.spotify.android/auth.svg)](https://search.maven.org/search?q=g:com.spotify.android)

# Breaking changes in Spotify Auth library version 5.0.0

The `RedirectUriReceiverActivity` intent-filter is no longer provided by the library. You must declare it in your app's `AndroidManifest.xml` with your redirect URI values:

```xml
<activity
    android:name="com.spotify.sdk.android.auth.browser.RedirectUriReceiverActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data
            android:scheme="your-redirect-scheme"
            android:host="your-redirect-host"
            android:pathPattern="your-path-pattern"/>
    </intent-filter>
</activity>
```

If you were previously using `manifestPlaceholders` for `redirectSchemeName`, `redirectHostName`, and `redirectPathPattern`, replace them with the values directly in the manifest. The `manifestPlaceholders` configuration in `build.gradle` is no longer needed.

If your redirect URI uses `https://`, add `android:autoVerify="true"` to the `<intent-filter>` to enable [Android App Links](https://developer.android.com/training/app-links) verification.

If your app is missing this intent-filter, the library will crash at runtime with an `IllegalStateException` when the auth flow starts.

# Breaking changes in Spotify Auth library version 2.0.0

In this version we replaced use of WebView with [Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs/) since Google and Facebook Login no longer support WebViews for authenticating users.

As part of this change the library API does not contain `AuthorizationClient#clearCookies` method anymore. Custom Tabs use the cookies from the browser.

# Integrating the library into your project

To add this library to your project add following dependency to your app `build.gradle` file:

```gradle
implementation "com.spotify.android:auth:<version>"
```

Since April 2021 we're publishing the library on MavenCentral instead of JCenter. Therefore to be able to get the library dependency, you should add MavenCentral into repositories block:
```gradle
repositories {
    mavenCentral()
    ...
}
```

You must also declare the `RedirectUriReceiverActivity` intent-filter in your app's `AndroidManifest.xml` (see [breaking changes in version 5.0.0](#breaking-changes-in-spotify-auth-library-version-500) above).

To learn more see the [Authentication Guide](https://developer.spotify.com/technologies/spotify-android-sdk/android-sdk-authentication-guide/)
and the [API reference](https://spotify.github.io/android-auth/auth-lib/docs/index.html).

# Flavors

Since Spotify Auth library version 2.1.0, two versions of the library are provided that differs in
their behaviour if the Spotify application cannot be used to login:
* `auth` - Opens the web browser to login to Spotify
* `store` - Redirects to the Android Play store to download the Spotify application

# Documentation

Complete API documentation is available for both library flavors:
* [Auth Flavor Javadoc](https://spotify.github.io/android-auth/auth-lib/docs/index.html) - Opens web browser for login
* [Store Flavor Javadoc](https://spotify.github.io/android-auth/auth-lib/docs-store/index.html) - Play Store fallback variant

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

