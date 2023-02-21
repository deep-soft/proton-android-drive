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

package me.proton.core.drive.trash.data.manager.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.data.workmanager.addTags
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.eventmanager.base.domain.usecase.UpdateEventAction
import me.proton.core.drive.base.domain.usecase.BroadcastMessages
import me.proton.core.drive.base.presentation.extension.log
import me.proton.core.drive.messagequeue.domain.entity.BroadcastMessage
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.trash.data.R
import me.proton.core.drive.trash.domain.notification.EmptyTrashExtra
import me.proton.core.drive.trash.domain.repository.DriveTrashRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class EmptyTrashWorker @AssistedInject constructor(
    private val driveTrashRepository: DriveTrashRepository,
    private val broadcastMessages: BroadcastMessages,
    private val updateEventAction: UpdateEventAction,
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val userId = UserId(inputData.getString(KEY_USER_ID) ?: "")
    private val shareId = ShareId(userId, inputData.getString(KEY_SHARE_ID) ?: "")

    override suspend fun doWork(): Result {
        try {
            updateEventAction(shareId) { driveTrashRepository.emptyTrash(shareId) }
            // Currently, we'll retrieve the content we just deleted but the backend will
            // improve the flow in the future where they will not returned item which have been
            // emptied from the trash. This comment can be removed when it's done [DRVWEB-1464]
            driveTrashRepository.refreshTrashContent(shareId)
            broadcastMessages(
                userId = userId,
                message = applicationContext.getString(R.string.trash_empty_operation_successful),
                type = BroadcastMessage.Type.SUCCESS,
                extra = EmptyTrashExtra(userId, shareId)
            )
            return Result.success()
        } catch (e: Exception) {
            e.log(LogTag.TRASH)
            broadcastMessages(
                userId = userId,
                message = applicationContext.getString(R.string.trash_error_occurred_emptying_trash),
                type = BroadcastMessage.Type.ERROR,
                extra = EmptyTrashExtra(userId, shareId, e)
            )
            return Result.failure()
        }
    }

    companion object {
        private const val KEY_USER_ID = "KEY_USER_ID"
        private const val KEY_SHARE_ID = "KEY_SHARE_ID"

        fun getWorkRequest(
            userId: UserId,
            shareId: ShareId,
            tags: List<String> = emptyList(),
        ): OneTimeWorkRequest = OneTimeWorkRequest.Builder(EmptyTrashWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putString(KEY_USER_ID, userId.id)
                    .putString(KEY_SHARE_ID, shareId.id)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTags(listOf(userId.id) + tags)
            .build()
    }
}
