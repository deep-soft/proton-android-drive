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
package me.proton.core.drive.folder.data.extension

import me.proton.core.drive.folder.data.api.request.CreateFolderRequest
import me.proton.core.drive.folder.domain.entity.FolderInfo

fun FolderInfo.toCreateFolderRequest() =
    CreateFolderRequest(
        parentLinkId = parentLinkId,
        name = name,
        hash = hash,
        nodeKey = nodeKey,
        nodePassphrase = nodePassphrase,
        nodePassphraseSignature = nodePassphraseSignature,
        nodeHashKey = nodeHashKey,
        signatureAddress = signatureAddress,
        xAttr = xAttr,
    )
