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
package me.proton.core.drive.thumbnail.presentation.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import me.proton.core.drive.base.presentation.R
import me.proton.core.drive.base.presentation.entity.toFileTypeCategory
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.thumbnail.presentation.entity.ThumbnailVO
import me.proton.core.drive.thumbnail.presentation.painter.ThumbnailPainterWrapper

private val DriveLink.File.thumbnailVO get() = ThumbnailVO(
    volumeId = volumeId,
    fileId = id,
    revisionId = activeRevisionId,
)

@Composable
fun DriveLink.thumbnailPainter(radius: Dp = 0.dp) = ThumbnailPainterWrapper(
    painter = when {
        this is DriveLink.Folder -> painterResource(id = R.drawable.ic_folder_48)
        this is DriveLink.File && hasThumbnail -> with(LocalDensity.current) {
            val iconResId = mimeType.toFileTypeCategory().iconResId
            rememberImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .transformations(RoundedCornersTransformation(radius.toPx()))
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .placeholder(iconResId)
                    .error(iconResId)
                    .data(thumbnailVO)
                    .build()
            )
        }
        else -> painterResource(id = mimeType.toFileTypeCategory().iconResId)
    }
)
