package spam.blocker.ui.widgets

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            withContext(IO) {
                // cancel previous
                dismiss()

                val result = state.showSnackbar(
                    if (content.length > 50) content.substring(0, 50) + "…" else content,
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
    fun dismiss() {
        state.currentSnackbarData?.dismiss()
    }
}