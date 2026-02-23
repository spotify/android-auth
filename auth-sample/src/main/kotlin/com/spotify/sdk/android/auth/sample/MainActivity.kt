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

package com.spotify.sdk.android.auth.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.sdk.android.auth.BuildConfig
import com.spotify.sdk.android.authentication.sample.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val CLIENT_ID = "089d841ccc194c10a77afad9e1c11d54"
        const val REDIRECT_URI = "spotify-sdk://auth"
        const val AUTH_TOKEN_REQUEST_CODE = 0x10
        const val AUTH_CODE_REQUEST_CODE = 0x11
    }

    private val okHttpClient = OkHttpClient()
    private var accessToken: String? = null
    private var accessCode: String? = null
    private var call: Call? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = String.format(
            Locale.US, "Spotify Auth Sample %s", BuildConfig.LIB_VERSION_NAME
        )
    }

    override fun onDestroy() {
        cancelCall()
        super.onDestroy()
    }

    fun onGetUserProfileClicked(view: View) {
        if (accessToken == null) {
            val snackbar = Snackbar.make(
                findViewById(R.id.activity_main),
                R.string.warning_need_token,
                Snackbar.LENGTH_SHORT
            )
            snackbar.view.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            snackbar.show()
            return
        }

        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        cancelCall()
        call = okHttpClient.newCall(request)

        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                setResponse("Failed to fetch data: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body?.string() ?: "")
                    setResponse(jsonObject.toString(3))
                } catch (e: JSONException) {
                    setResponse("Failed to parse data: $e")
                }
            }
        })
    }

    fun onRequestCodeClicked(view: View) {
        val request = getAuthenticationRequest(AuthorizationResponse.Type.CODE)
        AuthorizationClient.openLoginActivity(this, AUTH_CODE_REQUEST_CODE, request)
    }

    fun onRequestTokenClicked(view: View) {
        val request = getAuthenticationRequest(AuthorizationResponse.Type.TOKEN)
        AuthorizationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request)
    }

    private fun getAuthenticationRequest(type: AuthorizationResponse.Type): AuthorizationRequest {
        return AuthorizationRequest.Builder(CLIENT_ID, type, getRedirectUri().toString())
            .setShowDialog(false)
            .setScopes(arrayOf("user-read-email"))
            .setCampaign("your-campaign-token")
            .build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val response = AuthorizationClient.getResponse(resultCode, data)

        when (requestCode) {
            AUTH_TOKEN_REQUEST_CODE -> {
                accessToken = response.accessToken
                updateTokenView()
            }
            AUTH_CODE_REQUEST_CODE -> {
                accessCode = response.code
                updateCodeView()
            }
        }
    }

    private fun setResponse(text: String) {
        runOnUiThread {
            val responseView = findViewById<TextView>(R.id.response_text_view)
            responseView.text = text
        }
    }

    private fun updateTokenView() {
        val tokenView = findViewById<TextView>(R.id.token_text_view)
        tokenView.text = getString(R.string.token, accessToken)
    }

    private fun updateCodeView() {
        val codeView = findViewById<TextView>(R.id.code_text_view)
        codeView.text = getString(R.string.code, accessCode)
    }

    private fun cancelCall() {
        call?.cancel()
    }

    private fun getRedirectUri(): Uri {
        return Uri.parse(REDIRECT_URI)
    }
}
