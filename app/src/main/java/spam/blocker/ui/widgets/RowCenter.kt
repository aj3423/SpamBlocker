package spam.blocker.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// content aligned center vertically
@Composable
inline fun RowVCenter(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement. Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
    ) {
        content()
    }
}
// content aligned center vertically, items spaced by x horizontally
@Composable
inline fun RowVCenterSpaced(
    space: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.dp),
    ) {
        content()
    }
}

// content aligned center vertically, items spaced by x horizontally
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowSpaced(
    space: Int,
    modifier: Modifier = Modifier,
    vSpace: Int = 4,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space.dp),
        verticalArrangement = Arrangement.spacedBy(vSpace.dp)
    ) {
        content()
    }
}

// both horizontal and vertical centered
@Composable
inline fun RowCenter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
