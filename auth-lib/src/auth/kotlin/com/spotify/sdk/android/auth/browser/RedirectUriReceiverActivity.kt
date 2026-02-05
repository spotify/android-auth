package com.spotify.sdk.android.auth.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.spotify.sdk.android.auth.LoginActivity

/**
 * Activity that receives the auth response sent by the browser's Custom Tab via deeplink.
 * The sole purpose of this activity is to forward the response back to [LoginActivity].
 * This activity is used only during browser based auth flow - when the Spotify app is not
 * installed on the device.
 */
class RedirectUriReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, LoginActivity::class.java)
        intent.data = getIntent().data
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
