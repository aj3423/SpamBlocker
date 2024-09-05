package spam.blocker.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import spam.blocker.ui.M
import spam.blocker.util.Lambda

// The builtin FloatingActionButton is not customizable.
@Composable
fun Fab(
    modifier: Modifier = Modifier,
    visible: Boolean,
    iconId: Int,
    bgColor: Color,
    iconColor: Color = Color.White,
    fabSize: Int = 40,
    iconSize: Int = 30,
    onClick: Lambda,
) {
    // Animate show/hide
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Card(
            elevation = CardDefaults.elevatedCardElevation(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = bgColor
            ),
            modifier = Modifier
                .size(fabSize.dp)
                .clickable {
                    onClick()
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = M
                        .size(iconSize.dp)
                        .align(Alignment.Center),
                    tint = iconColor,
                    painter = painterResource(id = iconId),
                    contentDescription = ""
                )
            }
        }
    }
}

@Composable
fun FabWrapper(
    fabRow : @Composable BoxScope.(Modifier)->Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = M.fillMaxSize()) {
        content()

        fabRow(M.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp))
    }
}