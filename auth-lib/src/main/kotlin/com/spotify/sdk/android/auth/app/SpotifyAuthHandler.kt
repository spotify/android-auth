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

import android.app.Activity
import com.spotify.sdk.android.auth.AuthorizationHandler
import com.spotify.sdk.android.auth.AuthorizationRequest

class SpotifyAuthHandler : AuthorizationHandler {

    private var spotifyNativeAuthUtil: SpotifyNativeAuthUtil? = null

    override fun start(contextActivity: Activity, request: AuthorizationRequest): Boolean {
        val util = SpotifyNativeAuthUtil(
            contextActivity,
            request,
            Sha1HashUtilImpl()
        )
        spotifyNativeAuthUtil = util
        return util.startAuthActivity()
    }

    override fun stop() {
        spotifyNativeAuthUtil?.stopAuthActivity()
    }

    override fun setOnCompleteListener(listener: AuthorizationHandler.OnCompleteListener?) {
        // no-op
    }

    override fun isAuthInProgress(): Boolean {
        // not supported, always return false
        return false
    }
}
