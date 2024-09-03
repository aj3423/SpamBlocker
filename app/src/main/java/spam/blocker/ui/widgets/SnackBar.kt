package spam.blocker.ui.widgets

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import spam.blocker.util.Lambda

object SnackBar {
    val state = SnackbarHostState()

    fun show(
        coroutine: CoroutineScope,
        content: String,
        actionLabel: String,
        onAction: Lambda
    ) {
        coroutine.launch {
            // cancel previous
            state.currentSnackbarData?.dismiss()

            val result = state.showSnackbar(
                content,
                actionLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction()
            }
        }
    }
}