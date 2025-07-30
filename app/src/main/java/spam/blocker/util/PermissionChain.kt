package spam.blocker.util

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.PermissionLauncher.launcherProtected
import spam.blocker.util.PermissionLauncher.launcherRegular
import spam.blocker.util.Util.doOnce


class PermissionWrapper(
    val perm: PermissionType.Basic,

    val isOptional: Boolean = false,
    // Show a prompt dialog before asking for this permission, explaining why it's required
    val prompt: String? = null,
)

/*
    Convenient class for asking for multiple permissions,

    A callback is triggered with param:
    - true: all required permissions are granted
    - false: at least one of required permissions failed.
 */
class PermissionChain() {
    // final callback
    private lateinit var onResult: (Boolean) -> Unit

    private lateinit var currList: MutableList<PermissionWrapper>
    private lateinit var curr: PermissionWrapper

    private lateinit var popupTrigger: MutableState<Boolean>

    // Prepare UI elements, initialized once in MainActivity
    @Composable
    fun Compose() {
        val ctx = LocalContext.current

        popupTrigger = remember { mutableStateOf(false) }
        if (popupTrigger.value) {
            PopupDialog(
                trigger = popupTrigger,
                content = {
                    HtmlText(curr.prompt!!, color = LocalPalette.current.textGrey)
                },
                buttons = {
                    GreyButton("cancel") {
                        popupTrigger.value = false
                        onResult(false)
                    }
                    Spacer(modifier = M.width(10.dp))

                    StrokeButton("ok", Teal200) {
                        popupTrigger.value = false
                        handle(ctx)
                    }
                }
            )
        }

        launcherRegular = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // Update the global permission
            curr.perm.onResult(ctx, isGranted)

            if (isGranted || curr.isOptional) {
                checkNext(ctx)
            } else {
                onResult(false)
            }
        }

        launcherProtected = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            // The app process will be killed when the "all file access" is granted and then revoked
            //   (by turning on the permission switch in system settings and then immediately turned off).
            // After returning back to this app, android will launch a new process, but these
            //   `lateinit` variables haven't been initialized in the new process, which leads to a crash.
            // So check it first, if it's uninitialized, ignore and return.
            if (!::curr.isInitialized) {
                return@rememberLauncherForActivityResult
            }

            val isGranted = curr.perm.check(ctx)

            // Call the result callback
            curr.perm.onResult(ctx, isGranted)

            if (isGranted || curr.isOptional) {
                checkNext(ctx)
            } else {
                onResult(false)
            }
        }
    }

    fun ask(
        ctx: Context,
        permissions: List<PermissionWrapper>,
        onResult: (Boolean) -> Unit
    ) {
        this.onResult = onResult
        currList = ArrayList(permissions) // make a copy
        checkNext(ctx)
    }

    private fun checkNext(ctx: Context) {
        if (currList.isEmpty()) {
            onResult(true)
            return
        }

        curr = currList.first()
        currList.removeAt(0)

        if (curr.perm.isGranted) { // already granted
            checkNext(ctx)
            return
        }

        if (curr.isOptional) {
            val isFirstTime = doOnce(ctx, "ask_once_${curr.perm::class.java.simpleName.substringAfterLast('$')}") {
                handleCurrPermission(ctx)
            }
            if (!isFirstTime) {
                checkNext(ctx)
            }
            return
        } else {
            handleCurrPermission(ctx)
        }
    }

    private fun handleCurrPermission(ctx: Context) {
        if (curr.prompt == null) {
            handle(ctx)
        } else { // show prompt dialog
            popupTrigger.value = true
        }
    }

    private fun handle(ctx: Context) {
        curr.perm.ask(ctx)
    }
}

