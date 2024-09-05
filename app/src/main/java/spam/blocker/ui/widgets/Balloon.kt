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
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.util.loge


const val BalloonCornerRadius = 6
const val BalloonBorderWidthDark = 0.2
const val BalloonBorderWidthLight = 0.6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalloonQuestionMark(helpTooltipId: Int) {
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
                            DarkOrange,
                        shape = RoundedCornerShape(BalloonCornerRadius.dp)
                    )
            ) {
                val state = rememberScrollState()
                HtmlText(
                    Str(helpTooltipId),
                    modifier = M
                    .verticalScroll(state)
                    .verticalScrollbar(state, offsetX = 30, persistent = true)
                )
            }
        },
        state = tooltipState
    ) {
        ResImage(
            R.drawable.ic_question, ColdGrey, M
                .size(30.dp)
                .clickable { // put before `.padding` for larger clicking area
                    scope.launch { tooltipState.show() }
                }
                .padding(horizontal = 6.dp)
        )
    }
}

