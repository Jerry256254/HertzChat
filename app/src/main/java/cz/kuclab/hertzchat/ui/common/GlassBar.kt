package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import dev.chrisbanes.haze.HazeState

/**
 * The one top bar for every secondary screen (contacts, settings, profile,
 * assistant, file viewer, QR): a floating frosted pill with an optional back
 * circle and trailing actions - the same island as the chat bars, so the
 * whole app reads as one material. Screens with scrolling content pass their
 * [hazeState] and put `.haze(...)` on the list; static screens pass null and
 * the bar keeps its plain translucent fill.
 */
@Composable
fun GlassBar(
    title: String,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zpět",
                onClick = onBack,
                hazeState = hazeState,
            )
        }
        GlassSurface(
            shape = HertzShapes.Pill,
            hazeState = hazeState,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 8.dp else 0.dp, end = if (actions != null) 8.dp else 0.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = HertzGlass.contentOnGlass(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(vertical = 11.dp, horizontal = 16.dp),
            )
        }
        actions?.invoke(this)
    }
}
