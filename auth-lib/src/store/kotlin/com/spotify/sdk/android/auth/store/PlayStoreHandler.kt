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

package com.spotify.sdk.android.auth.store

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.spotify.sdk.android.auth.AuthorizationHandler
import com.spotify.sdk.android.auth.AuthorizationRequest

/**
 * An AuthorizationHandler that opens the play store to download the main Spotify application
 */
class PlayStoreHandler : AuthorizationHandler {

    private var listener: AuthorizationHandler.OnCompleteListener? = null

    override fun start(contextActivity: Activity, request: AuthorizationRequest): Boolean {
        Log.d(TAG, "start")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(
            "https://play.google.com/store/apps/details?id=$APP_PACKAGE_NAME"
        )
        intent.`package` = "com.android.vending"

        val componentName = intent.resolveActivity(contextActivity.packageManager)

        val listener = this.listener
        if (componentName == null) {
            listener?.onError(
                ClassNotFoundException("Couldn't find an activity to handle a play store link")
            )
            return false
        }

        contextActivity.startActivity(intent)

        listener?.onCancel()
        return true
    }

    override fun stop() {
        Log.d(TAG, "stop")
    }

    /**
     * [OnCompleteListener.onError] will be called if no play store application is installed
     * [OnCompleteListener.onCancel] will be called if the play store is launched
     */
    override fun setOnCompleteListener(listener: AuthorizationHandler.OnCompleteListener?) {
        this.listener = listener
    }

    override fun isAuthInProgress(): Boolean {
        // not supported, always return false
        return false
    }

    companion object {
        private val TAG = PlayStoreHandler::class.java.simpleName
        private const val APP_PACKAGE_NAME = "com.spotify.music"
    }
}
