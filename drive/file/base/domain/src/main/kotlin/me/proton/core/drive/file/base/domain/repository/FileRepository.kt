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
package me.proton.core.drive.file.base.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.file.base.domain.entity.BlockTokenInfo
import me.proton.core.drive.file.base.domain.entity.FileInfo
import me.proton.core.drive.file.base.domain.entity.NewFileInfo
import me.proton.core.drive.file.base.domain.entity.Revision
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.share.domain.entity.ShareId
import java.io.File
import java.io.InputStream

interface FileRepository {

    suspend fun createNewFile(shareId: ShareId, newFileInfo: NewFileInfo): Result<FileInfo>

    suspend fun fetchRevision(
        fileId: FileId,
        revisionId: String,
        fromBlockIndex: Int,
        pageSize: Int,
    ): Revision

    suspend fun updateRevision(
        fileId: FileId,
        revisionId: String,
        blockTokenInfos: List<BlockTokenInfo>,
        manifestSignature: String,
        signatureAddress: String,
        blockNumber: Long,
        state: Long,
        xAttr: String,
    ): Result<Unit>

    /**
     * Retrieve the thumbnail URL for a given file and revision id
     */
    suspend fun fetchThumbnailUrl(
        fileId: FileId,
        revisionId: String,
    ): Result<String>

    suspend fun getUrlInputStream(
        userId: UserId,
        url: String,
    ): Result<InputStream>

    suspend fun uploadFile(
        userId: UserId,
        uploadUrl: String,
        uploadFile: File,
        uploadingProgress: MutableStateFlow<Long>,
    ): Result<Unit>
}
