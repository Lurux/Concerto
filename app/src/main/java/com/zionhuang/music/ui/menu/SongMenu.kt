package com.zionhuang.music.ui.menu

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.music.LocalDatabase
import com.zionhuang.music.R
import com.zionhuang.music.constants.ListItemHeight
import com.zionhuang.music.constants.ListThumbnailSize
import com.zionhuang.music.db.entities.Playlist
import com.zionhuang.music.db.entities.PlaylistEntity
import com.zionhuang.music.db.entities.PlaylistSongMap
import com.zionhuang.music.db.entities.Song
import com.zionhuang.music.extensions.toMediaItem
import com.zionhuang.music.models.toMediaMetadata
import com.zionhuang.music.playback.PlayerConnection
import com.zionhuang.music.playback.queues.YouTubeQueue
import com.zionhuang.music.ui.component.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SongMenu(
    originalSong: Song,
    navController: NavController,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var playlists by remember {
        mutableStateOf(emptyList<Playlist>())
    }

    LaunchedEffect(Unit) {
        database.playlistsByCreateDateDesc().collect {
            playlists = it
        }
    }

    if (showChoosePlaylistDialog) {
        ListDialog(
            onDismiss = { showChoosePlaylistDialog = false }
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.dialog_title_create_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreatePlaylistDialog = true
                    }
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    modifier = Modifier.clickable {
                        showChoosePlaylistDialog = false
                        onDismiss()
                        coroutineScope.launch {
                            database.query {
                                insert(PlaylistSongMap(
                                    songId = song.id,
                                    playlistId = playlist.id,
                                    position = playlist.songCount
                                ))
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.dialog_title_create_playlist)) },
            onDismiss = { showCreatePlaylistDialog = false },
            onDone = { playlistName ->
                database.query {
                    insert(PlaylistEntity(
                        name = playlistName
                    ))
                }
            }
        )
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(
                items = song.artists,
                key = { it.id }
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            placeholder = painterResource(R.drawable.ic_artist),
                            error = painterResource(R.drawable.ic_artist),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape)
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    SongListItem(
        song = song,
        showBadges = false,
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        insert(song.song.toggleLike())
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (song.song.liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = null
                )
            }
        }
    )

    Divider()

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = R.drawable.ic_radio,
            title = R.string.menu_start_radio
        ) {
            onDismiss()
            playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()))
        }
        GridMenuItem(
            icon = R.drawable.ic_playlist_play,
            title = R.string.menu_play_next
        ) {
            onDismiss()
            playerConnection.playNext(song.toMediaItem())
        }
        GridMenuItem(
            icon = R.drawable.ic_queue_music,
            title = R.string.menu_add_to_queue
        ) {
            onDismiss()
            playerConnection.addToQueue((song.toMediaItem()))
        }
        GridMenuItem(
            icon = R.drawable.ic_edit,
            title = R.string.menu_edit,
            enabled = false
        ) {

        }
        GridMenuItem(
            icon = R.drawable.ic_playlist_add,
            title = R.string.menu_add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        GridMenuItem(
            icon = R.drawable.ic_file_download,
            title = R.string.menu_download,
            enabled = false
        ) {

        }
        GridMenuItem(
            icon = R.drawable.ic_artist,
            title = R.string.menu_view_artist
        ) {
            if (song.artists.size == 1) {
                navController.navigate("artist/${song.artists[0].id}")
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        }
        if (song.song.albumId != null) {
            GridMenuItem(
                icon = R.drawable.ic_album,
                title = R.string.menu_view_album
            ) {
                onDismiss()
                navController.navigate("album/${song.song.albumId}")
            }
        }
        GridMenuItem(
            icon = R.drawable.ic_share,
            title = R.string.menu_share
        ) {
            onDismiss()
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
        GridMenuItem(
            icon = R.drawable.ic_delete,
            title = R.string.menu_delete
        ) {
            onDismiss()
            database.query {
                delete(song)
            }
        }
    }
}
