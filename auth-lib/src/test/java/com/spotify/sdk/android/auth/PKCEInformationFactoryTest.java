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

import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PKCEInformationFactoryTest {

    @Test
    public void shouldCreatePKCEInformation() throws NoSuchAlgorithmException {
        PKCEInformation pkceInfo = PKCEInformationFactory.create();

        assertNotNull(pkceInfo);
        assertNotNull(pkceInfo.getVerifier());
        assertNotNull(pkceInfo.getChallenge());
        assertEquals("S256", pkceInfo.getCodeChallengeMethod());
    }

    @Test
    public void shouldGenerateCodeVerifierWithCorrectLength() throws NoSuchAlgorithmException {
        PKCEInformation pkceInfo = PKCEInformationFactory.create();

        assertEquals(128, pkceInfo.getVerifier().length());
    }

    @Test
    public void shouldGenerateUniqueCodeVerifiers() throws NoSuchAlgorithmException {
        PKCEInformation pkceInfo1 = PKCEInformationFactory.create();
        PKCEInformation pkceInfo2 = PKCEInformationFactory.create();

        assertTrue(!pkceInfo1.getVerifier().equals(pkceInfo2.getVerifier()));
        assertTrue(!pkceInfo1.getChallenge().equals(pkceInfo2.getChallenge()));
    }

    @Test
    public void shouldGenerateValidBase64UrlEncodedChallenge() throws NoSuchAlgorithmException {
        PKCEInformation pkceInfo = PKCEInformationFactory.create();

        String challenge = pkceInfo.getChallenge();
        assertTrue(challenge.matches("^[A-Za-z0-9_-]+$"));
        assertTrue(!challenge.contains("="));
        assertTrue(!challenge.contains("+"));
        assertTrue(!challenge.contains("/"));
    }
}

