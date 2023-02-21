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
package me.proton.core.drive.crypto.domain.usecase.file

import me.proton.core.crypto.common.pgp.DecryptedFile
import me.proton.core.drive.crypto.domain.usecase.base.UseSessionKey
import me.proton.core.drive.cryptobase.domain.CryptoScope
import me.proton.core.drive.cryptobase.domain.usecase.DecryptFile
import me.proton.core.drive.cryptobase.domain.usecase.GetPublicKeyRing
import me.proton.core.drive.cryptobase.domain.usecase.VerifyFileSignature
import me.proton.core.drive.key.domain.entity.ContentKey
import me.proton.core.drive.key.domain.entity.Key
import me.proton.core.drive.key.domain.extension.keyHolder
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class DecryptAndVerifyFiles @Inject constructor(
    private val useSessionKey: UseSessionKey,
    private val decryptFile: DecryptFile,
    private val verifyFileSignature: VerifyFileSignature,
    private val getPublicKeyRing: GetPublicKeyRing,
) {
    suspend operator fun invoke(
        contentKey: ContentKey,
        decryptSignatureKey: Key.Node,
        verifySignatureKey: Key,
        input: List<Pair<String?, File>>,
        output: List<File>,
        coroutineContext: CoroutineContext = CryptoScope.EncryptAndDecryptWithIO.coroutineContext,
    ): Result<List<DecryptedFile>> = useSessionKey(contentKey) { sessionKey ->
        input.mapIndexed { index, (encSignature, file) ->
            val outputFile = decryptFile(sessionKey, file, output[index], coroutineContext).getOrThrow()
            DecryptedFile(
                file = outputFile,
                status = verifyFileSignature(
                    decryptKey = decryptSignatureKey.keyHolder,
                    verifyKeyRing = getPublicKeyRing(verifySignatureKey.keyHolder).getOrThrow(),
                    file = outputFile,
                    encSignature = encSignature,
                    coroutineContext
                ).getOrThrow(),
                filename = "",
                lastModifiedEpochSeconds = -1,
            )
        }
    }
}
