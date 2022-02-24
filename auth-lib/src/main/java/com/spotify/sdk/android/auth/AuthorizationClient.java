/*
 * Copyright (c) 2015-2016 Spotify AB
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.sdk.android.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.spotify.sdk.android.auth.app.SpotifyAuthHandler;
import com.spotify.sdk.android.auth.browser.BrowserAuthHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * AuthorizationClient provides helper methods to initialize an manage the Spotify authorization flow.
 *
 * <p>
 * This client provides two versions of authorization:
 * <ol>
 * <li><h3>Single Sign-On using Spotify Android application with a fallback to <a href="https://accounts.spotify.com">Spotify Accounts Service</a> in a WebView</h3>
 *
 * <p>SDK will try to fetch the authorization code/access token using the Spotify Android client.
 * If Spotify is not installed on the device, SDK will fallback to the WebView based authorization
 * and open <a href="https://accounts.spotify.com">Spotify Accounts Service</a> in a dialog.
 * After authorization flow is completed, result is returned to the activity
 * that invoked the {@code AuthorizationClient}.</p>
 *
 * <p>If Spotify is installed on the device, SDK will connect to the Spotify client and
 * try to fetch the authorization code/access token for current user.
 * Since the user is already logged into Spotify they don't need to fill their username and password.
 * If the SDK application requests scopes that have not been approved, the user will see
 * a list of scopes and can choose to approve or reject them.</p>
 *
 * <p>If Spotify is not installed on the device, SDK will open a dialog and load Spotify Accounts Service
 * into a WebView. User will have to enter their username and password to login to Spotify.
 * They will also need to approve any scopes the the SDK application requests and that they
 * haven't approved before.</p>
 *
 * <p>In both cases (SSO and WebView fallback) the result of the authorization flow will be returned
 * in the {@code onActivityResult} method of the activity that initiated it.</p>
 *
 * <p>
 * For login flow to work, LoginActivity needs to be added to the {@code AndroidManifest.xml}:
 *
 * <pre>{@code
 * <activity
 *         android:name="LoginActivity"
 *         android:theme="@android:style/Theme.Translucent.NoTitleBar" />
 * }</pre>
 *
 * <pre>{@code
 * // Code called from an activity
 * private static final int REQUEST_CODE = 1337;
 *
 * final AuthorizationRequest request = new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
 *     .setScopes(new String[]{"user-read-private", "playlist-read", "playlist-read-private", "streaming"})
 *     .build();
 *
 * AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);
 * }</pre>
 *
 * It is also possible to use {@code LoginActivity} from other component such as Fragments:
 * <pre>{@code
 * // To start LoginActivity from a Fragment:
 * Intent intent = AuthorizationClient.createLoginActivityIntent(getActivity(), request);
 * startActivityForResult(intent, REQUEST_CODE);
 *
 * // To close LoginActivity
 * AuthorizationClient.stopLoginActivity(getActivity(), REQUEST_CODE);
 * }</pre>
 * <p>
 * To process the result activity needs to override {@code onActivityResult} callback
 *
 * <pre>{@code
 * protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
 *     super.onActivityResult(requestCode, resultCode, intent);
 *
 *     // Check if result comes from the correct activity
 *     if (requestCode == REQUEST_CODE) {
 *         AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);
 *         switch (response.getType()) {
 *             // Response was successful and contains auth token
 *             case TOKEN:
 *                 // Handle successful response
 *                 String token = response.getAccessToken();
 *                 break;
 *
 *            // Auth flow returned an error
 *            case ERROR:
 *                 // Handle error response
 *                 break;
 *
 *            // Most likely auth flow was cancelled
 *            default:
 *                // Handle other cases
 *         }
 *     }
 * }
 * }</pre>
 * </li>
 * <li>
 * <h3>Opening <a href="https://accounts.spotify.com">Spotify Accounts Service</a> in a web browser</h3>
 * <p>
 * In this scenario the SDK creates an intent that will open the browser. Authorization
 * takes part in the browser (not in the SDK application). After authorization is completed
 * browser redirects back to the SDK app.
 *
 * <pre>{@code
 * // Code called from an activity
 * final AuthorizationRequest request = new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
 *     .setScopes(new String[]{"user-read-private", "playlist-read", "playlist-read-private", "streaming"})
 *     .build();
 *
 * AuthorizationClient.openLoginInBrowser(this, request);
 * }</pre>
 * <p>
 * To receive the result {@code AndroidManifest.xml} must contain following:
 *
 * <pre>{@code
 * // The activity that should process the response from auth flow
 * <activity
 *     android:name=".MainActivity"
 *     android:label="@string/app_name"
 *     android:launchMode="singleInstance" >
 *     <intent-filter>
 *         // Any other intent filters that this activity requires
 *     </intent-filter>
 *
 *     // An intent filter that will receive the response from the authorization service
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW"/>
 *
 *         <category android:name="android.intent.category.DEFAULT"/>
 *         <category android:name="android.intent.category.BROWSABLE"/>
 *
 *         // this needs to match the scheme and host of the redirect uri
 *         <data
 *             android:host="callback"
 *             android:scheme="yourcustomprotocol"/>
 *    </intent-filter>
 * </activity>
 * }</pre>
 * <p>
 * To process the result the receiving activity ({@code MainActivity} in this example) needs to override one of its
 * callbacks. With launch mode set to {@code singleInstance} this callback is {@code onNewIntent}:
 *
 * <pre><code>
 * protected void onNewIntent(Intent intent) {
 *     super.onNewIntent(intent);
 *     Uri uri = intent.getData();
 *     if (uri != null) {
 *         AuthorizationResponse response = AuthorizationResponse.fromUri(uri);
 *         switch (response.getType()) {
 *             // Response was successful and contains auth token
 *             case TOKEN:
 *                 // Handle successful response
 *                 String token = response.getAccessToken();
 *                 break;
 *
 *            // Auth flow returned an error
 *            case ERROR:
 *                 // Handle error response
 *                 break;
 *
 *            // Most likely auth flow was cancelled
 *            default:
 *                // Handle other cases
 *         }
 *     }
 * }
 * </code></pre>
 * </li>
 * </ol>
 *
 * @see <a href="https://developer.spotify.com/web-api/authorization-guide">Web API Authorization guide</a>
 */
public class AuthorizationClient {
    private static final String TAG = "Spotify Auth Client";

    static final String MARKET_VIEW_PATH = "market://";
    static final String MARKET_SCHEME = "market";
    static final String MARKET_PATH = "details";

    static final String PLAY_STORE_SCHEME = "https";
    static final String PLAY_STORE_AUTHORITY = "play.google.com";
    static final String PLAY_STORE_PATH = "store/apps/details";

    static final String SPOTIFY_ID = "com.spotify.music";
    static final String SPOTIFY_SDK = "spotify-sdk";
    static final String ANDROID_SDK = "android-sdk";
    static final String DEFAULT_CAMPAIGN = "android-sdk";

    static final class PlayStoreParams {
        public static final String ID = "id";
        public static final String REFERRER = "referrer";
        public static final String UTM_SOURCE = "utm_source";
        public static final String UTM_MEDIUM = "utm_medium";
        public static final String UTM_CAMPAIGN = "utm_campaign";
    }

    /**
     * The activity that receives and processes the result of authorization flow
     * and returns it to the context activity that invoked the flow.
     * An instance of {@link LoginActivity}
     */
    private final Activity mLoginActivity;
    private boolean mAuthorizationPending;

    /**
     * A handler that performs authorization.
     * It is created with {@code mLoginActivity} as a context.
     * This activity will receive the result through the
     * {@link AuthorizationClientListener}
     */
    private AuthorizationHandler mCurrentHandler;

    private List<AuthorizationHandler> mAuthorizationHandlers = new ArrayList<>();

    private AuthorizationClientListener mAuthorizationClientListener;

    interface AuthorizationClientListener {

        /**
         * Auth flow was completed.
         * The response can be successful and contain access token or authorization code.
         * The response can be an error response and contain error message.
         * It can also be an empty response which indicated that the
         * user cancelled authorization flow.
         *
         * @param response Response containing a result of authorization flow.
         */
        void onClientComplete(AuthorizationResponse response);

        /**
         * Auth flow was cancelled before it could be completed.
         * This callbacks indicates that the auth flow was interrupted
         * for example because of underlying LoginActivity was paused or stopped.
         * This is different from the situation when user completes the flow
         * by closing LoginActivity (e.g. by pressing the back button).
         */
        void onClientCancelled();
    }

    /**
     * Triggers an intent to open the Spotify accounts service in a browser. Make sure that the
     * redirectUri is set to an URI your app is registered for in your AndroidManifest.xml. To
     * get your clientId and to set the redirectUri, please see the
     * <a href="https://developer.spotify.com/my-applications">my applications</a>
     * part of our developer site.
     *
     * @param contextActivity The activity that should start the intent to open a browser.
     * @param request         Authorization request
     */
    public static void openLoginInBrowser(Activity contextActivity, AuthorizationRequest request) {
        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, request.toUri());
        contextActivity.startActivity(launchBrowser);
    }

    /**
     * Get an intent to open the LoginActivity.
     * This method can be used to open this activity from components different than
     * activities; for example Fragments.
     * <pre>{@code
     * // To start LoginActivity from a Fragment:
     * Intent intent = AuthorizationClient.createLoginActivityIntent(getActivity(), request);
     * startActivityForResult(intent, REQUEST_CODE);
     *
     * // To close LoginActivity
     * AuthorizationClient.stopLoginActivity(getActivity(), REQUEST_CODE);
     * }</pre>
     *
     * @param contextActivity A context activity for the LoginActivity.
     * @param request         Authorization request
     * @return The intent to open LoginActivity with.
     * @throws IllegalArgumentException if any of the arguments is null
     */
    public static Intent createLoginActivityIntent(Activity contextActivity, AuthorizationRequest request) {
        Intent intent = LoginActivity.getAuthIntent(contextActivity, request);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /**
     * Opens LoginActivity which performs authorization.
     * The result of the authorization flow will be received by the
     * {@code contextActivity} in the {@code onActivityResult} callback.
     * The successful result of the authorization flow will contain an access token that can be used
     * to make calls to the Web API and/or to play music with Spotify.
     *
     * @param contextActivity A context activity for the LoginActivity.
     * @param requestCode     Request code for LoginActivity.
     * @param request         Authorization request
     * @throws IllegalArgumentException if any of the arguments is null
     */
    public static void openLoginActivity(Activity contextActivity, int requestCode, AuthorizationRequest request) {
        Intent intent = createLoginActivityIntent(contextActivity, request);
        contextActivity.startActivityForResult(intent, requestCode);
    }

    /**
     * Stops any running LoginActivity
     *
     * @param contextActivity The activity that was used to launch LoginActivity
     *                        with {@link #openLoginActivity(android.app.Activity, int, AuthorizationRequest)}
     * @param requestCode     Request code that was used to launch LoginActivity
     */
    public static void stopLoginActivity(Activity contextActivity, int requestCode) {
        contextActivity.finishActivity(requestCode);
    }

    /**
     * Extracts {@link AuthorizationResponse}
     * from the LoginActivity result.
     *
     * @param resultCode Result code returned with the activity result.
     * @param intent     Intent received with activity result. Should contain a Uri with result data.
     * @return response object.
     */
    public static AuthorizationResponse getResponse(int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK && LoginActivity.getResponseFromIntent(intent) != null) {
            return LoginActivity.getResponseFromIntent(intent);
        } else {
            return new AuthorizationResponse.Builder()
                    .setType(AuthorizationResponse.Type.EMPTY)
                    .build();
        }
    }

    /**
     * Opens Spotify in the Play Store or browser.
     *
     * @param contextActivity The activity that should start the intent to open the download page.
     */
    public static void openDownloadSpotifyActivity(Activity contextActivity) {
        openDownloadSpotifyActivity(contextActivity, DEFAULT_CAMPAIGN);
    }

    /**
     * Opens Spotify in the Play Store or browser.
     *
     * @param contextActivity The activity that should start the intent to open the download page.
     * @param campaign A Spotify-provided campaign ID. <code>null</code> if not provided.
     */
    public static void openDownloadSpotifyActivity(Activity contextActivity, String campaign) {

        Uri.Builder uriBuilder = new Uri.Builder();

        if (isAvailable(contextActivity, new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_VIEW_PATH)))) {
            uriBuilder.scheme(MARKET_SCHEME)
                    .appendPath(MARKET_PATH);
        } else {
            uriBuilder.scheme(PLAY_STORE_SCHEME)
                    .authority(PLAY_STORE_AUTHORITY)
                    .appendEncodedPath(PLAY_STORE_PATH);
        }

        uriBuilder.appendQueryParameter(PlayStoreParams.ID, SPOTIFY_ID);

        Uri.Builder referrerBuilder = new Uri.Builder();
        referrerBuilder.appendQueryParameter(PlayStoreParams.UTM_SOURCE, SPOTIFY_SDK)
                .appendQueryParameter(PlayStoreParams.UTM_MEDIUM, ANDROID_SDK);

        if (TextUtils.isEmpty(campaign)) {
            referrerBuilder.appendQueryParameter(PlayStoreParams.UTM_CAMPAIGN, DEFAULT_CAMPAIGN);
        } else {
            referrerBuilder.appendQueryParameter(PlayStoreParams.UTM_CAMPAIGN, campaign);
        }

        uriBuilder.appendQueryParameter(PlayStoreParams.REFERRER, referrerBuilder.build().getEncodedQuery());

        contextActivity.startActivity(new Intent(Intent.ACTION_VIEW, uriBuilder.build()));
    }

    public static boolean isAvailable(Context ctx, Intent intent) {
        final PackageManager mgr = ctx.getPackageManager();
        List<ResolveInfo> list =
                mgr.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public AuthorizationClient(Activity activity) {
        mLoginActivity = activity;

        mAuthorizationHandlers.add(new SpotifyAuthHandler());
        mAuthorizationHandlers.add(new BrowserAuthHandler());
    }

    /**
     * This listener will be used when authorization flow will return a result.
     *
     * @param listener The listener to be notified when authorization flow completes.
     */
    void setOnCompleteListener(AuthorizationClientListener listener) {
        mAuthorizationClientListener = listener;
    }

    /**
     * Performs authorization.
     * First it will try to bind spotify auth service, if this is not possible
     * it will fallback to showing accounts page in the webview.
     *
     * @param request Authorization request
     */
    void authorize(AuthorizationRequest request) {
        if (mAuthorizationPending) return;
        mAuthorizationPending = true;
        for (AuthorizationHandler authHandler : mAuthorizationHandlers) {
            if (tryAuthorizationHandler(authHandler, request)) {
                mCurrentHandler = authHandler;
                break;
            }
        }
    }

    /**
     * Authorization process was interrupted.
     * This can happen when auth flow is not completed
     * but was cancelled e.g. when underlying LoginActivity
     * was paused or stopped.
     */
    void cancel() {
        if (!mAuthorizationPending) {
            return;
        }

        mAuthorizationPending = false;
        closeAuthorizationHandler(mCurrentHandler);

        if (mAuthorizationClientListener != null) {
            mAuthorizationClientListener.onClientCancelled();
            mAuthorizationClientListener = null;
        }
    }

    /**
     * Authorization returned a result.
     * The result doesn't have to contain a response uri
     * e.g when back button was pressed.
     *
     * @param response The uri returned by auth flow.
     */
    void complete(AuthorizationResponse response) {
        sendComplete(mCurrentHandler, response);
    }

    private void sendComplete(AuthorizationHandler authHandler, AuthorizationResponse response) {
        mAuthorizationPending = false;
        closeAuthorizationHandler(authHandler);

        if (mAuthorizationClientListener != null) {
            mAuthorizationClientListener.onClientComplete(response);
            mAuthorizationClientListener = null;
        } else {
            Log.w(TAG, "Can't deliver the Spotify Auth response. The listener is null");
        }
    }

    private boolean tryAuthorizationHandler(final AuthorizationHandler authHandler, AuthorizationRequest request) {
        authHandler.setOnCompleteListener(new AuthorizationHandler.OnCompleteListener() {
            @Override
            public void onComplete(AuthorizationResponse response) {
                Log.i(TAG, String.format("Spotify auth response:%s", response.getType().name()));
                sendComplete(authHandler, response);
            }

            @Override
            public void onCancel() {
                Log.i(TAG, "Spotify auth response: User cancelled");
                AuthorizationResponse response = new AuthorizationResponse.Builder()
                        .setType(AuthorizationResponse.Type.EMPTY)
                        .build();

                sendComplete(authHandler, response);
            }

            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Spotify auth Error", error);
                AuthorizationResponse response = new AuthorizationResponse.Builder()
                        .setType(AuthorizationResponse.Type.ERROR)
                        .setError(error.getMessage())
                        .build();

                sendComplete(authHandler, response);
            }
        });

        if (!authHandler.start(mLoginActivity, request)) {
            closeAuthorizationHandler(authHandler);
            return false;
        }
        return true;
    }

    private void closeAuthorizationHandler(AuthorizationHandler authHandler) {
        if (authHandler != null) {
            authHandler.setOnCompleteListener(null);
            authHandler.stop();
        }
    }
}
