package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cz.kuclab.hertzchat.ui.theme.HertzMatte
import cz.kuclab.hertzchat.ui.theme.HertzShapes

/** The one card style used across the whole app - matte-translucent and uniformly rounded (see [HertzShapes]). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = HertzMatte.card(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
        shape = HertzShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        content = content,
    )
}
