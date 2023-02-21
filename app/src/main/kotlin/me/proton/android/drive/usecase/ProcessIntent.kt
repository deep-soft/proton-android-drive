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

package me.proton.android.drive.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcelable
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.android.drive.extension.deepLinkBaseUrl
import me.proton.android.drive.log.DriveLogTag
import me.proton.android.drive.receiver.NotificationBroadcastReceiver.Companion.EXTRA_NOTIFICATION_ID
import me.proton.android.drive.ui.MainActivity
import me.proton.android.drive.ui.navigation.Screen
import me.proton.android.drive.ui.navigation.UriWithFileName
import me.proton.android.drive.ui.viewmodel.AccountViewModel
import me.proton.core.drive.base.domain.usecase.BroadcastMessages
import me.proton.core.drive.drivelink.upload.domain.usecase.ValidateUploadLimit
import me.proton.core.drive.messagequeue.domain.entity.BroadcastMessage
import me.proton.core.drive.notification.domain.usecase.RemoveNotification
import me.proton.core.drive.upload.domain.usecase.GetUploadFileName
import me.proton.core.util.kotlin.CoreLogger
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.takeIfNotEmpty
import javax.inject.Inject
import me.proton.android.drive.R as Presentation

class ProcessIntent @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val removeNotification: RemoveNotification,
    private val broadcastMessages: BroadcastMessages,
    private val getUploadFileName: GetUploadFileName,
    private val validateUploadLimit: ValidateUploadLimit,
) {
    operator fun invoke(intent: Intent, deepLinkIntent: MutableSharedFlow<Intent>, accountViewModel: AccountViewModel) {
        CoroutineScope(Job() + Dispatchers.IO).launch {
            with (intent) {
                getStringExtra(EXTRA_NOTIFICATION_ID)?.let { extraNotificationId ->
                    processExtraNotificationId(extraNotificationId)
                }
                onActionSend { processActionSend(intent, deepLinkIntent, accountViewModel) }
                onActionSendMultiple { processActionSendMultiple(intent, deepLinkIntent, accountViewModel) }
            }
        }
    }

    private suspend fun processExtraNotificationId(extraNotificationId: String) =
        removeNotification(extraNotificationId.deserialize())

    private suspend fun processActionSend(
        intent: Intent,
        deepLinkIntent: MutableSharedFlow<Intent>,
        accountViewModel: AccountViewModel,
    ) = actionSend(
        deepLinkIntent = deepLinkIntent,
        accountViewModel = accountViewModel,
    ) {
        getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
            listOf(UriWithFileName(uri.toString(), getUploadFileName(uri.toString())))
        } ?: emptyList()
    }

    private suspend fun processActionSendMultiple(
        intent: Intent,
        deepLinkIntent: MutableSharedFlow<Intent>,
        accountViewModel: AccountViewModel,
    ) {
        getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris ->
            val userId = accountViewModel.primaryAccount.filterNotNull().first().userId
            validateUploadLimit(userId, uris.size)
                .onSuccess {
                    actionSend(
                        deepLinkIntent = deepLinkIntent,
                        accountViewModel = accountViewModel,
                    ) {
                        uris.map { uri ->
                            UriWithFileName(uri.toString(), getUploadFileName(uri.toString()))
                        }
                    }
                }
        }
    }

    private suspend fun actionSend(
        deepLinkIntent: MutableSharedFlow<Intent>,
        accountViewModel: AccountViewModel,
        uploadUris: suspend () -> List<UriWithFileName>,
    ) {
        withTimeoutOrNull(
            timeMillis = PRIMARY_ACCOUNT_READY_TIMEOUT_MILLIS,
        ) {
            accountViewModel.state.first { state -> state == AccountViewModel.State.AccountReady }
            val userId = accountViewModel.primaryAccount.filterNotNull().first().userId
            uploadUris().takeIfNotEmpty()?.let { uploadUris ->
                val uploadRoute = Screen.Upload(
                    userId = userId,
                    uris = uploadUris,
                )
                with (deepLinkIntent) {
                    subscriptionCount.first { count -> count > 0 }
                    emit(
                        Intent(
                            Intent.ACTION_VIEW,
                            "${appContext.deepLinkBaseUrl}/$uploadRoute".toUri(),
                            appContext,
                            MainActivity::class.java
                        )
                    )
                }
            } ?: broadcastMessages(
                userId = userId,
                message = appContext.getString(
                    Presentation.string.in_app_notification_upload_files_only,
                    appContext.getString(Presentation.string.app_name)
                ),
                type = BroadcastMessage.Type.INFO,
            )
        } ?: CoreLogger.d(DriveLogTag.UI, "Timeout expired while waiting for primary account ready")
    }

    companion object {
        private const val PRIMARY_ACCOUNT_READY_TIMEOUT_MILLIS = 5_000L
    }
}

inline fun <T> Intent.onActionSend(block: (intent: Intent) -> T): T? = takeIf {
    action == Intent.ACTION_SEND
}?.let {
    block(this)
}

inline fun <T> Intent.onActionSendMultiple(block: (intent: Intent) -> T): T? = takeIf {
    action == Intent.ACTION_SEND_MULTIPLE
}?.let {
    block(this)
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
inline fun <reified T : Parcelable> getParcelableExtra(
    intent: Intent,
    name: String,
    clazz: Class<T>,
) = with (intent) {
    if (Build.VERSION.SDK_INT >= TIRAMISU) {
        getParcelableExtra(name, clazz)
    } else {
        getParcelableExtra(name)
    }
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
inline fun <reified T : Parcelable> getParcelableArrayListExtra(
    intent: Intent,
    name: String,
    clazz: Class<T>,
) = with (intent) {
    if (Build.VERSION.SDK_INT >= TIRAMISU) {
        getParcelableArrayListExtra(name, clazz)
    } else {
        getParcelableArrayListExtra(name)
    }
}
