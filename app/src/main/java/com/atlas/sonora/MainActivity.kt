package com.atlas.sonora

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Ink = Color(0xFF080A11)
private val Panel = Color(0xFF151A28)
private val Violet = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9CA5B8)
private val White = Color(0xFFF7F7FB)

class MainActivity : ComponentActivity() {
    private val vm: SonoraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Violet,
                onBackground = White, onSurface = White)) { SonoraApp(vm) }
        }
    }

    override fun onStart() { super.onStart(); vm.onStart(this) }
    override fun onStop() { vm.onStop(); super.onStop() }
}

@Composable
private fun Label(text: String) =
    Text(text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SonoraApp(vm: SonoraViewModel) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canPlay = vm.picks.any { it.uri != null }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ATLAS", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                                Text("SONORA", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                                Text("A soundtrack that learns you.", color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(Color(0xFF392A69), Color(0xFF151B30))), RoundedCornerShape(26.dp)
                        ).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("YOUR AI MUSIC AGENT", color = Color(0xFFD8CEFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Find the next\nsong you'll love.", color = White, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                            Text("Picks adapt to your mood, likes and skips, then play straight through Spotify.",
                                color = Color(0xFFD8D3E8), fontSize = 13.sp, lineHeight = 19.sp)
                            Button(onClick = { keyboard?.hide(); vm.recommend() }, enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = White)
                                else Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp)); Text(if (vm.busy) "THINKING…" else "GENERATE MY MIX", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("SET YOUR VIBE")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf("Surprise me", "Focus", "Chill", "Energy", "Late night", "Melancholy", "Cinematic").forEach { option ->
                                    FilterChip(selected = vm.mood == option, onClick = { vm.mood = option },
                                        label = { Text(option, fontSize = 13.sp) })
                                }
                            }
                            OutlinedTextField(value = vm.prompt, onValueChange = { vm.prompt = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Describe the sound you're craving…", color = Muted) },
                                minLines = 2, maxLines = 3, shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.recommend() }))
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Label("YOUR TASTE MEMORY")
                            Text("♥ ${vm.liked.size} loved     × ${vm.disliked.size} avoided", color = White)
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label("YOUR NEXT DISCOVERIES")
                                Text(vm.message, color = Muted, fontSize = 12.sp)
                            }
                            if (canPlay) FilledTonalButton(onClick = { vm.playAll(context) }) {
                                Icon(Icons.Default.PlayArrow, null); Text("PLAY ALL")
                            }
                        }
                    }
                    if (vm.picks.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Panel, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                            Text("Your first mix is waiting.", color = Muted)
                        }
                    } else items(vm.picks, key = { it.key }) { track -> TrackCard(track, vm) }
                    item {
                        OutlinedButton(onClick = { openSpotify(context) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("OPEN SPOTIFY")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Playback runs inside the Spotify app (Premium required). ATLAS only sends it play commands; it does not extract or re-stream audio.",
                            color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
                vm.nowPlaying?.let { NowPlayingBar(it, vm) }
            }
        }
    }
}

@Composable
private fun TrackCard(track: TrackPick, vm: SonoraViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
        .clickable { vm.play(track, context) }.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (track.image != null) AsyncImage(model = track.image, contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, tint = Violet, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.play(track, context) }) {
                Icon(Icons.Default.PlayCircleFilled, if (track.uri != null) "Play" else "Find on Spotify",
                    tint = Violet, modifier = Modifier.size(34.dp))
            }
        }
        Text(track.reason, color = Color(0xFFBEC4D2), fontSize = 13.sp, lineHeight = 18.sp)
        Row {
            TextButton(onClick = { vm.feedback(track, true) }) { Icon(Icons.Default.FavoriteBorder, null); Spacer(Modifier.width(4.dp)); Text("LOVE IT") }
            TextButton(onClick = { vm.feedback(track, false) }) { Icon(Icons.Default.ThumbDownOffAlt, null); Spacer(Modifier.width(4.dp)); Text("NOT FOR ME") }
        }
    }
}

@Composable
private fun NowPlayingBar(now: NowPlaying, vm: SonoraViewModel) {
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.MusicNote, null, tint = Violet)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(now.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.togglePlay() }) {
            Icon(if (now.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (now.paused) "Resume" else "Pause", tint = White)
        }
        IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Next", tint = White) }
    }
}
