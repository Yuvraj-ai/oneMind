package com.onemind.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for AI/LLM infrastructure.
 *
 * LocalModelProvider, CloudModelProvider, ProviderManager, ModelRegistry,
 * and ModelDownloadManager are all @Inject-constructable singletons,
 * so they don't need explicit @Provides methods.
 *
 * This module exists as a placeholder for future provider bindings
 * (e.g. binding LlmProvider interface to the active provider).
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule
