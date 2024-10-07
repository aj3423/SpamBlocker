package spam.blocker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color


@Immutable
data class CustomColorsPalette(
    val textGrey: Color = Color.Unspecified,
    val textGreen: Color = Color.Unspecified,
    val schedule: Color = Color.Unspecified,
    val enabled: Color = Color.Unspecified,
    val disabled: Color = Color.Unspecified,
    val pass: Color = Color.Unspecified,
    val block: Color = Color.Unspecified,
    val cardBorder: Color = Color.Unspecified,

    // switch box
    val switchTrackOn: Color = Color.Unspecified,
    val switchThumbOn: Color = Color.Unspecified,
    val switchTrackOff: Color = Color.Unspecified,
    val switchThumbOff: Color = Color.Unspecified,

    // popup
    val dialogBg: Color = Color.Unspecified,
    val dialogBorder: Color = Color.Unspecified,
    val menuBg: Color = Color.Unspecified,
    val menuBorder: Color = Color.Unspecified,

    val bottomNavBg: Color = Color.Unspecified, // bg of bottom nav bar

    val balloonBorder: Color = Color.Unspecified,
//    val fragmentBg         :Color = Color.Unspecified,
//    val inputHint          :Color = Color.Unspecified,
//    val groupBorder        :Color = Color.Unspecified,
//    val launcherBackground :Color = Color.Unspecified,
)

val LightCustomColorsPalette = CustomColorsPalette(
    textGrey = ColdGrey,
    textGreen = Emerald,

    schedule = MayaBlue,
    enabled = Teal200,
    disabled = ColdGrey,
    pass = Emerald,
    block = Salmon,
    cardBorder = SwissCoffee,

    switchTrackOn = Color(0xffb3f4ed),
    switchThumbOn = Teal200,
    switchTrackOff = Color(0xffb2b2b2),
    switchThumbOff = Color(0xffececec),

    bottomNavBg = Color.White,
    dialogBg = Color.White,
    dialogBorder = SwissCoffee,

    menuBg = Color.White,
    menuBorder = LightGrey,

    balloonBorder = OrangeRed,
)

val DarkCustomColorsPalette = CustomColorsPalette(
    textGrey = SilverGrey,
    textGreen = LightGreen,

    schedule = MayaBlue,
    enabled = Teal200,
    disabled = ColdGrey,
    pass = Emerald,
    block = Salmon,
    cardBorder = DarkGrey,

    switchTrackOn = Color(0xff0e4f48),
    switchThumbOn = Teal200,
    switchTrackOff = Color(0xff5a5a5a),
    switchThumbOff = ColdGrey,

    bottomNavBg = RaisinBlack,
    dialogBg = RaisinBlack,
    dialogBorder = Grey383838,

    menuBg = RaisinBlack,
    menuBorder = Grey424242,

    balloonBorder = DarkOrange,
)

// Usage:
//   LocalPalette.current.extraColor1
val LocalPalette = staticCompositionLocalOf { CustomColorsPalette() }

private val LightColorScheme = lightColorScheme(
    primary = Teal200,
    secondary = Teal200,
    tertiary = Pink40,
    background = Color.White,
    surfaceTint = White, // this affects pop menu bg
    surface = White, // systembar
    surfaceContainer = White, // balloon bg
)
private val DarkColorScheme = darkColorScheme(
    primary = Teal200,
    secondary = Teal200,
    tertiary = Pink80,
    background = Black111111,
    surfaceTint = White, // this affects pop menu bg
    surface = Black111111, // systembar

//    onPrimary               = Color.Red,
//    primaryContainer        = Color.Red,
//    onPrimaryContainer      = Color.Red,
//    inversePrimary          = Color.Red,
//    onSecondary             = Color.Red,
//    secondaryContainer      = Color.Red,
//    onSecondaryContainer    = Color.Red,
//    onTertiary              = Color.Red,

//    tertiaryContainer       = Color.Yellow,
//    onTertiaryContainer     = Color.Yellow,
//    onBackground            = Color.Yellow,
//    onSurface               = Color.Yellow,
//    surfaceVariant          = Color.Yellow,
//    onSurfaceVariant        = Color.Yellow,
//    inverseSurface          = Color.Yellow,

//    inverseOnSurface        = Color.Blue,
//    error                   = Color.Blue,
//    onError                 = Color.Blue,
//    errorContainer          = Color.Blue,
//    onErrorContainer        = Color.Blue,
//    outline                 = Color.Blue,
//    outlineVariant          = Color.Blue,
//    scrim                   = Color.Blue,

//    surfaceBright           = Color.Green,
//    surfaceContainerHigh    = Color.Green,
//    surfaceContainerHighest = Color.Green,
//    surfaceContainerLow     = Color.Green,
//    surfaceContainerLowest  = Color.Green,
//    surfaceDim              = Color.Green,
)

@Composable
fun AppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // logic for which custom palette to use
    val customColorsPalette =
        if (darkTheme) DarkCustomColorsPalette
        else LightCustomColorsPalette

    // here is the important point, where you will expose custom objects
    CompositionLocalProvider(
        LocalPalette provides customColorsPalette // our custom palette
    ) {
        MaterialTheme(
            typography = Typography,
            colorScheme = colorScheme, // the MaterialTheme still uses the "normal" palette
            content = content,
        )
    }
}