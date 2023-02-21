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
package me.proton.core.drive.drivelink.rename.domain.usecase

import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.crypto.domain.usecase.link.CreateRenameInfo
import me.proton.core.drive.eventmanager.base.domain.usecase.UpdateEventAction
import me.proton.core.drive.link.domain.entity.Link
import me.proton.core.drive.link.domain.entity.LinkId
import me.proton.core.drive.link.domain.repository.LinkRepository
import me.proton.core.drive.link.domain.usecase.GetLink
import javax.inject.Inject

class RenameLink @Inject constructor(
    private val linkRepository: LinkRepository,
    private val createRenameInfo: CreateRenameInfo,
    private val getLink: GetLink,
    private val updateEventAction: UpdateEventAction,
) {
    suspend operator fun invoke(
        parentFolder: Link.Folder,
        link: Link,
        linkName: String,
    ): Result<Unit> = coRunCatching {
        updateEventAction(
            shareId = link.id.shareId,
        ) {
            linkRepository.renameLink(
                linkId = link.id,
                renameInfo = createRenameInfo(parentFolder, link, linkName, link.mimeType).getOrThrow()
            ).getOrThrow()
        }
    }

    suspend operator fun invoke(
        linkId: LinkId,
        linkName: String,
    ): Result<Unit> = coRunCatching {
        val link = getLink(linkId).toResult().getOrThrow()
        val parentFolderId = requireNotNull(link.parentId) { "Parent folder must not be null" }
        val parentFolder = getLink(parentFolderId).toResult().getOrThrow()
        invoke(parentFolder, link, linkName).getOrThrow()
    }
}
