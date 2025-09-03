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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Response from a token exchange request.
 * Contains either the access token information or error details.
 */
public class TokenExchangeResponse {

    private final boolean mIsSuccess;
    private final String mAccessToken;
    private final String mTokenType;
    private final int mExpiresIn;
    private final String mScope;
    private final String mRefreshToken;
    private final String mError;
    private final String mErrorDescription;

    private TokenExchangeResponse(final boolean isSuccess,
                                 @Nullable final String accessToken,
                                 @Nullable final String tokenType,
                                 final int expiresIn,
                                 @Nullable final String scope,
                                 @Nullable final String refreshToken,
                                 @Nullable final String error,
                                 @Nullable final String errorDescription) {
        mIsSuccess = isSuccess;
        mAccessToken = accessToken;
        mTokenType = tokenType;
        mExpiresIn = expiresIn;
        mScope = scope;
        mRefreshToken = refreshToken;
        mError = error;
        mErrorDescription = errorDescription;
    }

    /**
     * @return true if the token exchange was successful, false otherwise
     */
    public boolean isSuccess() {
        return mIsSuccess;
    }

    /**
     * @return the access token if successful, null otherwise
     */
    @Nullable
    public String getAccessToken() {
        return mAccessToken;
    }

    /**
     * @return the token type (usually "Bearer") if successful, null otherwise
     */
    @Nullable
    public String getTokenType() {
        return mTokenType;
    }

    /**
     * @return the number of seconds the access token is valid for, 0 if not provided or on error
     */
    public int getExpiresIn() {
        return mExpiresIn;
    }

    /**
     * @return the scope of the access token if provided, null otherwise
     */
    @Nullable
    public String getScope() {
        return mScope;
    }

    /**
     * @return the refresh token if provided, null otherwise
     */
    @Nullable
    public String getRefreshToken() {
        return mRefreshToken;
    }

    /**
     * @return the error code if the request failed, null if successful
     */
    @Nullable
    public String getError() {
        return mError;
    }

    /**
     * @return the error description if the request failed, null if successful
     */
    @Nullable
    public String getErrorDescription() {
        return mErrorDescription;
    }

    /**
     * Creates a TokenExchangeResponse from an HTTP response.
     *
     * @param responseCode The HTTP response code
     * @param responseBody The response body as JSON string
     * @return A TokenExchangeResponse instance
     */
    @NonNull
    static TokenExchangeResponse fromHttpResponse(final int responseCode, @Nullable final String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return fromError("invalid_response", "Empty response body");
        }

        try {
            final JSONObject json = new JSONObject(responseBody);

            if (responseCode == 200) {
                // Success response
                final String accessToken = json.optString("access_token", null);
                final String tokenType = json.optString("token_type", null);
                final int expiresIn = json.optInt("expires_in", 0);
                final String scope = json.optString("scope", null);
                final String refreshToken = json.optString("refresh_token", null);

                if (accessToken == null || accessToken.isEmpty()) {
                    return fromError("invalid_response", "Missing access_token in response");
                }

                return fromSuccess(accessToken, tokenType, expiresIn, scope, refreshToken);
            } else {
                // Error response
                final String error = json.optString("error", "unknown_error");
                final String errorDescription = json.optString("error_description", null);
                return fromError(error, errorDescription);
            }
        } catch (final JSONException e) {
            return fromError("invalid_response", "Invalid JSON response: " + e.getMessage());
        }
    }

    /**
     * Creates a successful TokenExchangeResponse.
     *
     * @param accessToken The access token
     * @param tokenType The token type
     * @param expiresIn The expiration time in seconds
     * @param scope The token scope
     * @param refreshToken The refresh token (optional)
     * @return A successful TokenExchangeResponse
     */
    @NonNull
    static TokenExchangeResponse fromSuccess(@NonNull final String accessToken,
                                           @Nullable final String tokenType,
                                           final int expiresIn,
                                           @Nullable final String scope,
                                           @Nullable final String refreshToken) {
        return new TokenExchangeResponse(true, accessToken, tokenType, expiresIn, scope, refreshToken, null, null);
    }

    /**
     * Creates an error TokenExchangeResponse.
     *
     * @param error The error code
     * @param errorDescription The error description (optional)
     * @return An error TokenExchangeResponse
     */
    @NonNull
    static TokenExchangeResponse fromError(@NonNull final String error, @Nullable final String errorDescription) {
        return new TokenExchangeResponse(false, null, null, 0, null, null, error, errorDescription);
    }
}

