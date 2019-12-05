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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * The activity that manages the login flow.
 * It should not be started directly. Instead use
 * {@link AuthorizationClient#openLoginActivity(android.app.Activity, int, AuthorizationRequest)}
 */
public class LoginActivity extends Activity implements AuthorizationClient.AuthorizationClientListener {

    private static final String TAG = LoginActivity.class.getName();
    private static final String NO_CALLER_ERROR = "Can't use LoginActivity with a null caller. " +
            "Possible reasons: calling activity has a singleInstance mode " +
            "or LoginActivity is in a singleInstance/singleTask mode";

    private static final String NO_REQUEST_ERROR = "No authorization request";

    static final String EXTRA_AUTH_REQUEST = "EXTRA_AUTH_REQUEST";
    static final String EXTRA_AUTH_RESPONSE = "EXTRA_AUTH_RESPONSE";
    public static final String REQUEST_KEY = "request";
    public static final String RESPONSE_KEY = "response";

    private AuthorizationClient mAuthorizationClient = new AuthorizationClient(this);
    private AuthorizationRequest mRequest;

    public static final int REQUEST_CODE = 1138;

    private static final int RESULT_ERROR = -2;
    private boolean mBackgrounded;


    public static Intent getAuthIntent(Activity contextActivity, AuthorizationRequest request) {
        if (contextActivity == null || request == null) {
            throw new IllegalArgumentException("Context activity or request can't be null");
        }

        // Put request into a bundle to work around classloader problems on Samsung devices
        // https://stackoverflow.com/questions/28589509/android-e-parcel-class-not-found-when-unmarshalling-only-on-samsung-tab3
        Bundle bundle = new Bundle();
        bundle.putParcelable(REQUEST_KEY, request);

        Intent intent = new Intent(contextActivity, LoginActivity.class);
        intent.putExtra(EXTRA_AUTH_REQUEST, bundle);

        return intent;
    }

    static AuthorizationResponse getResponseFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }

        Bundle responseBundle = intent.getBundleExtra(EXTRA_AUTH_RESPONSE);
        if (responseBundle == null) {
            return null;
        }

        return responseBundle.getParcelable(RESPONSE_KEY);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        mAuthorizationClient.complete(AuthorizationResponse.fromUri(data));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.com_spotify_sdk_login_activity);

        mRequest = getRequestFromIntent();

        mAuthorizationClient.setOnCompleteListener(this);

        if (getCallingActivity() == null) {
            Log.e(TAG, NO_CALLER_ERROR);
            finish();
        } else if (mRequest == null) {
            Log.e(TAG, NO_REQUEST_ERROR);
            setResult(Activity.RESULT_CANCELED);
            finish();
        } else {
            Log.d(TAG, mRequest.toUri().toString());
            mAuthorizationClient.authorize(mRequest);
        }
    }

    private AuthorizationRequest getRequestFromIntent() {
        Bundle requestBundle = getIntent().getBundleExtra(EXTRA_AUTH_REQUEST);
        if (requestBundle == null) {
            return null;
        }
        return requestBundle.getParcelable(REQUEST_KEY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // User got back to the activity from the auth flow
        if (mBackgrounded) {
            mBackgrounded = false;
            onClientCancelled();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Activity is not in foreground anymore, auth flow took over
        mBackgrounded = true;
    }

    @Override
    protected void onDestroy() {
        mAuthorizationClient.cancel();
        mAuthorizationClient.setOnCompleteListener(null);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_CODE) {
            AuthorizationResponse.Builder response = new AuthorizationResponse.Builder();

            if (resultCode == RESULT_ERROR) {
                response.setType(AuthorizationResponse.Type.ERROR);

                String errorMessage;
                if (intent == null) {
                    errorMessage = "Invalid message format";
                } else {
                    errorMessage = intent.getStringExtra(SpotifyNativeAuthUtil.EXTRA_ERROR);
                }
                if (errorMessage == null) {
                    errorMessage = "Unknown error";
                }
                response.setError(errorMessage);

            } else if (resultCode == RESULT_OK) {
                Bundle data = intent.getParcelableExtra(SpotifyNativeAuthUtil.EXTRA_REPLY);

                if (data == null) {
                    response.setType(AuthorizationResponse.Type.ERROR);
                    response.setError("Missing response data");
                } else {
                    String responseType = data.getString(SpotifyNativeAuthUtil.KEY_RESPONSE_TYPE, "unknown");
                    Log.d(TAG, "Response: " + responseType);
                    response.setState(data.getString(SpotifyNativeAuthUtil.KEY_STATE, null));
                    switch (responseType) {
                        case SpotifyNativeAuthUtil.RESPONSE_TYPE_TOKEN:
                            String token = data.getString(SpotifyNativeAuthUtil.KEY_ACCESS_TOKEN);
                            int expiresIn = data.getInt(SpotifyNativeAuthUtil.KEY_EXPIRES_IN);

                            response.setType(AuthorizationResponse.Type.TOKEN);
                            response.setAccessToken(token);
                            response.setExpiresIn(expiresIn);
                            break;
                        case SpotifyNativeAuthUtil.RESPONSE_TYPE_CODE:
                            String code = data.getString(SpotifyNativeAuthUtil.KEY_AUTHORIZATION_CODE);
                            response.setType(AuthorizationResponse.Type.CODE);
                            response.setCode(code);
                            break;
                        default:
                            response.setType(AuthorizationResponse.Type.UNKNOWN);
                            break;
                    }
                }

            } else {
                response.setType(AuthorizationResponse.Type.EMPTY);
            }

            mAuthorizationClient.setOnCompleteListener(this);
            mAuthorizationClient.complete(response.build());
        }
    }

    @Override
    public void onClientComplete(AuthorizationResponse response) {
        Intent resultIntent = new Intent();

        // Put response into a bundle to work around classloader problems on Samsung devices
        // https://stackoverflow.com/questions/28589509/android-e-parcel-class-not-found-when-unmarshalling-only-on-samsung-tab3
        Bundle bundle = new Bundle();
        bundle.putParcelable(RESPONSE_KEY, response);

        resultIntent.putExtra(EXTRA_AUTH_RESPONSE, bundle);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onClientCancelled() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
