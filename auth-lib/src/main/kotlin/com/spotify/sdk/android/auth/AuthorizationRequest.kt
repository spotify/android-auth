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
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import androidx.annotation.VisibleForTesting

/**
 * An object that helps construct the request that is sent to Spotify authorization service.
 * To create one use [AuthorizationRequest.Builder]
 *
 * @see <a href="https://developer.spotify.com/web-api/authorization-guide">Web API Authorization guide</a>
 */
data class AuthorizationRequest internal constructor(
    val clientId: String,
    val responseType: String,
    val redirectUri: String,
    val state: String?,
    val scopes: Array<String>?,
    val showDialog: Boolean,
    val customParams: Map<String, String>,
    private val campaign: String?,
    val pkceInformation: PKCEInformation?
) : Parcelable {

    @Suppress("DEPRECATION")
    constructor(source: Parcel) : this(
        clientId = source.readString() ?: throw IllegalStateException("clientId cannot be null in parcel"),
        responseType = source.readString() ?: throw IllegalStateException("responseType cannot be null in parcel"),
        redirectUri = source.readString() ?: throw IllegalStateException("redirectUri cannot be null in parcel"),
        state = source.readString(),
        scopes = source.createStringArray(),
        showDialog = source.readByte() == 1.toByte(),
        customParams = HashMap(),
        campaign = source.readString(),
        pkceInformation = source.readParcelable(PKCEInformation::class.java.classLoader)
    ) {
        val bundle = source.readBundle(javaClass.classLoader) ?: Bundle()
        for (key in bundle.keySet()) {
            bundle.getString(key)?.let { value ->
                (customParams as HashMap)[key] = value
            }
        }
    }

    fun getCustomParam(key: String): String? {
        return customParams[key]
    }

    fun getCampaign(): String {
        return campaign?.takeUnless { TextUtils.isEmpty(it) } ?: ANDROID_SDK
    }

    fun getSource(): String = SPOTIFY_SDK

    fun getMedium(): String = ANDROID_SDK

    fun toUri(): Uri {
        val uriBuilder = Uri.Builder()
        uriBuilder.scheme(ACCOUNTS_SCHEME)
            .authority(ACCOUNTS_AUTHORITY)
            .appendPath(ACCOUNTS_PATH)
            .appendQueryParameter(AccountsQueryParameters.CLIENT_ID, clientId)
            .appendQueryParameter(AccountsQueryParameters.RESPONSE_TYPE, responseType)
            .appendQueryParameter(AccountsQueryParameters.REDIRECT_URI, redirectUri)
            .appendQueryParameter(AccountsQueryParameters.SHOW_DIALOG, showDialog.toString())
            .appendQueryParameter(AccountsQueryParameters.UTM_SOURCE, getSource())
            .appendQueryParameter(AccountsQueryParameters.UTM_MEDIUM, getMedium())
            .appendQueryParameter(AccountsQueryParameters.UTM_CAMPAIGN, getCampaign())

        if (scopes != null && scopes.isNotEmpty()) {
            uriBuilder.appendQueryParameter(AccountsQueryParameters.SCOPE, scopesToString())
        }

        if (state != null) {
            uriBuilder.appendQueryParameter(AccountsQueryParameters.STATE, state)
        }

        if (customParams.isNotEmpty()) {
            for ((key, value) in customParams) {
                uriBuilder.appendQueryParameter(key, value)
            }
        }

        if (pkceInformation != null) {
            uriBuilder.appendQueryParameter(AccountsQueryParameters.CODE_CHALLENGE, pkceInformation.challenge)
            uriBuilder.appendQueryParameter(AccountsQueryParameters.CODE_CHALLENGE_METHOD, pkceInformation.codeChallengeMethod)
        }

        return uriBuilder.build()
    }

    private fun scopesToString(): String {
        if (scopes == null) return ""
        val concatScopes = StringBuilder()
        for (scope in scopes) {
            concatScopes.append(scope)
            concatScopes.append(SCOPES_SEPARATOR)
        }
        return concatScopes.toString().trim()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(clientId)
        dest.writeString(responseType)
        dest.writeString(redirectUri)
        dest.writeString(state)
        dest.writeStringArray(scopes)
        dest.writeByte(if (showDialog) 1 else 0)
        dest.writeString(campaign)
        dest.writeParcelable(pkceInformation, flags)

        val bundle = Bundle()
        for ((key, value) in customParams) {
            bundle.putString(key, value)
        }
        dest.writeBundle(bundle)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthorizationRequest

        if (clientId != other.clientId) return false
        if (responseType != other.responseType) return false
        if (redirectUri != other.redirectUri) return false
        if (state != other.state) return false
        if (scopes != null) {
            if (other.scopes == null) return false
            if (!scopes.contentEquals(other.scopes)) return false
        } else if (other.scopes != null) return false
        if (showDialog != other.showDialog) return false
        if (customParams != other.customParams) return false
        if (campaign != other.campaign) return false
        if (pkceInformation != other.pkceInformation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + responseType.hashCode()
        result = 31 * result + redirectUri.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + (scopes?.contentHashCode() ?: 0)
        result = 31 * result + showDialog.hashCode()
        result = 31 * result + customParams.hashCode()
        result = 31 * result + (campaign?.hashCode() ?: 0)
        result = 31 * result + (pkceInformation?.hashCode() ?: 0)
        return result
    }

    /**
     * Use this builder to create an [AuthorizationRequest]
     *
     * @see AuthorizationRequest
     */
    class Builder(
        private val clientId: String,
        private val responseType: AuthorizationResponse.Type,
        private val redirectUri: String
    ) {
        private var state: String? = null
        private var scopes: Array<String>? = null
        private var showDialog: Boolean = false
        private var campaign: String? = null
        private var pkceInformation: PKCEInformation? = null
        private val customParams = HashMap<String, String>()

        init {
            require(redirectUri.isNotEmpty()) { "Redirect URI cannot be empty" }
        }

        fun setState(state: String?) = apply {
            this.state = state
        }

        fun setScopes(scopes: Array<String>?) = apply {
            this.scopes = scopes
        }

        fun setShowDialog(showDialog: Boolean) = apply {
            this.showDialog = showDialog
        }

        fun setCustomParam(key: String, value: String) = apply {
            require(key.isNotEmpty()) { "Custom parameter key cannot be empty" }
            require(value.isNotEmpty()) { "Custom parameter value cannot be empty" }
            customParams[key] = value
        }

        fun setCampaign(campaign: String?) = apply {
            this.campaign = campaign
        }

        fun setPkceInformation(pkceInformation: PKCEInformation?) = apply {
            this.pkceInformation = pkceInformation
        }

        fun build(): AuthorizationRequest {
            return AuthorizationRequest(
                clientId,
                responseType.toString(),
                redirectUri,
                state,
                scopes,
                showDialog,
                customParams,
                campaign,
                pkceInformation
            )
        }
    }

    companion object {
        const val ACCOUNTS_SCHEME = "https"
        const val ACCOUNTS_AUTHORITY = "accounts.spotify.com"
        const val ACCOUNTS_PATH = "authorize"
        const val SCOPES_SEPARATOR = " "
        @VisibleForTesting
        const val SPOTIFY_SDK = "spotify-sdk"
        @VisibleForTesting
        const val ANDROID_SDK = "android-sdk"

        @JvmField
        val CREATOR = object : Parcelable.Creator<AuthorizationRequest> {
            override fun createFromParcel(source: Parcel): AuthorizationRequest {
                return AuthorizationRequest(source)
            }

            override fun newArray(size: Int): Array<AuthorizationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}
