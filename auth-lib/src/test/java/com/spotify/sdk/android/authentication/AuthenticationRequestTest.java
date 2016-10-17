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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class AuthenticationRequestTest {

    private AuthenticationResponse.Type mResponseType = AuthenticationResponse.Type.TOKEN;
    private String mRedirectUri = "redirect:uri";
    private String mClientId = "12345567";

    private String mDefaultCampaign = AuthenticationRequest.ANDROID_SDK;

    private Uri.Builder getBaseAuthUri(String clientId, String responseType, String redirectUrl, String campaign) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme(AuthenticationRequest.ACCOUNTS_SCHEME)
                .authority(AuthenticationRequest.ACCOUNTS_AUTHORITY)
                .appendPath(AuthenticationRequest.ACCOUNTS_PATH)
                .appendQueryParameter(AuthenticationRequest.QueryParams.CLIENT_ID, clientId)
                .appendQueryParameter(AuthenticationRequest.QueryParams.RESPONSE_TYPE, responseType)
                .appendQueryParameter(AuthenticationRequest.QueryParams.REDIRECT_URI, redirectUrl)
                .appendQueryParameter(AuthenticationRequest.QueryParams.SHOW_DIALOG, String.valueOf(false))
                .appendQueryParameter(AuthenticationRequest.QueryParams.UTM_SOURCE, AuthenticationRequest.SPOTIFY_SDK)
                .appendQueryParameter(AuthenticationRequest.QueryParams.UTM_MEDIUM, mDefaultCampaign)
                .appendQueryParameter(AuthenticationRequest.QueryParams.UTM_CAMPAIGN, campaign);

        return uriBuilder;
    }


    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfClientIdIsNull() {
        new AuthenticationRequest.Builder(null, mResponseType, mRedirectUri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfResponseTypeIsNull() {
        new AuthenticationRequest.Builder(mClientId, null, mRedirectUri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfRedirectUriIsNull() {
        new AuthenticationRequest.Builder(mClientId, mResponseType, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfRedirectUriIsEmpty() {
        new AuthenticationRequest.Builder(mClientId, mResponseType, "");
    }

    @Test
    public void shouldBuildCorrectUri() {

        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri).build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());

    }

    @Test
    public void shouldSetScopes() {
        String[] expectedScopes = {"scope1", "scope2"};

        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setScopes(expectedScopes)
                .build();

        String[] scopes = authenticationRequest.getScopes();

        assertEquals(expectedScopes.length, scopes.length);
        assertEquals(expectedScopes[0], scopes[0]);
        assertEquals(expectedScopes[1], scopes[1]);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        uriBuilder.appendQueryParameter(AuthenticationRequest.QueryParams.SCOPE, "scope1 scope2");
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());

    }

    @Test
    public void shouldNotSetNullScopes() {
        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setScopes(null)
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());
    }

    @Test
    public void shouldNotSetEmptyScopes() {
        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setScopes(new String[]{})
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());
    }

    @Test
    public void shouldSetState() {
        String testState = "test_state";

        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setState(testState)
                .build();

        assertEquals(authenticationRequest.getState(), testState);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        uriBuilder.appendQueryParameter(AuthenticationRequest.QueryParams.STATE, testState);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());

    }

    @Test
    public void shouldSetCampaign() {
        String testCampaign = "test_campaign";

        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCampaign(testCampaign)
                .build();

        assertEquals(authenticationRequest.getCampaign(), testCampaign);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, testCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());
    }

    @Test
    public void shouldUseDefaultCampaign() {

        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .build();

        assertEquals(authenticationRequest.getCampaign(), mDefaultCampaign);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());
    }

    @Test
    public void shouldNotSetNullState() {
        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setState(null)
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());
    }

    @Test
    public void shouldSetCustomParams() {
        String customKey1 = "custom_key_1";
        String customValue1 = "custom_value_1";
        String customKey2 = "custom_key_2";
        String customValue2 = "custom_value_2";

        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam(customKey1, customValue1)
                .setCustomParam(customKey2, customValue2)
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        uriBuilder.appendQueryParameter(customKey1, customValue1);
        uriBuilder.appendQueryParameter(customKey2, customValue2);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authenticationRequest.toUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullCustomParamKey() {
        new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam(null, "testValue")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyCustomParamKey() {
        new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam("", "testValue")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullCustomParamValue() {
        new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam("testKey", null)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyCustomParamValue() {
        new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam("testKey", "")
                .build();
    }

    @Test
    public void shouldMarshallCorrectly() {
        String key1 = "key_1";
        String key2 = "key_2";

        AuthenticationRequest request =
                new AuthenticationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                        .setState("testState")
                        .setScopes(new String[]{"scope1", "scope2"})
                        .setCustomParam(key1, "value_1")
                        .setCustomParam(key2, "value_2")
                        .build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AuthenticationRequest requestFromParcel = AuthenticationRequest.CREATOR.createFromParcel(parcel);

        assertEquals(request.getClientId(), requestFromParcel.getClientId());
        assertEquals(request.getRedirectUri(), requestFromParcel.getRedirectUri());
        assertEquals(request.getResponseType(), requestFromParcel.getResponseType());
        assertArrayEquals(request.getScopes(), requestFromParcel.getScopes());
        assertEquals(request.getState(), requestFromParcel.getState());
        assertEquals(request.getCustomParam(key1), requestFromParcel.getCustomParam(key1));
        assertEquals(request.getCustomParam(key2), requestFromParcel.getCustomParam(key2));
    }
}
