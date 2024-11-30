package spam.blocker.ui.widgets

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import spam.blocker.ui.theme.LocalPalette

@SuppressLint("ComposableNaming") @Composable
fun Str(strId: Int): String {
    return stringResource(id = strId)
}
@SuppressLint("ComposableNaming") @Composable
fun PluralStr(count: Int, strId: Int): String {
    return pluralStringResource(id = strId, count = count, formatArgs = arrayOf(count))
}

@Composable
fun ResIcon(
    iconId: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Icon(
        modifier = modifier,
        tint = color,
        painter = painterResource(id = iconId),
        contentDescription = ""
    )
}
@Composable
fun GreyIcon(
    iconId: Int,
    modifier: Modifier = Modifier,
) {
    ResIcon(iconId, modifier = modifier, color = LocalPalette.current.textGrey)
}
@Composable
fun GreyIcon16(
    iconId: Int,
    modifier: Modifier = Modifier,
) {
    GreyIcon(iconId, modifier = modifier.size(16.dp))
}
// for input box leading icons
@Composable
fun GreyIcon18(
    iconId: Int,
    modifier: Modifier = Modifier,
) {
    GreyIcon(iconId, modifier = modifier.size(18.dp))
}
@Composable
fun GreyIcon20(
    iconId: Int,
    modifier: Modifier = Modifier,
) {
    GreyIcon(iconId, modifier = modifier.size(20.dp))
}

@Composable
fun ResImage(
    resId: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        modifier = modifier,
        colorFilter = ColorFilter.tint(color),
        imageVector = ImageVector.vectorResource(id = resId),
        contentDescription = ""
    )
}
