package spam.blocker.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Orange
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.util.Lambda1
import spam.blocker.util.Lambda2
import spam.blocker.util.Util
import spam.blocker.util.Util.regexWildcardNotSupported
import spam.blocker.util.hasFlag
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.setFlag
import spam.blocker.util.toFlagStr


// Code copied from OutlinedInputBox, modifications:
//  - reduce height:
//      defaultMinSize( minHeight = 36.dp ),
//  - reduce the content padding-top:
//      contentPadding = PaddingValues( 16.dp, 12.dp ),

const val MAX_STR_LEN = 1000

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
    limitTextLength: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null, // size should be 18.dp by default
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingTextStr: String? = null,
    warnings: SnapshotStateList<String> = mutableStateListOf<String>(),
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

    // The input box will freeze for super long text, to solve this, if the input text exceeds
    // this length, the text will be truncated to this length, and the edit will be disabled.
    // There is no point to edit such long text, it has to be a imported rule.
    val exceedsMaxLen = limitTextLength && (value.text.length > MAX_STR_LEN)

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
            value = if (exceedsMaxLen)
                value.copy(text = value.text.substring(0, MAX_STR_LEN))
            else
                value,
            modifier = M
                .fillMaxWidth()
                .defaultMinSize(
                    minHeight = 36.dp, // 36 is enough
                ),
            onValueChange = onValueChange,
            enabled = enabled && !exceedsMaxLen,
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
                        12.dp
                    ),

                    value = value.text,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    label = label,
                    leadingIcon = leadingIcon,
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

        // Warnings, yellow text
        val warningList = warnings.toMutableList()

        if (exceedsMaxLen) {
            warningList += Str(R.string.text_too_long)
        }
        warningList.forEach {
            Text(
                text = it,
                color = Orange,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                modifier = M.padding(4.dp),
            )
        }

        // Errors, red text
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
    modifier: Modifier = Modifier,
    onValueChange: (Int?, Boolean) -> Unit,
    enabled: Boolean = true,
    allowEmpty: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    helpTooltip: @Composable (() -> Unit)? = null,
) {
    // Code learned from the built-in BasicTextField.kt
    var state by remember {
        mutableStateOf(
            TextFieldValue(text = intValue?.toString() ?: "")
        )
    }
    LaunchedEffect(intValue) {
        if (intValue?.toString() != state.text) {
            state = TextFieldValue(
                text = intValue?.toString() ?: "",
                selection = TextRange(intValue?.toString()?.length ?: 0)
            )
        }
    }


    // Last String value that either text field was recomposed with or updated in the onValueChange
    // callback. We keep track of it to prevent calling onValueChange(String) for same String when
    // CoreTextField's onValueChange is called multiple times without recomposition in between.
    var lastText by remember(intValue) { mutableStateOf(intValue?.toString() ?: "") }
    var hasError by remember(lastText) {
        mutableStateOf(
            if (allowEmpty && lastText.isEmpty())
                false
            else
                lastText.toIntOrNull() == null
        )
    }

    InputBox(
        value = state,
        modifier = modifier,
        onValueChange = { newState ->
            state = newState

            val newText = newState.text
            val int = newText.toIntOrNull()

            hasError = if (allowEmpty && newText.isEmpty()) false else int == null

            if (int == null) {
                if (newText.isEmpty()) { // empty string
                    lastText = newState.text
                }
                // It's up to the caller to check if it's null and decide whether to
                // update the state.
                onValueChange(null, hasError)
            } else {
                val stringChangedSinceLastInvocation = lastText != newState.text
                lastText = newState.text

                if (stringChangedSinceLastInvocation) {
                    onValueChange(int, hasError)
                }
            }
        },
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        enabled = enabled,
        supportingTextStr = if (hasError) Str(R.string.invalid_number) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        trailingIcon = {
            RowVCenter {
                if (lastText.isNotEmpty() && enabled) {
                    GreyIcon16(
                        R.drawable.ic_clear,
                        modifier = M.clickable{
                            state = TextFieldValue()
                            lastText = ""
                            onValueChange(null, true)
                        }
                    )
                }
                helpTooltip?.let { it() }
            }
        }
    )
}

@Composable
fun NumberInputBox(
    intValue: Int?,
    modifier: Modifier = Modifier,
    onValueChange: (Int?, Boolean) -> Unit,
    enabled: Boolean = true,
    allowEmpty: Boolean = false,
    labelId: Int? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIconId: Int? = null,
    helpTooltipId: Int? = null,
) {
    NumberInputBox(
        intValue = intValue,
        modifier = modifier,
        onValueChange = onValueChange,
        enabled = enabled,
        allowEmpty = allowEmpty,
        label = if (labelId == null) null else { { Text(Str(labelId)) } },
        placeholder = placeholder,
        leadingIcon = if (leadingIconId == null ) {
            null
        } else {
            {
                ResIcon(iconId = leadingIconId, modifier = M.size(18.dp))
            }
        },
        helpTooltip = {
            helpTooltipId?.let {
                BalloonQuestionMark(LocalContext.current.getString(it))
            }
        },
    )
}

@Composable
fun StrInputBox(
    text: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIconId: Int? = null,
    helpTooltip: String? = null,
    enabled: Boolean = true,

    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 10,
) {
    // Code learned from the built-in BasicTextField.kt
    var state by remember {
        mutableStateOf(
            TextFieldValue(text = text)
        )
    }
    // update state when the text is changed from other places rather typing in the textbox itself.
    LaunchedEffect(text) {
        val oldSelection = state.selection
        state = TextFieldValue(
            text = text,
            selection = oldSelection
        )
    }
    // Last String value that either text field was recomposed with or updated in the onValueChange
    // callback. We keep track of it to prevent calling onValueChange(String) for same String when
    // CoreTextField's onValueChange is called multiple times without recomposition in between.
    var lastText by remember(text) { mutableStateOf(text) }

    InputBox(
        value = state,
        modifier = modifier,
        enabled = enabled,
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
        leadingIcon = leadingIconId?.let {
            { GreyIcon18(it) }
        },
        keyboardOptions = KeyboardOptions(),
        trailingIcon = {
            RowVCenter {
                if (lastText.isNotEmpty() && enabled) {
                    GreyIcon16(
                        R.drawable.ic_clear,
                        modifier = M.clickable{
                            state = TextFieldValue()
                            lastText = ""
                            onValueChange("")
                        }
                    )
                }

                helpTooltip?.let {
                    BalloonQuestionMark(it)
                }
            }
        }
    )
}

val regexTestString = mutableStateOf("")

@Composable
fun TestRegexDialog(
    trigger: MutableState<Boolean>,
    regexStr: String,
    regexFlags: Int,
) {
    val C = LocalPalette.current

    val result: MutableState<Boolean?> = remember { mutableStateOf(null) }

    fun clearResult() {
        result.value = null
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            BalloonQuestionMark(Str(R.string.help_test_regex))
            StrokeButton(
                label = Str(R.string.test),
                color = Teal200,
                onClick = {
                    result.value = regexStr.regexMatchesNumber(regexTestString.value, regexFlags)
                }
            )
        },
    ) {
        StrInputBox(
            text = regexTestString.value,
            label = {
                Text(
                    Str(R.string.target_text),
                    color = Color.Unspecified
                )
            },
            onValueChange = {
                regexTestString.value = it
                clearResult()
            },
            leadingIconId = R.drawable.ic_find_check,
            maxLines = 10,
        )
        if (result.value != null) {

            Text(
                text = Str(
                    if (result.value == true)
                        R.string.match_found else R.string.match_not_found
                ),
                color = if (result.value == true)
                    C.pass else C.block
            )
        }
    }
}

@Composable
fun RegexInputBox(
    regexStr: String,
    onRegexStrChange: Lambda2<String, Boolean>,
    regexFlags: MutableIntState,// don't replace this type to Int, it'll cause bug, not sure why
    onFlagsChange: Lambda1<Int>,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null, // it can be a clickable icon
    helpTooltipId: Int? = null,
    testable: Boolean = false,
    showFlagsIcon: Boolean = true,
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

    fun validateError(): String? {
        return Util.validateRegex(
            ctx,
            lastText,
            regexFlags.intValue.hasFlag(Def.FLAG_REGEX_RAW_NUMBER) ||
                    regexFlags.intValue.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP) ||
                    regexFlags.intValue.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
        )
    }

    fun validateWarning(): List<String> {

        val ret = mutableListOf<String>()
        if (regexWildcardNotSupported(lastText)) {
            ret += ctx.getString(R.string.waning_using_wildcard_as_regex)
        }
        return ret
    }

    var errorStr by remember(lastText) {
        mutableStateOf(validateError())
    }

    val warnings = remember(lastText) {
        mutableStateListOf<String>().apply {
            addAll(validateWarning())
        }
    }

    InputBox(
        modifier = modifier,
        value = state,
        onValueChange = { newState ->
            state = newState

            val stringChangedSinceLastInvocation = lastText != newState.text
            lastText = newState.text

            if (stringChangedSinceLastInvocation) {
                warnings.apply { // update warnings
                    clear()
                    addAll(validateWarning())
                }
                errorStr = validateError() // update errorStr before callback
                onRegexStrChange(lastText, errorStr != null)
            }
        },
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        limitTextLength = true,
        warnings = warnings,
        supportingTextStr = errorStr,
        singleLine = false,
        maxLines = 10,
        trailingIcon = {
            val hasI =
                remember { mutableStateOf(regexFlags.intValue.hasFlag(Def.FLAG_REGEX_IGNORE_CASE)) }
            val hasD =
                remember { mutableStateOf(regexFlags.intValue.hasFlag(Def.FLAG_REGEX_DOT_MATCH_ALL)) }
            val hasR =
                remember { mutableStateOf(regexFlags.intValue.hasFlag(Def.FLAG_REGEX_RAW_NUMBER)) }
            val hasCC =
                remember { mutableStateOf(regexFlags.intValue.hasFlag(Def.FLAG_REGEX_OMIT_CC)) }

            // a fix for Tooltip+DropdownMenu
            val dropdownOffset = remember {
                mutableStateOf(Offset.Zero)
            }

            // Validate the regex on flags change, it should disappear for `+123` when RawMode is turned on.
            LaunchedEffect(regexFlags.intValue) {
                errorStr = validateError()
            }

            val dropdownItems = remember {
                val list = mutableListOf(
                    CustomItem {
                        RowVCenter(
                            modifier = M.padding(horizontal = 10.dp)
                        ) {
                            GreyLabel(Str(R.string.regex_flags))
                            BalloonQuestionMark(
                                tooltip = Str(R.string.help_regex_flags),
                                dropdownOffset.value.round()
                            )
                        }
                    },
                    DividerItem(thickness = 1),
                )

                val ret = list + ctx.resources.getStringArray(R.array.regex_flags_list)
                    .mapIndexed { idx, label ->
                        CheckItem(
                            label = label,
                            state = when (idx) {
                                0 -> hasR
                                1 -> hasCC
                                2 -> hasI
                                else -> hasD // 3
                            },
                            onCheckChange = { checked ->
                                when (idx) {
                                    0 -> hasR.value = checked
                                    1 -> hasCC.value = checked
                                    2 -> hasI.value = checked
                                    3 -> hasD.value = checked
                                }
                                val newVal = regexFlags.intValue.setFlag(
                                    when (idx) {
                                        0 -> Def.FLAG_REGEX_RAW_NUMBER
                                        1 -> Def.FLAG_REGEX_OMIT_CC

                                        2 -> Def.FLAG_REGEX_IGNORE_CASE
                                        else -> Def.FLAG_REGEX_DOT_MATCH_ALL // 3
                                    },
                                    checked
                                )

                                onFlagsChange(newVal)
                            },
                        )
                    }
                ret
            }

            // Trailing icons
            // This is an ugly workaround for adding 10.dp paddingEnd to the last icon
            //  when there are more than 1 icons. Because when there are more than 1 icons,
            //  the paddingEnd becomes 0, no idea why.
            var iconCount = 0

            if (showFlagsIcon) iconCount++
            if (testable) iconCount++
            if (helpTooltipId != null) iconCount++

            val paddingEnd = if (iconCount > 1) 10 else 0

            RowVCenter(modifier = M.padding(paddingEnd.dp)) {

                // flags icon
                if (showFlagsIcon) {
                    DropdownWrapper(
                        items = dropdownItems,
                        modifier = M
                            .onGloballyPositioned {
                                dropdownOffset.value = it.positionOnScreen()
                            }
                    ) { expanded ->

                        val imdlc = regexFlags.intValue.toFlagStr()
                        val clickableModifier = M
                            .clickable {
                                expanded.value = true
                            }
                        if (imdlc == "") {
                            GreyIcon(
                                modifier = clickableModifier,
                                iconId = R.drawable.ic_flags,
                            )
                        } else {
                            Text(
                                text = imdlc,
                                color = Color.Magenta,
                                modifier = clickableModifier
                                    .defaultMinSize(minWidth = 24.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }

                // Testing Tube icon
                if (testable) {
                    val trigger = remember { mutableStateOf(false) }

                    if (trigger.value) {
                        TestRegexDialog(
                            trigger = trigger,
                            regexStr = state.text,
                            regexFlags = regexFlags.intValue,
                        )
                    }

                    ResIcon(
                        R.drawable.ic_tube,
                        color = Teal200,
                        modifier = M
                            .clickable {
                                trigger.value = true
                            }
                            .size(24.dp),
                    )
                }

                // Help question icon
                helpTooltipId?.let {
                    BalloonQuestionMark(LocalContext.current.getString(it))
                }
            }
        }
    )
}

@Composable
fun PriorityBox(
    priority: Int,
    onValueChange: (Int?, Boolean) -> Unit,
) {
    NumberInputBox(
        intValue = priority,
        onValueChange = onValueChange,
        label = { Text(Str(R.string.priority)) },
        leadingIcon = {
            ResIcon(
                iconId = R.drawable.ic_priority,
                color = LightMagenta,
                modifier = M.size(18.dp)
            )
        }
    )
}
