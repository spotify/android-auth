package com.spotify.sdk.android.authentication;

import android.app.Activity;

public interface AuthenticationHandler {

    interface OnCompleteListener {
        void onComplete(AuthenticationResponse response);

        void onCancel();

        void onError(Throwable error);

    }

    boolean start(Activity contextActivity, AuthenticationRequest request);

    void stop();

    void setOnCompleteListener(OnCompleteListener listener);
}
