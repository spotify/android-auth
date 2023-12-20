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

package com.spotify.sdk.android.auth.browser;

import static com.spotify.sdk.android.auth.browser.CustomTabsSupportChecker.getPackageSupportingCustomTabs;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import com.spotify.sdk.android.auth.AuthorizationHandler;
import com.spotify.sdk.android.auth.AuthorizationRequest;

/**
 * An AuthorizationHandler that opens the Spotify web auth page in a Custom Tab or users default web browser.
 */
public class BrowserAuthHandler implements AuthorizationHandler {

    private static final String TAG = BrowserAuthHandler.class.getSimpleName();

    private CustomTabsSession mTabsSession;
    private CustomTabsServiceConnection mTabConnection;
    private boolean mIsAuthInProgress = false;
    private Context mContext;
    private Uri mUri;

    @Override
    public boolean start(Activity contextActivity, AuthorizationRequest request) {
        Log.d(TAG, "start");
        mContext = contextActivity;
        mUri = request.toUri();
        String packageSupportingCustomTabs = getPackageSupportingCustomTabs(mContext, request);
        boolean shouldLaunchCustomTab = !TextUtils.isEmpty(packageSupportingCustomTabs);

        if (internetPermissionNotGranted(mContext)) {
            Log.e(TAG, "Missing INTERNET permission");
        }

        if (shouldLaunchCustomTab) {
            Log.d(TAG, "Launching auth in a Custom Tab using package:" + packageSupportingCustomTabs);
            mTabConnection = new CustomTabsServiceConnection() {
                @Override
                public void onCustomTabsServiceConnected(@NonNull ComponentName name, @NonNull CustomTabsClient client) {
                    client.warmup(0L);
                    mTabsSession = client.newSession(new CustomTabsCallback());
                    if (mTabsSession != null) {
                        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().setSession(mTabsSession).build();
                        customTabsIntent.launchUrl(mContext, request.toUri());
                        mIsAuthInProgress = true;
                    } else {
                        unbindCustomTabsService();
                        Log.i(TAG, "Auth using CustomTabs aborted, reason: CustomTabsSession is null.");
                        launchAuthInBrowserFallback();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.i(TAG, "Auth using CustomTabs aborted, reason: CustomTabsService disconnected.");
                    mTabsSession = null;
                    mTabConnection = null;
                }
            };
            CustomTabsClient.bindCustomTabsService(mContext, packageSupportingCustomTabs, mTabConnection);
        } else {
            Log.d(TAG, "Launching auth inside a web browser");
            launchAuthInBrowserFallback();
        }
        return true;
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        unbindCustomTabsService();
        mContext = null;
        mIsAuthInProgress = false;
    }

    @Override
    public void setOnCompleteListener(@Nullable OnCompleteListener listener) {
        // no-op
    }

    @Override
    public boolean isAuthInProgress() {
        return mIsAuthInProgress;
    }

    private void launchAuthInBrowserFallback() {
        if (internetPermissionNotGranted(mContext)) {
            Log.e(TAG, "Missing INTERNET permission");
        }
        mContext.startActivity(new Intent(Intent.ACTION_VIEW, mUri));
        mIsAuthInProgress = true;
    }

    private boolean internetPermissionNotGranted(Context context) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        return pm.checkPermission(Manifest.permission.INTERNET, packageName) != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Unbinds from the Custom Tabs Service.
     */
    public void unbindCustomTabsService() {
        if (mTabConnection == null) return;
        mContext.unbindService(mTabConnection);
        mTabsSession = null;
        mTabConnection = null;
    }
}
