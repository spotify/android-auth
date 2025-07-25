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

import static com.spotify.sdk.android.auth.AccountsQueryParameters.ASSOCIATED_CONTENT;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * An object that helps construct the request that is sent to Spotify authorization service.
 * To create one use {@link AuthorizationRequest.Builder}
 *
 * @see <a href="https://developer.spotify.com/web-api/authorization-guide">Web API Authorization guide</a>
 */
public class AuthorizationRequest implements Parcelable {

    static final String ACCOUNTS_SCHEME = "https";
    static final String ACCOUNTS_AUTHORITY = "accounts.spotify.com";
    static final String ACCOUNTS_PATH = "authorize";
    static final String SCOPES_SEPARATOR = " ";
    @VisibleForTesting
    public static final String SPOTIFY_SDK = "spotify-sdk";
    @VisibleForTesting
    public static final String ANDROID_SDK = "android-sdk";
    private static final String KEY_URI = "uri";
    private static final String KEY_URL = "url";

    private final String mClientId;
    private final String mResponseType;
    private final String mRedirectUri;
    private final String mState;
    private final String[] mScopes;
    private final boolean mShowDialog;
    private final Map<String, String> mCustomParams;
    private final String mCampaign;
    private final String mContentUri;
    private final String mContentUrl;

    /**
     * Use this builder to create an {@link AuthorizationRequest}
     *
     * @see AuthorizationRequest
     */
    public static class Builder {

        private final String mClientId;
        private final AuthorizationResponse.Type mResponseType;
        private final String mRedirectUri;

        private String mState;
        private String[] mScopes;
        private boolean mShowDialog;
        private String mCampaign;
        private final Map<String, String> mCustomParams = new HashMap<>();
        private String mContentUri;
        private String mContentUrl;

        public Builder(String clientId, AuthorizationResponse.Type responseType, String redirectUri) {
            if (clientId == null) {
                throw new IllegalArgumentException("Client ID can't be null");
            }
            if (responseType == null) {
                throw new IllegalArgumentException("Response type can't be null");
            }
            if (redirectUri == null || redirectUri.length() == 0) {
                throw new IllegalArgumentException("Redirect URI can't be null or empty");
            }

            mClientId = clientId;
            mResponseType = responseType;
            mRedirectUri = redirectUri;
        }

        public Builder setState(String state) {
            mState = state;
            return this;
        }

        public Builder setScopes(String[] scopes) {
            mScopes = scopes;
            return this;
        }

        public Builder setShowDialog(boolean showDialog) {
            mShowDialog = showDialog;
            return this;
        }

        public Builder setCustomParam(String key, String value) {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("Custom parameter key can't be null or empty");
            }
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Custom parameter value can't be null or empty");
            }
            mCustomParams.put(key, value);
            return this;
        }

        public Builder setCampaign(String campaign) {
            mCampaign = campaign;
            return this;
        }

        public Builder setContentUri(String contentUri) {
            mContentUri = contentUri;
            return this;
        }

        public Builder setContentUrl(String contentUrl) {
            mContentUrl = contentUrl;
            return this;
        }

        public AuthorizationRequest build() {
            return new AuthorizationRequest(mClientId, mResponseType, mRedirectUri,
                    mState, mScopes, mShowDialog, mCustomParams, mCampaign, mContentUri, mContentUrl);
        }
    }

    public AuthorizationRequest(Parcel source) {
        mClientId = source.readString();
        mResponseType = source.readString();
        mRedirectUri = source.readString();
        mState = source.readString();
        mScopes = source.createStringArray();
        mShowDialog = source.readByte() == 1;
        mCustomParams = new HashMap<>();
        mCampaign = source.readString();
        mContentUri = source.readString();
        mContentUrl = source.readString();
        Bundle bundle = source.readBundle(getClass().getClassLoader());
        for (String key : bundle.keySet()) {
            mCustomParams.put(key, bundle.getString(key));
        }
    }

    public String getClientId() {
        return mClientId;
    }

    public String getResponseType() {
        return mResponseType;
    }

    public String getRedirectUri() {
        return mRedirectUri;
    }

    public String getState() {
        return mState;
    }

    public String[] getScopes() {
        return mScopes;
    }

    public String getCustomParam(String key) {
        return mCustomParams.get(key);
    }

    public String getEncodedContent() {
        JSONObject contentJson = getContentsJson();

        if(contentJson.length() == 0) {
            return null; // No content to encode
        }

        return Base64.encodeToString(contentJson.toString().getBytes(Charset.forName("UTF-8")), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    @NonNull
    private JSONObject getContentsJson() {
        JSONObject contentJson = new JSONObject();

        if(mContentUri != null && !mContentUri.isEmpty()) {
            try {
                contentJson.put(KEY_URI, mContentUri);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error creating JSON for content URI: " + e.getMessage());
            }
        }

        if(mContentUrl != null && !mContentUrl.isEmpty()) {
            try {
                contentJson.put(KEY_URL, mContentUrl);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error creating JSON for content URL: " + e.getMessage());
            }
        }
        return contentJson;
    }

    @NonNull
    public String getCampaign() { return TextUtils.isEmpty(mCampaign) ? ANDROID_SDK : mCampaign; }

    @NonNull
    public String getSource() { return SPOTIFY_SDK; }

    @NonNull
    public String getMedium() { return ANDROID_SDK; }

    private AuthorizationRequest(String clientId,
                                 AuthorizationResponse.Type responseType,
                                 String redirectUri,
                                 String state,
                                 String[] scopes,
                                 boolean showDialog,
                                 Map<String, String> customParams,
                                 String campaign,
                                 String contentUri,
                                 String contentUrl
                                 ) {

        mClientId = clientId;
        mResponseType = responseType.toString();
        mRedirectUri = redirectUri;
        mState = state;
        mScopes = scopes;
        mShowDialog = showDialog;
        mCustomParams = customParams;
        mCampaign = campaign;
        mContentUri = contentUri;
        mContentUrl = contentUrl;
    }

    public Uri toUri() {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme(ACCOUNTS_SCHEME)
                .authority(ACCOUNTS_AUTHORITY)
                .appendPath(ACCOUNTS_PATH)
                .appendQueryParameter(AccountsQueryParameters.CLIENT_ID, mClientId)
                .appendQueryParameter(AccountsQueryParameters.RESPONSE_TYPE, mResponseType)
                .appendQueryParameter(AccountsQueryParameters.REDIRECT_URI, mRedirectUri)
                .appendQueryParameter(AccountsQueryParameters.SHOW_DIALOG, String.valueOf(mShowDialog))
                .appendQueryParameter(AccountsQueryParameters.UTM_SOURCE, getSource())
                .appendQueryParameter(AccountsQueryParameters.UTM_MEDIUM, getMedium())
                .appendQueryParameter(AccountsQueryParameters.UTM_CAMPAIGN, getCampaign());

        if (mScopes != null && mScopes.length > 0) {
            uriBuilder.appendQueryParameter(AccountsQueryParameters.SCOPE, scopesToString());
        }

        if (mState != null) {
            uriBuilder.appendQueryParameter(AccountsQueryParameters.STATE, mState);
        }

        if (mCustomParams.size() > 0) {
            for (Map.Entry<String, String> entry : mCustomParams.entrySet()) {
                uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
        }

        String associatedContent = getEncodedContent();

        if(associatedContent != null) {
            uriBuilder.appendQueryParameter(ASSOCIATED_CONTENT, associatedContent);
        }

        return uriBuilder.build();
    }

    private String scopesToString() {
        StringBuilder concatScopes = new StringBuilder();
        for (String scope : mScopes) {
            concatScopes.append(scope);
            concatScopes.append(SCOPES_SEPARATOR);
        }
        return concatScopes.toString().trim();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mClientId);
        dest.writeString(mResponseType);
        dest.writeString(mRedirectUri);
        dest.writeString(mState);
        dest.writeStringArray(mScopes);
        dest.writeByte((byte) (mShowDialog ? 1 : 0));
        dest.writeString(mCampaign);
        dest.writeString(mContentUri);
        dest.writeString(mContentUrl);

        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : mCustomParams.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        dest.writeBundle(bundle);
    }

    public static final Parcelable.Creator<AuthorizationRequest> CREATOR = new Parcelable.Creator<AuthorizationRequest>() {

        @Override
        public AuthorizationRequest createFromParcel(Parcel source) {
            return new AuthorizationRequest(source);
        }

        @Override
        public AuthorizationRequest[] newArray(int size) {
            return new AuthorizationRequest[size];
        }
    };
}
