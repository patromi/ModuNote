package com.example.modunote.utils

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

fun TextFieldValue.insertAtCursor(textToInsert: String): TextFieldValue {
    val newText = text.replaceRange(selection.start, selection.end, textToInsert)
    return copy(text = newText, selection = TextRange(selection.start + textToInsert.length))
}
