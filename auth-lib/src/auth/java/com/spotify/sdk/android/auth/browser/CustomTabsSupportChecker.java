package com.spotify.sdk.android.auth.browser;

import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.spotify.sdk.android.auth.AuthorizationRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that checks if auth can be done in a Custom Tab and returns a package name of the app
 * that supports Custom Tabs. If auth flow cannot be done using a Custom Tab, it returns
 * an empty string.
 */
public final class CustomTabsSupportChecker {
    private static final String TAG = CustomTabsSupportChecker.class.getSimpleName();

    static String getPackageSupportingCustomTabs(Context context, AuthorizationRequest request) {
        String redirectUri = request.getRedirectUri();
        final String packageSupportingCustomTabs = getPackageNameSupportingCustomTabs(
                context.getPackageManager(), request.toUri()
        );
        // CustomTabs seems to have problem with redirecting back the app after auth when URI has http/https scheme
        if (!redirectUri.startsWith("http") && !redirectUri.startsWith("https") &&
                hasBrowserSupportForCustomTabs(packageSupportingCustomTabs) &&
                hasRedirectUriActivity(context.getPackageManager(), redirectUri)) {
            return packageSupportingCustomTabs;
        } else {
            return "";
        }
    }

    private static String getPackageNameSupportingCustomTabs(PackageManager pm, Uri uri) {
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

    private static boolean hasBrowserSupportForCustomTabs(String packageSupportingCustomTabs) {
        if (TextUtils.isEmpty(packageSupportingCustomTabs)) {
            Log.d(TAG, "No package supporting CustomTabs found.");
            return false;
        } else {
            return true;
        }
    }

    private static boolean hasRedirectUriActivity(PackageManager pm, String redirectUri) {
        if (pm == null) {
            return false;
        }
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse(redirectUri));
        List<ResolveInfo> infoList = pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);

        for (ResolveInfo info : infoList) {
            if (RedirectUriReceiverActivity.class.getName().equals(info.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }
}
