package com.example.llmnotes.core.di

import com.example.llmnotes.core.data.repository.ChatRepositoryImpl
import com.example.llmnotes.core.data.repository.GoogleDriveRepository
import com.example.llmnotes.core.data.repository.NoteRepositoryImpl
import com.example.llmnotes.domain.repository.ChatRepository
import com.example.llmnotes.domain.repository.DriveRepository
import com.example.llmnotes.domain.repository.NoteRepository
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
