package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

enum class SyncState(val label: String) {
    DISCONNECTED("Not Signed In"),
    SYNCING("Syncing…"),
    SYNCED("Synced"),
    OFFLINE("Offline Queue"),
    ERROR("Sync Error")
}

data class CloudSyncStatus(
    val syncState: SyncState = SyncState.DISCONNECTED,
    val lastSyncedAtMillis: Long = 0L,
    val lastSyncedDevice: String = "",
    val errorMessage: String? = null
)

fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String?
): Nothing {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val errInfo = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path ?: "")
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid ?: "")
                put("email", user?.email ?: "")
                put("emailVerified", user?.isEmailVerified ?: false)
            }
        )
    }
    val json = errInfo.toString()
    Log.e("FirestoreError", json)
    throw RuntimeException("Firestore Error: $json", error)
}
