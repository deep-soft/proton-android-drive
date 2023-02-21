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
package me.proton.core.drive.share.domain.repository

import kotlinx.coroutines.flow.Flow
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.share.domain.entity.Share
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.share.domain.entity.ShareInfo
import me.proton.core.drive.volume.domain.entity.VolumeId

interface ShareRepository {
    /**
     * Get reactive list of all drive shares for given user
     */
    fun getSharesFlow(userId: UserId): Flow<DataResult<List<Share>>>

    /**
     * Get reactive list of all drive shares for given user and volume
     */
    fun getSharesFlow(userId: UserId, volumeId: VolumeId): Flow<DataResult<List<Share>>>

    /**
     * Check if we have cached any share for given user
     */
    suspend fun hasShares(userId: UserId): Boolean

    /**
     * Check if we have cached any share for given user and volume
     */
    suspend fun hasShares(userId: UserId, volumeId: VolumeId): Boolean

    /**
     * Fetches shares from the server and stores it into cache
     */
    suspend fun fetchShares(userId: UserId): List<Share>

    /**
     * Get reactive share for given user and share id
     */
    fun getShareFlow(shareId: ShareId): Flow<DataResult<Share>>

    /**
     * Check if we have cached share for given user and share id
     */
    suspend fun hasShare(shareId: ShareId): Boolean

    /**
     * Check if we have cached share with key, passphrase and passphrase signature for given user and share id
     */
    suspend fun hasShareWithKey(shareId: ShareId): Boolean

    /**
     * Fetches share from the server and stores it into cache
     */
    suspend fun fetchShare(shareId: ShareId)

    /**
     * Ask the backend to delete a share (if [locallyOnly] is false) and remove it from the cache
     */
    suspend fun deleteShare(shareId: ShareId, locallyOnly: Boolean)

    /**
     * Remove shares from cache
     */
    suspend fun deleteShares(shareIds: List<ShareId>)

    /**
     * Create new share for a given volume id and share info
     */
    suspend fun createShare(userId: UserId, volumeId: VolumeId, shareInfo: ShareInfo): Result<ShareId>
}
