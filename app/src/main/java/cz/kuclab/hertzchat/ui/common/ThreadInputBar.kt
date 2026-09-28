package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzMatte
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.io.File

/**
 * The shared message composer for 1:1 and group threads, WhatsApp-style: the text
 * pill (attach + text) on the left, the send/mic action in its own circle on the
 * right. Only the center swaps between the text pill, the recording indicator and
 * the voice preview - the action slot is the same composition in every mode, so a
 * press-and-hold recording survives the pill swapping underneath the finger instead
 * of being disposed mid-press (which used to wedge the recorder on forever).
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
    pendingVoice: Pair<File, Long>?,
    onDeleteVoice: () -> Unit,
    onSend: () -> Unit,
    micButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = pendingVoice != null || draft.isNotBlank() || attachments.isNotEmpty()
    Column(modifier = modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        PendingAttachmentsTray(
            attachments = attachments,
            onRemove = onRemoveAttachment,
            modifier = Modifier.padding(bottom = if (attachments.isEmpty()) 0.dp else 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    pendingVoice != null -> VoicePreviewBar(
                        file = pendingVoice.first,
                        durationMs = pendingVoice.second,
                        onDelete = onDeleteVoice,
                    )
                    isRecording -> VoiceRecordingIndicator(elapsedMs = recordElapsedMs)
                    else -> Surface(shape = HertzShapes.Pill, color = HertzMatte.input()) {
                        Row(
                            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (leading != null) {
                                leading()
                            } else {
                                Box(modifier = Modifier.size(10.dp))
                            }
                            Box(
                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                BasicTextField(
                                    value = draft,
                                    onValueChange = onDraftChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 5,
                                    decorationBox = { innerTextField ->
                                        if (draft.isEmpty()) {
                                            Text(
                                                placeholder,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                textAlign = TextAlign.Start,
                                            )
                                        }
                                        innerTextField()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // The action slot is unconditional: the same composition in every mode,
            // so a held mic press is never disposed and cancelled by a mode swap.
            Box(modifier = Modifier.padding(start = 8.dp)) {
                if (canSend) {
                    ChatInputAccentButton(
                        onClick = onSend,
                        icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Odeslat",
                    )
                } else {
                    micButton()
                }
            }
        }
    }
}
