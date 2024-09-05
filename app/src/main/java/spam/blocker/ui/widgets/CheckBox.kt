package spam.blocker.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda1
import spam.blocker.util.loge


@Composable
fun CheckBox(
    checked: Boolean,
    onCheckChange: Lambda1<Boolean>,
    modifier: Modifier = Modifier,
    label: (@Composable ()->Unit)? = null,
) {
    RowVCenterSpaced(
        space = 4,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                indication = rememberRipple(color = MaterialTheme.colorScheme.primary),
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    onCheckChange(!checked)
                }
            )
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                uncheckedColor = LocalPalette.current.textGrey,
            ),
        )
        label?.let {
            label()
        }
    }
}
