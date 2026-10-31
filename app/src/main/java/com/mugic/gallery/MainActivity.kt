package com.mugic.gallery

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Photo(val id: Long, val uri: Uri)

/** Har photo ka "vibe" uske dominant rang se decide hota hai. */
enum class Vibe(val label: String, val color: Color) {
    FIRE("🔥 Fire", Color(0xFFFF4D4D)), SUNSET("🌇 Sunset", Color(0xFFFF9F43)),
    SUNSHINE("☀️ Sunshine", Color(0xFFFFD93D)), NATURE("🌿 Nature", Color(0xFF4CD97B)),
    OCEAN("🌊 Ocean", Color(0xFF3FA9F5)), DREAM("🔮 Dream", Color(0xFFA06BFF)),
    NIGHT("🌙 Night", Color(0xFF3B3B58)), PASTEL("🍦 Pastel", Color(0xFFF3E9E2)),
    MONO("🩶 Mono", Color(0xFF9A9A9A))
}

fun vibeOf(c: Int): Vibe {
    val hsv = FloatArray(3); AColor.colorToHSV(c, hsv)
    val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
    return when {
        v < .25f -> Vibe.NIGHT
        s < .15f -> if (v > .8f) Vibe.PASTEL else Vibe.MONO
        h < 20 || h >= 340 -> Vibe.FIRE
        h < 45 -> Vibe.SUNSET
        h < 70 -> Vibe.SUNSHINE
        h < 170 -> Vibe.NATURE
        h < 260 -> Vibe.OCEAN
        else -> Vibe.DREAM
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }

    private fun loadPhotos(): List<Photo> {
        val out = mutableListOf<Photo>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Photo(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
            }
        }
        return out
    }

    @Composable
    fun App() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        var granted by remember {
            mutableStateOf(checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        var photos by remember { mutableStateOf(emptyList<Photo>()) }
        val colors = remember { mutableStateMapOf<Long, Int>() }
        var selected by remember { mutableStateOf<Vibe?>(null) }
        var viewerStart by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
        LaunchedEffect(granted) {
            if (!granted) return@LaunchedEffect
            photos = withContext(Dispatchers.IO) { loadPhotos() }
            withContext(Dispatchers.IO) {
                photos.forEach { p ->
                    runCatching {
                        val bmp = contentResolver.loadThumbnail(p.uri, Size(64, 64), null)
                        colors[p.id] = Palette.from(bmp).generate().getDominantColor(AColor.GRAY)
                    }
                }
            }
        }

        val shown = photos.filter { selected == null || colors[it.id]?.let(::vibeOf) == selected }

        Box(Modifier.fillMaxSize().background(Color(0xFF0E0E12))) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibe Gallery", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        enabled = shown.isNotEmpty(),
                        onClick = { viewerStart = shown.indices.random() }
                    ) { Text("🎲 Surprise me") }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Vibe.values().toList()) { v ->
                        val n = photos.count { colors[it.id]?.let(::vibeOf) == v }
                        if (n > 0) FilterChip(
                            selected = selected == v,
                            onClick = { selected = if (selected == v) null else v },
                            label = { Text("${v.label} $n") },
                            leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(v.color)) }
                        )
                    }
                }
                if (!granted) Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Button(onClick = { launcher.launch(perm) }) { Text("Photos ka access do") }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { p ->
                        val tint = colors[p.id]?.let { Color(it) } ?: Color.DarkGray
                        val shape = RoundedCornerShape(14.dp)
                        AsyncImage(
                            model = p.uri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(shape).border(2.dp, tint, shape)
                                .clickable { viewerStart = shown.indexOf(p) }
                        )
                    }
                }
            }
            viewerStart?.let { Viewer(shown, it, colors) { viewerStart = null } }
        }
    }

    @Composable
    fun Viewer(list: List<Photo>, start: Int, colors: Map<Long, Int>, onClose: () -> Unit) {
        BackHandler(onBack = onClose)
        val pager = rememberPagerState(start) { list.size }
        val tint by animateColorAsState(
            colors[list[pager.currentPage].id]?.let { Color(it) } ?: Color.Black, label = "ambient"
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = .85f), Color.Black)))) {
            HorizontalPager(pager) { page ->
                var zoom by remember { mutableFloatStateOf(1f) }
                AsyncImage(
                    model = list[page].uri, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2.5f }) }
                )
            }
            val v = colors[list[pager.currentPage].id]?.let(::vibeOf)
            Text(
                v?.label ?: "", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
