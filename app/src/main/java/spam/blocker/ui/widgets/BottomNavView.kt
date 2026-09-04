package spam.blocker.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.ui.M
import spam.blocker.ui.theme.White
import spam.blocker.util.Lambda1
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
    val C = G.palette


    var itemWidth by remember {
        mutableFloatStateOf(0F)
    }

    val density = LocalDensity.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = M
            .heightIn(min = BottomNavHeight.dp)
            .fillMaxWidth()
            .background(C.dialogBg)
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
            modifier = M.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // 3 tab items
            vm.tabItems.forEach { tab ->
                Surface( // for the round clicking ripple
                    shape = RoundedCornerShape(30.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = M
                            .width(itemWidth.dp)
                            .background(C.dialogBg)
                            .padding(vertical = 4.dp)
                            .clickable {
                                val currentRoute = vm.tabItems.firstOrNull { it.isSelected.value }?.route

                                if (currentRoute == tab.route) { // reselect current tab
                                    vm.onTabReSelected(tab.route)
                                } else { // select new tab
                                    currentRoute?.let {
                                        vm.onTabLeave(it)
                                    }

                                    // Keep exactly one selected tab.
                                    vm.tabItems.forEach {
                                        it.isSelected.value = it.route == tab.route
                                    }

                                    vm.onTabSelected(tab.route)
                                }
                            }
                    ) {
                        val badgeText = tab.badgeText()

                        BadgedBox(
                            badge = {
                                badgeText?.let {
                                    CompositionLocalProvider( // lock the sim number size regardless of system font scaling
                                        LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 1f)
                                    ) {
                                        Badge(
                                            modifier = M.offset(x = 8.dp, y = (-2).dp),
                                            containerColor = C.error,
                                            contentColor = White
                                        ) {
                                            Text(text = badgeText)
                                        }
                                    }
                                }
                            }
                        ) {
                            // icon
                            ResIcon(
                                iconId = tab.icon,
                                modifier = M.size(24.dp),
                                color = if (tab.isSelected.value) C.infoBlue else C.textGrey
                            )
                        }

                        // label
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            color = if (tab.isSelected.value) C.infoBlue else C.textGrey
                        )
                    }
                }
            }
        }
    }
}
