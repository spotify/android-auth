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

package com.spotify.sdk.android.auth.app;

import static com.spotify.sdk.android.auth.IntentExtras.KEY_ASSOCIATED_CONTENT;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_CLIENT_ID;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_REDIRECT_URI;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_REQUESTED_SCOPES;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_RESPONSE_TYPE;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_STATE;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_UTM_CAMPAIGN;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_UTM_MEDIUM;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_UTM_SOURCE;
import static com.spotify.sdk.android.auth.IntentExtras.KEY_VERSION;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.LoginActivity;

public class SpotifyNativeAuthUtil {

    /*
     * The version of the auth protocol. More info about this protocol in
     * {@link com.spotify.sdk.android.auth.IntentExtras}.
     */
    private static final int PROTOCOL_VERSION = 1;

    private static final String SPOTIFY_AUTH_ACTIVITY_ACTION = "com.spotify.sso.action.START_AUTH_FLOW";
    private static final String SPOTIFY_PACKAGE_NAME = "com.spotify.music";
    private static final String[] SPOTIFY_PACKAGE_SUFFIXES = new String[]{
            ".debug",
            ".canary",
            ".partners",
            ""
    };

    private static final String[] SPOTIFY_SIGNATURE_HASH = new String[]{
            "25a9b2d2745c098361edaa3b87936dc29a28e7f1",
            "80abdd17dcc4cb3a33815d354355bf87c9378624",
            "88df4d670ed5e01fc7b3eff13b63258628ff5a00",
            "d834ae340d1e854c5f4092722f9788216d9221e5",
            "1cbedd9e7345f64649bad2b493a20d9eea955352",
            "4b3d76a2de89033ea830f476a1f815692938e33b",
    };

    private final Activity mContextActivity;
    private final AuthorizationRequest mRequest;
    @NonNull
    private final Sha1HashUtil mSha1HashUtil;

    public SpotifyNativeAuthUtil(Activity contextActivity,
                                 AuthorizationRequest request,
                                 @NonNull Sha1HashUtil sha1HashUtil) {
        mContextActivity = contextActivity;
        mRequest = request;
        mSha1HashUtil = sha1HashUtil;
    }

    public boolean startAuthActivity() {
        Intent intent = createAuthActivityIntent(mContextActivity, mSha1HashUtil);
        if (intent == null) {
            return false;
        }
        intent.putExtra(KEY_VERSION, PROTOCOL_VERSION);

        intent.putExtra(KEY_CLIENT_ID, mRequest.getClientId());
        intent.putExtra(KEY_REDIRECT_URI, mRequest.getRedirectUri());
        intent.putExtra(KEY_RESPONSE_TYPE, mRequest.getResponseType());
        intent.putExtra(KEY_REQUESTED_SCOPES, mRequest.getScopes());
        intent.putExtra(KEY_STATE, mRequest.getState());
        intent.putExtra(KEY_UTM_SOURCE, mRequest.getSource());
        intent.putExtra(KEY_UTM_CAMPAIGN, mRequest.getCampaign());
        intent.putExtra(KEY_UTM_MEDIUM, mRequest.getMedium());

        String associatedContent = mRequest.getEncodedContent();

        if(associatedContent != null) {
            intent.putExtra(KEY_ASSOCIATED_CONTENT, associatedContent);
        }

        try {
            mContextActivity.startActivityForResult(intent, LoginActivity.REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Creates an intent that will launch the auth flow on the currently installed Spotify application
     * @param context The context of the caller
     * @return The auth Intent or null if the Spotify application couldn't be found
     */
    @Nullable
    public static Intent createAuthActivityIntent(@NonNull Context context) {
        return createAuthActivityIntent(context, new Sha1HashUtilImpl());
    }

    @VisibleForTesting
    static Intent createAuthActivityIntent(@NonNull Context context, @NonNull Sha1HashUtil sha1HashUtil) {
        Intent intent = null;
        for (String suffix : SPOTIFY_PACKAGE_SUFFIXES) {
            intent = tryResolveActivity(context,
                    SPOTIFY_PACKAGE_NAME + suffix,
                    sha1HashUtil);
            if (intent != null) {
                break;
            }
        }
        return intent;
    }

    /**
     * Check if a version of the Spotify main application is installed
     *
     * @param context The context of the caller, used to check if the app is installed
     * @return True if a Spotify app is installed, false otherwise
     */
    public static boolean isSpotifyInstalled(@NonNull Context context) {
        return isSpotifyInstalled(context, new Sha1HashUtilImpl());
    }

    @VisibleForTesting
    static boolean isSpotifyInstalled(@NonNull Context context, @NonNull Sha1HashUtil sha1HashUtil) {
        return createAuthActivityIntent(context, sha1HashUtil) != null;
    }

    @Nullable
    private static Intent tryResolveActivity(@NonNull Context context,
                                             @NonNull String packageName,
                                             @NonNull Sha1HashUtil sha1HashUtil) {
        Intent intent = new Intent(SPOTIFY_AUTH_ACTIVITY_ACTION);
        intent.setPackage(packageName);

        ComponentName componentName = intent.resolveActivity(context.getPackageManager());

        if (componentName == null) {
            return null;
        }

        if (!validateSignature(context, componentName.getPackageName(), sha1HashUtil)) {
            return null;
        }

        return intent;
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static boolean validateSignature(@NonNull Context context,
                                             String spotifyPackageName,
                                             @NonNull Sha1HashUtil sha1HashUtil) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                        spotifyPackageName,
                        PackageManager.GET_SIGNING_CERTIFICATES);

                if (packageInfo.signingInfo == null) {
                    return false;
                }
                if(packageInfo.signingInfo.hasMultipleSigners()){
                    return validateSignatures(sha1HashUtil, packageInfo.signingInfo.getApkContentsSigners());
                }
                else{
                    return validateSignatures(sha1HashUtil, packageInfo.signingInfo.getSigningCertificateHistory());
                }
            } else {
                final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                        spotifyPackageName,
                        PackageManager.GET_SIGNATURES);

                return validateSignatures(sha1HashUtil, packageInfo.signatures);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    private static boolean validateSignatures(@NonNull Sha1HashUtil sha1HashUtil,
                                              @Nullable Signature[] apkSignatures) {
        if (apkSignatures == null || apkSignatures.length == 0) {
            return false;
        }

        for (Signature actualApkSignature : apkSignatures) {
            final String signatureString = actualApkSignature.toCharsString();
            final String sha1Signature = sha1HashUtil.sha1Hash(signatureString);
            boolean matchesSignature = false;
            for (String knownSpotifyHash : SPOTIFY_SIGNATURE_HASH) {
                if (knownSpotifyHash.equalsIgnoreCase(sha1Signature)) {
                    matchesSignature = true;
                    break;
                }
            }

            // Abort upon finding a non matching signature
            if (!matchesSignature) {
                return false;
            }
        }
        return true;
    }

    public void stopAuthActivity() {
        mContextActivity.finishActivity(LoginActivity.REQUEST_CODE);
    }
}
