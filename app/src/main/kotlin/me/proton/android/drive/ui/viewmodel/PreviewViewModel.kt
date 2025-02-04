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

package me.proton.android.drive.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import me.proton.android.drive.extension.getDefaultMessage
import me.proton.android.drive.ui.effect.PreviewEffect
import me.proton.android.drive.ui.navigation.PagerType
import me.proton.android.drive.ui.navigation.Screen
import me.proton.core.domain.arch.mapSuccessValueOrNull
import me.proton.core.domain.arch.transformSuccess
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.extension.filterSuccessOrError
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.log.LogTag.VIEW_MODEL
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.base.presentation.entity.toFileTypeCategory
import me.proton.core.drive.base.presentation.extension.log
import me.proton.core.drive.base.presentation.extension.require
import me.proton.core.drive.base.presentation.viewmodel.UserViewModel
import me.proton.core.drive.documentsprovider.domain.usecase.GetDocumentUri
import me.proton.core.drive.drivelink.crypto.domain.usecase.GetDecryptedDriveLink
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.drivelink.domain.extension.isNameEncrypted
import me.proton.core.drive.drivelink.domain.usecase.GetDriveLink
import me.proton.core.drive.drivelink.domain.usecase.GetDriveLinksCount
import me.proton.core.drive.drivelink.download.domain.usecase.GetFile
import me.proton.core.drive.drivelink.list.domain.usecase.GetDecryptedDriveLinks
import me.proton.core.drive.drivelink.offline.domain.usecase.GetDecryptedOfflineDriveLinks
import me.proton.core.drive.drivelink.offline.domain.usecase.GetOfflineDriveLinksCount
import me.proton.core.drive.drivelink.sorting.domain.usecase.SortDriveLinks
import me.proton.core.drive.files.preview.presentation.component.PreviewComposable
import me.proton.core.drive.files.preview.presentation.component.event.PreviewViewEvent
import me.proton.core.drive.files.preview.presentation.component.state.ContentState
import me.proton.core.drive.files.preview.presentation.component.state.PreviewContentState
import me.proton.core.drive.files.preview.presentation.component.state.PreviewViewState
import me.proton.core.drive.files.preview.presentation.component.toComposable
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.link.domain.entity.FolderId
import me.proton.core.drive.link.domain.entity.LinkId
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.sorting.domain.usecase.GetSorting
import me.proton.core.util.kotlin.CoreLogger
import javax.inject.Inject
import me.proton.core.drive.i18n.R as I18N
import me.proton.core.presentation.R as CorePresentation

@HiltViewModel
@SuppressLint("StaticFieldLeak")
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val configurationProvider: ConfigurationProvider,
    getDriveLink: GetDriveLink,
    getDecryptedDriveLink: GetDecryptedDriveLink,
    private val getFile: GetFile,
    private val getDocumentUri: GetDocumentUri,
    getDecryptedDriveLinks: GetDecryptedDriveLinks,
    getDecryptedOfflineDriveLinks: GetDecryptedOfflineDriveLinks,
    getOfflineDriveLinksCount: GetOfflineDriveLinksCount,
    getDriveLinksCount: GetDriveLinksCount,
    getSorting: GetSorting,
    sortDriveLinks: SortDriveLinks,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), UserViewModel by UserViewModel(savedStateHandle) {

    private val trigger = MutableSharedFlow<Trigger>(1).apply {
        val shareId = savedStateHandle.require<String>(Screen.PagerPreview.SHARE_ID)
        val fileId = savedStateHandle.require<String>(Screen.PagerPreview.FILE_ID)
        tryEmit(Trigger(FileId(ShareId(userId, shareId), fileId)))
    }
    private val fileId: FileId
        get() = trigger.replayCache.first().fileId

    private val provider: PreviewContentProvider =
        when (savedStateHandle.require<PagerType>(Screen.PagerPreview.PAGER_TYPE)) {
            PagerType.FOLDER -> FolderContentProvider(
                userId = userId,
                getDriveLink = getDriveLink,
                getDecryptedDriveLink = getDecryptedDriveLink,
                getDriveLinksCount = getDriveLinksCount,
                getDecryptedDriveLinks = getDecryptedDriveLinks,
                getSorting = getSorting,
                sortDriveLinks = sortDriveLinks,
                coroutineScope = viewModelScope,
                fileId = fileId,
            )
            PagerType.SINGLE -> SingleContentProvider(
                getDecryptedDriveLink = getDecryptedDriveLink,
            )
            PagerType.OFFLINE -> OfflineContentProvider(
                userId = userId,
                getDecryptedOfflineDriveLinks = getDecryptedOfflineDriveLinks,
                getSorting = getSorting,
                getDecryptedDriveLink = getDecryptedDriveLink,
                sortDriveLinks = sortDriveLinks,
                getOfflineDriveLinksCount = getOfflineDriveLinksCount,
                coroutineScope = viewModelScope,
            )
        }

    private val contentStatesCache = mutableMapOf<FileId, Flow<ContentState>>()

    private val driveLinks: StateFlow<List<DriveLink.File>?> = trigger.transformLatest { trigger ->
        emitAll(
            provider.getDriveLinks(trigger.fileId)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _previewEffect = MutableSharedFlow<PreviewEffect>()
    private val isFullscreen = MutableStateFlow(false)
    private val renderFailed = MutableStateFlow<Throwable?>(null)
    val initialViewState = PreviewViewState(
        navigationIconResId = CorePresentation.drawable.ic_arrow_back,
        isFullscreen = isFullscreen,
        previewContentState = PreviewContentState.Loading,
        items = emptyList(),
        currentIndex = 0,
    )
    val viewState: Flow<PreviewViewState> = driveLinks.filterNotNull().transformLatest { driveLinks ->
        val indexOfFirst = driveLinks.indexOfFirst { link -> link.id == fileId }
        val contentStates =
            driveLinks.associateBy({ link -> link.id }) { link -> link.getContentStateFlow() }
        val (previewContentState, index) = when {
            driveLinks.isEmpty() || indexOfFirst == -1 -> PreviewContentState.Empty to 0
            else -> PreviewContentState.Content to indexOfFirst
        }
        val previewViewState = initialViewState.copy(
            isFullscreen = isFullscreen,
            previewContentState = previewContentState,
            items = driveLinks.map { link ->
                val category = link.mimeType.toFileTypeCategory()
                PreviewViewState.Item(
                    key = link.id.id,
                    title = link.name,
                    isTitleEncrypted = link.isNameEncrypted,
                    category = category,
                    contentState = requireNotNull(contentStates[link.id])
                )
            },
            currentIndex = index,
        )
        CoreLogger.d(VIEW_MODEL, "$previewViewState")
        emit(previewViewState)
    }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val previewEffect: Flow<PreviewEffect> = _previewEffect.asSharedFlow()
        .onStart { emit(PreviewEffect.Fullscreen(isFullscreen.value)) }

    fun viewEvent(
        navigateBack: () -> Unit,
        navigateToFileOrFolderOptions: (linkId: LinkId) -> Unit,
    ): PreviewViewEvent = object : PreviewViewEvent {
        override val onTopAppBarNavigation = { navigateBack() }
        override val onMoreOptions = { navigateToFileOrFolderOptions(fileId) }
        override val onSingleTap = { toggleFullscreen() }
        override val onRenderFailed = { throwable: Throwable -> renderFailed.value = throwable }
        override val mediaControllerVisibility = { visible: Boolean ->
            if ((visible && isFullscreen.value) || (!visible && !isFullscreen.value)) {
                toggleFullscreen()
            }
        }
    }

    suspend fun onPageChanged(page: Int) {
        val links = driveLinks.value.orEmpty()
        if (page in links.indices) {
            val driveLink = links[page]
            if (trigger.replayCache.first().fileId != driveLink.id) {
                trigger.emit(Trigger(driveLink.id))
            }
        }
    }

    fun retry(verifySignature: Boolean) {
        viewModelScope.launch {
            trigger.emit(
                Trigger(
                    fileId = fileId,
                    verifySignature = verifySignature
                )
            )
        }
    }

    fun toggleFullscreen() {
        viewModelScope.launch {
            isFullscreen.value = !isFullscreen.value
            _previewEffect.emit(PreviewEffect.Fullscreen(isFullscreen.value))
        }
    }

    private fun getContentState(
        getFileState: GetFile.State,
        renderFailed: Throwable? = null,
    ): ContentState {
        return renderFailed?.let { throwable ->
            ContentState.Error.NonRetryable(
                message = throwable.getDefaultMessage(
                    context = appContext,
                    useExceptionMessage = configurationProvider.useExceptionMessage,
                ),
                messageResId = 0,
            )
        } ?: getFileState.toContentState(this)
    }

    fun getUri(fileId: FileId) = getDocumentUri(userId, fileId)
    private fun DriveLink.File.getContentStateFlow(): Flow<ContentState> =
        contentStatesCache.getOrPut(id) {
            if (mimeType.toFileTypeCategory().toComposable() == PreviewComposable.Unknown) {
                NO_PREVIEW_SUPPORTED
            } else {
                trigger.filter { trigger -> trigger.fileId == id }.flatMapLatest { trigger ->
                    combine(
                        getFile(this, trigger.verifySignature),
                        renderFailed,
                    ) { fileState, renderFailed ->
                        getContentState(fileState, renderFailed)
                    }
                }
            }
        }

    private data class Trigger(
        val fileId: FileId,
        val verifySignature: Boolean = true,
    )

    companion object {
        private val NO_PREVIEW_SUPPORTED = flowOf(ContentState.Available(Uri.EMPTY))
    }
}


fun GetFile.State.toContentState(viewModel: PreviewViewModel): ContentState {
    return when (this) {
        is GetFile.State.Downloading -> ContentState.Downloading(progress)
        GetFile.State.Decrypting -> ContentState.Decrypting
        is GetFile.State.Ready -> ContentState.Available(viewModel.getUri(fileId))
        GetFile.State.Error.NoConnection,
        is GetFile.State.Error.Downloading -> ContentState.Error.Retryable(
            messageResId = I18N.string.description_file_download_failed,
            actionResId = I18N.string.common_retry
        ) {
            viewModel.retry(verifySignature = true)
        }
        is GetFile.State.Error.Decrypting -> ContentState.Error.NonRetryable(
            message = null,
            messageResId = I18N.string.description_file_decryption_failed
        )
        is GetFile.State.Error.VerifyingSignature -> ContentState.Error.Retryable(
            messageResId = I18N.string.description_file_verify_signature_failed,
            actionResId = I18N.string.ignore_file_signature_action
        ) {
            viewModel.retry(verifySignature = false)
        }
        GetFile.State.Error.NotFound -> ContentState.NotFound
    }
}

interface PreviewContentProvider {
    fun getDriveLinks(fileId: FileId): Flow<List<DriveLink.File>>
}

@ExperimentalCoroutinesApi
class FolderContentProvider(
    private val userId: UserId,
    private val getDriveLink: GetDriveLink,
    private val getDecryptedDriveLink: GetDecryptedDriveLink,
    private val getDriveLinksCount: GetDriveLinksCount,
    private val getDecryptedDriveLinks: GetDecryptedDriveLinks,
    private val getSorting: GetSorting,
    private val sortDriveLinks: SortDriveLinks,
    coroutineScope: CoroutineScope,
    fileId: FileId,
) : PreviewContentProvider {

    private val folderId: StateFlow<FolderId?> =
        getDriveLink(fileId)
            .transformSuccess { (_, driveLink) ->
                emitAll(
                    getDriveLink(userId, folderId = driveLink.parentId)
                )
            }
            .mapSuccessValueOrNull()
            .map { driveLink -> driveLink?.id }
            .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    private val decryptedDriveLinks: StateFlow<List<DriveLink.File>> =
        folderId
            .transformLatest { folderId ->
                folderId?.let {
                    emitAll(
                        getDriveLinksCount(folderId)
                            .distinctUntilChanged()
                            .mapLatest {
                                getDecryptedDriveLinks(folderId)
                                    .getOrNull()
                                    ?.filterIsInstance<DriveLink.File>()
                                    ?: emptyList<DriveLink.File>()
                            }
                    )
                } ?: emit(emptyList())
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    override fun getDriveLinks(fileId: FileId): Flow<List<DriveLink.File>> = combine(
        getSorting(userId),
        getDecryptedDriveLink(fileId).filterSuccessOrError().mapSuccessValueOrNull(),
        decryptedDriveLinks,
    ) { sorting, driveLink, links ->
        val driveLinks = if (driveLink == null) {
            links.toMutableList().apply {
                removeIf { link -> link.id == fileId }
            }
        } else {
            links.toMutableList().apply {
                if (removeIf { link -> link.id == fileId }) {
                    add(driveLink)
                }
            }
        }
        coRunCatching {
            sortDriveLinks(
                sorting = sorting,
                driveLinks = driveLinks,
            ).filterIsInstance<DriveLink.File>()
        }.fold(
            onSuccess = { sortedDriveLinks -> sortedDriveLinks },
            onFailure = { error ->
                error.log(LogTag.DEFAULT, "Sorting failed fallback to unsorted list")
                driveLinks
            }
        )
    }
}


@ExperimentalCoroutinesApi
class SingleContentProvider(
    private val getDecryptedDriveLink: GetDecryptedDriveLink,
) : PreviewContentProvider {

    override fun getDriveLinks(fileId: FileId): Flow<List<DriveLink.File>> =
        getDecryptedDriveLink(fileId)
            .filterSuccessOrError()
            .mapSuccessValueOrNull()
            .transformLatest { driveLink ->
                emit(listOfNotNull(driveLink))
            }
}

@ExperimentalCoroutinesApi
class OfflineContentProvider(
    private val userId: UserId,
    private val getDecryptedOfflineDriveLinks: GetDecryptedOfflineDriveLinks,
    private val getSorting: GetSorting,
    private val getDecryptedDriveLink: GetDecryptedDriveLink,
    private val sortDriveLinks: SortDriveLinks,
    getOfflineDriveLinksCount: GetOfflineDriveLinksCount,
    coroutineScope: CoroutineScope,
) : PreviewContentProvider {

    private val decryptedOfflineDriveLinks: StateFlow<List<DriveLink.File>> =
        getOfflineDriveLinksCount(userId)
            .distinctUntilChanged()
            .mapLatest {
                getDecryptedOfflineDriveLinks(userId,)
                    .getOrNull()
                    ?.filterIsInstance<DriveLink.File>()
                    ?: emptyList<DriveLink.File>()
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    override fun getDriveLinks(fileId: FileId): Flow<List<DriveLink.File>> = combine(
        getSorting(userId),
        getDecryptedDriveLink(fileId).filterSuccessOrError().mapSuccessValueOrNull(),
        decryptedOfflineDriveLinks,
    ) { sorting, driveLink, links ->
        val driveLinks = if (driveLink == null) {
            links.toMutableList().apply {
                removeIf { link -> link.id == fileId }
            }
        } else {
            links.toMutableList().apply {
                if (removeIf { link -> link.id == fileId }) {
                    add(driveLink)
                }
            }
        }
        coRunCatching {
            sortDriveLinks(
                sorting = sorting,
                driveLinks = driveLinks,
            ).filterIsInstance<DriveLink.File>()
        }.fold(
            onSuccess = { sortedDriveLinks -> sortedDriveLinks },
            onFailure = { error ->
                error.log(LogTag.DEFAULT, "Sorting failed fallback to unsorted list")
                driveLinks
            }
        )
    }
}
