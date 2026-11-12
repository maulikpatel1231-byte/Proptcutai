package com.example

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys. Defined by Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Ensures FirebaseApp is reliably initialized in this process.
 * First tries standard resource-based initialization, and if that fails or returns null,
 * falls back to explicit FirebaseOptions from the provisioned project configuration.
 */
fun ensureFirebaseInitialized(context: Context): FirebaseApp {
    if (FirebaseApp.getApps(context).isNotEmpty()) {
        return FirebaseApp.getInstance()
    }

    // 1. Try standard automatic initialization
    try {
        val app = FirebaseApp.initializeApp(context)
        if (app != null) {
            Log.d(TAG, "FirebaseApp successfully initialized from resources.")
            return app
        }
    } catch (e: Exception) {
        Log.w(TAG, "Standard FirebaseApp.initializeApp returned exception, using explicit fallback options", e)
    }

    // 2. Explicit options fallback guaranteeing initialization in all environments (CI builds, APKs, emulators)
    val options = FirebaseOptions.Builder()
        .setApplicationId("1:529705054152:android:e9e6c374a091fe017c5afd")
        .setApiKey("AIzaSyA8e8nEtFJDLkAITDURhHdca6aonjqq6-4")
        .setProjectId("gen-lang-client-0080109122")
        .setGcmSenderId("529705054152")
        .setStorageBucket("gen-lang-client-0080109122.firebasestorage.app")
        .build()

    return try {
        val app = FirebaseApp.initializeApp(context.applicationContext, options)
        Log.d(TAG, "FirebaseApp successfully initialized with explicit options.")
        app
    } catch (e: Exception) {
        Log.e(TAG, "Explicit FirebaseApp.initializeApp failed", e)
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseApp.getInstance()
        } else {
            throw e
        }
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is fully initialized before App Check operations
        val app = ensureFirebaseInitialized(context)

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = app.persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // CRITICAL: MUST use commit() instead of apply() so secret is written synchronously
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            // 3. Release: Look up recaptcha_site_key safely
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
