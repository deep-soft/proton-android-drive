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
package me.proton.core.drive.upload.data.worker

import androidx.work.Operation
import androidx.work.WorkManager
import me.proton.core.domain.entity.UserId

internal sealed class FileUploadFlow {

    abstract suspend fun enqueueWork(uploadTags: List<String>, uriString: String): Operation
    abstract val uploadFileLinkId: Long

    class FromScratch(
        private val workManager: WorkManager,
        private val userId: UserId,
        override val uploadFileLinkId: Long,
        private val shouldDeleteSource: Boolean,
    ) : FileUploadFlow() {

        override suspend fun enqueueWork(uploadTags: List<String>, uriString: String) = workManager
            .beginWith(
                CreateNewFileWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    tags = uploadTags,
                )
            ).then(
                EncryptBlocksWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    uriString = uriString,
                    shouldDeleteSource = shouldDeleteSource,
                    tags = uploadTags,
                )
            ).then(
                VerifyBlocksWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    tags = uploadTags,
                )
            ).then(
                GetBlocksUploadUrlWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    tags = uploadTags,
                )
            ).enqueue()
    }

    class FileAlreadyCreated(
        private val workManager: WorkManager,
        private val userId: UserId,
        override val uploadFileLinkId: Long,
        private val shouldDeleteSource: Boolean,
    ) : FileUploadFlow() {

        override suspend fun enqueueWork(uploadTags: List<String>, uriString: String) = workManager
            .beginWith(
                EncryptBlocksWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    uriString = uriString,
                    shouldDeleteSource = shouldDeleteSource,
                    tags = uploadTags
                )
            ).then(
                VerifyBlocksWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    tags = uploadTags,
                )
            ).then(
                GetBlocksUploadUrlWorker.getWorkRequest(
                    userId = userId,
                    uploadFileLinkId = uploadFileLinkId,
                    tags = uploadTags
                )
            ).enqueue()
    }
}
