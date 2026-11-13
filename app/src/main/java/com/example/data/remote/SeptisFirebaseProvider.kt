package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object SeptisFirebaseProvider {
    @Volatile
    private var configured = false

    fun getFirestore(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                            .build()
                    } catch (_: IllegalStateException) {
                        // Already configured prior to first call
                    }
                    configured = true
                }
            }
        }
        return db
    }
}
