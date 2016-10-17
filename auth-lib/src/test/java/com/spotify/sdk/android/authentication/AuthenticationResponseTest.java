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

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class AuthenticationResponseTest {
    @Test
    public void shouldCreateFromImplicitGrantFlow() {
        String responseUrl = "testschema://callback/#access_token=test_access_token&token_type=Bearer&expires_in=3600&state=test_state";
        Uri responseUri = Uri.parse(responseUrl);
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(AuthenticationResponse.Type.TOKEN, response.getType());
        assertEquals("test_state", response.getState());
        assertNull(response.getError());
        assertNull(response.getCode());
        assertEquals(3600, response.getExpiresIn());
        assertEquals("test_access_token", response.getAccessToken());
    }

    @Test
    public void shouldCreateFromAuthorizationCodeFlow() {
        String responseUrl = "testschema://callback/?code=test_authorization_code&state=test_state";
        Uri responseUri = Uri.parse(responseUrl);
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(AuthenticationResponse.Type.CODE, response.getType());
        assertEquals("test_state", response.getState());
        assertNull(response.getError());
        assertEquals("test_authorization_code", response.getCode());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getAccessToken());
    }

    @Test
    public void shouldCreateFromImplicitGrantFlowWithSpecialChars() {
        String testState = "! * ' ( ) ; : @ & = + $ , / ? % # [ ]";
        String responseUrl = "testschema://callback/#access_token=test_access_token&token_type=Bearer&expires_in=3600&state=" + Uri.encode(testState);

        Uri responseUri = Uri.parse(responseUrl);
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(testState, response.getState());
    }

    @Test
    public void shouldCreateFromAuthorizationCodeFlowWithSpecialChars() {
        String testState = "! * ' ( ) ; : @ & = + $ , / ? % # [ ]";
        String responseUrl = "testschema://callback/?code=test_authorization_code&state=" + Uri.encode(testState);

        Uri responseUri = Uri.parse(responseUrl);
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(testState, response.getState());
    }


    @Test
    public void shouldCreateFromErrorUrl() {
        Uri responseUri = Uri.parse("testschema://callback/?error=access_denied");
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(AuthenticationResponse.Type.ERROR, response.getType());
        assertNull(response.getState());
        assertEquals("access_denied", response.getError());
        assertNull(response.getCode());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getAccessToken());
    }

    @Test
    public void shouldCreateFromNullUrl() {
        AuthenticationResponse response = AuthenticationResponse.fromUri(null);

        assertEquals(AuthenticationResponse.Type.EMPTY, response.getType());
        assertNull(response.getState());
        assertNull(response.getError());
        assertNull(response.getCode());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getAccessToken());
    }

    @Test
    public void shouldCreateFromMalformedUrl() {
        String responseUrl = "testschema://callback/#access_token=test_access_token&token_type=Bearer&expires_in=glenn&state=test_state";
        Uri responseUri = Uri.parse(responseUrl);
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(AuthenticationResponse.Type.TOKEN, response.getType());
        assertEquals("test_state", response.getState());
        assertNull(response.getError());
        assertNull(response.getCode());
        assertEquals(0, response.getExpiresIn());
        assertEquals("test_access_token", response.getAccessToken());
    }

    @Test
    public void shouldCreateFromIncompleteUrl() {
        String responseUrl = "testschema://callback/";
        Uri responseUri = Uri.parse(responseUrl);
        AuthenticationResponse response = AuthenticationResponse.fromUri(responseUri);

        assertEquals(AuthenticationResponse.Type.UNKNOWN, response.getType());
        assertNull(response.getState());
        assertNull(response.getError());
        assertNull(response.getCode());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getAccessToken());
    }

    @Test
    public void shouldMarshallCorrectly() {
        AuthenticationResponse response = new AuthenticationResponse.Builder()
                .setState("testState")
                .setType(AuthenticationResponse.Type.TOKEN)
                .setCode("testCode")
                .setAccessToken("testToken")
                .setError("testError")
                .setExpiresIn(3600)
                .build();

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AuthenticationResponse responseFromParcel = AuthenticationResponse.CREATOR.createFromParcel(parcel);

        assertEquals(response.getAccessToken(), responseFromParcel.getAccessToken());
        assertEquals(response.getState(), responseFromParcel.getState());
        assertEquals(response.getCode(), responseFromParcel.getCode());
        assertEquals(response.getError(), responseFromParcel.getError());
        assertEquals(response.getExpiresIn(), responseFromParcel.getExpiresIn());
        assertEquals(response.getType(), responseFromParcel.getType());
    }
}
