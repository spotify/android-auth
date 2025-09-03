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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.spotify.sdk.android.auth.IntentExtras.KEY_ACCESS_TOKEN;
import static com.spotify.sdk.android.auth.AuthorizationResponse.Type.TOKEN;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_AUTHORIZATION_CODE;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_EXPIRES_IN;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_RESPONSE_TYPE;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_STATE;

/**
 * The activity that manages the login flow.
 * It should not be started directly. Instead use
 * {@link AuthorizationClient#openLoginActivity(android.app.Activity, int, AuthorizationRequest)}
 */
public class LoginActivity extends Activity implements AuthorizationClient.AuthorizationClientListener {

    static final String EXTRA_REPLY = "REPLY";
    static final String EXTRA_ERROR = "ERROR";

    static final String RESPONSE_TYPE_TOKEN = "token";
    static final String RESPONSE_TYPE_CODE = "code";

    private static final String TAG = LoginActivity.class.getName();
    private static final String NO_CALLER_ERROR = "Can't use LoginActivity with a null caller. " +
            "Possible reasons: calling activity has a singleInstance mode " +
            "or LoginActivity is in a singleInstance/singleTask mode";

    private static final String NO_REQUEST_ERROR = "No authorization request";

    static final String EXTRA_AUTH_REQUEST = "EXTRA_AUTH_REQUEST";
    static final String EXTRA_AUTH_RESPONSE = "EXTRA_AUTH_RESPONSE";
    public static final String REQUEST_KEY = "request";
    public static final String RESPONSE_KEY = "response";

    private final AuthorizationClient mAuthorizationClient = new AuthorizationClient(this);
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public static final int REQUEST_CODE = 1138;

    private static final int RESULT_ERROR = -2;

    @Override
    protected void onNewIntent(Intent intent) {
        final AuthorizationRequest originalRequest = getRequestFromIntent();
        super.onNewIntent(intent);
        final Uri responseUri = intent.getData();
        
        // Clear auth-in-progress state to prevent onResume from thinking user canceled
        if (responseUri != null) {
            mAuthorizationClient.clearAuthInProgress();
        }
        
        final AuthorizationResponse response = AuthorizationResponse.fromUri(responseUri);

        // Check if this is a CODE response from web fallback that needs token exchange
        if (response.getType() == AuthorizationResponse.Type.CODE) {
            // Check if original request was for TOKEN and has PKCE info
            if (originalRequest != null &&
                originalRequest.getResponseType().equals(TOKEN.toString()) &&
                originalRequest.getPkceInformation() != null) {
                    
                // Perform PKCE token exchange for web fallback
                final AuthorizationResponse.Builder responseBuilder = new AuthorizationResponse.Builder()
                        .setType(AuthorizationResponse.Type.TOKEN)
                        .setState(response.getState());

                performPkceTokenExchange(response.getCode(), originalRequest, responseBuilder);
                return; // Don't complete immediately, wait for async result
            }
        }

        // Handle normal responses (TOKEN from web, errors, etc.)
        mAuthorizationClient.complete(response);
    }

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.com_spotify_sdk_login_activity);

        final AuthorizationRequest request = getRequestFromIntent();

        mAuthorizationClient.setOnCompleteListener(this);

        if (getCallingActivity() == null) {
            Log.e(TAG, NO_CALLER_ERROR);
            finish();
        } else if (request == null) {
            Log.e(TAG, NO_REQUEST_ERROR);
            setResult(Activity.RESULT_CANCELED);
            finish();
        } else if (savedInstanceState == null) {
            Log.d(TAG, String.format("Spotify Auth starting with the request [%s]", request.toUri().toString()));
            mAuthorizationClient.authorize(request);
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
        // onResume is called (except other cases) in the case
        // of browser based auth flow when user pressed back/closed the Custom Tab and
        // LoginActivity came to the foreground again.
        mAuthorizationClient.notifyInCaseUserCanceledAuth();
    }

    @Override
    protected void onDestroy() {
        mAuthorizationClient.cancel();
        mAuthorizationClient.setOnCompleteListener(null);
        if (mExecutorService != null) {
            mExecutorService.shutdown();
        }
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
                    errorMessage = intent.getStringExtra(EXTRA_ERROR);
                }
                if (errorMessage == null) {
                    errorMessage = "Unknown error";
                }
                response.setError(errorMessage);

            } else if (resultCode == RESULT_OK) {
                Bundle data = intent.getParcelableExtra(EXTRA_REPLY);

                if (data == null) {
                    response.setType(AuthorizationResponse.Type.ERROR);
                    response.setError("Missing response data");
                } else {
                    String responseType = data.getString(KEY_RESPONSE_TYPE, "unknown");
                    Log.d(TAG, "Response: " + responseType);
                    response.setState(data.getString(KEY_STATE, null));
                    switch (responseType) {
                        case RESPONSE_TYPE_TOKEN:
                            String token = data.getString(KEY_ACCESS_TOKEN);
                            int expiresIn = data.getInt(KEY_EXPIRES_IN);

                            response.setType(AuthorizationResponse.Type.TOKEN);
                            response.setAccessToken(token);
                            response.setExpiresIn(expiresIn);
                            break;
                        case RESPONSE_TYPE_CODE:
                            final String code = data.getString(KEY_AUTHORIZATION_CODE);
                            final AuthorizationRequest originalRequest = getRequestFromIntent();

                            // Check if original request was for TOKEN and has PKCE info
                            if (originalRequest != null &&
                                originalRequest.getResponseType().equals(TOKEN.toString())) {

                                if (originalRequest.getPkceInformation() != null) {
                                    // Perform PKCE token exchange
                                    performPkceTokenExchange(code, originalRequest, response);
                                    return; // Don't complete immediately, wait for async result
                                } else {
                                    throw new IllegalStateException(
                                        "Exchanging the code for a token requires PKCE parameters");
                                }
                            } else {
                                // Regular code response
                                response.setType(AuthorizationResponse.Type.CODE);
                                response.setCode(code);
                            }
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
        Log.i(TAG, String.format("Spotify auth completing. The response is in EXTRA with key '%s'", RESPONSE_KEY));
        Bundle bundle = new Bundle();
        bundle.putParcelable(RESPONSE_KEY, response);

        resultIntent.putExtra(EXTRA_AUTH_RESPONSE, bundle);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onClientCancelled() {
        // Called only when LoginActivity is destroyed and no other result is set.
        Log.w(TAG, "Spotify Auth cancelled due to LoginActivity being finished");
        setResult(Activity.RESULT_CANCELED);
    }

    private void performPkceTokenExchange(final String code,
                                         final AuthorizationRequest originalRequest,
                                         final AuthorizationResponse.Builder responseBuilder) {
        Log.d(TAG, "Performing PKCE token exchange for code: " + code);

        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final PKCEInformation pkceInfo = originalRequest.getPkceInformation();
                    final TokenExchangeRequest tokenRequest = new TokenExchangeRequest.Builder()
                            .setClientId(originalRequest.getClientId())
                            .setCode(code)
                            .setRedirectUri(originalRequest.getRedirectUri())
                            .setCodeVerifier(pkceInfo.getVerifier())
                            .build();

                    final TokenExchangeResponse tokenResponse = tokenRequest.execute();

                    // Switch back to main thread to complete the response
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (tokenResponse.isSuccess()) {
                                // Convert to TOKEN response
                                responseBuilder.setType(AuthorizationResponse.Type.TOKEN);
                                responseBuilder.setAccessToken(tokenResponse.getAccessToken());
                                responseBuilder.setExpiresIn(tokenResponse.getExpiresIn());
                                Log.d(TAG, "PKCE token exchange successful");
                            } else {
                                // Convert to ERROR response
                                responseBuilder.setType(AuthorizationResponse.Type.ERROR);
                                final String errorMsg = tokenResponse.getError() +
                                    (tokenResponse.getErrorDescription() != null ?
                                        ": " + tokenResponse.getErrorDescription() : "");
                                responseBuilder.setError(errorMsg);
                                Log.e(TAG, "PKCE token exchange failed: " + errorMsg);
                            }

                            // Complete the authorization flow
                            mAuthorizationClient.setOnCompleteListener(LoginActivity.this);
                            mAuthorizationClient.complete(responseBuilder.build());
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "PKCE token exchange error", e);

                    // Switch back to main thread to complete with error
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            responseBuilder.setType(AuthorizationResponse.Type.ERROR);
                            responseBuilder.setError("Token exchange failed: " + e.getMessage());

                            mAuthorizationClient.setOnCompleteListener(LoginActivity.this);
                            mAuthorizationClient.complete(responseBuilder.build());
                        }
                    });
                }
            }
        });
    }
}
