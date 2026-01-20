package com.example.llmnotes.core.di

import com.example.llmnotes.core.ai.DefaultHardwareCapabilityProvider
import com.example.llmnotes.core.ai.DefaultLlmContext
import com.example.llmnotes.core.ai.HardwareCapabilityProvider
import com.example.llmnotes.core.ai.LlmContext
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindHardwareCapabilityProvider(
        defaultHardwareCapabilityProvider: DefaultHardwareCapabilityProvider
    ): HardwareCapabilityProvider

    @Binds
    @Singleton
    abstract fun bindLlmContext(
        defaultLlmContext: DefaultLlmContext
    ): LlmContext
}
