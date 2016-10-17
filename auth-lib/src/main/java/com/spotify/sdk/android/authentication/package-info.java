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

/**
 * Contains classes used in authentication process.
 * When called, SpotifyAuthentication opens a browser window and connects to the Spotify Accounts
 * Service at <a href="https://accounts.spotify.com" target="_top">https://accounts.spotify.com</a>.
 * User authentication and the authorization of scopes then follows the same path as user
 * authentication and authorization for the Spotify Web API.
 * For detailed information, see our <a href="https://developer.spotify.com/web-api/authorization-guide/" target="_top">Web API Authorization Guide.</a>
 */
package com.spotify.sdk.android.authentication;