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

import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import com.spotify.sdk.android.auth.AuthorizationHandler;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.R;

import java.util.ArrayList;
import java.util.List;

public class LoginDialog extends Dialog {

    private static final String TAG = LoginDialog.class.getName();

    public static final int CUSTOM_TAB_HIDDEN = 6;

    private static final int DEFAULT_THEME = android.R.style.Theme_Translucent_NoTitleBar;

    /**
     * The maximum width and height of the login dialog in density independent pixels.
     * This value is expressed in pixels because the maximum size
     * should look approximately the same independent from device's screen size and density.
     */
    private static final int MAX_WIDTH_DP = 400;
    private static final int MAX_HEIGHT_DP = 640;

    private final Uri mUri;
    private final String mRedirectUri;
    private AuthorizationHandler.OnCompleteListener mListener;
    private ProgressDialog mProgressDialog;
    private boolean mAttached;
    private boolean mResultDelivered;
    private CustomTabsClient mCustomTabsClient;
    private CustomTabsSession mTabsSession;
    private CustomTabsServiceConnection mTabConnection;

    public LoginDialog(Activity contextActivity, AuthorizationRequest request) {
        this(contextActivity, DEFAULT_THEME, request);
    }

    public LoginDialog(Activity contextActivity, int theme, AuthorizationRequest request) {
        super(contextActivity, theme);
        mUri = request.toUri();
        mRedirectUri = request.getRedirectUri();
    }

    public void setOnCompleteListener(AuthorizationHandler.OnCompleteListener listener) {
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mResultDelivered = false;

        mProgressDialog = new ProgressDialog(getContext());
        mProgressDialog.setMessage(getContext().getString(R.string.com_spotify_sdk_login_progress));
        mProgressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mProgressDialog.setOnCancelListener(dialogInterface -> dismiss());

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(R.layout.com_spotify_sdk_login_dialog);

        setLayoutSize();

        final String packageSupportingCustomTabs = getPackageNameSupportingCustomTabs(mUri);
        // CustomTabs seems to have problem with redirecting back the app after auth when URI has http/https scheme
        if (TextUtils.isEmpty(packageSupportingCustomTabs) || mRedirectUri.startsWith("http") || mRedirectUri.startsWith("https")) {
            Log.d(TAG, "No package supporting CustomTabs found, launching browser fallback.");
            launchAuthInBrowserFallback();
        } else {
            Log.d(TAG, "Launching auth in CustomTabs supporting package:" + packageSupportingCustomTabs);
            launchAuthInCustomTabs(packageSupportingCustomTabs);
        }
    }

    private String getPackageNameSupportingCustomTabs(Uri uri) {
        PackageManager pm = getContext().getPackageManager();
        Intent activityIntent = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        // Check for default handler
        ResolveInfo defaultViewHandlerInfo = pm.resolveActivity(activityIntent, 0);
        String defaultViewHandlerPackageName = null;
        if (defaultViewHandlerInfo != null) {
            defaultViewHandlerPackageName = defaultViewHandlerInfo.activityInfo.packageName;
        }
        Log.d(TAG, "Found default package name for handling VIEW intents: " + defaultViewHandlerPackageName);

        // Get all apps that can handle the intent
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        ArrayList<String> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null) {
                Log.d(TAG, "Adding " + info.activityInfo.packageName + " to supported packages");
                packagesSupportingCustomTabs.add(info.activityInfo.packageName);
            }
        }

        String packageNameToUse = null;
        if (packagesSupportingCustomTabs.size() == 1) {
            packageNameToUse = packagesSupportingCustomTabs.get(0);
        } else if (packagesSupportingCustomTabs.size() > 1) {
            if (!TextUtils.isEmpty(defaultViewHandlerPackageName)
                    && packagesSupportingCustomTabs.contains(defaultViewHandlerPackageName)) {
                packageNameToUse = defaultViewHandlerPackageName;
            } else {
                packageNameToUse = packagesSupportingCustomTabs.get(0);
            }
        }
        return packageNameToUse;
    }

    private void launchAuthInBrowserFallback() {
        if (internetPermissionNotGranted()) {
            Log.e(TAG, "Missing INTERNET permission");
        }
        getContext().startActivity(new Intent(Intent.ACTION_VIEW, mUri));
    }

    private void launchAuthInCustomTabs(String packageName) {
        if (internetPermissionNotGranted()) {
            Log.e(TAG, "Missing INTERNET permission");
        }

        mTabConnection = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(@NonNull ComponentName name, @NonNull CustomTabsClient client) {
                mCustomTabsClient = client;
                mCustomTabsClient.warmup(0L);
                mTabsSession = mCustomTabsClient.newSession(new AuthCustomTabsCallback());
                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().setSession(mTabsSession).build();
                customTabsIntent.launchUrl(getContext(), mUri);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mCustomTabsClient = null;
                mTabsSession = null;
                if (mTabConnection != null) mTabConnection.onServiceDisconnected(name);
            }
        };
        CustomTabsClient.bindCustomTabsService(getContext(), packageName, mTabConnection);
    }

    /**
     * Unbinds from the Custom Tabs Service.
     */
    public void unbindCustomTabsService() {
        if (mTabConnection == null) return;
        getContext().unbindService(mTabConnection);
        mCustomTabsClient = null;
        mTabsSession = null;
        mTabConnection = null;
    }

    @Override
    public void onAttachedToWindow() {
        mAttached = true;
        super.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        mAttached = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onStop() {
        if (!mResultDelivered && mListener != null) {
            mListener.onCancel();
        }
        mResultDelivered = true;
        mProgressDialog.dismiss();
        unbindCustomTabsService();
        super.onStop();
    }

    public void close() {
        if (mAttached) {
            dismiss();
        }
    }

    private boolean internetPermissionNotGranted() {
        PackageManager pm = getContext().getPackageManager();
        String packageName = getContext().getPackageName();
        return pm.checkPermission(Manifest.permission.INTERNET, packageName) != PackageManager.PERMISSION_GRANTED;
    }

    private void setLayoutSize() {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        int dialogWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        int dialogHeight = ViewGroup.LayoutParams.MATCH_PARENT;

        // If width or height measured in dp exceeds accepted range,
        // use max values and convert them back to pixels before setting the size.
        if (metrics.widthPixels / metrics.density > MAX_WIDTH_DP) {
            dialogWidth = (int) (MAX_WIDTH_DP * metrics.density);
        }

        if (metrics.heightPixels / metrics.density > MAX_HEIGHT_DP) {
            dialogHeight = (int) (MAX_HEIGHT_DP * metrics.density);
        }

        LinearLayout layout = findViewById(R.id.com_spotify_sdk_login_webview_container);
        layout.setLayoutParams(new FrameLayout.LayoutParams(dialogWidth, dialogHeight, Gravity.CENTER));
    }

    class AuthCustomTabsCallback extends CustomTabsCallback {
        @Override
        public void onNavigationEvent(int navigationEvent, @Nullable Bundle extras) {
            super.onNavigationEvent(navigationEvent, extras);
            if (navigationEvent == CUSTOM_TAB_HIDDEN) {
                close();
            }
        }
    }
}
