package com.spotify.sdk.android.auth

/*
 * Constants below have their counterparts in Spotify app where
 * they're used to parse messages received from SDK.
 * If any of these values needs to be changed, a new protocol version needs to be created on both
 * sides (auth-lib and Spotify app) and bumped accordingly.
 */
object IntentExtras {
    const val KEY_CLIENT_ID = "CLIENT_ID"
    const val KEY_REQUESTED_SCOPES = "SCOPES"
    const val KEY_STATE = "STATE"
    const val KEY_UTM_SOURCE = "UTM_SOURCE"
    const val KEY_UTM_MEDIUM = "UTM_MEDIUM"
    const val KEY_UTM_CAMPAIGN = "UTM_CAMPAIGN"
    const val KEY_REDIRECT_URI = "REDIRECT_URI"
    const val KEY_RESPONSE_TYPE = "RESPONSE_TYPE"
    const val KEY_ACCESS_TOKEN = "ACCESS_TOKEN"
    const val KEY_AUTHORIZATION_CODE = "AUTHORIZATION_CODE"
    const val KEY_EXPIRES_IN = "EXPIRES_IN"
    const val KEY_CODE_CHALLENGE = "CODE_CHALLENGE"
    const val KEY_CODE_CHALLENGE_METHOD = "CODE_CHALLENGE_METHOD"
    /*
     * This is used to pass information about the protocol version
     * to the AuthorizationActivity.
     * DO NOT CHANGE THIS.
     */
    const val KEY_VERSION = "VERSION"
}
