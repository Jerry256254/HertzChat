package cz.kuclab.hertzchat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzIcons
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import dev.chrisbanes.haze.HazeState
import java.io.File

/**
 * The shared message composer for 1:1 and group threads: one floating glass
 * island (same frosted material as the top bar) with messages scrolling
 * behind it. The row is fully symmetric - a 40dp circle on each end (attach
 * left, send/mic right, both exactly centered) with the text in the middle -
 * and the whole island stands exactly as tall as the top bar (48dp pill plus
 * the 8dp margin). Only the center swaps between the text field, the
 * recording indicator and the voice preview; the action slot is the same
 * composition in every mode, so a press-and-hold recording survives the
 * center swapping underneath the finger instead of being disposed mid-press.
 *
 * The two end circles never move: they sit at the island's bottom edge, so a
 * growing paragraph pushes the island's top up while the buttons stay exactly
 * where the thumb expects them. Only the text center rides vertically.
 */
@Composable
fun ThreadInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    placeholder: String,
    leading: (@Composable () -> Unit)?,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    isRecording: Boolean,
    recordElapsedMs: Long,
    recordLevel: Float,
    pendingVoice: Pair<File, Long>?,
    onDeleteVoice: () -> Unit,
    onSend: () -> Unit,
    micButton: @Composable () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    scrollDownVisible: Boolean = false,
    onScrollDown: (() -> Unit)? = null,
) {
    val canSend = pendingVoice != null || draft.isNotBlank() || attachments.isNotEmpty()
    val textMode = pendingVoice == null && !isRecording
    // A lone line is a pill; a paragraph becomes a rounded sheet instead of a
    // stretched broken sausage - the radius stays fixed while the island grows.
    val islandShape = if (textMode && isSheetIsland(draft)) {
        androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    } else {
        HertzShapes.Pill
    }
    // No navigationBarsPadding: both host screens sit inside Scaffold content,
    // which already offsets for the nav bar - adding it again floated the whole
    // island a nav-bar-height above the thread's bottom clearance and the newest
    // message hid underneath it, unreachable by scrolling.
    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        PendingAttachmentsTray(
            attachments = attachments,
            onRemove = onRemoveAttachment,
            modifier = Modifier.padding(bottom = if (attachments.isEmpty()) 0.dp else 8.dp),
        )
        // The scroll-down dot lives in this column (not in a Scaffold FAB slot),
        // so it always rides exactly above the send circle - same 40dp glass,
        // same blur, same x (16dp + half the dot = the send circle's center) -
        // and lifts with the island above the keyboard instead of sinking under it.
        AnimatedVisibility(
            visible = scrollDownVisible && onScrollDown != null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                GlassCircleButton(
                    icon = HertzIcons.ScrollDown,
                    contentDescription = "Sjet dolů",
                    onClick = { onScrollDown?.invoke() },
                    size = 40.dp,
                    hazeState = hazeState,
                )
            }
        }
        GlassSurface(
            shape = islandShape,
            // The island breathes with the text instead of snapping - paragraphs
            // grow it smoothly, sending shrinks it back the same way.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp).animateContentSize(),
            hazeState = hazeState,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (leading != null) {
                    leading()
                } else {
                    Box(modifier = Modifier.size(10.dp))
                }
                // Breathing room so the first glyphs never touch the attach circle.
                // The only child that stays centered: the circles around it are
                // bottom-anchored, so this override keeps single-line text sitting
                // in the pill's middle while paragraphs fill the grown sheet.
                Box(
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(start = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // The field stays composed (and focused) in every mode - it is
                    // only hidden, never removed - so recording with the keyboard
                    // open doesn't drop the keyboard or jump the layout mid-press.
                    // The overlay covers it fully, so stray taps can't land in it.
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.fillMaxWidth().alpha(if (textMode) 1f else 0f),
                        textStyle = LocalTextStyle.current.copy(color = HertzGlass.contentOnGlass()),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            if (draft.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = HertzGlass.contentOnGlass().copy(alpha = 0.55f),
                                    maxLines = 1,
                                    textAlign = TextAlign.Start,
                                )
                            }
                            innerTextField()
                        },
                    )
                    when {
                        pendingVoice != null -> VoicePreviewContent(
                            file = pendingVoice.first,
                            durationMs = pendingVoice.second,
                            onDelete = onDeleteVoice,
                        )
                        isRecording -> VoiceRecordingContent(elapsedMs = recordElapsedMs, level = recordLevel)
                    }
                }
                Box(modifier = Modifier.padding(start = 4.dp)) {
                    if (canSend) {
                        ChatInputSendButton(
                            onClick = onSend,
                            contentDescription = "Odeslat",
                        )
                    } else {
                        micButton()
                    }
                }
            }
        }
    }
}

/**
 * True once the draft outgrows a pill: an explicit line break, or enough
 * characters to wrap past two lines on a phone. Pure so the threshold stays
 * pinned by IslandShapeTest.
 */
internal fun isSheetIsland(draft: String): Boolean = draft.contains('\n') || draft.length > 60
