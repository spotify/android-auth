package com.spotify.sdk.android.authentication;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;

public class SpotifyAuthActivity {

    /*
     * This is used to pass information about the protocol version
     * to the AuthorizationActivity.
     * DO NOT CHANGE THIS.
     */
    private static final String EXTRA_VERSION = "VERSION";

    /*
     * Constants below have their counterparts in Spotify app where
     * they're used to parse messages received from SDK. 
     * If anything changes in the structure of those messages this version
     * should be bumped and new protocol version should be implemented on the other side. 
     */
    private static final int PROTOCOL_VERSION = 1;

    // PROTOCOL BEGIN

    static final String EXTRA_REPLY = "REPLY";
    static final String EXTRA_ERROR = "ERROR";

    static final String KEY_CLIENT_ID = "CLIENT_ID";
    static final String KEY_REQUESTED_SCOPES = "SCOPES";
    static final String KEY_REDIRECT_URI = "REDIRECT_URI";
    static final String KEY_RESPONSE_TYPE = "RESPONSE_TYPE";
    static final String KEY_ACCESS_TOKEN = "ACCESS_TOKEN";
    static final String KEY_AUTHORIZATION_CODE = "AUTHORIZATION_CODE";
    static final String KEY_EXPIRES_IN = "EXPIRES_IN";

    static final String RESPONSE_TYPE_TOKEN = "token";
    static final String RESPONSE_TYPE_CODE = "code";

    // PROTOCOL END

    private static final String SPOTIFY_AUTH_ACTIVITY_ACTION = "com.spotify.sso.action.START_AUTH_FLOW";
    private static final String SPOTIFY_PACKAGE_NAME = "com.spotify.music";
    private static final String[] SPOTIFY_PACKAGE_SUFFIXES = new String[]{
            ".debug",
            ".canary",
            ".partners",
            ""
    };


    private Activity mContextActivity;
    private AuthenticationRequest mRequest;

    public SpotifyAuthActivity(Activity contextActivity, AuthenticationRequest request) {
        mContextActivity = contextActivity;
        mRequest = request;
    }

    public boolean startAuthActivity() {
        Intent intent = createAuthActivityIntent();
        if (intent == null) {
            return false;
        }
        intent.putExtra(EXTRA_VERSION, PROTOCOL_VERSION);

        intent.putExtra(KEY_CLIENT_ID, mRequest.getClientId());
        intent.putExtra(KEY_REDIRECT_URI, mRequest.getRedirectUri());
        intent.putExtra(KEY_RESPONSE_TYPE, mRequest.getResponseType());
        intent.putExtra(KEY_REQUESTED_SCOPES, mRequest.getScopes());

        try {
            mContextActivity.startActivityForResult(intent, LoginActivity.REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            return false;
        }
        return true;
    }

    private Intent createAuthActivityIntent() {
        Intent intent = null;
        for (String suffix : SPOTIFY_PACKAGE_SUFFIXES) {
            intent = tryResolveActivity(SPOTIFY_PACKAGE_NAME + suffix);
            if (intent != null) {
                break;
            }
        }
        return intent;
    }

    private Intent tryResolveActivity(String packageName) {

        Intent intent = new Intent(SPOTIFY_AUTH_ACTIVITY_ACTION);
        intent.setPackage(packageName);

        ComponentName componentName = intent.resolveActivity(mContextActivity.getPackageManager());

        if (componentName == null) {
            return null;
        }
        return intent;
    }

    public void stopAuthActivity() {
        mContextActivity.finishActivity(LoginActivity.REQUEST_CODE);
    }
}
