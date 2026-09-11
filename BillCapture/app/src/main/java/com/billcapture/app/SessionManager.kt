package com.billcapture.app

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

/**
 * Wraps Google Sign-In state. The Drive scope requested here only grants access
 * to files this app creates (drive.file), which needs no app verification.
 */
object SessionManager {

    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    // Paste your OAuth *web* client ID here if you want an ID token as well.
    // Sign-in and Drive upload work without it as long as the Android OAuth
    // client (package name + SHA-1) exists in your Google Cloud project.
    private const val OAUTH_CLIENT_ID = "YOUR_OAUTH_CLIENT_ID_HERE"

    fun buildSignInClient(context: Context): GoogleSignInClient {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
        if (!OAUTH_CLIENT_ID.startsWith("YOUR_")) {
            builder.requestIdToken(OAUTH_CLIENT_ID)
        }
        return GoogleSignIn.getClient(context, builder.build())
    }

    fun signedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(context: Context): Boolean = signedInAccount(context) != null
}
