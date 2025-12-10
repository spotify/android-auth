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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * A utility class for exchanging an authorization code for an access token using PKCE verifier.
 * This implements the OAuth 2.0 Authorization Code Grant with PKCE as specified in RFC 7636.
 */
public class TokenExchangeRequest {

    private static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    private static final int TIMEOUT_MS = 10000; // 10 seconds

    private final String mClientId;
    private final String mCode;
    private final String mRedirectUri;
    private final String mCodeVerifier;

    /**
     * Creates a new token exchange request.
     *
     * @param clientId The client ID of the application
     * @param code The authorization code received from the authorization server
     * @param redirectUri The redirect URI used in the authorization request
     * @param codeVerifier The PKCE code verifier that was used to generate the code challenge
     */
    public TokenExchangeRequest(@NonNull final String clientId,
                               @NonNull final String code,
                               @NonNull final String redirectUri,
                               @NonNull final String codeVerifier) {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }
        if (redirectUri == null || redirectUri.isEmpty()) {
            throw new IllegalArgumentException("Redirect URI cannot be null or empty");
        }
        if (codeVerifier == null || codeVerifier.isEmpty()) {
            throw new IllegalArgumentException("Code verifier cannot be null or empty");
        }

        mClientId = clientId;
        mCode = code;
        mRedirectUri = redirectUri;
        mCodeVerifier = codeVerifier;
    }

    /**
     * Executes the token exchange request synchronously.
     * This method performs a blocking HTTP request and should not be called on the main thread.
     *
     * @return TokenExchangeResponse containing the access token or error information
     */
    @NonNull
    public TokenExchangeResponse execute() {
        HttpURLConnection connection = null;
        try {
            final URL url = new URL(TOKEN_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", CONTENT_TYPE_FORM);
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            final String requestBody = buildRequestBody();

            try (final OutputStream outputStream = connection.getOutputStream()) {
                try {
                    outputStream.write(requestBody.getBytes("UTF-8"));
                } catch (final java.io.UnsupportedEncodingException e) {
                    // UTF-8 is guaranteed to be supported on all platforms
                    throw new RuntimeException("UTF-8 encoding not supported", e);
                }
                outputStream.flush();
            }

            final int responseCode = connection.getResponseCode();
            final String responseBody = readResponse(connection, responseCode >= 400);

            return TokenExchangeResponse.fromHttpResponse(responseCode, responseBody);

        } catch (final IOException e) {
            return TokenExchangeResponse.fromError("network_error", "Network error: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private String buildRequestBody() {
        try {
            return "grant_type=" + URLEncoder.encode(GRANT_TYPE_AUTHORIZATION_CODE, "UTF-8") +
                   "&client_id=" + URLEncoder.encode(mClientId, "UTF-8") +
                   "&code=" + URLEncoder.encode(mCode, "UTF-8") +
                   "&redirect_uri=" + URLEncoder.encode(mRedirectUri, "UTF-8") +
                   "&code_verifier=" + URLEncoder.encode(mCodeVerifier, "UTF-8");
        } catch (final Exception e) {
            // This should never happen with UTF-8
            throw new RuntimeException("Failed to encode request parameters", e);
        }
    }

    @NonNull
    private String readResponse(@NonNull final HttpURLConnection connection, final boolean isError) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                isError ? connection.getErrorStream() : connection.getInputStream(),
                "UTF-8"))) {

            final StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return response.toString();
        }
    }

    /**
     * Builder class for creating TokenExchangeRequest instances.
     */
    public static class Builder {
        private String mClientId;
        private String mCode;
        private String mRedirectUri;
        private String mCodeVerifier;

        /**
         * Sets the client ID.
         *
         * @param clientId The client ID
         * @return This builder instance for method chaining
         */
        @NonNull
        public Builder setClientId(@NonNull final String clientId) {
            mClientId = clientId;
            return this;
        }

        /**
         * Sets the authorization code.
         *
         * @param code The authorization code
         * @return This builder instance for method chaining
         */
        @NonNull
        public Builder setCode(@NonNull final String code) {
            mCode = code;
            return this;
        }

        /**
         * Sets the redirect URI.
         *
         * @param redirectUri The redirect URI
         * @return This builder instance for method chaining
         */
        @NonNull
        public Builder setRedirectUri(@NonNull final String redirectUri) {
            mRedirectUri = redirectUri;
            return this;
        }

        /**
         * Sets the PKCE code verifier.
         *
         * @param codeVerifier The code verifier
         * @return This builder instance for method chaining
         */
        @NonNull
        public Builder setCodeVerifier(@NonNull final String codeVerifier) {
            mCodeVerifier = codeVerifier;
            return this;
        }

        /**
         * Builds the TokenExchangeRequest.
         *
         * @return A new TokenExchangeRequest instance
         * @throws IllegalArgumentException if any required field is null or empty
         */
        @NonNull
        public TokenExchangeRequest build() {
            return new TokenExchangeRequest(mClientId, mCode, mRedirectUri, mCodeVerifier);
        }
    }
}

