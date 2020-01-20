package com.spotify.sdk.android.auth;

/*
 * Constants below have their counterparts in Spotify app where
 * they're used to parse messages received from SDK.
 * If any of these values needs to be changed, a new protocol version needs to be created on both
 * sides (auth-lib and Spotify app) and bumped accordingly.
 */
public interface IntentExtras {
    String KEY_CLIENT_ID = "CLIENT_ID";
    String KEY_REQUESTED_SCOPES = "SCOPES";
    String KEY_STATE = "STATE";
    String KEY_REDIRECT_URI = "REDIRECT_URI";
    String KEY_RESPONSE_TYPE = "RESPONSE_TYPE";
    String KEY_ACCESS_TOKEN = "ACCESS_TOKEN";
    String KEY_AUTHORIZATION_CODE = "AUTHORIZATION_CODE";
    String KEY_EXPIRES_IN = "EXPIRES_IN";
    /*
     * This is used to pass information about the protocol version
     * to the AuthorizationActivity.
     * DO NOT CHANGE THIS.
     */
    String KEY_VERSION = "VERSION";
}
