package com.example.modunote.utils

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

fun TextFieldValue.insertAtCursor(textToInsert: String): TextFieldValue {
    val newText = text.replaceRange(selection.start, selection.end, textToInsert)
    return copy(text = newText, selection = TextRange(selection.start + textToInsert.length))
}
