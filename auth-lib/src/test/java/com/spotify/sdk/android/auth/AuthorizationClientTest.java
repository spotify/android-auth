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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class AuthorizationClientTest {

    @Test
    public void shouldLaunchIntentForPassedActivity() {
        AuthorizationRequest authorizationRequest = mock(AuthorizationRequest.class);
        Activity activity = mock(Activity.class);

        when(authorizationRequest.toUri()).thenReturn(Uri.parse("to://me"));
        Mockito.doNothing().when(activity).startActivity(any(Intent.class));
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);

        AuthorizationClient.openLoginInBrowser(activity, authorizationRequest);

        verify(activity, times(1)).startActivity(captor.capture());
        assertEquals(Intent.ACTION_VIEW, captor.getValue().getAction());
        assertEquals("to://me", captor.getValue().getData().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createLoginActivityIntentShouldThrowExceptionWhenBothPassedIsNull() {
        AuthorizationClient.createLoginActivityIntent(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createLoginActivityIntentShouldThrowExceptionWhenFirstPassedIsNull() {
        AuthorizationRequest authorizationRequest = mock(AuthorizationRequest.class);
        AuthorizationClient.createLoginActivityIntent(null, authorizationRequest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createLoginActivityIntentShouldThrowExceptionWhenSecondPassedIsNull() {
        Activity activity = mock(Activity.class);
        AuthorizationClient.createLoginActivityIntent(activity, null);
    }

    @Test
    public void createLoginActivityIntentShouldReturnAnIntentWithExtrasInIt() {
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest
                        .Builder("test", AuthorizationResponse.Type.TOKEN, "to://me")
                        .setScopes(new String[]{"testa", "toppen"})
                        .build();

        Activity activity = mock(Activity.class);

        Intent intent =
                AuthorizationClient.createLoginActivityIntent(activity, authorizationRequest);

        AuthorizationRequest extra = intent
                .getBundleExtra(LoginActivity.EXTRA_AUTH_REQUEST).getParcelable(LoginActivity.REQUEST_KEY);

        assertNotNull(extra);
        assertEquals("test", extra.getClientId());
        assertEquals("token", extra.getResponseType());
        assertEquals("to://me", extra.getRedirectUri());
        assertEquals(2, extra.getScopes().length);
    }

    @Test
    public void getResponseShouldReturnAParcableIfEverythingIsOk() {
        String responseUrl = "testschema://callback/#access_token=test_access_token" +
                "&token_type=Bearer&expires_in=3600&state=test_state";
        Uri responseUri = Uri.parse(responseUrl);
        AuthorizationResponse result = AuthorizationResponse.fromUri(responseUri);
        Intent intent = mock(Intent.class);
        Bundle bundle = new Bundle();
        bundle.putParcelable(LoginActivity.RESPONSE_KEY, result);
        when(intent.getBundleExtra(eq(LoginActivity.EXTRA_AUTH_RESPONSE)))
                .thenReturn(bundle);
        AuthorizationResponse response =
                AuthorizationClient.getResponse(Activity.RESULT_OK, intent);

        assertEquals(result.getType(), response.getType());
        assertEquals(result.getAccessToken(), response.getAccessToken());
    }

    @Test
    public void getResponseShouldReturnEmptyWhenSomethingWentWrong() {
        Intent intent = mock(Intent.class);
        AuthorizationResponse response =
                AuthorizationClient.getResponse(Activity.RESULT_CANCELED, intent);
        assertEquals(AuthorizationResponse.Type.EMPTY, response.getType());
        assertNull(response.getAccessToken());
    }

    @Test
    public void shouldCreateAnInstanceOfAuthorizationClientAndSendEvents() {
        Activity mContext = Robolectric.buildActivity(Activity.class).create().get();
        AuthorizationClient client = new AuthorizationClient(mContext);
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest
                        .Builder("test", AuthorizationResponse.Type.TOKEN, "to://me")
                        .setScopes(new String[]{"testa", "toppen"})
                        .build();

        client.authorize(authorizationRequest);

        AuthorizationClient.AuthorizationClientListener mockListener
                = mock(AuthorizationClient.AuthorizationClientListener.class);
        client.setOnCompleteListener(mockListener);

        AuthorizationResponse response = new AuthorizationResponse.Builder()
                .setType(AuthorizationResponse.Type.ERROR)
                .setError("Authorization failed")
                .build();
        client.complete(response);

        ArgumentCaptor<AuthorizationResponse> captor = ArgumentCaptor.forClass(AuthorizationResponse.class);
        verify(mockListener, times(1)).onClientComplete(captor.capture());
        assertEquals(AuthorizationResponse.Type.ERROR, captor.getValue().getType());
    }
}
