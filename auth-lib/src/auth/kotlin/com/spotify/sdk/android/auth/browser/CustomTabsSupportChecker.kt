package com.spotify.sdk.android.auth.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import com.spotify.sdk.android.auth.AuthorizationRequest

/**
 * Class that checks if auth can be done in a Custom Tab and returns a package name of the app
 * that supports Custom Tabs. If auth flow cannot be done using a Custom Tab, it returns
 * an empty string.
 */
internal object CustomTabsSupportChecker {
    private val TAG = CustomTabsSupportChecker::class.java.simpleName

    @JvmStatic
    fun getPackageSupportingCustomTabs(context: Context, request: AuthorizationRequest): String {
        val redirectUri = request.redirectUri
        val packageSupportingCustomTabs = getPackageNameSupportingCustomTabs(
            context.packageManager, request.toUri()
        )
        // CustomTabs seems to have problem with redirecting back the app after auth when URI has http/https scheme
        return if (!redirectUri.startsWith("http") && !redirectUri.startsWith("https") &&
            hasBrowserSupportForCustomTabs(packageSupportingCustomTabs) &&
            hasRedirectUriActivity(context.packageManager, redirectUri)
        ) {
            packageSupportingCustomTabs
        } else {
            ""
        }
    }

    private fun getPackageNameSupportingCustomTabs(pm: PackageManager, uri: Uri): String {
        val activityIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        // Check for default handler
        val defaultViewHandlerInfo = pm.resolveActivity(activityIntent, 0)
        val defaultViewHandlerPackageName = defaultViewHandlerInfo?.activityInfo?.packageName
        Log.d(TAG, "Found default package name for handling VIEW intents: $defaultViewHandlerPackageName")

        // Get all apps that can handle the intent
        val resolvedActivityList = pm.queryIntentActivities(activityIntent, 0)
        val packagesSupportingCustomTabs = resolvedActivityList
            .map { it.activityInfo.packageName }
            .filter { packageName ->
                val serviceIntent = Intent().apply {
                    action = ACTION_CUSTOM_TABS_CONNECTION
                    `package` = packageName
                }
                // Check if this package also resolves the Custom Tabs service.
                pm.resolveService(serviceIntent, 0) != null
            }
            .also { packages ->
                packages.forEach { packageName ->
                    Log.d(TAG, "Adding $packageName to supported packages")
                }
            }

        return when {
            packagesSupportingCustomTabs.isEmpty() -> ""
            packagesSupportingCustomTabs.size == 1 -> packagesSupportingCustomTabs[0]
            else -> {
                if (!defaultViewHandlerPackageName.isNullOrEmpty() &&
                    packagesSupportingCustomTabs.contains(defaultViewHandlerPackageName)
                ) {
                    defaultViewHandlerPackageName
                } else {
                    packagesSupportingCustomTabs[0]
                }
            }
        }
    }

    private fun hasBrowserSupportForCustomTabs(packageSupportingCustomTabs: String): Boolean {
        return packageSupportingCustomTabs.isNotEmpty().also { hasSupport ->
            if (!hasSupport) {
                Log.d(TAG, "No package supporting CustomTabs found.")
            }
        }
    }

    private fun hasRedirectUriActivity(pm: PackageManager?, redirectUri: String): Boolean {
        if (pm == null) {
            return false
        }
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = Uri.parse(redirectUri)
        }
        val infoList = pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)

        return infoList.any { info ->
            info.activityInfo.name == RedirectUriReceiverActivity::class.java.name
        }
    }
}
