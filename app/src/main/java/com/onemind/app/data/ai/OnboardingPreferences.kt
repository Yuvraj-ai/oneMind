package com.onemind.app.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "onemind_prefs")

/**
 * Manages onboarding and provider preference persistence via DataStore.
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_ACTIVE_PROVIDER_TYPE = stringPreferencesKey("active_provider_type")
        private val KEY_ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        private val KEY_CLOUD_BASE_URL = stringPreferencesKey("cloud_base_url")
        private val KEY_CLOUD_API_KEY = stringPreferencesKey("cloud_api_key")
        private val KEY_CLOUD_MODEL_NAME = stringPreferencesKey("cloud_model_name")
        private val KEY_CLOUD_SUPPORTS_VISION = booleanPreferencesKey("cloud_supports_vision")
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETE] == true
    }

    val activeModelId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_MODEL_ID]
    }

    val activeProviderType: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROVIDER_TYPE]
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun setActiveLocalModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROVIDER_TYPE] = "LOCAL"
            prefs[KEY_ACTIVE_MODEL_ID] = modelId
        }
    }

    suspend fun setActiveCloudProvider(baseUrl: String, apiKey: String, modelName: String, supportsVision: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROVIDER_TYPE] = "CLOUD"
            prefs[KEY_CLOUD_BASE_URL] = baseUrl
            prefs[KEY_CLOUD_API_KEY] = apiKey
            prefs[KEY_CLOUD_MODEL_NAME] = modelName
            prefs[KEY_CLOUD_SUPPORTS_VISION] = supportsVision
        }
    }

    /**
     * The stored cloud configuration, or null if none was saved.
     *
     * Uses `first()`, not `collect`. `DataStore.data` is an infinite flow — it emits
     * on every subsequent write and never completes — so collecting it here suspended
     * forever and the function never returned. It had no callers, which is the only
     * reason that had not been noticed.
     */
    suspend fun getCloudConfig(): CloudConfig? {
        val prefs = context.dataStore.data.first()
        val baseUrl = prefs[KEY_CLOUD_BASE_URL]
        val apiKey = prefs[KEY_CLOUD_API_KEY]
        val modelName = prefs[KEY_CLOUD_MODEL_NAME]
        val supportsVision = prefs[KEY_CLOUD_SUPPORTS_VISION] ?: false

        if (baseUrl == null || apiKey == null || modelName == null) return null
        return CloudConfig(baseUrl, apiKey, modelName, supportsVision)
    }

    /** Which provider the user last activated, or null if they never did. */
    suspend fun getActiveProviderType(): String? =
        context.dataStore.data.first()[KEY_ACTIVE_PROVIDER_TYPE]
}
