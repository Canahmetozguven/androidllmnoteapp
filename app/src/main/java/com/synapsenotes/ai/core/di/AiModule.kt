package com.synapsenotes.ai.core.di

import com.synapsenotes.ai.core.ai.DefaultHardwareCapabilityProvider
import com.synapsenotes.ai.core.ai.DefaultLlmContext
import com.synapsenotes.ai.core.ai.HardwareCapabilityProvider
import com.synapsenotes.ai.core.ai.LlmContext
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
