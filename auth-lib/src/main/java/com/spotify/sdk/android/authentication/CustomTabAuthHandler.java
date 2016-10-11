/*
 * Copyright (c) 2016 Spotify AB
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

package com.spotify.sdk.android.authentication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomTabAuthHandler implements AuthenticationHandler {

    private static final Set<String> CHROME_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.google.android.apps.chrome"
    ));

    private static final String ACTION_CUSTOM_TABS_CONNECTION =
            "android.support.customtabs.action.CustomTabsService";

    private static final String TAG = CustomTabAuthHandler.class.getSimpleName();
    private static final int SPOTIFY_GREEN = Color.rgb(30, 215, 96);

    @Override
    public boolean start(final Activity contextActivity, final AuthenticationRequest request) {

        final String packageName = getChromePackageName(contextActivity);
        if (packageName == null) {
            return false;
        }

        if (!hasCustomTabRedirectActivity(contextActivity, request.getRedirectUri())) {
            return false;
        }

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(SPOTIFY_GREEN);
        final CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.intent.setPackage(packageName);
        customTabsIntent.launchUrl(contextActivity, request.toUri());

        return true;
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
    }

    @Override
    public void setOnCompleteListener(OnCompleteListener listener) {
        // no-op
    }

    private String getChromePackageName(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent serviceIntent = new Intent(ACTION_CUSTOM_TABS_CONNECTION);
        List<ResolveInfo> servicesSupportingCustomTabs = pm.queryIntentServices(serviceIntent, 0);
        if (servicesSupportingCustomTabs != null) {
            for (ResolveInfo resolveInfo : servicesSupportingCustomTabs) {
                if (CHROME_PACKAGES.contains(resolveInfo.serviceInfo.packageName)) {
                    return resolveInfo.serviceInfo.packageName;
                }
            }
        }
        return null;
    }

    private boolean hasCustomTabRedirectActivity(Context context, String redirectUri) {
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse(redirectUri));
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);

        if (infos == null) {
            // Activity must be registered in the manifest.
            return false;
        } else if (infos.size() != 1) {
            // We should be the only activity registered in the system
            // to handle the url scheme.
            return false;
        } else {
            // We want to be sure that nobody tries to handle our intents
            // and steal some nice tokens.
            ActivityInfo ai = infos.get(0).activityInfo;
            if (!AuthCallbackActivity.class.getName().equals(ai.name)) {
                return false;
            }

            final IntentFilter filter = infos.get(0).filter;
            final String dataScheme = filter.getDataScheme(0);
            final String dataAuthority = filter.getDataAuthority(0).getHost();

            if (TextUtils.isEmpty(dataScheme) && TextUtils.isEmpty(dataAuthority)) {
                Log.w("SpotifyAuth", "Please provide valid callback URI for AuthCallbackActivity.\n" +
                        "You need add @string/com_spotify_sdk_redirect_scheme and @string/com_spotify_sdk_redirect_host to your resources or\n" +
                        "Add complete definition of AuthCallbackActivity");
                return false;
            }
        }
        return true;
    }
}
