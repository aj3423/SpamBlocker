package spam.blocker.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.util.Lambda1
import spam.blocker.util.spf
import kotlin.math.min
import kotlin.math.roundToInt

// Re-implement the bottom navigation because it's not customizable

const val BottomNavHeight = 56
const val MaxBottomBarWidth = 1000


data class TabItem(
    val route: String,
    val label: String,
    val icon: Int,

    // Using a State like selectedTab would cause extra recomposition,
    //  when it changes, all tabs got recomposed.
    // Use a State for each tab instead.
    val isSelected: MutableState<Boolean>,

    val badge: (@Composable BoxScope.() -> Unit)? = null,

    val content: @Composable () -> Unit,
)

data class BottomBarViewModel(
    val tabItems: List<TabItem>,
    val onTabSelected: Lambda1<String>,
    val onTabReSelected: Lambda1<String>
)

// Add a badge indicator to the top right of an Icon
@Composable
fun BoxScope.Badge(count: Int) {
    if (count > 0) {
        Box(
            modifier = M
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-4).dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = M.size(16.dp)
            ) {
                drawCircle(color = Salmon, radius = size.minDimension / 2)
            }
            Text(
                text = count.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = M.offset(y = 1.dp),
            )
        }
    }
}

@Composable
fun BottomBar(vm: BottomBarViewModel) {
    val C = LocalPalette.current
    val ctx = LocalContext.current


    var itemWidth by remember {
        mutableFloatStateOf(0F)
    }

    var currentRoute = remember { spf.Global(ctx).getActiveTab() }

    val density = LocalDensity.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = M
            .height(BottomNavHeight.dp)
            .fillMaxWidth()
            .background(C.bottomNavBg)
            .onGloballyPositioned {
                val totalWidthPx = min(it.size.width, MaxBottomBarWidth).toFloat()

                var totalWidthDp = with(density) {
                    totalWidthPx
                        .roundToInt()
                        .toDp().value.toInt()
                }
                totalWidthDp = min(totalWidthDp, 800)
                itemWidth = totalWidthDp / vm.tabItems.size.toFloat()
            }
    ) {
        RowVCenter(
            modifier = M.fillMaxSize(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 3 tab items
            vm.tabItems.forEach { tab ->
                Surface( // for the round clicking ripple
                    shape = RoundedCornerShape(30.dp),
                    modifier = M.fillMaxHeight()
                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = M
                            .fillMaxHeight()
                            .width(itemWidth.dp)
                            .background(C.bottomNavBg)
                            .clickable {
                                if (currentRoute == tab.route) { // reselect current tab
                                    vm.onTabReSelected(tab.route)
                                } else { // select new tab
                                    // hide the previous tab
                                    vm.tabItems.find { it.route == currentRoute }?.isSelected?.value =
                                        false
                                    // show the new tab
                                    tab.isSelected.value = true

                                    currentRoute = tab.route
                                    vm.onTabSelected(tab.route)
                                }
                            }
                    ) {
                        Box {
                            // icon
                            ResIcon(
                                iconId = tab.icon,
                                modifier = M
                                    .size(24.dp)
                                    .offset(y = 4.dp),
                                color = if (tab.isSelected.value) SkyBlue else C.textGrey
                            )
                            // badge

                            tab.badge?.invoke(this)
                        }
                        // label
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            color = if (tab.isSelected.value) SkyBlue else C.textGrey
                        )
                    }
                }
            }
        }
    }
}

