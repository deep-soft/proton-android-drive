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
package me.proton.core.drive.upload.domain.extension

import me.proton.core.drive.base.domain.entity.Bytes
import me.proton.core.drive.upload.domain.outputstream.MultipleFileOutputStream
import java.io.File
import java.io.InputStream

fun InputStream.saveToBlocks(
    destinationFolder: File,
    blockMaxSize: Bytes,
): List<File> =
    MultipleFileOutputStream(destinationFolder, blockMaxSize)
        .apply { use { outputStream -> copyTo(outputStream) } }
        .files
