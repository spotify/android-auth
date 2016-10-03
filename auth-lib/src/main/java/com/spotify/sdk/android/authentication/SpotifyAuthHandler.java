package com.spotify.sdk.android.authentication;

import android.app.Activity;
import android.util.Log;

class SpotifyAuthHandler implements AuthenticationHandler {

    private static String TAG = SpotifyAuthHandler.class.getSimpleName();

    private SpotifyAuthActivity mSpotifyAuthActivity;

    @Override
    public boolean start(Activity contextActivity, AuthenticationRequest request) {
        Log.d(TAG, "start");
        mSpotifyAuthActivity = new SpotifyAuthActivity(contextActivity, request);
        return mSpotifyAuthActivity.startAuthActivity();
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        if (mSpotifyAuthActivity != null) {
            mSpotifyAuthActivity.stopAuthActivity();
        }
    }

    @Override
    public void setOnCompleteListener(OnCompleteListener listener) {
        // no-op
    }
}