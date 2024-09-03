package spam.blocker.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.util.M
import spam.blocker.util.Lambda1
import spam.blocker.util.Lambda2
import spam.blocker.util.Util
import spam.blocker.util.hasFlag
import spam.blocker.util.setFlag
import spam.blocker.util.toFlagStr

// Code copied from OutlinedInputBox, modifications:
//  - reduce height
//  - remove the content padding-top
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle(
        color = LocalPalette.current.textGrey,
        fontWeight = FontWeight.SemiBold,
    ),
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIconId: Int? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingTextStr: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        // leading icon
        focusedLeadingIconColor = ColdGrey,
        unfocusedLeadingIconColor = ColdGrey.copy(alpha = 0.9f),

        // label
        focusedLabelColor = SkyBlue,
        unfocusedLabelColor = ColdGrey.copy(alpha = 0.9f),

        // border
        focusedBorderColor = SkyBlue,
        unfocusedBorderColor = LocalPalette.current.textGrey,

        // error
        errorBorderColor = Salmon,
        errorPlaceholderColor = Salmon,
        errorTextColor = Salmon,
        errorCursorColor = Salmon,
        errorSupportingTextColor = Salmon,
        errorLabelColor = Salmon,
    ),
) {

    Column(
        modifier = if (label != null) {
            modifier
                // Merge semantics at the beginning of the modifier chain to ensure padding is
                // considered part of the text field.
                .semantics(mergeDescendants = true) {}
                .padding(top = 6.dp)
        } else {
            modifier
        }
    ) {
        BasicTextField(
            value = value,
            modifier = M
                .defaultMinSize(
                    minWidth = 2000.dp, // use a large value, it wil be shrunk automatically
                    minHeight = 36.dp, // 36 is enough
                ),
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            cursorBrush = SolidColor(
                if (isError) Salmon else LocalPalette.current.textGrey
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            decorationBox = @Composable { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(

                    // the padding of the text within the box
                    contentPadding = PaddingValues(
                        16.dp,
                        10.dp
                    ), // remove the vertical padding TODO

                    value = value.text,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    label = label,
                    leadingIcon = if (leadingIconId == null) {
                        null
                    } else {
                        {
                            ResIcon(
                                leadingIconId,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    trailingIcon = trailingIcon,
                    prefix = prefix,
                    suffix = suffix,
                    singleLine = singleLine,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.ContainerBox(
                            enabled,
                            isError,
                            interactionSource,
                            colors,
                            shape
                        )
                    }
                )
            }
        )

        // support text
        if (supportingTextStr != null) {
            Text(
                text = supportingTextStr,
                color = Salmon,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                modifier = M.padding(4.dp),
            )
        }
    }
}

@Composable
fun NumberInputBox(
    intValue: Int?,
    onValueChange: (Int?, Boolean) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIconId: Int? = null,
) {
    // Code learned from the built-in BasicTextField.kt
    var state by remember {
        mutableStateOf(
            TextFieldValue(text = intValue?.toString() ?: "")
        )
    }
    // Last String value that either text field was recomposed with or updated in the onValueChange
    // callback. We keep track of it to prevent calling onValueChange(String) for same String when
    // CoreTextField's onValueChange is called multiple times without recomposition in between.
    var lastText by remember(intValue) { mutableStateOf(intValue?.toString() ?: "") }
    var hasError by remember(lastText) { mutableStateOf(lastText.toIntOrNull() == null) }

    InputBox(
        value = state,
        onValueChange = { newState ->
            val newText = newState.text
            val int = newText.toIntOrNull()

            hasError = int == null

            if (int == null) {
                if (newText.isEmpty()) { // empty string
                    state = newState
                    lastText = newState.text
                }
                // It's up to the caller to check if it's null and decide whether to
                // update the state.
                onValueChange(null, hasError)
            } else {
                state = newState

                val stringChangedSinceLastInvocation = lastText != newState.text
                lastText = newState.text

                if (stringChangedSinceLastInvocation) {
                    onValueChange(int, hasError)
                }
            }
        },
        label = label,
        placeholder = placeholder,
        leadingIconId = leadingIconId,
        supportingTextStr = if (hasError) Str(R.string.invalid_number) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        trailingIcon = {
            if (lastText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        state = TextFieldValue()
                        lastText = ""
                        onValueChange(null, true)
                    }) {
                    ResIcon(
                        R.drawable.ic_clear,
                        color = LocalPalette.current.textGrey,
                        modifier = M.size(16.dp),
                    )
                }
            }
        }
    )
}


@Composable
fun StrInputBox(
    text: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIconId: Int? = null,

    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 10,
) {
    // Code learned from the built-in BasicTextField.kt
    var state by remember {
        mutableStateOf(
            TextFieldValue(text = text)
        )
    }
    // Last String value that either text field was recomposed with or updated in the onValueChange
    // callback. We keep track of it to prevent calling onValueChange(String) for same String when
    // CoreTextField's onValueChange is called multiple times without recomposition in between.
    var lastText by remember(text) { mutableStateOf(text) }

    InputBox(
        value = state,
        onValueChange = { newState ->
            state = newState

            val stringChangedSinceLastInvocation = lastText != newState.text
            lastText = newState.text

            if (stringChangedSinceLastInvocation) {
                onValueChange(lastText)
            }
        },
        label = label,
        singleLine = singleLine,
        maxLines = maxLines,
        placeholder = placeholder,
        leadingIconId = leadingIconId,
        keyboardOptions = KeyboardOptions(),
        trailingIcon = {
            if (lastText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        state = TextFieldValue()
                        lastText = ""
                        onValueChange("")
                    }) {
                    ResIcon(
                        R.drawable.ic_clear,
                        color = LocalPalette.current.textGrey,
                        modifier = M.size(16.dp),
                    )
                }
            }
        }
    )
}

@Composable
fun RegexInputBox(
    regexStr: String,
    onRegexStrChange: Lambda2<String, Boolean>,
    regexFlags: Int,
    onRegexFlagsChange: Lambda1<Int>,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIconId: Int? = null,
) {
    val ctx = LocalContext.current

    // Code learned from the built-in BasicTextField.kt
    var state by remember {
        mutableStateOf(
            TextFieldValue(text = regexStr)
        )
    }
    // Last String value that either text field was recomposed with or updated in the onValueChange
    // callback. We keep track of it to prevent calling onValueChange(String) for same String when
    // CoreTextField's onValueChange is called multiple times without recomposition in between.
    var lastText by remember(regexStr) { mutableStateOf(regexStr) }
    var errorStr = remember(lastText) {
        Util.validateRegex(ctx, lastText, regexFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER))
    }

    InputBox(
        modifier = modifier,
        value = state,
        onValueChange = { newState ->
            state = newState

            val stringChangedSinceLastInvocation = lastText != newState.text
            lastText = newState.text

            errorStr =
                Util.validateRegex(ctx, lastText, regexFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER))

            if (stringChangedSinceLastInvocation) {
                onRegexStrChange(lastText, errorStr != null)
            }
        },
        label = label,
        placeholder = placeholder,
        leadingIconId = leadingIconId,
        supportingTextStr = errorStr,
        singleLine = false,
        maxLines = 10,
        trailingIcon = {
            val C = LocalPalette.current

            val dropdownItems = remember {
                val list = mutableListOf(
                    CustomItem {
                        RowVCenter(
                            modifier = M.padding(horizontal = 10.dp)
                        ) {
                            GreyLabel(Str(R.string.regex_flags))
                            BalloonQuestionMark(helpTooltipId = R.string.help_regex_flags)
                        }
                    },
                    DividerItem(thickness = 1, color = C.disabled),
                )

                val ret = list + ctx.resources.getStringArray(R.array.regex_flags_list)
                    .mapIndexed { idx, label ->
                        CheckItem(
                            label = label,
                            checked = when (idx) {
                                0 -> regexFlags.hasFlag(Def.FLAG_REGEX_IGNORE_CASE)
                                1 -> regexFlags.hasFlag(Def.FLAG_REGEX_DOT_MATCH_ALL)
                                else -> regexFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER)
                            },
                            onCheckChange = { checked ->
                                val newFlag = regexFlags.setFlag(
                                    when (idx) {
                                        0 -> Def.FLAG_REGEX_IGNORE_CASE
                                        1 -> Def.FLAG_REGEX_DOT_MATCH_ALL
                                        else -> Def.FLAG_REGEX_RAW_NUMBER
                                    },
                                    checked
                                )
                                onRegexFlagsChange(newFlag)
                            },
                        )
                    }
                ret
            }

            DropdownWrapper(items = dropdownItems) { expanded ->
                val imdlc = regexFlags.toFlagStr()
                val modifier = M.clickable {
                    expanded.value = true
                }
                if (imdlc == "") {
                    GreyIcon(
                        modifier = modifier.padding(8.dp),
                        iconId = R.drawable.ic_flags,
                    )
                } else {
                    Text(
                        text = imdlc,
                        color = Color.Magenta,
                        modifier = modifier,
                    )
                }
            }
        }
    )
}
