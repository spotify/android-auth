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
import android.os.Parcelable;

/**
 * An object that contains the parsed response from the Spotify authorization service.
 * To create one use {@link AuthorizationResponse.Builder} or
 * parse from {@link android.net.Uri} with {@link #fromUri(android.net.Uri)}
 *
 * @see <a href="https://developer.spotify.com/web-api/authorization-guide">Web API Authorization guide</a>
 */
public class AuthorizationResponse implements Parcelable {

    /**
     * The type of the authorization response.
     */
    public enum Type {
        /**
         * The response is a code reply.
         *
         * @see <a href="https://developer.spotify.com/web-api/authorization-guide/#authorization-code-flow">Authorization code flow</a>
         */
        CODE("code"),

        /**
         * The response is an implicit grant with access token.
         *
         * @see <a href="https://developer.spotify.com/web-api/authorization-guide/#implicit-grant-flow">Implicit grant flow</a>
         */
        TOKEN("token"),

        /**
         * The response is an error response.
         *
         * @see <a href="https://developer.spotify.com/web-api/authorization-guide">Web API Authorization guide</a>
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

        private final String mType;

        Type(String type) {
            mType = type;
        }

        @Override
        public String toString() {
            return mType;
        }
    }

    private final Type mType;
    private final String mCode;
    private final String mAccessToken;
    private final String mState;
    private final String mError;
    private final int mExpiresIn;

    /**
     * Use this builder to create an {@link AuthorizationResponse}
     *
     * @see AuthorizationResponse
     */
    public static class Builder {

        private Type mType;
        private String mCode;
        private String mAccessToken;
        private String mState;
        private String mError;
        private int mExpiresIn;

        Builder setType(Type type) {
            mType = type;
            return this;
        }

        Builder setCode(String code) {
            mCode = code;
            return this;
        }

        Builder setAccessToken(String accessToken) {
            mAccessToken = accessToken;
            return this;
        }

        Builder setState(String state) {
            mState = state;
            return this;
        }

        Builder setError(String error) {
            mError = error;
            return this;
        }

        Builder setExpiresIn(int expiresIn) {
            mExpiresIn = expiresIn;
            return this;
        }

        AuthorizationResponse build() {
            return new AuthorizationResponse(mType, mCode, mAccessToken, mState, mError, mExpiresIn);
        }
    }

    private AuthorizationResponse(Type type,
                                  String code,
                                  String accessToken,
                                  String state,
                                  String error,
                                  int expiresIn) {
        mType = type != null ? type : Type.UNKNOWN;
        mCode = code;
        mAccessToken = accessToken;
        mState = state;
        mError = error;
        mExpiresIn = expiresIn;
    }

    public AuthorizationResponse(Parcel source) {
        mExpiresIn = source.readInt();
        mError = source.readString();
        mState = source.readString();
        mAccessToken = source.readString();
        mCode = source.readString();
        mType = Type.values()[source.readInt()];
    }

    /**
     * Parses the URI returned from the Spotify accounts service.
     *
     * @param uri URI
     * @return Authorization response. If parsing failed, this object will be populated with
     * the given error codes.
     */
    public static AuthorizationResponse fromUri(Uri uri) {
        AuthorizationResponse.Builder builder = new AuthorizationResponse.Builder();
        if (uri == null) {
            builder.setType(Type.EMPTY);
            return builder.build();
        }

        String possibleError = uri.getQueryParameter(AccountsQueryParameters.ERROR);
        if (possibleError != null) {
            String state = uri.getQueryParameter(AccountsQueryParameters.STATE);
            builder.setError(possibleError);
            builder.setState(state);
            builder.setType(Type.ERROR);
            return builder.build();
        }

        String possibleCode = uri.getQueryParameter(AccountsQueryParameters.CODE);
        if (possibleCode != null) {
            String state = uri.getQueryParameter(AccountsQueryParameters.STATE);
            builder.setCode(possibleCode);
            builder.setState(state);
            builder.setType(Type.CODE);
            return builder.build();
        }

        String possibleImplicit = uri.getEncodedFragment();
        if (possibleImplicit != null && possibleImplicit.length() > 0) {
            String[] parts = possibleImplicit.split("&");
            String accessToken = null;
            String state = null;
            String expiresIn = null;
            for (String part : parts) {
                String[] partSplit = part.split("=");
                if (partSplit.length == 2) {
                    if (partSplit[0].startsWith(AccountsQueryParameters.ACCESS_TOKEN)) {
                        accessToken = Uri.decode(partSplit[1]);
                    }
                    if (partSplit[0].startsWith(AccountsQueryParameters.STATE)) {
                        state = Uri.decode(partSplit[1]);
                    }
                    if (partSplit[0].startsWith(AccountsQueryParameters.EXPIRES_IN)) {
                        expiresIn = Uri.decode(partSplit[1]);
                    }
                }
            }
            builder.setAccessToken(accessToken);
            builder.setState(state);
            if (expiresIn != null) {
                try {
                    builder.setExpiresIn(Integer.parseInt(expiresIn));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            builder.setType(Type.TOKEN);
            return builder.build();
        }

        builder.setType(Type.UNKNOWN);
        return builder.build();
    }

    public Type getType() {
        return mType;
    }

    public String getCode() {
        return mCode;
    }

    public String getAccessToken() {
        return mAccessToken;
    }

    public String getState() {
        return mState;
    }

    public String getError() {
        return mError;
    }

    public int getExpiresIn() {
        return mExpiresIn;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mExpiresIn);
        dest.writeString(mError);
        dest.writeString(mState);
        dest.writeString(mAccessToken);
        dest.writeString(mCode);
        dest.writeInt(mType.ordinal());
    }

    public static final Parcelable.Creator<AuthorizationResponse> CREATOR = new Parcelable.Creator<AuthorizationResponse>() {
        @Override
        public AuthorizationResponse createFromParcel(Parcel source) {
            return new AuthorizationResponse(source);
        }

        @Override
        public AuthorizationResponse[] newArray(int size) {
            return new AuthorizationResponse[size];
        }
    };
}
