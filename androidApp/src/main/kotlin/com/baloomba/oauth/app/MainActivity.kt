package com.baloomba.oauth.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Placeholder entry point. Replaced by the Compose host in phase 5. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "OAuth scaffold" })
    }
}
