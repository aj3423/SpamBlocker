package spam.blocker.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SwissCoffee

@Composable
fun Section(
    title: String,
    horizontalPadding : Int = 0,
    bgColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable ()->Unit,
) {
    val C = LocalPalette.current
    Box(modifier = M.padding(top = 8.dp, bottom = 8.dp)) {
        // the rectangle section border line
        Box(
            modifier = M
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding.dp)
                .border(0.5.dp, C.cardBorder, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .wrapContentHeight()
        ) {
            content()
        }

        // the section title
        Box(
            modifier = M
                .wrapContentWidth()
                .offset(20.dp, (-8).dp)
                .background(bgColor)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = SwissCoffee,
                lineHeight = 13.sp,
                modifier = Modifier.padding(10.dp, 0.dp),
            )
        }


    }
}