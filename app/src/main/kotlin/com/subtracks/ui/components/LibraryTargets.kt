package com.subtracks.ui.components

import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import kotlinx.coroutines.flow.first

/** Loads a stored album, playlist or artist reference from the local mirror; null when it is no longer there. */
internal suspend fun resolveMenuTarget(
    item: PinnedItem,
    libraryRepository: LibraryRepository,
    sourceRepository: SourceRepository,
): MenuTarget? =
    when (item.kind) {
        PinKind.Album -> {
            libraryRepository.album(item.sourceId, item.refId).first()?.let {
                MenuTarget.Album(it, sourceRepository.coverArt(it.coverArt, true))
            }
        }

        PinKind.Playlist -> {
            libraryRepository.playlist(item.sourceId, item.refId).first()?.let {
                MenuTarget.Playlist(it, sourceRepository.coverArt(it.coverArt, true))
            }
        }

        PinKind.Artist -> {
            libraryRepository.artist(item.sourceId, item.refId).first()?.let {
                MenuTarget.Artist(it, sourceRepository.artistArt(it, true).primary)
            }
        }
    }
