/*
 * Copyright (c) 2021-2023 Proton AG.
 * This file is part of Proton Core.
 *
 * Proton Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Core.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.proton.core.drive.base.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.proton.core.drive.base.data.provider.ExifImageResolutionProvider
import me.proton.core.drive.base.data.provider.MetadataRetrieverVideoResolutionProvider
import me.proton.core.drive.base.data.provider.MimeTypeProviderImpl
import me.proton.core.drive.base.data.provider.StorageLocationProviderImpl
import me.proton.core.drive.base.domain.provider.MediaResolutionProvider
import me.proton.core.drive.base.domain.provider.MimeTypeProvider
import me.proton.core.drive.base.domain.provider.StorageLocationProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BaseModule {
    @Singleton
    @Provides
    fun provideStorageLocationProvider(
        @ApplicationContext appContext: Context,
    ): StorageLocationProvider =
        StorageLocationProviderImpl(appContext)

    @Singleton
    @Provides
    @IntoSet
    fun provideImageResolutionProvider(
        @ApplicationContext appContext: Context,
    ): MediaResolutionProvider =
        ExifImageResolutionProvider(appContext)

    @Singleton
    @Provides
    @IntoSet
    fun provideVideoResolutionProvider(
        @ApplicationContext appContext: Context,
    ): MediaResolutionProvider =
        MetadataRetrieverVideoResolutionProvider(appContext)

    @Singleton
    @Provides
    fun provideMimeTypeProvider(): MimeTypeProvider =
        MimeTypeProviderImpl(Job() + Dispatchers.IO)
}
