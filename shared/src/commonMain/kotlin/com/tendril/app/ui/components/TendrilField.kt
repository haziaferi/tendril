package com.tendril.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.theme.body

/**
 * L6 (2026-09-17) — the desktop's text field: a `BasicTextField` in one row of [height] (36 dp;
 * the find bar's 28; 48 under Touch, the phone's minimum), a hairline on `outline`, radius 6,
 * `surface` fill, 12 dp side padding, the placeholder in `onSurfaceVariant`. Notion Calendar's
 * and Obsidian's palette fields measured 37 / 38 CSS px; Material's `OutlinedTextField` is 56
 * (the audit's #6 family). One frame for the switcher, the find bar, the AI key and the sync
 * passphrase; [leading] and [trailing] sit inside the hairline.
 */
@Composable
fun TendrilField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    height: Dp = 36.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    // The String form keeps the selection beside the text (the recommended pattern), so a caller
    // that resets the text to "" resets the caret too — `QuickAddField` lost characters after a
    // reset on Material's String overload (the Month grid's walk).
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    val current = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    TendrilField(
        value = current,
        onValueChange = { fieldValue = it; if (it.text != value) onValueChange(it.text) },
        modifier = modifier, placeholder = placeholder, height = height, leading = leading, trailing = trailing,
        visualTransformation = visualTransformation, focusRequester = focusRequester, onPreviewKeyEvent = onPreviewKeyEvent,
    )
}

/** The `TextFieldValue` form — a caller that owns the selection (the quick-add line) uses it directly. */
@Composable
fun TendrilField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    height: Dp = 36.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val rowHeight = if (LocalDensityProfile.current.pointer) height else maxOf(height, 48.dp)
    Row(
        modifier = modifier
            .height(rowHeight)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Box(modifier = Modifier.weight(1f)) {
            if (value.text.isEmpty() && placeholder != null) {
                Text( // type: PLACEHOLDER — the field's own text at the field's size
                    placeholder, style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            var fieldModifier = Modifier.fillMaxWidth()
            if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
            if (onPreviewKeyEvent != null) fieldModifier = fieldModifier.onPreviewKeyEvent(onPreviewKeyEvent)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = fieldModifier,
            )
        }
        trailing?.invoke()
    }
}
