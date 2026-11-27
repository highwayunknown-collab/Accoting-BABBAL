package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CloudAuthManager(private val appContext: Context) {

    private val auth: FirebaseAuth = Firebase.auth

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun attemptAutoSignIn(activityContext: Context): Result<FirebaseUser?> {
        if (auth.currentUser != null) return Result.success(auth.currentUser)
        return try {
            val webClientId = activityContext.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val user = handleGoogleCredentialResult(result.credential)
            Result.success(user)
        } catch (_: GetCredentialCancellationException) {
            Result.success(null)
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (e: GetCredentialException) {
            Log.d("CloudAuthManager", "Silent sign-in skipped: ${e.message}")
            Result.success(null)
        } catch (e: Exception) {
            Log.w("CloudAuthManager", "Silent sign-in failed", e)
            Result.success(null)
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        return try {
            val webClientId = activityContext.getString(R.string.default_web_client_id)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val user = handleGoogleCredentialResult(result.credential)
                ?: return Result.failure(IllegalStateException("Google Sign-In did not return a user account."))
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in cancelled", e))
        } catch (e: GetCredentialException) {
            Result.failure(Exception(e.message ?: "Google Sign-In unavailable on this device.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun handleGoogleCredentialResult(credential: androidx.credentials.Credential): FirebaseUser? {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            return authResult.user
        }
        return null
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            auth.signOut()
            runCatching {
                val credentialManager = CredentialManager.create(appContext)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}
