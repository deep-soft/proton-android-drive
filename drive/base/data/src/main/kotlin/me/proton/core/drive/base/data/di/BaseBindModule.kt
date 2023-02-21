/*
 * Copyright (c) 2022-2023 Proton AG.
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

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import me.proton.core.drive.base.data.formatter.DateTimeFormatterImpl
import me.proton.core.drive.base.data.provider.ExifImageResolutionProvider
import me.proton.core.drive.base.data.provider.MetadataRetrieverVideoResolutionProvider
import me.proton.core.drive.base.data.provider.MimeTypeProviderImpl
import me.proton.core.drive.base.data.usecase.CopyToClipboardImpl
import me.proton.core.drive.base.data.usecase.Sha256Impl
import me.proton.core.drive.base.domain.formatter.DateTimeFormatter
import me.proton.core.drive.base.domain.provider.MediaResolutionProvider
import me.proton.core.drive.base.domain.provider.MimeTypeProvider
import me.proton.core.drive.base.domain.usecase.CopyToClipboard
import me.proton.core.drive.base.domain.usecase.Sha256
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
interface BaseBindModule {

    @Binds
    @Singleton
    fun bindsDateTimeFormatterImpl(impl: DateTimeFormatterImpl): DateTimeFormatter

    @Binds
    @Singleton
    fun bindsCopyToClipboardImpl(impl: CopyToClipboardImpl): CopyToClipboard

    @Binds
    @Singleton
    fun bindsSha256Impl(impl: Sha256Impl): Sha256
}
