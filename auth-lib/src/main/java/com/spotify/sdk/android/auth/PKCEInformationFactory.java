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

import android.util.Base64;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class PKCEInformationFactory {

    private static final int CODE_VERIFIER_LENGTH = 128;
    private static final String CODE_VERIFIER_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    @NonNull
    public static PKCEInformation create() throws NoSuchAlgorithmException {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        return PKCEInformation.sha256(codeVerifier, codeChallenge);
    }

    @NonNull
    private static String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder codeVerifier = new StringBuilder();

        for (int i = 0; i < CODE_VERIFIER_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(CODE_VERIFIER_CHARSET.length());
            codeVerifier.append(CODE_VERIFIER_CHARSET.charAt(randomIndex));
        }

        return codeVerifier.toString();
    }

    @NonNull
    private static String generateCodeChallenge(@NonNull String codeVerifier) throws NoSuchAlgorithmException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes("US-ASCII"));
            return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (final java.io.UnsupportedEncodingException e) {
            // US-ASCII is guaranteed to be supported on all platforms
            throw new RuntimeException("US-ASCII encoding not supported", e);
        }
    }
}

