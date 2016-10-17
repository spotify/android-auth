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

package com.spotify.sdk.android.authentication;

import android.app.Activity;
import android.util.Log;

class WebViewAuthHandler implements AuthenticationHandler {

    private static String TAG = WebViewAuthHandler.class.getSimpleName();

    private LoginDialog mLoginDialog;
    private OnCompleteListener mListener;

    @Override
    public boolean start(Activity contextActivity, AuthenticationRequest request) {
        Log.d(TAG, "start");
        mLoginDialog = new LoginDialog(contextActivity, request);
        mLoginDialog.setOnCompleteListener(mListener);
        mLoginDialog.show();
        return true;
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        if (mLoginDialog != null) {
            mLoginDialog.close();
            mLoginDialog = null;
        }
    }

    @Override
    public void setOnCompleteListener(OnCompleteListener listener) {
        mListener = listener;
        if (mLoginDialog != null) {
            mLoginDialog.setOnCompleteListener(listener);
        }
    }
}