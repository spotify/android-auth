package com.spotify.sdk.android.authentication;

import android.app.Activity;
import android.util.Log;

class WebViewAuthHandler implements AuthenticationHandler {

    private static String TAG = WebViewAuthHandler.class.getSimpleName();

    private LoginDialog mLoginDialog;
    private OnCompleteListener mListener;

    @Override
    public boolean start(Activity contextActivity, AuthenticationRequest request) {
        Log.d(TAG, "start");
        mLoginDialog = new LoginDialog(contextActivity, request);
        mLoginDialog.setOnCompleteListener(mListener);
        mLoginDialog.show();
        return true;
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        if (mLoginDialog != null) {
            mLoginDialog.close();
            mLoginDialog = null;
        }
    }

    @Override
    public void setOnCompleteListener(OnCompleteListener listener) {
        mListener = listener;
        if (mLoginDialog != null) {
            mLoginDialog.setOnCompleteListener(listener);
        }
    }
}