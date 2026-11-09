package com.example.waterreminder.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import com.example.waterreminder.R

object Ink {
    val Bg = Color(0xFF000000)
    val Paper = Color(0xFFF2EEE6)
    val Muted = Color(0xFF8F8A80)
    val Water = Color(0xFF49C6F2)
    val WaterDeep = Color(0xFF1580C4)
    val Ember = Color(0xFFFF8A3D)
    val Skip = Color(0xFFE8696B)
    val Done = Color(0xFF7BE495)
    val Track = Color(0xFF262626)
}

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) =
    Font(R.font.bricolage, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Bricolage: FontFamily = FontFamily(bricolage(400), bricolage(800))

fun display(size: TextUnit) = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = size, color = Ink.Paper)

fun body(size: TextUnit) = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(400), fontSize = size, color = Ink.Paper)

@Composable
fun HydrateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Ink.Water, secondary = Ink.Ember, background = Ink.Bg, surface = Ink.Track,
            error = Ink.Skip, onPrimary = Ink.Bg, onSecondary = Ink.Bg, onBackground = Ink.Paper,
            onSurface = Ink.Paper, onError = Ink.Bg,
        ),
        typography = Typography(defaultFontFamily = Bricolage),
        content = content,
    )
}
