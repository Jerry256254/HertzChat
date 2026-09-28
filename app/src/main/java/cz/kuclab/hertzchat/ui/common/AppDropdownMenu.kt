package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A drop-in replacement for [androidx.compose.material3.DropdownMenu]: the same
 * anchored popup with the app's opaque menu surface instead of Material3's
 * default tonal fill, which reads as barely-there in this dark palette.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}
