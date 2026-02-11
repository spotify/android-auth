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

package com.spotify.sdk.android.auth.browser

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.spotify.sdk.android.auth.AuthorizationHandler
import com.spotify.sdk.android.auth.AuthorizationRequest

/**
 * An AuthorizationHandler that opens the Spotify web auth page in a Custom Tab or users default web browser.
 */
class BrowserAuthHandler : AuthorizationHandler {

    private var tabsSession: CustomTabsSession? = null
    private var tabConnection: CustomTabsServiceConnection? = null
    private var isAuthInProgress = false
    private var context: Context? = null
    private var uri: Uri? = null

    override fun start(contextActivity: Activity, request: AuthorizationRequest): Boolean {
        Log.d(TAG, "start")
        context = contextActivity
        uri = request.toUri()
        val packageSupportingCustomTabs = CustomTabsSupportChecker.getPackageSupportingCustomTabs(contextActivity, request)
        val shouldLaunchCustomTab = !TextUtils.isEmpty(packageSupportingCustomTabs)

        if (internetPermissionNotGranted(contextActivity)) {
            Log.e(TAG, "Missing INTERNET permission")
        }

        if (shouldLaunchCustomTab) {
            Log.d(TAG, "Launching auth in a Custom Tab using package:$packageSupportingCustomTabs")
            val connection = object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                    client.warmup(0L)
                    val session = client.newSession(CustomTabsCallback())
                    tabsSession = session
                    if (session != null) {
                        val customTabsIntent = CustomTabsIntent.Builder().setSession(session).build()
                        context?.let { customTabsIntent.launchUrl(it, request.toUri()) }
                        isAuthInProgress = true
                    } else {
                        unbindCustomTabsService()
                        Log.i(TAG, "Auth using CustomTabs aborted, reason: CustomTabsSession is null.")
                        launchAuthInBrowserFallback()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    Log.i(TAG, "Auth using CustomTabs aborted, reason: CustomTabsService disconnected.")
                    tabsSession = null
                    tabConnection = null
                }
            }
            tabConnection = connection
            CustomTabsClient.bindCustomTabsService(contextActivity, packageSupportingCustomTabs, connection)
        } else {
            Log.d(TAG, "Launching auth inside a web browser")
            launchAuthInBrowserFallback()
        }
        return true
    }

    override fun stop() {
        Log.d(TAG, "stop")
        unbindCustomTabsService()
        context = null
        isAuthInProgress = false
    }

    override fun setOnCompleteListener(listener: AuthorizationHandler.OnCompleteListener?) {
        // no-op
    }

    override fun isAuthInProgress(): Boolean {
        return isAuthInProgress
    }

    private fun launchAuthInBrowserFallback() {
        context?.let { ctx ->
            if (internetPermissionNotGranted(ctx)) {
                Log.e(TAG, "Missing INTERNET permission")
            }
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
            isAuthInProgress = true
        }
    }

    private fun internetPermissionNotGranted(context: Context): Boolean {
        val pm = context.packageManager
        val packageName = context.packageName
        return pm.checkPermission(Manifest.permission.INTERNET, packageName) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Unbinds from the Custom Tabs Service.
     */
    fun unbindCustomTabsService() {
        val conn = tabConnection ?: return
        context?.unbindService(conn)
        tabsSession = null
        tabConnection = null
    }

    companion object {
        private val TAG = BrowserAuthHandler::class.java.simpleName
    }
}
