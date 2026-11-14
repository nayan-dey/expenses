package com.kharcha.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer

/** Loads one of the app's stored pictures off the main thread. Null while loading or if it is missing. */
@Composable
fun rememberPicture(name: String, maxSide: Int): ImageBitmap? {
    val images = LocalContainer.current.images
    val picture by produceState<ImageBitmap?>(initialValue = null, name, maxSide) {
        value = images.load(name, maxSide)?.asImageBitmap()
    }
    return picture
}

/** A small square preview of an attached picture. */
@Composable
fun Thumb(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 76.dp) {
    val picture = rememberPicture(name, 300)
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = "Open the picture", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, contentDescription = "Attached picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            KIcon(Ic.image, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A picture shown large on a dark backdrop, with buttons to close, share or remove it. */
@Composable
fun PictureViewer(name: String, onDismiss: () -> Unit, onShare: () -> Unit, onRemove: (() -> Unit)?) {
    val picture = rememberPicture(name, 1600)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
        ) {
            if (picture != null) {
                Image(
                    picture,
                    contentDescription = "Attached picture",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 72.dp),
                )
            }
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter),
            ) {
                IconBtn(Ic.close, "Close", onDismiss, style = IconBtnStyle.CONTAINER)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconBtn(Ic.share, "Share the picture", onShare, style = IconBtnStyle.CONTAINER)
                if (onRemove != null) {
                    HGap(8.dp)
                    IconBtn(Ic.delete, "Remove the picture", { onRemove(); onDismiss() }, style = IconBtnStyle.CONTAINER)
                }
            }
        }
    }
}
