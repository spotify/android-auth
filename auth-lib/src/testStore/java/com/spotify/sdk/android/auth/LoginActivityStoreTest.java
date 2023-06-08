package com.spotify.sdk.android.auth;/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Robolectric.buildActivity;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class LoginActivityStoreTest {

    @Test
    public void shouldFinishLoginActivityWithErrorIfPlayStoreIsNotInstalled() {
        Activity context = Robolectric
                .buildActivity(Activity.class)
                .create()
                .get();

        AuthorizationRequest request = new AuthorizationRequest.Builder("test", AuthorizationResponse.Type.TOKEN, "test://test").build();

        Bundle bundle = new Bundle();
        bundle.putParcelable(LoginActivity.REQUEST_KEY, request);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_AUTH_REQUEST, bundle);

        ActivityController<LoginActivity> loginActivityActivityController = buildActivity(LoginActivity.class, intent);

        final LoginActivity loginActivity = loginActivityActivityController.get();

        final ShadowActivity shadowLoginActivity = shadowOf(loginActivity);
        shadowLoginActivity.setCallingActivity(context.getComponentName());

        loginActivityActivityController.create();

        assertTrue(loginActivity.isFinishing());
        assertEquals(Activity.RESULT_OK, shadowLoginActivity.getResultCode());
        AuthorizationResponse response = LoginActivity.getResponseFromIntent(shadowLoginActivity.getResultIntent());
        assertNotNull(response);
        assertEquals(AuthorizationResponse.Type.ERROR, response.getType());
    }

}
