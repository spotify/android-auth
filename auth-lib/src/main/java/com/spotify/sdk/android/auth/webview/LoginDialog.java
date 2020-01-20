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

package com.spotify.sdk.android.auth.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.spotify.sdk.android.auth.AccountsQueryParameters;
import com.spotify.sdk.android.auth.AuthorizationHandler;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;
import com.spotify.sdk.android.auth.R;

import java.util.Locale;

public class LoginDialog extends Dialog {

    private static final String TAG = LoginDialog.class.getName();

    /**
     * RegEx describing which uris should be opened in the WebView.
     * When uri that is not whitelisted is received, the activity will finish.
     */
    private static final String WEBVIEW_URIS = "^(.+\\.facebook\\.com)|(accounts\\.spotify\\.com)$";

    private static final int DEFAULT_THEME = android.R.style.Theme_Translucent_NoTitleBar;

    /**
     * The maximum width and height of the login dialog in density independent pixels.
     * This value is expressed in pixels because the maximum size
     * should look approximately the same independent from device's screen size and density.
     */
    private static final int MAX_WIDTH_DP = 400;
    private static final int MAX_HEIGHT_DP = 640;

    private final Uri mUri;
    private AuthorizationHandler.OnCompleteListener mListener;
    private ProgressDialog mProgressDialog;
    private boolean mAttached;
    private boolean mResultDelivered;

    public LoginDialog(Activity contextActivity, AuthorizationRequest request) {
        super(contextActivity, DEFAULT_THEME);
        mUri = request.toUri();
    }

    public LoginDialog(Activity contextActivity, int theme, AuthorizationRequest request) {
        super(contextActivity, theme);
        mUri = request.toUri();
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
        mProgressDialog.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                dismiss();
            }
        });

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setBackgroundDrawableResource(android.R.drawable.screen_background_dark_transparent);

        setContentView(R.layout.com_spotify_sdk_login_dialog);

        setLayoutSize();

        createWebView(mUri);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView(Uri uri) {
        if (!internetPermissionGranted()) {
            Log.e(TAG, "Missing INTERNET permission");
        }

        final WebView webView = findViewById(R.id.com_spotify_sdk_login_webview);
        final LinearLayout mWebViewContainer = findViewById(R.id.com_spotify_sdk_login_webview_container);

        final String redirectUri = uri.getQueryParameter(AccountsQueryParameters.REDIRECT_URI);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (mAttached) {
                    mProgressDialog.dismiss();
                }
                webView.setVisibility(View.VISIBLE);
                mWebViewContainer.setVisibility(View.VISIBLE);
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (mAttached) {
                    mProgressDialog.show();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                final String caseSafeRequestRedirectUri = redirectUri.toLowerCase(Locale.ENGLISH);
                final String caseSafeResponseRedirectUri = url.toLowerCase(Locale.ENGLISH);
                Uri responseUri = Uri.parse(url);
                if (caseSafeResponseRedirectUri.startsWith(caseSafeRequestRedirectUri)) {
                    sendComplete(responseUri);
                    return true;
                } else if (responseUri.getAuthority().matches(WEBVIEW_URIS)) {
                    return false;
                }
                final String errorMessage =
                        String.format(
                                "Can't redirect due to mismatch. \nRequest redirect-uri: %s\nResponse redirect-uri: %s",
                                redirectUri, responseUri);
                Log.e(TAG, errorMessage);
                sendError(new RuntimeException(errorMessage));
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                sendError(new Error(String.format("%s, code: %s, failing url: %s", description, errorCode, failingUrl)));
            }
        });

        webView.loadUrl(uri.toString());
    }

    private void sendComplete(Uri responseUri) {
        mResultDelivered = true;
        if (mListener != null) {
            mListener.onComplete(AuthorizationResponse.fromUri(responseUri));
        }
        close();
    }

    private void sendError(Throwable error) {
        mResultDelivered = true;
        if (mListener != null) {
            mListener.onError(error);
        }
        close();
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
        super.onStop();
    }

    public void close() {
        if (mAttached) {
            dismiss();
        }
    }

    private boolean internetPermissionGranted() {
        PackageManager pm = getContext().getPackageManager();
        String packageName = getContext().getPackageName();
        return pm.checkPermission(Manifest.permission.INTERNET, packageName) == PackageManager.PERMISSION_GRANTED;
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

        LinearLayout layout = (LinearLayout) findViewById(R.id.com_spotify_sdk_login_webview_container);
        layout.setLayoutParams(new FrameLayout.LayoutParams(dialogWidth, dialogHeight, Gravity.CENTER));
    }

    public static void clearCookies(Context context) {
        WebViewUtils.clearFacebookCookies(context);

        WebViewUtils.clearCookiesForDomain(context, "spotify.com");
        WebViewUtils.clearCookiesForDomain(context, ".spotify.com");
        WebViewUtils.clearCookiesForDomain(context, "https://spotify.com");
        WebViewUtils.clearCookiesForDomain(context, "https://.spotify.com");
    }
}
