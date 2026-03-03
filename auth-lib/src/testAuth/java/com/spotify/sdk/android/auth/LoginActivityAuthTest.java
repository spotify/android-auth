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

package com.spotify.sdk.android.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Robolectric.buildActivity;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;
import com.spotify.sdk.android.auth.LoginActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class LoginActivityAuthTest {

    private static class LoginActivitySetup {
        final LoginActivity loginActivity;
        final ShadowActivity shadowLoginActivity;

        LoginActivitySetup(LoginActivity loginActivity, ShadowActivity shadowLoginActivity) {
            this.loginActivity = loginActivity;
            this.shadowLoginActivity = shadowLoginActivity;
        }
    }

    private LoginActivitySetup createLoginActivity() {
        Activity context = Robolectric
                .buildActivity(Activity.class)
                .create()
                .get();

        PKCEInformation pkceInfo = PKCEInformation.sha256("test_verifier", "test_challenge");
        AuthorizationRequest request = new AuthorizationRequest.Builder("test", AuthorizationResponse.Type.TOKEN, "test://test")
                .setPkceInformation(pkceInfo)
                .build();

        Bundle bundle = new Bundle();
        bundle.putParcelable(LoginActivity.REQUEST_KEY, request);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_AUTH_REQUEST, bundle);

        ActivityController<LoginActivity> loginActivityController = buildActivity(LoginActivity.class, intent);
        LoginActivity loginActivity = loginActivityController.get();
        ShadowActivity shadowLoginActivity = shadowOf(loginActivity);
        shadowLoginActivity.setCallingActivity(context.getComponentName());
        loginActivityController.create();

        return new LoginActivitySetup(loginActivity, shadowLoginActivity);
    }

    private void assertCompletion(LoginActivitySetup setup, AuthorizationResponse response, int expectedResultCode) {
        setup.loginActivity.onClientComplete(response);

        assertTrue(setup.loginActivity.isFinishing());
        assertEquals(expectedResultCode, setup.shadowLoginActivity.getResultCode());
        assertEquals(response, setup.shadowLoginActivity.getResultIntent().getBundleExtra(LoginActivity.EXTRA_AUTH_RESPONSE).get(LoginActivity.RESPONSE_KEY));
    }

    @Test
    public void shouldFinishLoginActivityWhenCompleted() {
        LoginActivitySetup setup = createLoginActivity();

        AuthorizationResponse response = new AuthorizationResponse.Builder()
                .setType(AuthorizationResponse.Type.TOKEN)
                .setAccessToken("test_token")
                .setExpiresIn(3600)
                .build();

        assertFalse(setup.loginActivity.isFinishing());

        assertCompletion(setup, response, Activity.RESULT_OK);
    }

    @Test
    public void shouldReturnResultCanceledWhenUserCancels() {
        LoginActivitySetup setup = createLoginActivity();

        AuthorizationResponse response = new AuthorizationResponse.Builder()
                .setType(AuthorizationResponse.Type.CANCELLED)
                .build();

        assertCompletion(setup, response, Activity.RESULT_CANCELED);
    }

    @Test
    public void shouldReturnResultOkForTechnicalErrors() {
        LoginActivitySetup setup = createLoginActivity();

        AuthorizationResponse response = new AuthorizationResponse.Builder()
                .setType(AuthorizationResponse.Type.EMPTY)
                .build();

        assertCompletion(setup, response, Activity.RESULT_OK);
    }

    @Test
    public void shouldDetectCancellationAfterRecreationWithoutResponse() {
        Activity context = Robolectric.buildActivity(Activity.class).create().get();

        PKCEInformation pkceInfo = PKCEInformation.sha256("test_verifier", "test_challenge");
        AuthorizationRequest request = new AuthorizationRequest.Builder(
                "test", AuthorizationResponse.Type.TOKEN, "test://test")
                .setPkceInformation(pkceInfo)
                .build();

        Bundle requestBundle = new Bundle();
        requestBundle.putParcelable(LoginActivity.REQUEST_KEY, request);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_AUTH_REQUEST, requestBundle);

        // Create and initialize the first activity (triggers authorize, sets authInProgress=true)
        ActivityController<LoginActivity> controller = buildActivity(LoginActivity.class, intent);
        shadowOf(controller.get()).setCallingActivity(context.getComponentName());
        controller.create();

        // Save instance state before "destruction"
        Bundle savedState = new Bundle();
        controller.saveInstanceState(savedState);

        // Simulate recreation: new activity with same intent but no response URI
        Intent recreatedIntent = new Intent(context, LoginActivity.class);
        recreatedIntent.putExtra(LoginActivity.EXTRA_AUTH_REQUEST, requestBundle);

        ActivityController<LoginActivity> controller2 = buildActivity(LoginActivity.class, recreatedIntent);
        LoginActivity recreatedActivity = controller2.get();
        ShadowActivity recreatedShadow = shadowOf(recreatedActivity);
        recreatedShadow.setCallingActivity(context.getComponentName());
        controller2.create(savedState).start().resume();

        // onResume should detect authInProgress=true with no handler and deliver CANCELLED
        assertTrue(recreatedActivity.isFinishing());
        assertEquals(Activity.RESULT_CANCELED, recreatedShadow.getResultCode());
        AuthorizationResponse response = recreatedShadow.getResultIntent()
                .getBundleExtra(LoginActivity.EXTRA_AUTH_RESPONSE)
                .getParcelable(LoginActivity.RESPONSE_KEY);
        assertEquals(AuthorizationResponse.Type.CANCELLED, response.getType());
    }

    @Test
    public void shouldProcessResponseInIntentDataAfterRecreation() {
        Activity context = Robolectric.buildActivity(Activity.class).create().get();

        PKCEInformation pkceInfo = PKCEInformation.sha256("test_verifier", "test_challenge");
        AuthorizationRequest request = new AuthorizationRequest.Builder(
                "test", AuthorizationResponse.Type.TOKEN, "test://test")
                .setPkceInformation(pkceInfo)
                .build();

        Bundle requestBundle = new Bundle();
        requestBundle.putParcelable(LoginActivity.REQUEST_KEY, request);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_AUTH_REQUEST, requestBundle);

        // Create and initialize the first activity
        ActivityController<LoginActivity> controller = buildActivity(LoginActivity.class, intent);
        shadowOf(controller.get()).setCallingActivity(context.getComponentName());
        controller.create();

        // Save instance state before "destruction"
        Bundle savedState = new Bundle();
        controller.saveInstanceState(savedState);

        // Simulate recreation with a response URI in the intent
        // (redirect arrived while activity was destroyed)
        Intent recreatedIntent = new Intent(context, LoginActivity.class);
        recreatedIntent.putExtra(LoginActivity.EXTRA_AUTH_REQUEST, requestBundle);
        recreatedIntent.setData(Uri.parse("test://test?code=test_code"));

        ActivityController<LoginActivity> controller2 = buildActivity(LoginActivity.class, recreatedIntent);
        LoginActivity recreatedActivity = controller2.get();
        ShadowActivity recreatedShadow = shadowOf(recreatedActivity);
        recreatedShadow.setCallingActivity(context.getComponentName());
        controller2.create(savedState);

        // Response should be processed in onCreate, activity should finish with RESULT_OK
        assertTrue(recreatedActivity.isFinishing());
        assertEquals(Activity.RESULT_OK, recreatedShadow.getResultCode());
        AuthorizationResponse response = recreatedShadow.getResultIntent()
                .getBundleExtra(LoginActivity.EXTRA_AUTH_RESPONSE)
                .getParcelable(LoginActivity.RESPONSE_KEY);
        assertEquals(AuthorizationResponse.Type.CODE, response.getType());
        assertEquals("test_code", response.getCode());
    }

}
