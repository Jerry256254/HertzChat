package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzShapes

/** The one card style used across the whole app - the shared glass material (see [GlassSurface]). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = HertzGlass.fill(),
    shadowElevation: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = HertzShapes.Card,
        fill = containerColor,
        shadowElevation = shadowElevation,
    ) {
        Column(content = content)
    }
}
