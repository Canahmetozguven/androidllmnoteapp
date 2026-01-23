package com.synapsenotes.ai.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("llm_notes_prefs", Context.MODE_PRIVATE)

    var activeChatModelId: String?
        get() = prefs.getString("active_chat_model_id", null)
        set(value) = prefs.edit().putString("active_chat_model_id", value).apply()

    var activeEmbeddingModelId: String?
        get() = prefs.getString("active_embedding_model_id", null)
        set(value) = prefs.edit().putString("active_embedding_model_id", value).apply()
        
    var activeChatModelFilename: String?
        get() = prefs.getString("active_chat_model_filename", null)
        set(value) = prefs.edit().putString("active_chat_model_filename", value).apply()

    var activeEmbeddingModelFilename: String?
        get() = prefs.getString("active_embedding_model_filename", null)
        set(value) = prefs.edit().putString("active_embedding_model_filename", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()
}
