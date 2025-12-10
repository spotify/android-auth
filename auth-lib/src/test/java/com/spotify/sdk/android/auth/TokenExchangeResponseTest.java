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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class TokenExchangeResponseTest {

    @Test
    public void shouldParseSuccessfulResponse() {
        final String responseBody = "{\"access_token\":\"test_access_token\"," +
                "\"token_type\":\"Bearer\"," +
                "\"expires_in\":3600," +
                "\"scope\":\"user-read-private playlist-read\"," +
                "\"refresh_token\":\"test_refresh_token\"}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, responseBody);

        assertTrue(response.isSuccess());
        assertEquals("test_access_token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600, response.getExpiresIn());
        assertEquals("user-read-private playlist-read", response.getScope());
        assertEquals("test_refresh_token", response.getRefreshToken());
        assertNull(response.getError());
        assertNull(response.getErrorDescription());
    }

    @Test
    public void shouldParseSuccessfulResponseWithMinimalFields() {
        final String responseBody = "{\"access_token\":\"test_access_token\"}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, responseBody);

        assertTrue(response.isSuccess());
        assertEquals("test_access_token", response.getAccessToken());
        assertNull(response.getTokenType());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getScope());
        assertNull(response.getRefreshToken());
        assertNull(response.getError());
        assertNull(response.getErrorDescription());
    }

    @Test
    public void shouldParseErrorResponse() {
        final String responseBody = "{\"error\":\"invalid_grant\"," +
                "\"error_description\":\"The provided authorization grant is invalid\"}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(400, responseBody);

        assertFalse(response.isSuccess());
        assertNull(response.getAccessToken());
        assertNull(response.getTokenType());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getScope());
        assertNull(response.getRefreshToken());
        assertEquals("invalid_grant", response.getError());
        assertEquals("The provided authorization grant is invalid", response.getErrorDescription());
    }

    @Test
    public void shouldParseErrorResponseWithMinimalFields() {
        final String responseBody = "{\"error\":\"invalid_request\"}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(400, responseBody);

        assertFalse(response.isSuccess());
        assertNull(response.getAccessToken());
        assertEquals("invalid_request", response.getError());
        assertNull(response.getErrorDescription());
    }

    @Test
    public void shouldHandleEmptyResponseBody() {
        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, "");

        assertFalse(response.isSuccess());
        assertEquals("invalid_response", response.getError());
        assertEquals("Empty response body", response.getErrorDescription());
    }

    @Test
    public void shouldHandleNullResponseBody() {
        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, null);

        assertFalse(response.isSuccess());
        assertEquals("invalid_response", response.getError());
        assertEquals("Empty response body", response.getErrorDescription());
    }

    @Test
    public void shouldHandleInvalidJson() {
        final String responseBody = "invalid json response";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, responseBody);

        assertFalse(response.isSuccess());
        assertEquals("invalid_response", response.getError());
        assertTrue(response.getErrorDescription().startsWith("Invalid JSON response:"));
    }

    @Test
    public void shouldHandleMissingAccessTokenInSuccessResponse() {
        final String responseBody = "{\"token_type\":\"Bearer\",\"expires_in\":3600}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, responseBody);

        assertFalse(response.isSuccess());
        assertEquals("invalid_response", response.getError());
        assertEquals("Missing access_token in response", response.getErrorDescription());
    }

    @Test
    public void shouldHandleEmptyAccessTokenInSuccessResponse() {
        final String responseBody = "{\"access_token\":\"\",\"token_type\":\"Bearer\"}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(200, responseBody);

        assertFalse(response.isSuccess());
        assertEquals("invalid_response", response.getError());
        assertEquals("Missing access_token in response", response.getErrorDescription());
    }

    @Test
    public void shouldHandleErrorResponseWithoutErrorField() {
        final String responseBody = "{\"some_other_field\":\"value\"}";

        final TokenExchangeResponse response = TokenExchangeResponse.fromHttpResponse(400, responseBody);

        assertFalse(response.isSuccess());
        assertEquals("unknown_error", response.getError());
        assertNull(response.getErrorDescription());
    }

    @Test
    public void shouldCreateSuccessResponseUsingFactoryMethod() {
        final TokenExchangeResponse response = TokenExchangeResponse.fromSuccess(
                "test_token", "Bearer", 3600, "user-read-private", "refresh_token");

        assertTrue(response.isSuccess());
        assertEquals("test_token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600, response.getExpiresIn());
        assertEquals("user-read-private", response.getScope());
        assertEquals("refresh_token", response.getRefreshToken());
        assertNull(response.getError());
        assertNull(response.getErrorDescription());
    }

    @Test
    public void shouldCreateErrorResponseUsingFactoryMethod() {
        final TokenExchangeResponse response = TokenExchangeResponse.fromError(
                "invalid_grant", "Grant is invalid");

        assertFalse(response.isSuccess());
        assertNull(response.getAccessToken());
        assertEquals("invalid_grant", response.getError());
        assertEquals("Grant is invalid", response.getErrorDescription());
    }
}

