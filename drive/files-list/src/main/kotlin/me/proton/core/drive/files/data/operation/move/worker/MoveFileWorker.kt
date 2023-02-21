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

package me.proton.core.drive.files.data.operation.move.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.data.api.ProtonApiCode
import me.proton.core.drive.base.data.workmanager.addTags
import me.proton.core.drive.base.data.workmanager.onProtonHttpException
import me.proton.core.drive.base.domain.extension.resultValueOrNull
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.usecase.BroadcastMessages
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.drivelink.selection.domain.usecase.GetSelectedDriveLinks
import me.proton.core.drive.files.R
import me.proton.core.drive.files.domain.operation.notification.MoveFileExtra
import me.proton.core.drive.files.domain.usecase.ChangeParent
import me.proton.core.drive.link.domain.entity.Folder
import me.proton.core.drive.link.domain.entity.FolderId
import me.proton.core.drive.link.domain.entity.LinkId
import me.proton.core.drive.link.domain.usecase.GetLink
import me.proton.core.drive.link.selection.domain.entity.SelectionId
import me.proton.core.drive.link.selection.domain.usecase.DeselectLinks
import me.proton.core.drive.messagequeue.domain.entity.BroadcastMessage
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.util.kotlin.CoreLogger
import java.util.Collections
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@HiltWorker
class MoveFileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val getLink: GetLink,
    private val broadcastMessages: BroadcastMessages,
    private val changeParent: ChangeParent,
    private val getSelectedDriveLinks: GetSelectedDriveLinks,
    private val deselectLinks: DeselectLinks,
) : CoroutineWorker(appContext, params) {

    private val userId = UserId(requireNotNull(inputData.getString(KEY_USER_ID)))
    private val selectionId = SelectionId(requireNotNull(inputData.getString(KEY_SELECTION_ID)))
    private val moveToFolderId = FolderId(
        ShareId(userId, requireNotNull(inputData.getString(KEY_MOVE_TO_SHARE_ID))),
        requireNotNull(inputData.getString(KEY_MOVE_TO_FOLDER_ID)),
    )
    private val allowUndo = inputData.getBoolean(KEY_ALLOW_UNDO, true)

    override suspend fun doWork(): Result = supervisorScope {
        val driveLinks = getSelectedDriveLinks(selectionId).first()
        val folder = getLink(moveToFolderId).resultValueOrNull()
        if (driveLinks.isEmpty() || folder == null) {
            broadcastMessages(
                userId = userId,
                message = applicationContext.getString(R.string.file_operation_error_occurred_moving_file),
                type = BroadcastMessage.Type.ERROR
            )
            deselectLinks(selectionId)
            return@supervisorScope Result.failure()
        }
        val succeeded: MutableList<DriveLink> = Collections.synchronizedList(arrayListOf())
        val deferred = driveLinks.map { driveLink ->
            async {
                changeParent(driveLink.id, folder.id)
                    .onSuccess {
                        succeeded.add(driveLink)
                    }
                    .onFailure { error ->
                        if (!error.handledFileAlreadyExists()) {
                            CoreLogger.d(LogTag.MOVE, error, "An error occurred while moving the file")
                            broadcastMessages(
                                userId = userId,
                                message = applicationContext.getString(R.string.file_operation_error_occurred_moving_file),
                                type = BroadcastMessage.Type.ERROR,
                                extra = MoveFileExtra(
                                    userId = userId,
                                    links = listOf(driveLink.parentId to driveLink.id),
                                    folderId = moveToFolderId,
                                    allowUndo = allowUndo,
                                    exception = error,
                                )
                            )
                        }
                    }
            }
        }
        try {
            deferred.awaitAll()
            if (succeeded.isNotEmpty()) {
                broadcastMessages(
                    userId = userId,
                    message = succeeded.message,
                    extra = MoveFileExtra(
                        userId = userId,
                        links = succeeded.map { driveLink -> driveLink.parentId to driveLink.id },
                        folderId = moveToFolderId,
                        allowUndo = allowUndo,
                    )
                )
            }
        } catch (e: CancellationException) {
            CoreLogger.d(LogTag.MOVE, e, e.message.orEmpty())
            cancel()
        }
        deselectLinks(selectionId)
        return@supervisorScope Result.success()
    }

    private val List<DriveLink>.message: String get() = when (size) {
        1 -> applicationContext.getString(
            if (first() is Folder) {
                R.string.file_operation_moving_folder_successful
            } else {
                R.string.file_operation_moving_file_successful
            }
        )
        else -> applicationContext.getString(
            R.string.file_operation_moving_multiple_successful,
            size,
        )
    }

    private fun Throwable.handledFileAlreadyExists(): Boolean =
        onProtonHttpException { protonCode ->
            if (protonCode == ProtonApiCode.ALREADY_EXISTS) {
                broadcastMessages(
                    userId = userId,
                    message = applicationContext.getString(
                        R.string.file_operation_error_file_already_exists_at_destination
                    ),
                    type = BroadcastMessage.Type.ERROR,
                )
                return@onProtonHttpException true
            }
            return@onProtonHttpException false
        } ?: false

    companion object {

        private const val KEY_USER_ID = "KEY_USER_ID"
        private const val KEY_SELECTION_ID = "KEY_SELECTION_ID"
        private const val KEY_MOVE_TO_FOLDER_ID = "KEY_MOVE_TO_FOLDER_ID"
        private const val KEY_MOVE_TO_SHARE_ID = "KEY_MOVE_TO_SHARE_ID"
        private const val KEY_ALLOW_UNDO = "KEY_ALLOW_UNDO"

        fun getWorkRequest(
            userId: UserId,
            selectionId: SelectionId,
            folderId: LinkId,
            allowUndo: Boolean,
            tags: Collection<String> = emptyList(),
        ): OneTimeWorkRequest = OneTimeWorkRequest.Builder(MoveFileWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putString(KEY_USER_ID, userId.id)
                    .putString(KEY_SELECTION_ID, selectionId.id)
                    .putString(KEY_MOVE_TO_FOLDER_ID, folderId.id)
                    .putString(KEY_MOVE_TO_SHARE_ID, folderId.shareId.id)
                    .putBoolean(KEY_ALLOW_UNDO, allowUndo)
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
