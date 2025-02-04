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

package me.proton.android.drive.ui.test.flow.creatingFolder

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.proton.android.drive.ui.robot.CreateFolderRobot
import me.proton.android.drive.ui.robot.FilesTabRobot
import me.proton.android.drive.ui.robot.MoveToFolderRobot
import me.proton.android.drive.ui.rules.UserLoginRule
import me.proton.android.drive.ui.rules.WelcomeScreenRule
import me.proton.android.drive.ui.test.BaseTest
import me.proton.android.drive.ui.toolkits.getRandomString
import me.proton.core.test.quark.data.User
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreatingFolderFlowSuccessTest : BaseTest() {

    private val randomFolderName get() = getRandomString()
    private val user
        get() = User(
            dataSetScenario = "2",
            name = "proton_drive_${getRandomString(20)}"
        )

    @get:Rule
    val welcomeScreenRule = WelcomeScreenRule(false)

    @get:Rule
    val userLoginRule = UserLoginRule(testUser = user, shouldSeedUser = true)

    @Test
    fun createFolderViaMoveWindow() {
        val subFolderName = "folder1"
        val newFolderName = getRandomString()

        FilesTabRobot
            .scrollToItemWithName(subFolderName)
            .clickMoreOnItem(subFolderName)
            .clickMove()
            .clickAddFolderToRoot()
            .typeFolderName(newFolderName)
            .clickCreate()

        MoveToFolderRobot
            .dismissFolderCreateSuccessGrowler(newFolderName, MoveToFolderRobot)
            .scrollToItemWithName(newFolderName) // Can be removed after DRVAND-569 is fixed
            .verify {
                itemIsDisplayed(name = newFolderName)
            }
    }

    @Test
    fun createFolderInGridLayout() {
        FilesTabRobot
            .clickLayoutSwitcher()
            .clickPlusButton()
            .clickCreateFolder()

        createFolder(randomFolderName)
    }

    @Test
    fun createAFolderViaSubFolderPlusButton() {
        val subFolderName = "folder1"

        FilesTabRobot
            .clickOnFolder(subFolderName)
            .clickPlusButton()
            .clickCreateFolder()

        createFolder(randomFolderName)
    }

    private fun createFolder(folderName: String) {
        CreateFolderRobot
            .typeFolderName(folderName)
            .clickCreate()

        FilesTabRobot
            .dismissFolderCreateSuccessGrowler(folderName, FilesTabRobot)
            .verify {
                itemIsDisplayed(name = folderName)
            }
    }
}
