package com.example.pain_tracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pain_tracker.viewmodel.HourlyPainBar
import com.example.pain_tracker.viewmodel.InsightsViewModel
import com.example.pain_tracker.viewmodel.InsightsViewMode
import com.example.pain_tracker.viewmodel.ScorePoint
import com.example.pain_tracker.viewmodel.InsightsSummary
import kotlin.math.hypot
import kotlin.math.roundToInt

// ── palette ───────────────────────────────────────────────────────────────────
private val BgColor       = Color(0xFFFCF4EC)
private val Surface1      = Color(0xFF7A9B6A)
private val Surface2      = Color(0xFF725241)
private val Border        = Color(0xFF6B3820)
private val TextPrimary   = Color(0xFF6B3820)
private val TextMuted     = Color(0xFFDAD8D8)
private val PinkAccent    = Color(0xFFCB5A6C)
private val AmberAccent   = Color(0xFFFFB13D)
private val GreenAccent   = Color(0xFF96F32F)

private fun scoreColor(s: Float) = when {
    s >= 70f -> GreenAccent
    s >= 45f -> AmberAccent
    else     -> PinkAccent
}

private fun painLevelColor(level: Float) = when {
    level >= 7f -> PinkAccent
    level >= 4f -> AmberAccent
    level > 0f  -> GreenAccent
    else        -> Border.copy(alpha = 0.12f)
}

// ── screen ────────────────────────────────────────────────────────────────────
@Composable
fun InsightsScreen(vm: InsightsViewModel = viewModel()) {
    val viewMode      by vm.viewMode.collectAsState()
    val chartPoints   by vm.chartPoints.collectAsState()
    val summary       by vm.summary.collectAsState()
    val hourlyBars    by vm.hourlyPainBars.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("insights", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PinkAccent, strokeWidth = 2.dp)
            }
        }

        ViewModeTabs(selected = viewMode, onSelect = { vm.setViewMode(it) })
        Spacer(Modifier.height(24.dp))

        // ════════════════════════════════════════════════════════════════════
        // CHART 1 — Score over time
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("score over time")
        Spacer(Modifier.height(8.dp))

        if (chartPoints.isEmpty() && !isLoading) {
            EmptyState()
        } else {
            val chartHeight = if (viewMode == InsightsViewMode.THREE_MONTH) 260.dp else 240.dp
            ScoreLineChart(
                points   = chartPoints,
                viewMode = viewMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        summary?.let { SummarySection(it, viewMode) }
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = Border.copy(alpha = 0.2f), thickness = 0.5.dp)
        Spacer(Modifier.height(28.dp))

        // ════════════════════════════════════════════════════════════════════
        // CHART 2 — Peak pain by hour
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("peak pain by hour")
        Spacer(Modifier.height(4.dp))
        Text(
            "when your pain tends to be worst",
            color    = TextPrimary.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        if (hourlyBars.all { it.sessionCount == 0 } && !isLoading) {
            EmptyState()
        } else {
            HourlyPainChart(
                bars     = hourlyBars,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 12.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        val peakBar = hourlyBars.maxByOrNull { it.avgPeakLevel }
        if (peakBar != null && peakBar.sessionCount > 0) {
            PeakHourCallout(peakBar)
        }

        Spacer(Modifier.height(24.dp))
        ScoreLegend()
    }
}

// ── line chart (interactive) ──────────────────────────────────────────────────
@Composable
fun ScoreLineChart(points: List<ScorePoint>, viewMode: InsightsViewMode, modifier: Modifier = Modifier) {
    val animProgress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(900, easing = EaseInOutCubic))
    }
    val progress  by animProgress.asState()
    val dataPoints = points.filter { it.score >= 0f }

    // Interactivity state
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val rotateLabels = viewMode == InsightsViewMode.THREE_MONTH
    val maxLabels = when (viewMode) {
        InsightsViewMode.WEEK        -> 7
        InsightsViewMode.MONTH       -> 8
        InsightsViewMode.THREE_MONTH -> 7
        InsightsViewMode.YEAR        -> 12
    }
    val labelStep = (points.size.toFloat() / maxLabels).coerceAtLeast(1f).toInt()

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val padLeft = 36.dp.toPx()
                        val padRight = 12.dp.toPx()
                        val chartW = size.width - padLeft - padRight

                        // Map touch X to closest data point
                        val relativeX = offset.x - padLeft
                        val ratio = relativeX / chartW
                        val exactIndex = ratio * (points.size - 1).coerceAtLeast(1)
                        val closestIndex = exactIndex.roundToInt().coerceIn(0, points.lastIndex)

                        val pt = points[closestIndex]
                        selectedIndex = if (pt.score >= 0f) {
                            dataPoints.indexOf(pt)
                        } else {
                            null // Tapped on an empty day
                        }
                    }
                }
        ) {
            val padLeft   = 36.dp.toPx()
            val padRight  = 12.dp.toPx()
            val padTop    = 12.dp.toPx()
            val padBottom = if (rotateLabels) 52.dp.toPx() else 38.dp.toPx()
            val chartW    = size.width  - padLeft - padRight
            val chartH    = size.height - padTop  - padBottom

            if (points.isEmpty()) return@Canvas

            val yLabelPaint = android.graphics.Paint().apply {
                color       = android.graphics.Color.argb(140, 107, 56, 32)
                textSize    = 9.dp.toPx()
                textAlign   = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }
            val xLabelPaint = android.graphics.Paint().apply {
                color       = android.graphics.Color.argb(160, 107, 56, 32)
                textSize    = 8.5.dp.toPx()
                textAlign   = if (rotateLabels) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            // Grid + y labels
            listOf(0f, 25f, 50f, 75f, 100f).forEach { level ->
                val y = padTop + chartH * (1f - level / 100f)
                drawLine(color = Border.copy(alpha = 0.15f), start = Offset(padLeft, y), end = Offset(padLeft + chartW, y), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("${level.toInt()}", padLeft - 5.dp.toPx(), y + 4.dp.toPx(), yLabelPaint)
            }

            // X labels
            points.forEachIndexed { i, pt ->
                if (i % labelStep != 0 && i != points.lastIndex) return@forEachIndexed
                val x = padLeft + (i.toFloat() / (points.size - 1).coerceAtLeast(1)) * chartW
                val yBase = size.height - 6.dp.toPx()
                if (rotateLabels) {
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-40f, x, yBase)
                    drawContext.canvas.nativeCanvas.drawText(pt.dateLabel, x, yBase, xLabelPaint)
                    drawContext.canvas.nativeCanvas.restore()
                } else {
                    drawContext.canvas.nativeCanvas.drawText(pt.dateLabel, x, yBase, xLabelPaint)
                }
            }

            if (dataPoints.isEmpty()) return@Canvas

            fun ptOffset(pt: ScorePoint): Offset {
                val idx = points.indexOf(pt).toFloat()
                return Offset(padLeft + (idx / (points.size - 1).coerceAtLeast(1)) * chartW, padTop + chartH * (1f - pt.score / 100f))
            }

            val offsets = dataPoints.map { ptOffset(it) }

            if (offsets.size >= 2) {
                val fillPath = buildCatmullRomPath(offsets, progress)
                fillPath.lineTo(offsets.last().x, padTop + chartH)
                fillPath.lineTo(offsets.first().x, padTop + chartH)
                fillPath.close()
                drawPath(path = fillPath, brush = Brush.verticalGradient(listOf(PinkAccent.copy(alpha = 0.35f), PinkAccent.copy(alpha = 0f)), startY = padTop, endY = padTop + chartH))
                drawPath(path = buildCatmullRomPath(offsets, progress), color = PinkAccent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val visibleCount = (offsets.size * progress).toInt().coerceAtLeast(if (offsets.isNotEmpty()) 1 else 0)
            offsets.take(visibleCount).forEachIndexed { i, off ->
                drawCircle(color = BgColor, radius = 5.dp.toPx(), center = off)
                drawCircle(color = scoreColor(dataPoints[i].score), radius = 4.dp.toPx(), center = off)
            }

            // Draw interactive tooltip
            selectedIndex?.takeIf { it < visibleCount }?.let { idx ->
                val off = offsets[idx]
                val scoreStr = "${dataPoints[idx].score.toInt()}"

                // Draw vertical guideline
                drawLine(
                    color = Border.copy(alpha = 0.3f),
                    start = Offset(off.x, padTop),
                    end = Offset(off.x, padTop + chartH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Highlight point
                drawCircle(color = BgColor, radius = 8.dp.toPx(), center = off)
                drawCircle(color = scoreColor(dataPoints[idx].score), radius = 6.dp.toPx(), center = off)

                // Draw Tooltip Box
                val rectWidth = 44.dp.toPx()
                val rectHeight = 24.dp.toPx()
                val rectTop = off.y - rectHeight - 12.dp.toPx()

                drawRoundRect(
                    color = Surface2,
                    topLeft = Offset(off.x - rectWidth/2, rectTop),
                    size = Size(rectWidth, rectHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                drawContext.canvas.nativeCanvas.drawText(
                    scoreStr,
                    off.x,
                    rectTop + rectHeight/2 + 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        setColor(android.graphics.Color.WHITE)
                        textSize = 12.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                )
            }
        }
    }
}

// ── hourly pain bar chart (interactive) ───────────────────────────────────────
@Composable
fun HourlyPainChart(bars: List<HourlyPainBar>, modifier: Modifier = Modifier) {
    val animProgress = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(700, easing = EaseOutCubic))
    }
    val progress by animProgress.asState()

    // Interactivity state
    var selectedHourIndex by remember(bars) { mutableStateOf<Int?>(null) }

    val maxLevel = bars.maxOfOrNull { it.avgPeakLevel }?.coerceAtLeast(1f) ?: 1f

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val padLeft = 10.dp.toPx()
                        val padRight = 10.dp.toPx()
                        val chartW = size.width - padLeft - padRight
                        val barSlotW = chartW / bars.size

                        val clickedIndex = ((offset.x - padLeft) / barSlotW).toInt().coerceIn(0, 23)
                        selectedHourIndex = if (bars[clickedIndex].sessionCount > 0) {
                            clickedIndex
                        } else {
                            null
                        }
                    }
                }
        ) {
            val padLeft   = 10.dp.toPx()
            val padRight  = 10.dp.toPx()
            val padTop    = 24.dp.toPx() // Increased top padding for tooltips
            val padBottom = 28.dp.toPx()
            val chartW    = size.width  - padLeft - padRight
            val chartH    = size.height - padTop  - padBottom

            val barCount  = bars.size
            val barSlotW  = chartW / barCount
            val barW      = (barSlotW * 0.65f).coerceAtLeast(4.dp.toPx())
            val cornerR   = (barW / 2f).coerceAtMost(6.dp.toPx())

            val xLabelPaint = android.graphics.Paint().apply {
                color       = android.graphics.Color.argb(150, 107, 56, 32)
                textSize    = 8.dp.toPx()
                textAlign   = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            bars.forEachIndexed { i, bar ->
                val cx    = padLeft + barSlotW * i + barSlotW / 2f
                val barH  = (bar.avgPeakLevel / maxLevel) * chartH * progress
                val top   = padTop + chartH - barH
                val left  = cx - barW / 2f
                val right = cx + barW / 2f
                val bottom = padTop + chartH

                val isSelected = i == selectedHourIndex
                val color = if (isSelected) Surface2 else painLevelColor(bar.avgPeakLevel)

                if (barH > 0f) {
                    val path = Path().apply {
                        moveTo(left, bottom)
                        lineTo(left, top + cornerR)
                        quadraticTo(left, top, left + cornerR, top)
                        lineTo(right - cornerR, top)
                        quadraticTo(right, top, right, top + cornerR)
                        lineTo(right, bottom)
                        close()
                    }
                    drawPath(path, color = color)

                    // Draw tooltip if selected
                    if (isSelected) {
                        val tipText = "%.1f".format(bar.avgPeakLevel)
                        val rectW = 36.dp.toPx()
                        val rectH = 20.dp.toPx()

                        drawRoundRect(
                            color = Surface2,
                            topLeft = Offset(cx - rectW/2, top - rectH - 6.dp.toPx()),
                            size = Size(rectW, rectH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            tipText,
                            cx,
                            top - 6.dp.toPx() - rectH/2 + 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                setColor(android.graphics.Color.WHITE)
                                textSize = 11.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                        )
                    }
                } else {
                    drawRect(color = Border.copy(alpha = 0.08f), topLeft = Offset(left, padTop + chartH - 2.dp.toPx()), size = Size(barW, 2.dp.toPx()))
                }

                if (i % 3 == 0) {
                    val label = when (i) {
                        0    -> "12am"
                        12   -> "12pm"
                        else -> if (i < 12) "${i}am" else "${i - 12}pm"
                    }
                    drawContext.canvas.nativeCanvas.drawText(label, cx, size.height - 4.dp.toPx(), xLabelPaint)
                }
            }

            drawLine(
                color       = Border.copy(alpha = 0.2f),
                start       = Offset(padLeft, padTop + chartH),
                end         = Offset(padLeft + chartW, padTop + chartH),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

// ── Components below are unchanged ────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
fun ViewModeTabs(selected: InsightsViewMode, onSelect: (InsightsViewMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(Surface2.copy(alpha = 0.15f)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        InsightsViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier.weight(1f).padding(4.dp).clip(RoundedCornerShape(9.dp)).background(if (isSelected) PinkAccent else Color.Transparent).clickable { onSelect(mode) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(mode.label, color = if (isSelected) Color.White else TextPrimary, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
fun PeakHourCallout(bar: HourlyPainBar) {
    val hourLabel = when (bar.hour) { 0 -> "12am"; 12 -> "12pm"; in 1..11 -> "${bar.hour}am"; else -> "${bar.hour - 12}pm" }
    val nextLabel = when (val nextHour = (bar.hour + 1) % 24) { 0 -> "12am"; 12 -> "12pm"; in 1..11 -> "${nextHour}am"; else -> "${nextHour - 12}pm" }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(PinkAccent.copy(alpha = 0.10f)).border(0.5.dp, PinkAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⚠", fontSize = 18.sp)
        Column {
            Text("peak pain window: $hourLabel – $nextLabel", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("${bar.sessionCount} session${if (bar.sessionCount != 1) "s" else ""} · avg level ${"%.1f".format(bar.avgPeakLevel)}/10", color = TextPrimary.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

private fun buildCatmullRomPath(offsets: List<Offset>, progress: Float): Path {
    val path = Path()
    if (offsets.isEmpty()) return path
    if (offsets.size == 1) { path.moveTo(offsets[0].x, offsets[0].y); return path }

    val totalLen = offsets.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat()
    val targetLen = totalLen * progress
    var drawnLen  = 0f
    var done      = false

    path.moveTo(offsets[0].x, offsets[0].y)

    for (i in 0 until offsets.size - 1) {
        if (done) break
        val p0 = if (i > 0) offsets[i - 1] else offsets[i]
        val p1 = offsets[i]
        val p2 = offsets[i + 1]
        val p3 = if (i + 2 < offsets.size) offsets[i + 2] else offsets[i + 1]

        val segLen    = hypot(p2.x - p1.x, p2.y - p1.y)
        val remaining = targetLen - drawnLen
        val t         = if (remaining >= segLen) 1f else remaining / segLen.coerceAtLeast(0.001f)

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        if (t >= 1f) {
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
            drawnLen += segLen
        } else {
            val endX = cubicAt(p1.x, cp1x, cp2x, p2.x, t)
            val endY = cubicAt(p1.y, cp1y, cp2y, p2.y, t)
            val c1x  = lerp(p1.x, cp1x, t)
            val c1y  = lerp(p1.y, cp1y, t)
            val c2x  = lerp(lerp(p1.x, cp1x, t), lerp(cp1x, cp2x, t), t)
            val c2y  = lerp(lerp(p1.y, cp1y, t), lerp(cp1y, cp2y, t), t)
            path.cubicTo(c1x, c1y, c2x, c2y, endX, endY)
            done = true
        }
    }
    return path
}

private fun cubicAt(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
    val mt = 1f - t
    return mt * mt * mt * a + 3f * mt * mt * t * b + 3f * mt * t * t * c + t * t * t * d
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun SummarySection(summary: InsightsSummary, mode: InsightsViewMode) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("overview — last ${mode.label}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightCard("avg score", "%.0f".format(summary.avgScore), scoreColor(summary.avgScore), Modifier.weight(1f))
            InsightCard("best day",  "%.0f".format(summary.bestScore), scoreColor(summary.bestScore), Modifier.weight(1f))
            InsightCard("worst day", "%.0f".format(summary.worstScore), scoreColor(summary.worstScore), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightCard("sessions", "${summary.totalSessions}", TextPrimary, Modifier.weight(1f))
            InsightCard("days tracked", "${summary.daysTracked}", TextPrimary, Modifier.weight(1f))
            val trend = summary.avgScore - 50f
            InsightCard(
                "trend",
                when { trend > 15f -> "↑ good"; trend > -15f -> "→ neutral"; else -> "↓ rough" },
                when { trend > 15f -> GreenAccent; trend > -15f -> AmberAccent; else -> PinkAccent },
                Modifier.weight(1f),
                smallValue = true
            )
        }
    }
}

@Composable
fun InsightCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier, smallValue: Boolean = false) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Surface1).border(0.5.dp, Border, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label.uppercase(), color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = if (smallValue) 13.sp else 22.sp, fontWeight = FontWeight.Medium, lineHeight = if (smallValue) 16.sp else 22.sp)
    }
}

@Composable
fun EmptyState() {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(Surface2.copy(alpha = 0.08f)).border(0.5.dp, Border.copy(alpha = 0.3f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("no data yet", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("start logging pain sessions\nto see your trends here", color = Border.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ScoreLegend() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("score key", color = TextPrimary.copy(alpha = 0.5f), fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(GreenAccent to "70–100  good", AmberAccent to "45–69  moderate", PinkAccent to "0–44  rough").forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text(label, color = TextPrimary.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }
        }
    }
}