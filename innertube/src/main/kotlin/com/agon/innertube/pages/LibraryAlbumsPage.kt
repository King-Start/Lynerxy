package com.agon.innertube.pages

import com.agon.innertube.models.Album
import com.agon.innertube.models.AlbumItem
import com.agon.innertube.models.Artist
import com.agon.innertube.models.ArtistItem
import com.agon.innertube.models.MusicResponsiveListItemRenderer
import com.agon.innertube.models.MusicTwoRowItemRenderer
import com.agon.innertube.models.PlaylistItem
import com.agon.innertube.models.SongItem
import com.agon.innertube.models.YTItem
import com.agon.innertube.models.oddElements
import com.agon.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
