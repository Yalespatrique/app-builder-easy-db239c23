package com.asterplay.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary

@Composable
fun PosterCard(
    title: String,
    logo: String?,
    aspect: Float = 2f / 3f, // pôster (filmes/séries). Use 16/9 pra canais.
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(180), label = "scale")

    Column(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Surface(
            onClick = onClick,
            colors = SurfaceDefaults.colors(
                containerColor = BgSurface,
                focusedContainerColor = BgElevated,
            ),
            shape = SurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .then(
                    if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp))
                    else Modifier
                )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Icon(
                        imageVector = if (aspect < 1f) Icons.Default.Movie else Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                }
            }
        }
        Text(
            text = title,
            color = if (focused) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
}
