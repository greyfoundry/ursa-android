package dev.astoris.ursa.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.core.text.BidiFormatter

fun bidirectionalWrap(value: String): String = BidiFormatter.getInstance().unicodeWrap(value)

@Composable
fun leftToRightTextStyle(): TextStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
