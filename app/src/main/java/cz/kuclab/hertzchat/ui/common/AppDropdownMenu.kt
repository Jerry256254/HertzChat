package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A drop-in replacement for [DropdownMenu] with the same shape/elevation language as
 * [AppCard] instead of Material3's default popup surface. In this app's dark palette the
 * stock menu's low tonal elevation reads as barely-there - a flat rectangle that blends
 * into the background rather than reading as a floating card.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The rounding has to be applied to the menu's own container shape - see
    // RoundedMenuSurface in ActionMenu.kt for why a Modifier can't do it.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp)),
        typography = MaterialTheme.typography,
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}
