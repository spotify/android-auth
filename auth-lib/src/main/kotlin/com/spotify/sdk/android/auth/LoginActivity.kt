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

package com.spotify.sdk.android.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spotify.sdk.android.auth.AuthorizationResponse.Type
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The activity that manages the login flow.
 * It should not be started directly. Instead use
 * [AuthorizationClient.openLoginActivity]
 */
class LoginActivity : Activity(), AuthorizationClient.AuthorizationClientListener {

    private val authorizationClient = AuthorizationClient(this)
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onNewIntent(intent: Intent) {
        val originalRequest = getRequestFromIntent()
        super.onNewIntent(intent)
        val responseUri = intent.data

        // Clear auth-in-progress state to prevent onResume from thinking user canceled
        if (responseUri != null) {
            authorizationClient.clearAuthInProgress()
        }

        val response = AuthorizationResponse.fromUri(responseUri)

        // Check if this is a CODE response from web fallback that needs token exchange
        if (response.type == Type.CODE) {
            // Check if original request was for TOKEN and has PKCE info
            response.code?.let { code ->
                if (originalRequest != null &&
                    originalRequest.responseType == Type.TOKEN.toString() &&
                    originalRequest.pkceInformation != null
                ) {
                    // Perform PKCE token exchange for web fallback
                    val responseBuilder = AuthorizationResponse.Builder()
                        .setType(Type.TOKEN)
                        .setState(response.state)

                    performPkceTokenExchange(code, originalRequest, responseBuilder)
                    return // Don't complete immediately, wait for async result
                }
            }
        }

        // Handle normal responses (TOKEN from web, errors, etc.)
        authorizationClient.complete(response)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.com_spotify_sdk_login_activity)

        val request = getRequestFromIntent()

        authorizationClient.setOnCompleteListener(this)

        if (callingActivity == null) {
            Log.e(TAG, NO_CALLER_ERROR)
            finish()
        } else if (request == null) {
            Log.e(TAG, NO_REQUEST_ERROR)
            setResult(RESULT_CANCELED)
            finish()
        } else if (savedInstanceState == null) {
            Log.d(TAG, String.format("Spotify Auth starting with the request [%s]", request.toUri().toString()))
            authorizationClient.authorize(request)
        }
    }

    private fun getRequestFromIntent(): AuthorizationRequest? {
        val requestBundle = intent.getBundleExtra(EXTRA_AUTH_REQUEST) ?: return null
        return requestBundle.getParcelable(REQUEST_KEY)
    }

    override fun onResume() {
        super.onResume()
        // onResume is called (except other cases) in the case
        // of browser based auth flow when user pressed back/closed the Custom Tab and
        // LoginActivity came to the foreground again.
        authorizationClient.notifyInCaseUserCanceledAuth()
    }

    override fun onDestroy() {
        authorizationClient.cancel()
        authorizationClient.setOnCompleteListener(null)
        executorService.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == REQUEST_CODE) {
            val response = AuthorizationResponse.Builder()

            if (resultCode == RESULT_ERROR) {
                response.setType(Type.ERROR)

                val errorMessage = if (intent == null) {
                    "Invalid message format"
                } else {
                    intent.getStringExtra(EXTRA_ERROR) ?: "Unknown error"
                }
                response.setError(errorMessage)

            } else if (resultCode == RESULT_OK) {
                @Suppress("DEPRECATION")
                val data: Bundle? = intent?.getParcelableExtra(EXTRA_REPLY)

                if (data == null) {
                    response.setType(Type.ERROR)
                    response.setError("Missing response data")
                } else {
                    val responseType = data.getString(IntentExtras.KEY_RESPONSE_TYPE, "unknown")
                    Log.d(TAG, "Response: $responseType")
                    response.setState(data.getString(IntentExtras.KEY_STATE, null))
                    when (responseType) {
                        RESPONSE_TYPE_TOKEN -> {
                            val token = data.getString(IntentExtras.KEY_ACCESS_TOKEN)
                            val expiresIn = data.getInt(IntentExtras.KEY_EXPIRES_IN)

                            response.setType(Type.TOKEN)
                            response.setAccessToken(token)
                            response.setExpiresIn(expiresIn)
                        }
                        RESPONSE_TYPE_CODE -> {
                            val code = data.getString(IntentExtras.KEY_AUTHORIZATION_CODE)
                            val originalRequest = getRequestFromIntent()

                            // Check if original request was for TOKEN and has PKCE info
                            if (code != null && originalRequest != null &&
                                originalRequest.responseType == Type.TOKEN.toString()
                            ) {
                                if (originalRequest.pkceInformation != null) {
                                    // Perform PKCE token exchange
                                    performPkceTokenExchange(code, originalRequest, response)
                                    return // Don't complete immediately, wait for async result
                                } else {
                                    throw IllegalStateException(
                                        "Exchanging the code for a token requires PKCE parameters"
                                    )
                                }
                            } else {
                                // Regular code response
                                response.setType(Type.CODE)
                                response.setCode(code)
                            }
                        }
                        else -> response.setType(Type.UNKNOWN)
                    }
                }

            } else {
                response.setType(Type.EMPTY)
            }

            authorizationClient.setOnCompleteListener(this)
            authorizationClient.complete(response.build())
        }
    }

    override fun onClientComplete(response: AuthorizationResponse) {
        val resultIntent = Intent()

        // Put response into a bundle to work around classloader problems on Samsung devices
        // https://stackoverflow.com/questions/28589509/android-e-parcel-class-not-found-when-unmarshalling-only-on-samsung-tab3
        Log.i(TAG, String.format("Spotify auth completing. The response is in EXTRA with key '%s'", RESPONSE_KEY))
        val bundle = Bundle()
        bundle.putParcelable(RESPONSE_KEY, response)

        resultIntent.putExtra(EXTRA_AUTH_RESPONSE, bundle)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onClientCancelled() {
        // Called only when LoginActivity is destroyed and no other result is set.
        Log.w(TAG, "Spotify Auth cancelled due to LoginActivity being finished")
        setResult(RESULT_CANCELED)
    }

    private fun performPkceTokenExchange(
        code: String,
        originalRequest: AuthorizationRequest,
        responseBuilder: AuthorizationResponse.Builder
    ) {
        Log.d(TAG, "Performing PKCE token exchange for code: $code")

        executorService.execute {
            try {
                val pkceInfo = originalRequest.pkceInformation ?: run {
                    mainHandler.post {
                        responseBuilder.setType(Type.ERROR)
                        responseBuilder.setError("PKCE information is missing")
                        authorizationClient.setOnCompleteListener(this@LoginActivity)
                        authorizationClient.complete(responseBuilder.build())
                    }
                    return@execute
                }
                val tokenRequest = TokenExchangeRequest.Builder()
                    .setClientId(originalRequest.clientId)
                    .setCode(code)
                    .setRedirectUri(originalRequest.redirectUri)
                    .setCodeVerifier(pkceInfo.verifier)
                    .build()

                val tokenResponse = tokenRequest.execute()

                // Switch back to main thread to complete the response
                mainHandler.post {
                    if (tokenResponse.isSuccess) {
                        // Convert to TOKEN response
                        responseBuilder.setType(Type.TOKEN)
                        responseBuilder.setAccessToken(tokenResponse.accessToken)
                        responseBuilder.setExpiresIn(tokenResponse.expiresIn)
                        responseBuilder.setRefreshToken(tokenResponse.refreshToken)
                        Log.d(TAG, "PKCE token exchange successful")
                    } else {
                        // Convert to ERROR response
                        responseBuilder.setType(Type.ERROR)
                        val errorMsg = tokenResponse.error +
                                if (tokenResponse.errorDescription != null)
                                    ": " + tokenResponse.errorDescription
                                else ""
                        responseBuilder.setError(errorMsg)
                        Log.e(TAG, "PKCE token exchange failed: $errorMsg")
                    }

                    // Complete the authorization flow
                    authorizationClient.setOnCompleteListener(this@LoginActivity)
                    authorizationClient.complete(responseBuilder.build())
                }

            } catch (e: Exception) {
                Log.e(TAG, "PKCE token exchange error", e)

                // Switch back to main thread to complete with error
                mainHandler.post {
                    responseBuilder.setType(Type.ERROR)
                    responseBuilder.setError("Token exchange failed: " + e.message)

                    authorizationClient.setOnCompleteListener(this@LoginActivity)
                    authorizationClient.complete(responseBuilder.build())
                }
            }
        }
    }

    companion object {
        const val EXTRA_REPLY = "REPLY"
        const val EXTRA_ERROR = "ERROR"

        const val RESPONSE_TYPE_TOKEN = "token"
        const val RESPONSE_TYPE_CODE = "code"

        private val TAG = LoginActivity::class.java.name
        private const val NO_CALLER_ERROR = "Can't use LoginActivity with a null caller. " +
                "Possible reasons: calling activity has a singleInstance mode " +
                "or LoginActivity is in a singleInstance/singleTask mode"

        private const val NO_REQUEST_ERROR = "No authorization request"

        const val EXTRA_AUTH_REQUEST = "EXTRA_AUTH_REQUEST"
        const val EXTRA_AUTH_RESPONSE = "EXTRA_AUTH_RESPONSE"
        const val REQUEST_KEY = "request"
        const val RESPONSE_KEY = "response"

        const val REQUEST_CODE = 1138

        private const val RESULT_ERROR = -2

        @JvmStatic
        fun getAuthIntent(contextActivity: Activity, request: AuthorizationRequest): Intent {
            // Put request into a bundle to work around classloader problems on Samsung devices
            // https://stackoverflow.com/questions/28589509/android-e-parcel-class-not-found-when-unmarshalling-only-on-samsung-tab3
            val bundle = Bundle()
            bundle.putParcelable(REQUEST_KEY, request)

            val intent = Intent(contextActivity, LoginActivity::class.java)
            intent.putExtra(EXTRA_AUTH_REQUEST, bundle)

            return intent
        }

        @JvmStatic
        fun getResponseFromIntent(intent: Intent?): AuthorizationResponse? {
            return intent?.getBundleExtra(EXTRA_AUTH_RESPONSE)?.getParcelable(RESPONSE_KEY)
        }
    }
}
