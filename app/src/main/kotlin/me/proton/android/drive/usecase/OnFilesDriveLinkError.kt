/*
 * Copyright (c) 2023 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.drive.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.android.drive.extension.getDefaultMessage
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.data.extension.isRetryable
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.usecase.BroadcastMessages
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.files.presentation.state.ListContentState
import me.proton.core.drive.messagequeue.domain.entity.BroadcastMessage
import me.proton.core.drive.share.domain.exception.ShareException
import javax.inject.Inject
import me.proton.core.drive.i18n.R as I18N

class OnFilesDriveLinkError @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val broadcastMessages: BroadcastMessages,
    private val configurationProvider: ConfigurationProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        previous: DataResult<DriveLink.Folder>?,
        error: DataResult.Error,
        contentState: MutableStateFlow<ListContentState>,
    ) {
        when {
            error.isTransient(previous) -> error.broadcastMessage(userId)
            else -> contentState.emit(error.toListContentState())
        }
    }

    private fun DataResult.Error.isTransient(previous: DataResult<DriveLink.Folder>?): Boolean =
        (previous != null && previous !is DataResult.Error) || cause is ShareException.MainShareLocked

    private fun DataResult.Error.broadcastMessage(userId: UserId) = broadcastMessage?.let { message ->
        broadcastMessages(
            userId = userId,
            message = message,
            type = BroadcastMessage.Type.ERROR,
        )
    }

    private fun DataResult.Error.toListContentState() = ListContentState.Error(
        message = errorMessage,
        actionResId = if (this.isRetryable) I18N.string.common_retry else null
    )

    private val DataResult.Error.broadcastMessage: String? get() =
        when (val cause = this.cause) {
            is NoSuchElementException -> null
            null -> null
            else -> cause.getDefaultMessage(appContext, configurationProvider.useExceptionMessage)
        }

    private val DataResult.Error.errorMessage: String get() =
        broadcastMessage ?: appContext.getString(
            I18N.string.error_could_not_load_folder_content
        )
}
