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

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A utility class for exchanging an authorization code for an access token using PKCE verifier.
 * This implements the OAuth 2.0 Authorization Code Grant with PKCE as specified in RFC 7636.
 */
class TokenExchangeRequest(
    private val clientId: String,
    private val code: String,
    private val redirectUri: String,
    private val codeVerifier: String
) {

    init {
        require(clientId.isNotEmpty()) { "Client ID cannot be empty" }
        require(code.isNotEmpty()) { "Authorization code cannot be empty" }
        require(redirectUri.isNotEmpty()) { "Redirect URI cannot be empty" }
        require(codeVerifier.isNotEmpty()) { "Code verifier cannot be empty" }
    }

    /**
     * Executes the token exchange request synchronously.
     * This method performs a blocking HTTP request and should not be called on the main thread.
     *
     * @return TokenExchangeResponse containing the access token or error information
     */
    fun execute(): TokenExchangeResponse {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(TOKEN_ENDPOINT)
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", CONTENT_TYPE_FORM)
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            val requestBody = buildRequestBody()

            connection.outputStream.use { outputStream ->
                try {
                    outputStream.write(requestBody.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    // UTF-8 is guaranteed to be supported on all platforms
                    throw RuntimeException("UTF-8 encoding not supported", e)
                }
                outputStream.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = readResponse(connection, responseCode >= 400)

            return TokenExchangeResponse.fromHttpResponse(responseCode, responseBody)

        } catch (e: IOException) {
            return TokenExchangeResponse.fromError("network_error", "Network error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildRequestBody(): String {
        return try {
            "grant_type=" + URLEncoder.encode(GRANT_TYPE_AUTHORIZATION_CODE, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    "&code=" + URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
                    "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")
        } catch (e: Exception) {
            // This should never happen with UTF-8
            throw RuntimeException("Failed to encode request parameters", e)
        }
    }

    private fun readResponse(connection: HttpURLConnection, isError: Boolean): String {
        BufferedReader(
            InputStreamReader(
                if (isError) connection.errorStream else connection.inputStream,
                "UTF-8"
            )
        ).use { reader ->
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            return response.toString()
        }
    }

    /**
     * Builder class for creating TokenExchangeRequest instances.
     */
    class Builder {
        private var clientId: String? = null
        private var code: String? = null
        private var redirectUri: String? = null
        private var codeVerifier: String? = null

        /**
         * Sets the client ID.
         *
         * @param clientId The client ID
         * @return This builder instance for method chaining
         */
        fun setClientId(clientId: String) = apply {
            this.clientId = clientId
        }

        /**
         * Sets the authorization code.
         *
         * @param code The authorization code
         * @return This builder instance for method chaining
         */
        fun setCode(code: String) = apply {
            this.code = code
        }

        /**
         * Sets the redirect URI.
         *
         * @param redirectUri The redirect URI
         * @return This builder instance for method chaining
         */
        fun setRedirectUri(redirectUri: String) = apply {
            this.redirectUri = redirectUri
        }

        /**
         * Sets the PKCE code verifier.
         *
         * @param codeVerifier The code verifier
         * @return This builder instance for method chaining
         */
        fun setCodeVerifier(codeVerifier: String) = apply {
            this.codeVerifier = codeVerifier
        }

        /**
         * Builds the TokenExchangeRequest.
         *
         * @return A new TokenExchangeRequest instance
         * @throws IllegalArgumentException if any required field is null or empty
         */
        fun build(): TokenExchangeRequest {
            val clientIdValue = clientId
            val codeValue = code
            val redirectUriValue = redirectUri
            val codeVerifierValue = codeVerifier

            require(clientIdValue != null) { "Client ID must be set" }
            require(codeValue != null) { "Authorization code must be set" }
            require(redirectUriValue != null) { "Redirect URI must be set" }
            require(codeVerifierValue != null) { "Code verifier must be set" }

            return TokenExchangeRequest(
                clientId = clientIdValue,
                code = codeValue,
                redirectUri = redirectUriValue,
                codeVerifier = codeVerifierValue
            )
        }
    }

    companion object {
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        private const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        private const val TIMEOUT_MS = 10000 // 10 seconds
    }
}
