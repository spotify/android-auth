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

package com.spotify.sdk.android.auth

import org.json.JSONException
import org.json.JSONObject

/**
 * Response from a token exchange request.
 * Contains either the access token information or error details.
 */
data class TokenExchangeResponse private constructor(
    /** True if the token exchange was successful, false otherwise */
    val isSuccess: Boolean,
    /** The access token if successful, null otherwise */
    val accessToken: String?,
    /** The token type (usually "Bearer") if successful, null otherwise */
    val tokenType: String?,
    /** The number of seconds the access token is valid for, 0 if not provided or on error */
    val expiresIn: Int,
    /** The scope of the access token if provided, null otherwise */
    val scope: String?,
    /** The refresh token if provided, null otherwise */
    val refreshToken: String?,
    /** The error code if the request failed, null if successful */
    val error: String?,
    /** The error description if the request failed, null if successful */
    val errorDescription: String?
) {

    companion object {
        /**
         * Creates a TokenExchangeResponse from an HTTP response.
         *
         * @param responseCode The HTTP response code
         * @param responseBody The response body as JSON string
         * @return A TokenExchangeResponse instance
         */
        @JvmStatic
        fun fromHttpResponse(responseCode: Int, responseBody: String?): TokenExchangeResponse {
            if (responseBody.isNullOrBlank()) {
                return fromError("invalid_response", "Empty response body")
            }

            return try {
                val json = JSONObject(responseBody)

                if (responseCode == 200) {
                    // Success response
                    val accessToken = json.optString("access_token", null)
                    val tokenType = json.optString("token_type", null)
                    val expiresIn = json.optInt("expires_in", 0)
                    val scope = json.optString("scope", null)
                    val refreshToken = json.optString("refresh_token", null)

                    if (accessToken.isNullOrEmpty()) {
                        return fromError("invalid_response", "Missing access_token in response")
                    }

                    fromSuccess(accessToken, tokenType, expiresIn, scope, refreshToken)
                } else {
                    // Error response
                    val error = json.optString("error", "unknown_error")
                    val errorDescription = json.optString("error_description", null)
                    fromError(error, errorDescription)
                }
            } catch (e: JSONException) {
                fromError("invalid_response", "Invalid JSON response: ${e.message}")
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
        @JvmStatic
        fun fromSuccess(
            accessToken: String,
            tokenType: String?,
            expiresIn: Int,
            scope: String?,
            refreshToken: String?
        ): TokenExchangeResponse {
            return TokenExchangeResponse(true, accessToken, tokenType, expiresIn, scope, refreshToken, null, null)
        }

        /**
         * Creates an error TokenExchangeResponse.
         *
         * @param error The error code
         * @param errorDescription The error description (optional)
         * @return An error TokenExchangeResponse
         */
        @JvmStatic
        fun fromError(error: String, errorDescription: String?): TokenExchangeResponse {
            return TokenExchangeResponse(false, null, null, 0, null, null, error, errorDescription)
        }
    }
}
