Spotify Authentication Library
------------------------------

This library is responsible for authenticating the user and fetching the access token
that can subsequently be used to play music or be used in requests to the Spotify Web API.

To add this library to your project copy the `spotify-android-auth-*.aar` file from the
[Android SDK repo](https://github.com/spotify/android-sdk) to the `libs`
folder in your app project and add the reference to its `build.gradle` file.
For version `1.0.0` it would be:

```
compile 'com.spotify.sdk:spotify-android-auth-1.0.0.aar@aar'
```

To learn more about working with authentication see the
[Authentication Guide](https://developer.spotify.com/technologies/spotify-android-sdk/android-sdk-authentication-guide/)
and the [API reference](https://developer.spotify.com/android-sdk-docs/authentication) on the developer site.