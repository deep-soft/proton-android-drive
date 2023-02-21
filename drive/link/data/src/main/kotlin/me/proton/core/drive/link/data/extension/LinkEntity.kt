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
package me.proton.core.drive.link.data.extension

import me.proton.core.drive.link.data.db.entity.LinkEntity
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.shareurl.base.domain.entity.ShareUrlId

val LinkEntity.isFolder
    get() = type == 1L

val LinkEntity.isShared
    get() = shared == 1L

fun LinkEntity.shareUrlId(): ShareUrlId? = shareUrlShareId?.let { shareId ->
    this.shareUrlId?.let { shareUrlId ->
        ShareUrlId(ShareId(userId = this.userId, shareId), shareUrlId)
    }
}
