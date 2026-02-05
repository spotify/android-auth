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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.spotify.sdk.android.auth.app.SpotifyAuthHandler
import com.spotify.sdk.android.auth.app.SpotifyNativeAuthUtil
import java.security.NoSuchAlgorithmException

/**
 * AuthorizationClient provides helper methods to initialize and manage the Spotify authorization flow.
 *
 * This client provides two versions of authorization:
 *
 * ## 1. Single Sign-On using Spotify Android application with browser fallback
 *
 * The SDK will try to fetch the authorization code/access token using the Spotify Android client.
 * If Spotify is not installed on the device, SDK will fallback to Custom Tabs based authorization
 * and open [Spotify Accounts Service](https://accounts.spotify.com) in a dialog.
 * After authorization flow is completed, result is returned to the activity that invoked [AuthorizationClient].
 *
 * **If Spotify is installed:** SDK will connect to the Spotify client and fetch the authorization code/access token
 * for current user. Since the user is already logged into Spotify they don't need to fill their username and password.
 * If the SDK application requests scopes that have not been approved, the user will see a list of scopes and can
 * choose to approve or reject them.
 *
 * **If Spotify is not installed:** SDK will open a dialog and load Spotify Accounts Service into a
 * [Custom Tab](https://developer.chrome.com/docs/android/custom-tabs/) of a supported browser.
 * In case there's no browser installed that supports Custom Tabs API, the SDK will fallback to opening the
 * Accounts page in the user's default browser. User will have to enter their username and password to login to Spotify.
 * They will also need to approve any scopes that the SDK application requests and that they haven't approved before.
 *
 * In both cases (SSO and browser fallback), the result of the authorization flow will be returned in the
 * `onActivityResult` method of the activity that initiated it.
 *
 * ### Example: Using from an Activity
 * ```kotlin
 * // Code called from an activity
 * private const val REQUEST_CODE = 1337
 *
 * val request = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
 *     .setScopes(arrayOf("user-read-private", "playlist-read", "playlist-read-private", "streaming"))
 *     .build()
 *
 * AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
 * ```
 *
 * ### Example: Using from a Fragment
 * It is also possible to use [LoginActivity] from other components such as Fragments:
 * ```kotlin
 * // To start LoginActivity from a Fragment:
 * val intent = AuthorizationClient.createLoginActivityIntent(requireActivity(), request)
 * startActivityForResult(intent, REQUEST_CODE)
 *
 * // To close LoginActivity
 * AuthorizationClient.stopLoginActivity(requireActivity(), REQUEST_CODE)
 * ```
 *
 * ### Example: Processing the result
 * To process the result, your activity needs to override `onActivityResult` callback:
 * ```kotlin
 * override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
 *     super.onActivityResult(requestCode, resultCode, intent)
 *
 *     // Check if result comes from the correct activity
 *     if (requestCode == REQUEST_CODE) {
 *         val response = AuthorizationClient.getResponse(resultCode, intent)
 *         when (response.type) {
 *             // Response was successful and contains auth token
 *             AuthorizationResponse.Type.TOKEN -> {
 *                 // Handle successful response
 *                 val token = response.accessToken
 *             }
 *             // Auth flow returned an error
 *             AuthorizationResponse.Type.ERROR -> {
 *                 // Handle error response
 *             }
 *             // Most likely auth flow was cancelled
 *             else -> {
 *                 // Handle other cases
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## 2. Opening Spotify Accounts Service in a web browser
 *
 * In this scenario the SDK creates an intent that will open the browser. Authorization
 * takes part in the browser (not in the SDK application). After authorization is completed
 * browser redirects back to the SDK app.
 *
 * ### Example: Browser-based authorization
 * ```kotlin
 * // Code called from an activity
 * val request = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
 *     .setScopes(arrayOf("user-read-private", "playlist-read", "playlist-read-private", "streaming"))
 *     .build()
 *
 * AuthorizationClient.openLoginInBrowser(this, request)
 * ```
 *
 * To process the result, the receiving activity needs to override one of its callbacks. With launch mode
 * set to `singleInstance` this callback is `onNewIntent`:
 *
 * ```kotlin
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     val uri = intent.data
 *     if (uri != null) {
 *         val response = AuthorizationResponse.fromUri(uri)
 *         when (response.type) {
 *             // Response was successful and contains auth token
 *             AuthorizationResponse.Type.TOKEN -> {
 *                 // Handle successful response
 *                 val token = response.accessToken
 *             }
 *             // Auth flow returned an error
 *             AuthorizationResponse.Type.ERROR -> {
 *                 // Handle error response
 *             }
 *             // Most likely auth flow was cancelled
 *             else -> {
 *                 // Handle other cases
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @see [Web API Authorization guide](https://developer.spotify.com/web-api/authorization-guide)
 */
class AuthorizationClient(
    /**
     * The activity that receives and processes the result of authorization flow
     * and returns it to the context activity that invoked the flow.
     * An instance of [LoginActivity]
     */
    private val loginActivity: Activity
) {

    private var authorizationPending = false

    /**
     * A handler that performs authorization.
     * It is created with [loginActivity] as a context.
     * This activity will receive the result through the [AuthorizationClientListener]
     */
    private var currentHandler: AuthorizationHandler? = null
    private val authorizationHandlers: MutableList<AuthorizationHandler> = ArrayList()
    private var authorizationClientListener: AuthorizationClientListener? = null

    init {
        authorizationHandlers.add(SpotifyAuthHandler())
        authorizationHandlers.add(FallbackHandlerProvider().provideFallback())
    }

    /**
     * Listener interface for receiving authorization flow completion events.
     */
    interface AuthorizationClientListener {
        /**
         * Auth flow was completed.
         * The response can be successful and contain access token or authorization code.
         * The response can be an error response and contain error message.
         * It can also be an empty response which indicates that the user cancelled authorization flow.
         *
         * @param response Response containing a result of authorization flow.
         */
        fun onClientComplete(response: AuthorizationResponse)

        /**
         * Auth flow was cancelled before it could be completed.
         * This callback indicates that the auth flow was interrupted,
         * for example because the underlying LoginActivity was paused or stopped.
         * This is different from the situation when user completes the flow
         * by closing LoginActivity (e.g. by pressing the back button).
         */
        fun onClientCancelled()
    }

    /**
     * Sets the listener that will be used when authorization flow returns a result.
     *
     * @param listener The listener to be notified when authorization flow completes.
     */
    fun setOnCompleteListener(listener: AuthorizationClientListener?) {
        authorizationClientListener = listener
    }

    fun authorize(request: AuthorizationRequest) {
        if (authorizationPending) return
        authorizationPending = true

        val processedRequest = validateAndConvertTokenRequest(request)

        for (authHandler in authorizationHandlers) {
            if (tryAuthorizationHandler(authHandler, processedRequest)) {
                currentHandler = authHandler
                break
            }
        }
    }

    fun cancel() {
        if (!authorizationPending) {
            return
        }

        authorizationPending = false
        closeAuthorizationHandler(currentHandler)

        authorizationClientListener?.onClientCancelled()
        authorizationClientListener = null
    }

    fun complete(response: AuthorizationResponse) {
        sendComplete(currentHandler, response)
    }

    private fun sendComplete(authHandler: AuthorizationHandler?, response: AuthorizationResponse) {
        authorizationPending = false
        closeAuthorizationHandler(authHandler)

        if (authorizationClientListener != null) {
            authorizationClientListener?.onClientComplete(response)
            authorizationClientListener = null
        } else {
            Log.w(TAG, "Can't deliver the Spotify Auth response. The listener is null")
        }
    }

    private fun tryAuthorizationHandler(
        authHandler: AuthorizationHandler,
        request: AuthorizationRequest
    ): Boolean {
        authHandler.setOnCompleteListener(object : AuthorizationHandler.OnCompleteListener {
            override fun onComplete(response: AuthorizationResponse) {
                Log.i(TAG, String.format("Spotify auth response:%s", response.type.name))
                sendComplete(authHandler, response)
            }

            override fun onCancel() {
                Log.i(TAG, "Spotify auth response: User cancelled")
                val response = AuthorizationResponse.Builder()
                    .setType(AuthorizationResponse.Type.EMPTY)
                    .build()
                sendComplete(authHandler, response)
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "Spotify auth Error", error)
                val response = AuthorizationResponse.Builder()
                    .setType(AuthorizationResponse.Type.ERROR)
                    .setError(error.message)
                    .build()
                sendComplete(authHandler, response)
            }
        })

        if (!authHandler.start(loginActivity, request)) {
            closeAuthorizationHandler(authHandler)
            return false
        }
        return true
    }

    private fun closeAuthorizationHandler(authHandler: AuthorizationHandler?) {
        authHandler?.setOnCompleteListener(null)
        authHandler?.stop()
    }

    fun notifyInCaseUserCanceledAuth() {
        if (currentHandler?.isAuthInProgress() == true) {
            Log.i(TAG, "Spotify auth response: User cancelled")
            val response = AuthorizationResponse.Builder()
                .setType(AuthorizationResponse.Type.EMPTY)
                .build()
            complete(response)
        }
    }

    fun clearAuthInProgress() {
        if (currentHandler != null) {
            Log.d(TAG, "Clearing auth in progress state")
            currentHandler?.stop()
            currentHandler = null
        }
    }

    private fun validateAndConvertTokenRequest(request: AuthorizationRequest): AuthorizationRequest {
        val isTokenRequest = AuthorizationResponse.Type.TOKEN.toString() == request.responseType
        val hasPkce = request.pkceInformation != null

        if (!isTokenRequest || !hasPkce) {
            return request
        }

        val isSpotifyInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(loginActivity)
        val isPKCESpotifyVersion = SpotifyNativeAuthUtil.isSpotifyVersionAtLeast(
            loginActivity,
            MIN_SPOTIFY_VERSION_FOR_TOKEN_CONVERSION
        )

        val shouldConvert = !isSpotifyInstalled || isPKCESpotifyVersion

        if (!shouldConvert) {
            return request
        }

        return AuthorizationRequest.Builder(
            request.clientId,
            AuthorizationResponse.Type.CODE,
            request.redirectUri
        )
            .setState(request.state)
            .setScopes(request.scopes)
            .setCampaign(request.getCampaign())
            .setPkceInformation(request.pkceInformation)
            .build()
    }

    companion object {
        private const val TAG = "Spotify Auth Client"

        const val MARKET_VIEW_PATH = "market://"
        const val MARKET_SCHEME = "market"
        const val MARKET_PATH = "details"

        const val PLAY_STORE_SCHEME = "https"
        const val PLAY_STORE_AUTHORITY = "play.google.com"
        const val PLAY_STORE_PATH = "store/apps/details"

        const val SPOTIFY_ID = "com.spotify.music"
        const val SPOTIFY_SDK = "spotify-sdk"
        const val ANDROID_SDK = "android-sdk"
        const val DEFAULT_CAMPAIGN = "android-sdk"

        /**
         * Minimum Spotify app version code required for TOKEN to CODE conversion.
         * Corresponds to version name 9.0.78.360.
         */
        @VisibleForTesting
        const val MIN_SPOTIFY_VERSION_FOR_TOKEN_CONVERSION = 132384743

        /**
         * Query parameters for Play Store intents.
         */
        object PlayStoreParams {
            const val ID = "id"
            const val REFERRER = "referrer"
            const val UTM_SOURCE = "utm_source"
            const val UTM_MEDIUM = "utm_medium"
            const val UTM_CAMPAIGN = "utm_campaign"
        }

        /**
         * Triggers an intent to open the Spotify accounts service in a browser.
         *
         * Make sure that the [redirectUri][AuthorizationRequest.redirectUri] is set to a URI your app is registered for
         * in your AndroidManifest.xml. To get your clientId and to set the redirectUri, please see the
         * [my applications](https://developer.spotify.com/my-applications) part of our developer site.
         *
         * @param contextActivity The activity that should start the intent to open a browser.
         * @param request Authorization request containing client credentials and configuration.
         */
        @JvmStatic
        fun openLoginInBrowser(contextActivity: Activity, request: AuthorizationRequest) {
            val launchBrowser = Intent(Intent.ACTION_VIEW, request.toUri())
            contextActivity.startActivity(launchBrowser)
        }

        /**
         * Creates an intent to open the [LoginActivity].
         *
         * This method can be used to open this activity from components different than activities;
         * for example Fragments.
         *
         * ### Example
         * ```kotlin
         * // To start LoginActivity from a Fragment:
         * val intent = AuthorizationClient.createLoginActivityIntent(requireActivity(), request)
         * startActivityForResult(intent, REQUEST_CODE)
         *
         * // To close LoginActivity
         * AuthorizationClient.stopLoginActivity(requireActivity(), REQUEST_CODE)
         * ```
         *
         * @param contextActivity The activity context to use for the intent.
         * @param request Authorization request containing client credentials and configuration.
         * @return Intent that can be used to start LoginActivity.
         */
        @JvmStatic
        fun createLoginActivityIntent(
            contextActivity: Activity,
            request: AuthorizationRequest
        ): Intent {
            val processedRequest = appendPkceIfTokenRequest(contextActivity, request)
            val intent = LoginActivity.getAuthIntent(contextActivity, processedRequest)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        /**
         * Opens the [LoginActivity] for result.
         *
         * ### Example
         * ```kotlin
         * private const val REQUEST_CODE = 1337
         *
         * val request = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
         *     .setScopes(arrayOf("user-read-private", "playlist-read"))
         *     .build()
         *
         * AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
         * ```
         *
         * @param contextActivity The activity that should start the intent to open LoginActivity.
         * @param requestCode Request code for activity result.
         * @param request Authorization request containing client credentials and configuration.
         */
        @JvmStatic
        fun openLoginActivity(
            contextActivity: Activity,
            requestCode: Int,
            request: AuthorizationRequest
        ) {
            val intent = createLoginActivityIntent(contextActivity, request)
            contextActivity.startActivityForResult(intent, requestCode)
        }

        /**
         * Stops any running LoginActivity.
         *
         * @param contextActivity The activity that was used to launch LoginActivity with [openLoginActivity].
         * @param requestCode Request code that was used to launch LoginActivity.
         */
        @JvmStatic
        fun stopLoginActivity(contextActivity: Activity, requestCode: Int) {
            contextActivity.finishActivity(requestCode)
        }

        /**
         * Extracts [AuthorizationResponse] from the LoginActivity result.
         *
         * @param resultCode Result code returned with the activity result.
         * @param intent Intent received with activity result. Should contain a Uri with result data.
         * @return Response object containing the authorization result.
         */
        @JvmStatic
        fun getResponse(resultCode: Int, intent: Intent?): AuthorizationResponse {
            return if (resultCode == Activity.RESULT_OK) {
                LoginActivity.getResponseFromIntent(intent) ?: AuthorizationResponse.Builder()
                    .setType(AuthorizationResponse.Type.EMPTY)
                    .build()
            } else {
                AuthorizationResponse.Builder()
                    .setType(AuthorizationResponse.Type.EMPTY)
                    .build()
            }
        }

        /**
         * Opens Spotify in the Play Store or browser.
         *
         * @param contextActivity The activity that should start the intent to open the download page.
         * @param campaign A Spotify-provided campaign ID. Uses [DEFAULT_CAMPAIGN] if not provided.
         */
        @JvmStatic
        @JvmOverloads
        fun openDownloadSpotifyActivity(
            contextActivity: Activity,
            campaign: String? = DEFAULT_CAMPAIGN
        ) {
            val uriBuilder = Uri.Builder()

            if (isAvailable(
                    contextActivity,
                    Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_VIEW_PATH))
                )
            ) {
                uriBuilder.scheme(MARKET_SCHEME)
                    .appendPath(MARKET_PATH)
            } else {
                uriBuilder.scheme(PLAY_STORE_SCHEME)
                    .authority(PLAY_STORE_AUTHORITY)
                    .appendEncodedPath(PLAY_STORE_PATH)
            }

            uriBuilder.appendQueryParameter(PlayStoreParams.ID, SPOTIFY_ID)

            val referrerBuilder = Uri.Builder()
            referrerBuilder.appendQueryParameter(PlayStoreParams.UTM_SOURCE, SPOTIFY_SDK)
                .appendQueryParameter(PlayStoreParams.UTM_MEDIUM, ANDROID_SDK)

            if (TextUtils.isEmpty(campaign)) {
                referrerBuilder.appendQueryParameter(PlayStoreParams.UTM_CAMPAIGN, DEFAULT_CAMPAIGN)
            } else {
                referrerBuilder.appendQueryParameter(PlayStoreParams.UTM_CAMPAIGN, campaign)
            }

            uriBuilder.appendQueryParameter(
                PlayStoreParams.REFERRER,
                referrerBuilder.build().encodedQuery
            )

            contextActivity.startActivity(Intent(Intent.ACTION_VIEW, uriBuilder.build()))
        }

        /**
         * Checks if there is an activity available to handle the given intent.
         *
         * @param ctx Context to use for PackageManager access.
         * @param intent Intent to check for available handlers.
         * @return `true` if at least one activity can handle the intent, `false` otherwise.
         */
        @JvmStatic
        fun isAvailable(ctx: Context, intent: Intent): Boolean {
            val mgr = ctx.packageManager
            val list: List<ResolveInfo> = mgr.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            return list.isNotEmpty()
        }

        private fun appendPkceIfTokenRequest(
            context: Context,
            request: AuthorizationRequest
        ): AuthorizationRequest {
            val isTokenRequest = AuthorizationResponse.Type.TOKEN.toString() == request.responseType
            val isSpotifyInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(context)
            val isPKCESpotifyVersion = SpotifyNativeAuthUtil.isSpotifyVersionAtLeast(
                context,
                MIN_SPOTIFY_VERSION_FOR_TOKEN_CONVERSION
            )
            val hasSpotifyVersionWithoutPKCESupportInstalled =
                isSpotifyInstalled && !isPKCESpotifyVersion
            if (!isTokenRequest || hasSpotifyVersionWithoutPKCESupportInstalled) {
                return request
            }

            return try {
                val pkceInfo = PKCEInformationFactory.create()

                AuthorizationRequest.Builder(
                    request.clientId,
                    AuthorizationResponse.Type.TOKEN,
                    request.redirectUri
                )
                    .setState(request.state)
                    .setScopes(request.scopes)
                    .setCampaign(request.getCampaign())
                    .setPkceInformation(pkceInfo)
                    .build()
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("Failed to generate PKCE information: " + e.message, e)
            }
        }
    }
}
