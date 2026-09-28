package cz.kuclab.hertzchat.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.data.db.DeliveryState
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Telegram-night outgoing bubble blue. */
val TelegramMine = Color(0xFF2B5278)

/** Telegram-night incoming bubble slate. */
val TelegramTheirs = Color(0xFF182533)

/** Read ticks on a blue bubble - pale blue reads, pure blue wouldn't. */
val TelegramReadTicks = Color(0xFF7FC4FF)

/** Floating bars/chips over the doodle background. */
val TelegramFloat = Color(0xFF1E2A38)

/** One row of a thread list: newest-first, day chips interleaved. */
sealed interface ChatDisplayItem {
    data class Day(val key: String, val label: String) : ChatDisplayItem
    data class Msg(val message: MessageEntity) : ChatDisplayItem
}

/** Ascending messages -> newest-first display rows with a day chip over each day's newest. */
fun buildChatDisplayItems(messages: List<MessageEntity>): List<ChatDisplayItem> {
    val out = ArrayList<ChatDisplayItem>(messages.size + 4)
    var currentDay: LocalDate? = null
    messages.asReversed().forEach { message ->
        val day = Instant.ofEpochMilli(message.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        if (day != currentDay) {
            currentDay = day
            out.add(ChatDisplayItem.Day(key = "day-$day", label = dayLabel(day)))
        }
        out.add(ChatDisplayItem.Msg(message))
    }
    return out
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Dnes"
        today.minusDays(1) -> "Včera"
        else -> day.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.getDefault()))
    }
}

/** 24-hour clock under every message, device locale. */
fun messageTimeLabel(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("H:mm", Locale.getDefault()))

/**
 * Subtle tiled doodles over the theme background - the Telegram-night thread look.
 * The tile grid is locked to the window origin so [FrostedBackdrop] tiles line up
 * seamlessly with it anywhere on screen.
 */
@Composable
fun ChatDoodleBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tile = remember {
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.chat_bg_tile)?.asImageBitmap()
        }.getOrNull()
    }
    var tileOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (tile != null) {
            Canvas(modifier = Modifier.fillMaxSize().onGloballyPositioned { tileOrigin = it.positionInRoot() }) {
                val tw = tile.width.toFloat()
                val th = tile.height.toFloat()
                var startX = -tileOrigin.x % tw
                if (startX > 0f) startX -= tw
                var startY = -tileOrigin.y % th
                if (startY > 0f) startY -= th
                var y = startY
                while (y < size.height) {
                    var x = startX
                    while (x < size.width) {
                        drawImage(tile, topLeft = Offset(x, y))
                        x += tw
                    }
                    y += th
                }
            }
        }
    }
}

/** Centered day separator pill ("Dnes", "27. září"). */
@Composable
fun DayChip(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = HertzShapes.Pill, color = TelegramFloat.copy(alpha = 0.85f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Clock time plus delivery ticks at a bubble's bottom edge: a spinner while the
 * peer hasn't confirmed, double ticks after (blue once read), an error mark on
 * final failure. Incoming messages show the time only.
 */
@Composable
fun MessageMetaRow(
    timestamp: Long,
    fromMe: Boolean,
    deliveryState: DeliveryState?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            messageTimeLabel(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
        if (fromMe) {
            when (deliveryState) {
                DeliveryState.PENDING, DeliveryState.SENDING, DeliveryState.SENT -> CircularProgressIndicator(
                    modifier = Modifier.padding(start = 4.dp).size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White.copy(alpha = 0.75f),
                )
                DeliveryState.FAILED -> Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = "Nepodařilo se odeslat",
                    tint = Color(0xFFFF8A80),
                    modifier = Modifier.padding(start = 3.dp).size(13.dp),
                )
                DeliveryState.READ -> Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = "Přečteno",
                    tint = TelegramReadTicks,
                    modifier = Modifier.padding(start = 3.dp).size(15.dp),
                )
                else -> Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = "Doručeno",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 3.dp).size(15.dp),
                )
            }
        }
    }
}

/** Clock time floating on a photo/video thumbnail (the download badge owns the other corner). */
@Composable
fun MediaTimeChip(message: MessageEntity, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = HertzShapes.Pill,
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        MessageMetaRow(
            timestamp = message.timestamp,
            fromMe = message.fromMe,
            deliveryState = message.deliveryState.takeIf { message.fromMe },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Round floating button for the overlay bars (back, options). */
@Composable
fun FloatingCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(TelegramFloat.copy(alpha = 0.82f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FrostedBackdrop(modifier = Modifier.fillMaxSize())
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * Frosted glass without any dependency: draws the pre-blurred background tile
 * locked to the same window-origin grid as [ChatDoodleBackground], so floating
 * bars look like a blurred continuation of the wallpaper, plus a dark tint for
 * legibility. Works on every API level with zero per-frame GPU cost - the
 * parent's translucent fill stays as a fallback when the tile is missing.
 * Must be a same-sized child of the frosted surface; the parent's clip shape
 * applies, so pills and circles frost cleanly to their edge.
 */
@Composable
fun FrostedBackdrop(
    modifier: Modifier = Modifier,
    tint: Color = TelegramFloat.copy(alpha = 0.55f),
) {
    val context = LocalContext.current
    val tile = remember {
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.chat_bg_tile_blurred)?.asImageBitmap()
        }.getOrNull()
    }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier = modifier.onGloballyPositioned { offset = it.positionInRoot() }) {
        if (tile != null) {
            val tw = tile.width.toFloat()
            val th = tile.height.toFloat()
            var startX = -offset.x % tw
            if (startX > 0f) startX -= tw
            var startY = -offset.y % th
            if (startY > 0f) startY -= th
            var y = startY
            while (y < size.height) {
                var x = startX
                while (x < size.width) {
                    drawImage(tile, topLeft = Offset(x, y))
                    x += tw
                }
                y += th
            }
        }
        drawRect(tint)
    }
}
