package spam.blocker.ui.widgets

import androidx.compose.runtime.Composable

data class FormField(val label: String, var value: String = "")

@Composable
fun FormInputField(formField: FormField) {
    StrInputBox(
        text = formField.value,
        onValueChange = { formField.value = it },
        label = { GreyLabel(formField.label) }
    )
}