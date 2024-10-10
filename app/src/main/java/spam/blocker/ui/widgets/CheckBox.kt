package spam.blocker.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda1


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
            .clickable(
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    onCheckChange(!checked)
                }
            )
            .clip(MaterialTheme.shapes.small)
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
