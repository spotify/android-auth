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

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class AuthorizationRequestTest {

    private AuthorizationResponse.Type mResponseType = AuthorizationResponse.Type.TOKEN;
    private String mRedirectUri = "redirect:uri";
    private String mClientId = "12345567";

    private String mDefaultCampaign = AuthorizationRequest.ANDROID_SDK;

    private Uri.Builder getBaseAuthUri(String clientId, String responseType, String redirectUrl, String campaign) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme(AuthorizationRequest.ACCOUNTS_SCHEME)
                .authority(AuthorizationRequest.ACCOUNTS_AUTHORITY)
                .appendPath(AuthorizationRequest.ACCOUNTS_PATH)
                .appendQueryParameter(AccountsQueryParameters.CLIENT_ID, clientId)
                .appendQueryParameter(AccountsQueryParameters.RESPONSE_TYPE, responseType)
                .appendQueryParameter(AccountsQueryParameters.REDIRECT_URI, redirectUrl)
                .appendQueryParameter(AccountsQueryParameters.SHOW_DIALOG, String.valueOf(false))
                .appendQueryParameter(AccountsQueryParameters.UTM_SOURCE, AuthorizationRequest.SPOTIFY_SDK)
                .appendQueryParameter(AccountsQueryParameters.UTM_MEDIUM, mDefaultCampaign)
                .appendQueryParameter(AccountsQueryParameters.UTM_CAMPAIGN, campaign);

        return uriBuilder;
    }


    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfClientIdIsNull() {
        new AuthorizationRequest.Builder(null, mResponseType, mRedirectUri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfResponseTypeIsNull() {
        new AuthorizationRequest.Builder(mClientId, null, mRedirectUri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfRedirectUriIsNull() {
        new AuthorizationRequest.Builder(mClientId, mResponseType, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfRedirectUriIsEmpty() {
        new AuthorizationRequest.Builder(mClientId, mResponseType, "");
    }

    @Test
    public void shouldBuildCorrectUri() {

        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri).build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());

    }

    @Test
    public void shouldSetScopes() {
        String[] expectedScopes = {"scope1", "scope2"};

        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setScopes(expectedScopes)
                .build();

        String[] scopes = authorizationRequest.getScopes();

        assertEquals(expectedScopes.length, scopes.length);
        assertEquals(expectedScopes[0], scopes[0]);
        assertEquals(expectedScopes[1], scopes[1]);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        uriBuilder.appendQueryParameter(AccountsQueryParameters.SCOPE, "scope1 scope2");
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());

    }

    @Test
    public void shouldNotSetNullScopes() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setScopes(null)
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());
    }

    @Test
    public void shouldNotSetEmptyScopes() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setScopes(new String[]{})
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());
    }

    @Test
    public void shouldSetState() {
        String testState = "test_state";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setState(testState)
                .build();

        assertEquals(authorizationRequest.getState(), testState);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        uriBuilder.appendQueryParameter(AccountsQueryParameters.STATE, testState);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());

    }

    @Test
    public void shouldSetCampaign() {
        String testCampaign = "test_campaign";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCampaign(testCampaign)
                .build();

        assertEquals(authorizationRequest.getCampaign(), testCampaign);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, testCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());
    }

    @Test
    public void shouldUseDefaultCampaign() {

        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .build();

        assertEquals(authorizationRequest.getCampaign(), mDefaultCampaign);

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());
    }

    @Test
    public void shouldNotSetNullState() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setState(null)
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());
    }

    @Test
    public void shouldSetCustomParams() {
        String customKey1 = "custom_key_1";
        String customValue1 = "custom_value_1";
        String customKey2 = "custom_key_2";
        String customValue2 = "custom_value_2";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam(customKey1, customValue1)
                .setCustomParam(customKey2, customValue2)
                .build();

        Uri.Builder uriBuilder = getBaseAuthUri(mClientId, mResponseType.toString(), mRedirectUri, mDefaultCampaign);
        uriBuilder.appendQueryParameter(customKey1, customValue1);
        uriBuilder.appendQueryParameter(customKey2, customValue2);
        Uri uri = uriBuilder.build();

        assertEquals(uri, authorizationRequest.toUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullCustomParamKey() {
        new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam(null, "testValue")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyCustomParamKey() {
        new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam("", "testValue")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullCustomParamValue() {
        new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam("testKey", null)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyCustomParamValue() {
        new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                .setCustomParam("testKey", "")
                .build();
    }

    @Test
    public void shouldMarshallCorrectly() {
        String key1 = "key_1";
        String key2 = "key_2";

        AuthorizationRequest request =
                new AuthorizationRequest.Builder(mClientId, mResponseType, mRedirectUri)
                        .setState("testState")
                        .setScopes(new String[]{"scope1", "scope2"})
                        .setCustomParam(key1, "value_1")
                        .setCustomParam(key2, "value_2")
                        .build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AuthorizationRequest requestFromParcel = AuthorizationRequest.CREATOR.createFromParcel(parcel);

        assertEquals(request.getClientId(), requestFromParcel.getClientId());
        assertEquals(request.getRedirectUri(), requestFromParcel.getRedirectUri());
        assertEquals(request.getResponseType(), requestFromParcel.getResponseType());
        assertArrayEquals(request.getScopes(), requestFromParcel.getScopes());
        assertEquals(request.getState(), requestFromParcel.getState());
        assertEquals(request.getCustomParam(key1), requestFromParcel.getCustomParam(key1));
        assertEquals(request.getCustomParam(key2), requestFromParcel.getCustomParam(key2));
    }
}
