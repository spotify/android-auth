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

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * An object that contains the parsed response from the Spotify authorization service.
 * To create one use [AuthorizationResponse.Builder] or
 * parse from [android.net.Uri] with [fromUri]
 *
 * @see <a href="https://developer.spotify.com/web-api/authorization-guide">Web API Authorization guide</a>
 */
@Parcelize
data class AuthorizationResponse internal constructor(
    val type: Type,
    val code: String?,
    val accessToken: String?,
    val state: String?,
    val error: String?,
    val expiresIn: Int,
    val refreshToken: String?
) : Parcelable {

    /**
     * The type of the authorization response.
     */
    enum class Type(private val type: String) {
        /**
         * The response is a code reply.
         *
         * @see <a href="https://developer.spotify.com/documentation/web-api/tutorials/code-flow">Authorization code flow</a>
         */
        CODE("code"),

        /**
         * The response is an implicit grant with access token.
         *
         * @see <a href="https://developer.spotify.com/documentation/web-api/tutorials/implicit-flow">Implicit grant flow</a>
         */
        TOKEN("token"),

        /**
         * The response is an error response.
         *
         * @see <a href="https://developer.spotify.com/documentation/web-api/concepts/authorization">Web API Authorization guide</a>
         */
        ERROR("error"),

        /**
         * Response doesn't contain data because auth flow was cancelled or LoginActivity killed.
         */
        EMPTY("empty"),

        /**
         * The response is unknown.
         */
        UNKNOWN("unknown");

        override fun toString(): String {
            return type
        }
    }

    /**
     * Use this builder to create an [AuthorizationResponse]
     *
     * @see AuthorizationResponse
     */
    class Builder {
        private var type: Type? = null
        private var code: String? = null
        private var accessToken: String? = null
        private var state: String? = null
        private var error: String? = null
        private var expiresIn: Int = 0
        private var refreshToken: String? = null

        fun setType(type: Type) = apply {
            this.type = type
        }

        fun setCode(code: String?) = apply {
            this.code = code
        }

        fun setAccessToken(accessToken: String?) = apply {
            this.accessToken = accessToken
        }

        fun setState(state: String?) = apply {
            this.state = state
        }

        fun setError(error: String?) = apply {
            this.error = error
        }

        fun setExpiresIn(expiresIn: Int) = apply {
            this.expiresIn = expiresIn
        }

        fun setRefreshToken(refreshToken: String?) = apply {
            this.refreshToken = refreshToken
        }

        fun build(): AuthorizationResponse {
            return AuthorizationResponse(
                type ?: Type.UNKNOWN,
                code,
                accessToken,
                state,
                error,
                expiresIn,
                refreshToken
            )
        }
    }

    companion object {
        /**
         * Parses the URI returned from the Spotify accounts service.
         *
         * @param uri URI
         * @return Authorization response. If parsing failed, this object will be populated with
         * the given error codes.
         */
        @JvmStatic
        fun fromUri(uri: Uri?): AuthorizationResponse {
            val builder = Builder()
            if (uri == null) {
                builder.setType(Type.EMPTY)
                return builder.build()
            }

            val possibleError = uri.getQueryParameter(AccountsQueryParameters.ERROR)
            if (possibleError != null) {
                val state = uri.getQueryParameter(AccountsQueryParameters.STATE)
                builder.setError(possibleError)
                builder.setState(state)
                builder.setType(Type.ERROR)
                return builder.build()
            }

            val possibleCode = uri.getQueryParameter(AccountsQueryParameters.CODE)
            if (possibleCode != null) {
                val state = uri.getQueryParameter(AccountsQueryParameters.STATE)
                builder.setCode(possibleCode)
                builder.setState(state)
                builder.setType(Type.CODE)
                return builder.build()
            }

            val possibleImplicit = uri.encodedFragment
            if (possibleImplicit != null && possibleImplicit.isNotEmpty()) {
                val parts = possibleImplicit.split("&")
                var accessToken: String? = null
                var state: String? = null
                var expiresIn: String? = null
                for (part in parts) {
                    val partSplit = part.split("=")
                    if (partSplit.size == 2) {
                        if (partSplit[0].startsWith(AccountsQueryParameters.ACCESS_TOKEN)) {
                            accessToken = Uri.decode(partSplit[1])
                        }
                        if (partSplit[0].startsWith(AccountsQueryParameters.STATE)) {
                            state = Uri.decode(partSplit[1])
                        }
                        if (partSplit[0].startsWith(AccountsQueryParameters.EXPIRES_IN)) {
                            expiresIn = Uri.decode(partSplit[1])
                        }
                    }
                }
                builder.setAccessToken(accessToken)
                builder.setState(state)
                if (expiresIn != null) {
                    try {
                        builder.setExpiresIn(expiresIn.toInt())
                    } catch (e: NumberFormatException) {
                        // Ignore
                    }
                }
                builder.setType(Type.TOKEN)
                return builder.build()
            }

            builder.setType(Type.UNKNOWN)
            return builder.build()
        }
    }
}
