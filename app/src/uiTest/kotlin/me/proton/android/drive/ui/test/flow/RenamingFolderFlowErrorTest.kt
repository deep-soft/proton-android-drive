/*
 * Copyright (c) 2023 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.drive.ui.test.flow

import me.proton.android.drive.ui.robot.FilesTabRobot
import me.proton.android.drive.ui.rules.UserLoginRule
import me.proton.android.drive.ui.rules.WelcomeScreenRule
import me.proton.android.drive.ui.test.BaseTest
import me.proton.android.drive.ui.toolkits.getRandomString
import me.proton.core.drive.base.presentation.R
import me.proton.core.test.android.instrumented.utils.StringUtils
import me.proton.core.test.quark.data.User
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RenamingFolderFlowErrorTest(
    private val itemToBeRenamed: String,
    private val newItemName: String,
    private val errorMessage: String,
): BaseTest() {

    private val user
        get() = User(
            dataSetScenario = "4",
            name = "proton_drive_${getRandomString(20)}"
        )

    @get:Rule
    val welcomeScreenRule = WelcomeScreenRule(false)

    @get:Rule
    val userLoginRule = UserLoginRule(testUser = user)

    @Test
    fun renameError() {
        FilesTabRobot
            .swipeUpToItemWithName(itemToBeRenamed)
            .clickMoreOnFolder(itemToBeRenamed)
            .clickRename()
            .clearName()
            .typeName(newItemName)
            .clickRename()
            .verify {
                nodeWithTextDisplayed(errorMessage)
            }
    }

    companion object {
        @get:Parameterized.Parameters(name = "folderToBeRenamed={0}, newFolderName={1}, errorMessage={2}")
        @get:JvmStatic
        val data = listOf(
            arrayOf("folder1", "folder2", "An item with that name already exists in current folder"),
            arrayOf("folder1", "", StringUtils.stringFromResource(R.string.common_error_name_is_blank)),
            arrayOf("folder1", getRandomString(256), StringUtils.stringFromResource(R.string.common_error_name_too_long, 255)),
            arrayOf("folder2", "folder1", "An item with that name already exists in current folder"),
        )
    }
}
