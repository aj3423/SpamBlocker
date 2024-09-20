package spam.blocker.ui.setting.regex

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SwipeInfo

@Composable
fun RuleSearchBox(
    vm: RuleViewModel,
) {
    val ctx = LocalContext.current

    if (vm.searchEnabled.value) {

        LeftDeleteSwipeWrapper(
            left = SwipeInfo(
                onSwipe = {
                    vm.searchEnabled.value = false
                    vm.filter = ""
                    vm.reload(ctx)
                }
            )
        ) {
            val focusRequester = remember { FocusRequester() }
            var textFieldLoaded by remember { mutableStateOf(false) }

            StrInputBox(
                text = "",
                leadingIconId = R.drawable.ic_find,
                onValueChange = {
                    vm.filter = it
                    vm.reload(ctx)
                },
                // Auto focus, and force scroll to input box.
                modifier = M
                    .focusRequester(focusRequester)
                    .onGloballyPositioned {
                        if (!textFieldLoaded) {
                            focusRequester.requestFocus() // IMPORTANT
                            textFieldLoaded = true // stop cyclic recompositions
                        }
                    }
            )
        }

        Spacer(M.height(8.dp))
    }
}
