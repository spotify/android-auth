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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class TokenExchangeRequestTest {

    private static final String TEST_CLIENT_ID = "test_client_id";
    private static final String TEST_CODE = "test_authorization_code";
    private static final String TEST_REDIRECT_URI = "redirect://uri";
    private static final String TEST_CODE_VERIFIER = "test_code_verifier_1234567890";

    @Test
    public void shouldCreateRequestWithValidParameters() {
        final TokenExchangeRequest request = new TokenExchangeRequest(
                TEST_CLIENT_ID,
                TEST_CODE,
                TEST_REDIRECT_URI,
                TEST_CODE_VERIFIER
        );

        assertNotNull(request);
    }

    @Test
    public void shouldCreateRequestUsingBuilder() {
        final TokenExchangeRequest request = new TokenExchangeRequest.Builder()
                .setClientId(TEST_CLIENT_ID)
                .setCode(TEST_CODE)
                .setRedirectUri(TEST_REDIRECT_URI)
                .setCodeVerifier(TEST_CODE_VERIFIER)
                .build();

        assertNotNull(request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullClientId() {
        new TokenExchangeRequest(null, TEST_CODE, TEST_REDIRECT_URI, TEST_CODE_VERIFIER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyClientId() {
        new TokenExchangeRequest("", TEST_CODE, TEST_REDIRECT_URI, TEST_CODE_VERIFIER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullCode() {
        new TokenExchangeRequest(TEST_CLIENT_ID, null, TEST_REDIRECT_URI, TEST_CODE_VERIFIER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyCode() {
        new TokenExchangeRequest(TEST_CLIENT_ID, "", TEST_REDIRECT_URI, TEST_CODE_VERIFIER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullRedirectUri() {
        new TokenExchangeRequest(TEST_CLIENT_ID, TEST_CODE, null, TEST_CODE_VERIFIER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyRedirectUri() {
        new TokenExchangeRequest(TEST_CLIENT_ID, TEST_CODE, "", TEST_CODE_VERIFIER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithNullCodeVerifier() {
        new TokenExchangeRequest(TEST_CLIENT_ID, TEST_CODE, TEST_REDIRECT_URI, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWithEmptyCodeVerifier() {
        new TokenExchangeRequest(TEST_CLIENT_ID, TEST_CODE, TEST_REDIRECT_URI, "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenBuilderMissingClientId() {
        new TokenExchangeRequest.Builder()
                .setCode(TEST_CODE)
                .setRedirectUri(TEST_REDIRECT_URI)
                .setCodeVerifier(TEST_CODE_VERIFIER)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenBuilderMissingCode() {
        new TokenExchangeRequest.Builder()
                .setClientId(TEST_CLIENT_ID)
                .setRedirectUri(TEST_REDIRECT_URI)
                .setCodeVerifier(TEST_CODE_VERIFIER)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenBuilderMissingRedirectUri() {
        new TokenExchangeRequest.Builder()
                .setClientId(TEST_CLIENT_ID)
                .setCode(TEST_CODE)
                .setCodeVerifier(TEST_CODE_VERIFIER)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenBuilderMissingCodeVerifier() {
        new TokenExchangeRequest.Builder()
                .setClientId(TEST_CLIENT_ID)
                .setCode(TEST_CODE)
                .setRedirectUri(TEST_REDIRECT_URI)
                .build();
    }
}