package spam.blocker.ui.widgets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.util.Lambda
import spam.blocker.util.thenIf

// A search box at the top of a list of records, e.g., history records and regex rules
@Composable
fun SearchBox(
    visible: MutableState<Boolean>,
    filter: MutableState<String>,
    autoExit: Boolean = true, // when empty, clicking the trailing X hides this search box
    autoFocus: Boolean = true,
    refresh: Lambda,
) {
    if (visible.value) {
        val focusRequester = remember { FocusRequester() }

        if (autoFocus) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        StrInputBox(
            text = filter.value,
            leadingIconId = R.drawable.ic_filter,
            onValueChange = {
                filter.value = it
                refresh()
            },
            alwaysShowClear = true,
            onClear = { prevText ->
                if (autoExit) {
                    if (prevText.isEmpty()) { // already empty, close
                        visible.value = false
                    } else { // not empty, clear content
                        filter.value = ""
                        refresh()
                    }
                } else {
                    filter.value = ""
                    refresh()
                }
            },
            modifier = M.thenIf(autoFocus) {
                focusRequester(focusRequester)
            }
        )

        Spacer(M.height(8.dp))
    }
}
