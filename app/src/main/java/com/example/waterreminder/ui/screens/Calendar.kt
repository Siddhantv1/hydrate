package com.example.waterreminder.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import com.example.waterreminder.domain.DayStatus
import com.example.waterreminder.domain.HydrateState
import com.example.waterreminder.domain.bestStreak
import com.example.waterreminder.domain.dayStatus
import com.example.waterreminder.domain.monthCells
import com.example.waterreminder.ui.draw.ring
import com.example.waterreminder.ui.draw.sketchPath
import com.example.waterreminder.ui.draw.sketchStroke
import com.example.waterreminder.ui.theme.Bricolage
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.body
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val CELL = 19.dp

/**
 * Streak calendar: one month at a time, met days filled with water, missed days outlined in faint ember,
 * today ringed. The bezel or the side arrows move between months (from the setup month to this month).
 */
@Composable
fun CalendarScreen(state: HydrateState) {
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    val firstMonth = state.firstDate.takeIf { it.isNotEmpty() }?.let { YearMonth.from(LocalDate.parse(it)) } ?: thisMonth
    var month by remember { mutableStateOf(thisMonth) }
    fun step(by: Long) { month = month.plusMonths(by).coerceIn(minOf(firstMonth, thisMonth), thisMonth) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize()
            .onRotaryScrollEvent { e -> if (e.verticalScrollPixels != 0f) step(if (e.verticalScrollPixels > 0) 1 else -1); true }
            .focusRequester(focus).focusable(),
        contentAlignment = Alignment.Center,
    ) {
        val title = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) +
            if (month.year != today.year) " ${month.year}" else ""
        CurvedLayout(Modifier.fillMaxSize()) {
            curvedText(title, style = CurvedTextStyle(fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight(800), color = Ink.Paper))
        }
        CurvedLayout(Modifier.fillMaxSize(), anchor = 90f, angularDirection = CurvedDirection.Angular.Reversed) {
            curvedText(
                "${state.streak}-day streak · best ${maxOf(bestStreak(state.metDays), state.streak)}",
                style = CurvedTextStyle(fontSize = 12.sp, fontFamily = Bricolage, color = Ink.Ember),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Box(Modifier.size(CELL, 13.dp), contentAlignment = Alignment.Center) {
                        Text(it, style = body(9.sp).copy(color = Ink.Muted))
                    }
                }
            }
            monthCells(month).chunked(7).forEach { week ->
                Row { week.forEach { day -> DayCell(day, state, today) } }
            }
        }

        val canPrev = month > firstMonth
        val canNext = month < thisMonth
        InkButton("Previous month", 26.dp, 11, { step(-1) }, Modifier.align(Alignment.CenterStart).offset(x = 4.dp), enabled = canPrev, touch = 40.dp) { ink ->
            val r = size.minDimension * 0.18f
            sketchStroke(listOf(center + Offset(r * 0.5f, -r), center + Offset(-r * 0.5f, 0f), center + Offset(r * 0.5f, r)), 12, ink)
        }
        InkButton("Next month", 26.dp, 13, { step(1) }, Modifier.align(Alignment.CenterEnd).offset(x = (-4).dp), enabled = canNext, touch = 40.dp) { ink ->
            val r = size.minDimension * 0.18f
            sketchStroke(listOf(center + Offset(-r * 0.5f, -r), center + Offset(r * 0.5f, 0f), center + Offset(-r * 0.5f, r)), 14, ink)
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, state: HydrateState, today: LocalDate) {
    if (day == null) return Box(Modifier.size(CELL))
    val status = dayStatus(day, state, today)
    Box(
        Modifier.size(CELL).drawBehind {
            val pts = ring(center, size.minDimension / 2f - 1.5.dp.toPx(), fromDeg = 160f, n = 8)
            when (status) {
                DayStatus.Met -> drawPath(sketchPath(pts, 91, closed = true), Ink.Water)
                DayStatus.Today -> sketchStroke(pts, 92, Ink.Paper, closed = true, width = 1.6.dp)
                DayStatus.Missed -> sketchStroke(pts, 93, Ink.Ember.copy(alpha = 0.45f), closed = true, width = 1.4.dp)
                DayStatus.Future, DayStatus.BeforeStart -> Unit
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val color = when (status) {
            DayStatus.Met -> Ink.Bg
            DayStatus.Future, DayStatus.BeforeStart -> Ink.Muted.copy(alpha = 0.55f)
            else -> Ink.Paper
        }
        Text("${day.dayOfMonth}", style = body(10.sp).copy(color = color))
    }
}
