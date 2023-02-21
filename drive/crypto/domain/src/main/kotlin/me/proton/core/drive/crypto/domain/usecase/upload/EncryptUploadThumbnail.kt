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
package me.proton.core.drive.crypto.domain.usecase.upload

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.crypto.domain.usecase.base.UseSessionKey
import me.proton.core.drive.cryptobase.domain.CryptoScope
import me.proton.core.drive.cryptobase.domain.usecase.EncryptAndSignData
import me.proton.core.drive.key.domain.entity.ContentKey
import me.proton.core.drive.key.domain.entity.Key
import me.proton.core.drive.key.domain.extension.keyHolder
import me.proton.core.drive.key.domain.usecase.GetAddressKeys
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class EncryptUploadThumbnail @Inject constructor(
    private val useSessionKey: UseSessionKey,
    private val encryptAndSignData: EncryptAndSignData,
    private val getAddressKeys: GetAddressKeys,
) {
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend operator fun invoke(
        contentKey: ContentKey,
        signKey: Key,
        input: ByteArray,
        output: File,
        coroutineContext: CoroutineContext = CryptoScope.EncryptAndDecryptWithIO.coroutineContext
    ): Result<File> = coRunCatching(coroutineContext) {
        useSessionKey(
            contentKey = contentKey,
            coroutineContext = coroutineContext
        ) { sessionKey ->
            output.outputStream().use { stream ->
                stream.write(
                    encryptAndSignData(sessionKey, signKey.keyHolder, input).getOrThrow()
                )
            }
            output
        }.getOrThrow()
    }

    suspend operator fun invoke(
        userId: UserId,
        contentKey: ContentKey,
        signatureAddress: String,
        input: ByteArray,
        output: File,
        coroutineContext: CoroutineContext = CryptoScope.EncryptAndDecryptWithIO.coroutineContext
    ): Result<File> =
        invoke(
            contentKey = contentKey,
            signKey = getAddressKeys(userId, signatureAddress),
            input = input,
            output = output,
            coroutineContext = coroutineContext
        )
}
