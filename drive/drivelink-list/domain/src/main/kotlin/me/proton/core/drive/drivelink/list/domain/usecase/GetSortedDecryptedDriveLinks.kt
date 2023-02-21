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

package me.proton.core.drive.drivelink.list.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import me.proton.core.drive.base.domain.extension.mapCatching
import me.proton.core.drive.drivelink.sorting.domain.usecase.SortDriveLinks
import me.proton.core.drive.link.domain.entity.FolderId
import me.proton.core.drive.link.domain.extension.userId
import me.proton.core.drive.sorting.domain.usecase.GetSorting
import javax.inject.Inject

class GetSortedDecryptedDriveLinks @Inject constructor(
    private val getSorting: GetSorting,
    private val sortDriveLinks: SortDriveLinks,
    private val getDecryptedDriveLinks: GetDecryptedDriveLinks,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(parentId: FolderId) = getSorting(parentId.userId).flatMapLatest { sorting ->
        getDecryptedDriveLinks(parentId)
            .mapCatching { driveLinks ->
                sortDriveLinks(sorting, driveLinks)
            }
    }
}
