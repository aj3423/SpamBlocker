package spam.blocker.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.White
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

    val badgeText: () -> String?,

    val content: @Composable () -> Unit,
)

data class BottomBarViewModel(
    val tabItems: List<TabItem>,
    val onTabSelected: Lambda1<String>,
    val onTabReSelected: Lambda1<String>,
    val onTabLeave: Lambda1<String>,
)

@Composable
fun BottomBar(vm: BottomBarViewModel) {
    val C = LocalPalette.current
    val ctx = LocalContext.current


    var itemWidth by remember {
        mutableFloatStateOf(0F)
    }

    var currentRoute = remember { spf.Global(ctx).activeTab }

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
                                    vm.onTabLeave(currentRoute)

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
                        val badgeText = tab.badgeText()

                        BadgedBox(
                            badge = {
                                badgeText?.let {
                                    Badge(
                                        containerColor = Salmon,
                                        contentColor = White
                                    ) {
                                        Text(text = badgeText)
                                    }
                                }
                            }
                        ) {
                            // icon
                            ResIcon(
                                iconId = tab.icon,
                                modifier = M
                                    .size(24.dp)
                                    .offset(y = 4.dp),
                                color = if (tab.isSelected.value) SkyBlue else C.textGrey
                            )
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

