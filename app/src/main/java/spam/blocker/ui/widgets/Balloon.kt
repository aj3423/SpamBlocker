package spam.blocker.ui.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.maxScreenHeight
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda1


const val BalloonCornerRadius = 6
const val BalloonBorderWidthDark = 0.2
const val BalloonBorderWidthLight = 0.6

// For embedding images, see HtmlText.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalloonWrapper(
    tooltip: String,
    onCustomLinkClick: Lambda1<String>? = null,
    body: @Composable (TooltipState) -> Unit,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
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
                        ),
                    onCustomLinkClick = onCustomLinkClick,
                    onRandomClick = {
                        scope.launch {
                            tooltipState.dismiss()
                        }
                    }
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
fun BalloonQuestionMark(tooltip: String, size: Int = 18) {
    BalloonWrapper(
        tooltip = tooltip,
    ) { tooltipState ->
        val scope = rememberCoroutineScope()
        ResImage(
            R.drawable.ic_question_circle, ColdGrey, M
                .width(size.dp)
                .clickable { // put before `.padding` for larger clicking area
                    scope.launch {
                        tooltipState.show()
                    }
                }
        )
    }
}
