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
package me.proton.core.drive.drivelink.crypto.domain.usecase

import me.proton.core.crypto.common.pgp.DecryptedFile
import me.proton.core.crypto.common.pgp.VerificationStatus
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.crypto.domain.usecase.file.DecryptAndVerifyFiles
import me.proton.core.drive.crypto.domain.usecase.file.VerifyManifestSignature
import me.proton.core.drive.cryptobase.domain.exception.VerificationException
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.file.base.domain.extension.requireSortedAscending
import me.proton.core.drive.key.domain.usecase.GetAddressKeys
import me.proton.core.drive.key.domain.usecase.GetContentKey
import me.proton.core.drive.key.domain.usecase.GetNodeKey
import me.proton.core.drive.link.domain.extension.shareId
import me.proton.core.drive.link.domain.extension.userId
import me.proton.core.drive.link.domain.usecase.GetLink
import me.proton.core.drive.linkdownload.domain.entity.DownloadState
import me.proton.core.drive.thumbnail.domain.usecase.GetThumbnailBlock
import me.proton.core.util.kotlin.CoreLogger
import java.io.File
import java.util.UUID
import javax.inject.Inject

class DecryptLinkContent @Inject constructor(
    private val getLink: GetLink,
    private val decryptAndVerifyFiles: DecryptAndVerifyFiles,
    private val getContentKey: GetContentKey,
    private val verifyManifestSignature: VerifyManifestSignature,
    private val getThumbnailBlock: GetThumbnailBlock,
    private val getNodeKey: GetNodeKey,
    private val getAddressKeys: GetAddressKeys,
) {
    suspend operator fun invoke(
        driveLink: DriveLink.File,
        targetFile: File,
        checkSignature: Boolean,
    ): Result<File> = coRunCatching {
        val link = getLink(driveLink.id).toResult().getOrThrow()
        val downloadState = requireNotNull(driveLink.downloadState as? DownloadState.Downloaded) {
            "Expected DownloadState.Downloaded, actual ${driveLink.downloadState?.javaClass?.simpleName}"
        }
        val encryptedBlocks = downloadState.blocks.also { blocks -> blocks.requireSortedAscending() }
        val contentKey = getContentKey(link).getOrThrow()
        val fileKey = getNodeKey(link).getOrThrow()
        val signatureAddress = requireNotNull(downloadState.signatureAddress) {
            "Download state signature address is null"
        }
        val manifestSignatureVerified = verifyManifestSignature(
            userId = link.userId,
            signatureAddress = signatureAddress,
            blocks = encryptedBlocks + listOfNotNull(
                if (link.hasThumbnail) getThumbnailBlock(link.id, driveLink.volumeId, link.activeRevisionId).getOrNull()
                else null
            ),
            manifestSignature = requireNotNull(downloadState.manifestSignature) {
                "Download state manifest signature is null"
            },
        ).getOrNull().also { verified ->
            if (verified != true) {
                CoreLogger.d(LogTag.DOWNLOAD, "Verification of manifest signature failed")
            }
        } ?: false
        decryptAndVerifyFiles(
            contentKey = contentKey,
            decryptSignatureKey = fileKey,
            verifySignatureKey = getAddressKeys(link.shareId.userId, signatureAddress),
            input = encryptedBlocks.map { block -> block.encSignature to File(block.url) },
            output = encryptedBlocks.map { block -> File("${block.url}.${UUID.randomUUID()}") },
        ).map { decryptedBlocks ->
            val verificationFailed  = decryptedBlocks.any { decryptedFile -> decryptedFile.status != VerificationStatus.Success }
            if ((verificationFailed || !manifestSignatureVerified) && checkSignature) {
                throw VerificationException("Verification of blocks failed")
            }
            decryptedBlocks.mergeBlocks(targetFile)
        }.getOrThrow()
    }

    private fun List<DecryptedFile>.mergeBlocks(targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { outputStream ->
            forEach { block ->
                block.file.inputStream().use { inputStream ->
                    inputStream.copyTo(
                        out = outputStream,
                        bufferSize = 65_536
                    )
                }
                outputStream.flush()
                block.file.delete()
            }
        }
        return targetFile
    }
}
