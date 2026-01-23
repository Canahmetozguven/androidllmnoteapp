package com.synapsenotes.ai.core.di

import com.synapsenotes.ai.core.data.repository.ChatRepositoryImpl
import com.synapsenotes.ai.core.data.repository.GoogleDriveRepository
import com.synapsenotes.ai.core.data.repository.NoteRepositoryImpl
import com.synapsenotes.ai.domain.repository.ChatRepository
import com.synapsenotes.ai.domain.repository.DriveRepository
import com.synapsenotes.ai.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        noteRepositoryImpl: NoteRepositoryImpl
    ): NoteRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindDriveRepository(
        googleDriveRepository: GoogleDriveRepository
    ): DriveRepository
}
