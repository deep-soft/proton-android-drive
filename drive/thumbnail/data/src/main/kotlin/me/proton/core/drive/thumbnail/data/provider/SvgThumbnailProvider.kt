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
package me.proton.core.drive.thumbnail.data.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.decode.SvgDecoder
import coil.size.PixelSize
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.core.drive.base.domain.entity.Bytes
import me.proton.core.drive.base.presentation.extension.compress
import me.proton.core.drive.thumbnail.domain.usecase.CreateThumbnail
import okio.buffer
import okio.source
import javax.inject.Inject

class SvgThumbnailProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : CreateThumbnail.Provider {

    private val decoder = SvgDecoder(context)
    private val bitmapPool = BitmapPool(maxSize = 0)
    private val options = Options(
        context = context,
        config = Bitmap.Config.RGB_565,
        allowRgb565 = true,
    )

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun getThumbnail(
        uriString: String,
        mimeType: String,
        maxWidth: Int,
        maxHeight: Int,
        maxSize: Bytes,
    ): ByteArray? {
        if (mimeType != "image/svg+xml") {
            return null
        }

        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val result = decoder.decode(
                    bitmapPool,
                    stream.source().buffer(),
                    PixelSize(maxWidth, maxHeight),
                    options
                )
                (result.drawable as? BitmapDrawable)?.bitmap?.compress(maxSize)
            }
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        } catch (e: Exception) {
            null // Coil catches all exception so I don't know which might be sent by the decoder
        }
    }
}
