package spam.blocker.ui.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.maxScreenHeight
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette


const val BalloonCornerRadius = 6
const val BalloonBorderWidthDark = 0.2
const val BalloonBorderWidthLight = 0.6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalloonWrapper(
    tooltip: String,
    offset: IntOffset? = null,
    body: @Composable (TooltipState) -> Unit,
) {

    val tooltipState = rememberTooltipState(isPersistent = true)

    TooltipBox(
        positionProvider = if (offset == null) {
            TooltipDefaults.rememberRichTooltipPositionProvider()
        } else {
            rememberPositionProvider(userOffset = offset)
        },
        tooltip = {
            RichTooltip(
                shape = RoundedCornerShape(BalloonCornerRadius.dp),
                modifier = Modifier
                    .border(
                        if (isSystemInDarkTheme()) BalloonBorderWidthDark.dp else BalloonBorderWidthLight.dp,
                        LocalPalette.current.balloonBorder,
                        shape = RoundedCornerShape(BalloonCornerRadius.dp)
                    )
            ) {

                val state = rememberScrollState()
                HtmlText(
                    tooltip,
                    modifier = M
                        .maxScreenHeight(0.9f)
                        .verticalScroll(state)
                        .simpleVerticalScrollbar(
                            state,
                            offsetX = 30,
                            persistent = true,
                            scrollBarColor = DarkOrange
                        )
                )
            }
        },
        state = tooltipState
    ) {
        body(tooltipState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalloonQuestionMark(tooltip: String, offset: IntOffset? = null) {
    BalloonWrapper(
        tooltip = tooltip,
        offset = offset,
    ) { tooltipState ->
        val scope = rememberCoroutineScope()
        ResImage(
            R.drawable.ic_question, ColdGrey, M
                .size(30.dp)
                .clickable { // put before `.padding` for larger clicking area
                    scope.launch {
                        tooltipState.show()
                    }
                }
                .padding(horizontal = 6.dp)
        )
    }
}
