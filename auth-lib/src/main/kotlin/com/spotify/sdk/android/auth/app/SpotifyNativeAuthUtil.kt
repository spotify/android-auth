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

package com.spotify.sdk.android.auth.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.IntentExtras
import com.spotify.sdk.android.auth.LoginActivity
import com.spotify.sdk.android.auth.PKCEInformation

class SpotifyNativeAuthUtil @JvmOverloads constructor(
    private val contextActivity: Activity,
    private val request: AuthorizationRequest,
    private val sha1HashUtil: Sha1HashUtil = Sha1HashUtilImpl()
) {

    fun startAuthActivity(): Boolean {
        val intent = createAuthActivityIntent(contextActivity, sha1HashUtil) ?: return false

        intent.putExtra(IntentExtras.KEY_VERSION, PROTOCOL_VERSION)
        intent.putExtra(IntentExtras.KEY_CLIENT_ID, request.clientId)
        intent.putExtra(IntentExtras.KEY_REDIRECT_URI, request.redirectUri)
        intent.putExtra(IntentExtras.KEY_RESPONSE_TYPE, request.responseType)
        intent.putExtra(IntentExtras.KEY_REQUESTED_SCOPES, request.scopes)
        intent.putExtra(IntentExtras.KEY_STATE, request.state)
        intent.putExtra(IntentExtras.KEY_UTM_SOURCE, request.getSource())
        intent.putExtra(IntentExtras.KEY_UTM_CAMPAIGN, request.getCampaign())
        intent.putExtra(IntentExtras.KEY_UTM_MEDIUM, request.getMedium())

        val pkceInfo = request.pkceInformation
        if (pkceInfo != null) {
            intent.putExtra(IntentExtras.KEY_CODE_CHALLENGE, pkceInfo.challenge)
            intent.putExtra(IntentExtras.KEY_CODE_CHALLENGE_METHOD, pkceInfo.codeChallengeMethod)
        }

        return try {
            contextActivity.startActivityForResult(intent, LoginActivity.REQUEST_CODE)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun stopAuthActivity() {
        contextActivity.finishActivity(LoginActivity.REQUEST_CODE)
    }

    companion object {
        /*
         * The version of the auth protocol. More info about this protocol in
         * {@link com.spotify.sdk.android.auth.IntentExtras}.
         */
        private const val PROTOCOL_VERSION = 1

        private const val SPOTIFY_AUTH_ACTIVITY_ACTION = "com.spotify.sso.action.START_AUTH_FLOW"
        private const val SPOTIFY_PACKAGE_NAME = "com.spotify.music"
        private val SPOTIFY_PACKAGE_SUFFIXES = arrayOf(
            ".debug",
            ".canary",
            ".partners",
            ""
        )

        private val SPOTIFY_SIGNATURE_HASH = arrayOf(
            "25a9b2d2745c098361edaa3b87936dc29a28e7f1",
            "80abdd17dcc4cb3a33815d354355bf87c9378624",
            "88df4d670ed5e01fc7b3eff13b63258628ff5a00",
            "d834ae340d1e854c5f4092722f9788216d9221e5",
            "1cbedd9e7345f64649bad2b493a20d9eea955352",
            "4b3d76a2de89033ea830f476a1f815692938e33b"
        )

        /**
         * Creates an intent that will launch the auth flow on the currently installed Spotify application
         * @param context The context of the caller
         * @return The auth Intent or null if the Spotify application couldn't be found
         */
        @JvmStatic
        fun createAuthActivityIntent(context: Context): Intent? {
            return createAuthActivityIntent(context, Sha1HashUtilImpl())
        }

        @VisibleForTesting
        @JvmStatic
        fun createAuthActivityIntent(context: Context, sha1HashUtil: Sha1HashUtil): Intent? {
            for (suffix in SPOTIFY_PACKAGE_SUFFIXES) {
                val intent = tryResolveActivity(
                    context,
                    SPOTIFY_PACKAGE_NAME + suffix,
                    sha1HashUtil
                )
                if (intent != null) {
                    return intent
                }
            }
            return null
        }

        /**
         * Check if a version of the Spotify main application is installed
         *
         * @param context The context of the caller, used to check if the app is installed
         * @return True if a Spotify app is installed, false otherwise
         */
        @JvmStatic
        fun isSpotifyInstalled(context: Context): Boolean {
            return isSpotifyInstalled(context, Sha1HashUtilImpl())
        }

        @VisibleForTesting
        @JvmStatic
        fun isSpotifyInstalled(context: Context, sha1HashUtil: Sha1HashUtil): Boolean {
            return createAuthActivityIntent(context, sha1HashUtil) != null
        }

        /**
         * Get the version code of the installed Spotify app
         *
         * @param context The context of the caller, used to check package info
         * @return Version code of Spotify app, or -1 if not installed or signature validation fails
         */
        @JvmStatic
        fun getSpotifyAppVersionCode(context: Context): Int {
            return getSpotifyAppVersionCode(context, Sha1HashUtilImpl())
        }

        @VisibleForTesting
        @JvmStatic
        fun getSpotifyAppVersionCode(context: Context, sha1HashUtil: Sha1HashUtil): Int {
            for (suffix in SPOTIFY_PACKAGE_SUFFIXES) {
                val packageName = SPOTIFY_PACKAGE_NAME + suffix
                try {
                    val packageInfo = context.packageManager.getPackageInfo(packageName, 0)

                    // Validate signature before returning version info
                    if (validateSignature(context, packageName, sha1HashUtil)) {
                        return packageInfo.versionCode
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {
                    // Try next package variant
                }
            }
            return -1 // Not found or signature validation failed
        }

        /**
         * Check if Spotify app version meets minimum requirement
         *
         * @param context The context of the caller, used to check package info
         * @param minVersionCode Minimum required version code
         * @return true if installed {@code version >= minVersionCode}, false otherwise
         */
        @JvmStatic
        fun isSpotifyVersionAtLeast(context: Context, minVersionCode: Int): Boolean {
            return isSpotifyVersionAtLeast(context, minVersionCode, Sha1HashUtilImpl())
        }

        @VisibleForTesting
        @JvmStatic
        fun isSpotifyVersionAtLeast(context: Context, minVersionCode: Int, sha1HashUtil: Sha1HashUtil): Boolean {
            val currentVersion = getSpotifyAppVersionCode(context, sha1HashUtil)
            return currentVersion >= minVersionCode
        }

        private fun tryResolveActivity(
            context: Context,
            packageName: String,
            sha1HashUtil: Sha1HashUtil
        ): Intent? {
            val intent = Intent(SPOTIFY_AUTH_ACTIVITY_ACTION)
            intent.`package` = packageName

            val componentName = intent.resolveActivity(context.packageManager) ?: return null

            if (!validateSignature(context, componentName.packageName, sha1HashUtil)) {
                return null
            }

            return intent
        }

        @SuppressLint("PackageManagerGetSignatures")
        private fun validateSignature(
            context: Context,
            spotifyPackageName: String,
            sha1HashUtil: Sha1HashUtil
        ): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = context.packageManager.getPackageInfo(
                        spotifyPackageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )

                    val signingInfo = packageInfo.signingInfo ?: return false
                    if (signingInfo.hasMultipleSigners()) {
                        validateSignatures(sha1HashUtil, signingInfo.apkContentsSigners)
                    } else {
                        validateSignatures(sha1HashUtil, signingInfo.signingCertificateHistory)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = context.packageManager.getPackageInfo(
                        spotifyPackageName,
                        PackageManager.GET_SIGNATURES
                    )

                    @Suppress("DEPRECATION")
                    validateSignatures(sha1HashUtil, packageInfo.signatures)
                }
            } catch (ignored: PackageManager.NameNotFoundException) {
                false
            }
        }

        private fun validateSignatures(
            sha1HashUtil: Sha1HashUtil,
            apkSignatures: Array<Signature>?
        ): Boolean {
            if (apkSignatures == null || apkSignatures.isEmpty()) {
                return false
            }

            for (actualApkSignature in apkSignatures) {
                val signatureString = actualApkSignature.toCharsString()
                val sha1Signature = sha1HashUtil.sha1Hash(signatureString)
                var matchesSignature = false
                for (knownSpotifyHash in SPOTIFY_SIGNATURE_HASH) {
                    if (knownSpotifyHash.equals(sha1Signature, ignoreCase = true)) {
                        matchesSignature = true
                        break
                    }
                }

                // Abort upon finding a non matching signature
                if (!matchesSignature) {
                    return false
                }
            }
            return true
        }
    }
}
