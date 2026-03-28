package com.example.pain_tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pain_tracker.model.*
import com.example.pain_tracker.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── colour palette ────────────────────────────────────────────────────────────
private val BgColor      = Color(0xFFFCF4EC) //cream 0xFFFCF4EC
private val Surface1    = Color(0xFF7A9B6A)
private val Surface2    = Color(0xFF725241)
private val Border      = Color(0xFF6B3820)
private val TextPrimary = Color(0xFF6B3820)

private val TextOnSurface = Color(0xFFFFFFFF)

private val TextMuted   = Color(0xFFDAD8D8)
private val PinkAccent  = Color(0xFFCB5A6C)
private val AmberAccent = Color(0xFFFFB13D)
private val GreenAccent = Color(0xFF96F32F)

private fun scoreColor(s: Int)  = if (s >= 70) GreenAccent else if (s >= 45) AmberAccent else PinkAccent
private fun zoneColor(l: ZoneLevel) = when(l) { ZoneLevel.SEVERE -> PinkAccent; ZoneLevel.MODERATE -> AmberAccent; ZoneLevel.MILD -> GreenAccent }

// ── top-level screen ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val monthScores       by vm.monthScores.collectAsState()
    val displayedSessions by vm.displayedSessions.collectAsState()
    val displayedScore    by vm.displayedScore.collectAsState()
    val showAddSheet      by vm.showAddSheet.collectAsState()
    val expandedId        by vm.expandedId.collectAsState()
    val selectedDay       by vm.selectedDay.collectAsState()
    val weekOffset        by vm.weekOffset.collectAsState()

    // NEW: Pager State for swipeable weeks (starts at 5000 to allow infinite swiping both ways)
    val pagerState = rememberPagerState(initialPage = 5000, pageCount = { 10000 })
    val coroutineScope = rememberCoroutineScope()

    // Determine current month/year based on what week is currently being viewed
    val currentCal = remember(weekOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
        // Adjust to the Thursday of that week to determine the month that "owns" this week
        cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
        cal
    }

    val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentCal.time).lowercase()
    // Generate dropdown options (e.g., 12 months back, 12 months forward)
    val monthOptions = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -12)
        (0..24).map {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).lowercase()
            cal.add(Calendar.MONTH, 1)
            label to Pair(y, m)
        }
    }

    // Sync ViewModel -> Pager (When clicking Today or picking a Month)
    LaunchedEffect(weekOffset) {
        val targetPage = 5000 + weekOffset
        if (pagerState.currentPage != targetPage) {
            // If we are jumping across multiple months, an animation will freeze the app.
            // So, if the jump is larger than 3 weeks, we just instantly snap there.
            if (abs(pagerState.currentPage - targetPage) > 3) {
                pagerState.scrollToPage(targetPage)
            } else {
                // If it's a short jump, do the pretty slide animation!
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    // Sync Pager -> ViewModel (When swiping left/right with your finger)
    LaunchedEffect(pagerState.settledPage) {
        val targetOffset = pagerState.settledPage - 5000
        if (weekOffset != targetOffset) {
            vm.setWeekOffset(targetOffset)
        }
    }

    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val dateLabel = if (selectedDay == today && weekOffset == 0) "today" else "${currentMonthYear.split(" ")[0].take(3)} $selectedDay"

    Scaffold(containerColor = BgColor,
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openAddSheet() },
                containerColor = PinkAccent, contentColor = Color.White, shape = CircleShape) {
                Icon(Icons.Default.Add, "Add session")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {

            // Re-added the Month section that was missing in your last paste!
            item {
                MonthCalendarSection(
                    monthScores = monthScores,
                    selectedDay = selectedDay,
                    currentMonthYear = currentMonthYear,
                    currentCal = currentCal,
                    monthOptions = monthOptions,
                    onMonthSelect = { y, m -> vm.setMonth(y, m) },
                    onDayClick = { y, m, d, score -> vm.selectDay(y, m, d, score) }
                )
            }
            item { HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            item {
                WeekStripSection(
                    scoresMap = monthScores,
                    selectedDay = selectedDay,
                    weekOffset = weekOffset,
                    pagerState = pagerState,
                    coroutineScope = coroutineScope,
                    onDayClick = { y, m, d, score -> vm.selectDay(y, m, d, score) },

                    onTodayClick = { vm.resetToToday() }
                )
            }
            item { HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("pain sessions — $dateLabel", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = { vm.openAddSheet() },
                        colors = ButtonDefaults.textButtonColors(contentColor = PinkAccent)) {
                        Text("+ add session", fontSize = 12.sp)
                    }
                }
            }
            items(displayedSessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    expanded = expandedId == session.id,
                    onToggle = { vm.toggleSession(session.id) },
                    onEdit = { vm.openAddSheet(session) }, // <--- Pass the edit logic here
                    onToggleSx = { sx -> vm.toggleSymptom(session.id, sx) },
                    onNotes = { notes -> vm.updateNotes(session.id, notes) }
                )
            }
            item { displayedScore?.let { DayScoreSummary(it) } }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editingSession by vm.editingSession.collectAsState()

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.closeAddSheet() },
            sheetState = sheetState,
            containerColor = BgColor // Matches your cream background
        ) {
            AddSessionSheet(
                existingSession = editingSession,
                onSave = { sh, sm, eh, em, peak, sx, notes ->
                    if (editingSession != null) {
                        vm.updateSession(editingSession!!.id, sh, sm, eh, em, peak, sx, notes)
                    } else {
                        vm.addManualSession(sh, sm, eh, em, peak, sx, notes)
                    }
                },
                onDelete = { session -> vm.deleteSession(session.id) }, // lambda now matches
                onDismiss = { vm.closeAddSheet() }
            )
        }
    }
}

// ── monthly calendar ──────────────────────────────────────────────────────────
@Composable
fun MonthCalendarSection(
    monthScores: Map<Int, DayScore>,
    selectedDay: Int,
    currentMonthYear: String,
    currentCal: Calendar,
    monthOptions: List<Pair<String, Pair<Int, Int>>>,
    onMonthSelect: (Int, Int) -> Unit,
    onDayClick: (Int, Int, Int, DayScore?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var expandedMonthMenu by remember { mutableStateOf(false) }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            // Interactive Dropdown Menu Box
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expandedMonthMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(currentMonthYear, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Month", tint = TextPrimary)
                }

                DropdownMenu(
                    expanded = expandedMonthMenu,
                    onDismissRequest = { expandedMonthMenu = false },
                    modifier = Modifier
                        .background(Surface1)
                        .heightIn(max = 240.dp) // <-- ADD THIS LINE to limit to ~5 items
                ) {
                    monthOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.first, color = TextPrimary) },
                            onClick = {
                                onMonthSelect(option.second.first, option.second.second)
                                expandedMonthMenu = false
                            }
                        )
                    }
                }
            }

            TextButton(onClick = { expanded = !expanded }, colors = ButtonDefaults.textButtonColors(contentColor = PinkAccent)) {
                Text(if (expanded) "hide calendar" else "show calendar", fontSize = 12.sp)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // CHANGED TO LOWERCASE HERE
                    listOf("s","m","t","w","t","f","s").forEach { d ->
                        Text(d, color = TextPrimary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    }
                }

                // Grab the year and month being displayed
                val currentYear = currentCal.get(Calendar.YEAR)
                val currentMonth = currentCal.get(Calendar.MONTH)

                // Calculate exact calendar grid offsets for accurate days
                val daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val firstDayCal = currentCal.clone() as Calendar
                firstDayCal.set(Calendar.DAY_OF_MONTH, 1)
                val offset = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sunday
                val rows = (offset + daysInMonth + 6) / 7
                var dayCounter = 1

                repeat(rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val cellIndex = row * 7 + col
                            if (cellIndex < offset || dayCounter > daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val day = dayCounter++
                                val ds = monthScores[day]
                                val isSelected = day == selectedDay

                                Box(contentAlignment = Alignment.Center,
                                    modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp)
                                        .clip(CircleShape).clickable { onDayClick(currentYear, currentMonth, day, ds) }
                                        .then(if (isSelected) Modifier.border(1.5.dp, PinkAccent, CircleShape) else Modifier)
                                        .background(Surface2.copy(alpha = 0.04f))) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(scoreEmoji(ds?.score ?: 75), fontSize = 14.sp)
                                        Text("$day", color = if (isSelected) PinkAccent else TextPrimary, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── week strip ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekStripSection(
    scoresMap: Map<Int, DayScore>,
    selectedDay: Int,
    weekOffset: Int,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    onDayClick: (Int, Int, Int, DayScore?) -> Unit,
    onTodayClick: () -> Unit // NEW Parameter
) {
    val labels = listOf("sun","mon","tue","wed","thu","fri","sat")

    val weekLabel = when (weekOffset) {
        0 -> "this week"
        -1 -> "last week"
        1 -> "next week"
        else -> if (weekOffset < 0) "${-weekOffset} weeks ago" else "in $weekOffset weeks" // Fixed wording logic
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(weekLabel, color = TextPrimary, fontSize = 11.sp)

            // NEW: Added the 'today' button alongside the arrows
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {

                // Only show "today" button if we are NOT on the current week
                AnimatedVisibility(visible = weekOffset != 0) {
                    Text(
                        text = "today",
                        color = PinkAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTodayClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev", tint = TextPrimary,
                        modifier = Modifier.clip(CircleShape).clickable { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }.padding(4.dp).size(20.dp))
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = TextPrimary,
                        modifier = Modifier.clip(CircleShape).clickable { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }.padding(4.dp).size(20.dp))
                }
            }
        }

        // NEW: Swappable Horizontal Pager
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->

            val pageWeekOffset = page - 5000
            val loopCal = Calendar.getInstance()
            loopCal.add(Calendar.WEEK_OF_YEAR, pageWeekOffset)
            while (loopCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                loopCal.add(Calendar.DAY_OF_MONTH, -1)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until 7) {
                    val itemYear = loopCal.get(Calendar.YEAR)
                    val itemMonth = loopCal.get(Calendar.MONTH)
                    val itemDay = loopCal.get(Calendar.DAY_OF_MONTH)
                    val isSelected = itemDay == selectedDay
                    val ds = scoresMap[itemDay]

                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onDayClick(itemYear, itemMonth, itemDay, ds) }.padding(vertical = 4.dp)) {

                        Text(labels[i], color = if (isSelected) PinkAccent else TextPrimary, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.size(38.dp)
                                .then(if (isSelected) Modifier.border(1.5.dp, PinkAccent, CircleShape) else Modifier)
                                .clip(CircleShape).background(Surface2.copy(alpha = 0.04f))) {
                            Text(scoreEmoji(ds?.score ?: 75), fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$itemDay", color = if (isSelected) PinkAccent else TextPrimary, fontSize = 10.sp)
                    }
                    loopCal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
        }
    }
}

// ── session card ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionCard(
    session: PainSession,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onToggleSx: (Symptom) -> Unit,
    onNotes: (String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = "${sdf.format(Date(session.startTime)).lowercase()} – ${sdf.format(Date(session.endTime)).lowercase()}"
    val durStr  = "${session.durationMinutes}m · ${if (session.source == SessionSource.SMARTWATCH) "smartwatch" else "manual"}"
    val (badgeText, badgeBg, badgeFg) = when {
        session.peakLevel >= 7f -> Triple("severe",   PinkAccent,  TextOnSurface)
        session.peakLevel >= 4f -> Triple("moderate", AmberAccent, TextOnSurface)
        else                    -> Triple("mild",     GreenAccent, TextOnSurface)
    }
    val totalZ = session.zones.sumOf { it.durationMinutes }.toFloat().coerceAtLeast(1f)
    var localNotes by remember(session.id) { mutableStateOf(session.notes) }
    Column(modifier = Modifier
        .fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
        .clip(RoundedCornerShape(14.dp)).background(Surface1)
        .clickable { onToggle() }.padding(14.dp)
    ) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(timeStr, color = TextOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(durStr,  color = TextMuted,   fontSize = 11.sp)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(badgeBg).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(badgeText, color = badgeFg, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            session.zones.forEach { z ->
                Box(modifier = Modifier.weight(z.durationMinutes / totalZ).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(zoneColor(z.level)))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            session.zones.forEach { z ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(zoneColor(z.level)))
                    Text("${z.durationMinutes}m ${z.level.name.lowercase()}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = Border, thickness = 0.5.dp)

                Text("symptoms", color = TextOnSurface, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                SymptomChips(
                    symptoms = Symptom.entries.toList(),
                    isActive = { it in session.symptoms },
                    onToggle = { onToggleSx(it) }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = localNotes, onValueChange = { localNotes = it; onNotes(it) },
                    placeholder = { Text("add notes...", color = TextPrimary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PinkAccent.copy(0.4f), unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = PinkAccent,
                        focusedContainerColor = BgColor, unfocusedContainerColor = BgColor),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary))

                // NEW: Dedicated Edit Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEdit() }) {
                        Text("edit", color = TextOnSurface, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── day score summary ─────────────────────────────────────────────────────────
@Composable
fun DayScoreSummary(ds: DayScore) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ScoreCard("day score", "${ds.score}", ds.label, scoreColor(ds.score), Modifier.weight(1f))
        ScoreCard("sessions", "${ds.sessions.size}", "${ds.totalMinutes}m total", TextPrimary, Modifier.weight(1f))
        ScoreCard("peak pain", "%.1f".format(ds.peakSeverity), "/ 10",
            if (ds.peakSeverity >= 7) PinkAccent else AmberAccent, Modifier.weight(1f))
    }
}

@Composable
fun ScoreCard(label: String, value: String, sub: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Surface1)
        .border(0.5.dp, Border, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label.uppercase(), color = TextPrimary, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp)
        Text(sub, color = TextMuted, fontSize = 11.sp)
    }
}

// ── add session sheet ─────────────────────────────────────────────────────────
@Composable
fun AddSessionSheet(
    existingSession: PainSession? = null,
    onSave: (Int, Int, Int, Int, Float, Set<Symptom>, String) -> Unit,
    onDelete: (PainSession) -> Unit,
    onDismiss: () -> Unit
) {
    val cal = Calendar.getInstance()

    // Helper to get time parts
    fun getTimePart(ts: Long, field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = ts }.get(field)

    var startHour by remember {
        mutableIntStateOf(existingSession?.let {
            getTimePart(
                it.startTime,
                Calendar.HOUR_OF_DAY
            )
        } ?: 14)
    }
    var startMinute by remember {
        mutableIntStateOf(existingSession?.let {
            getTimePart(
                it.startTime,
                Calendar.MINUTE
            )
        } ?: 30)
    }
    var endHour by remember {
        mutableIntStateOf(existingSession?.let {
            getTimePart(
                it.endTime,
                Calendar.HOUR_OF_DAY
            )
        } ?: 15)
    }
    var endMinute by remember {
        mutableIntStateOf(existingSession?.let {
            getTimePart(
                it.endTime,
                Calendar.MINUTE
            )
        } ?: 15)
    }
    var peakLevel by remember { mutableFloatStateOf(existingSession?.peakLevel ?: 5f) }
    var symptoms by remember { mutableStateOf(existingSession?.symptoms ?: emptySet()) }
    var notes by remember { mutableStateOf(existingSession?.notes ?: "") }

    // FIXED: peakColor must be defined here so it can be used below
    val peakColor =
        if (peakLevel >= 7f) PinkAccent else if (peakLevel >= 4f) AmberAccent else GreenAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (existingSession != null) "edit session" else "log session",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall
        )


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "start time",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                SimpleTimePicker(startHour, startMinute, { startHour = it }, { startMinute = it })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "end time",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                SimpleTimePicker(endHour, endMinute, { endHour = it }, { endMinute = it })
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("peak pain level", color = TextPrimary, fontSize = 12.sp)

        Text(
            text = "%.0f".format(peakLevel),
            color = peakColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Slider(
            value = peakLevel,
            onValueChange = { peakLevel = it },
            valueRange = 1f..10f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = peakColor,
                activeTrackColor = peakColor,
                inactiveTrackColor = Border
            )
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "symptoms",
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SymptomChips(
            symptoms = Symptom.entries.toList(),
            isActive = { it in symptoms },
            onToggle = { sx -> symptoms = if (sx in symptoms) symptoms - sx else symptoms + sx }
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "notes",
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            placeholder = { Text("optional notes...", color = TextPrimary, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PinkAccent.copy(0.4f), unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                cursorColor = PinkAccent,
                focusedContainerColor = BgColor, unfocusedContainerColor = BgColor
            )
        )

        Spacer(Modifier.height(20.dp))
        // Save Button
        Button(
            onClick = {
                onSave(
                    startHour,
                    startMinute,
                    endHour,
                    endMinute,
                    peakLevel,
                    symptoms,
                    notes
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Surface1)
        ) {
            Text(if (existingSession != null) "save changes" else "save session")
        }

        if (existingSession != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = { onDelete(existingSession) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("delete record", color = PinkAccent, fontSize = 13.sp)
            }
        }
    }
}


// ── symptom chips (wrapping rows, no FlowRow dependency) ─────────────────────
@Composable
fun SymptomChips(symptoms: List<Symptom>, isActive: (Symptom) -> Boolean, onToggle: (Symptom) -> Unit) {
    val chunkSize = 3
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        symptoms.chunked(chunkSize).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { sx ->
                    val active = isActive(sx)
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) PinkAccent.copy( 0.8f) else Surface2.copy(alpha = 0.6f))
                        .border(0.5.dp,  Border, RoundedCornerShape(10.dp))
                        .clickable { onToggle(sx) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(sx.label, color = if (active) TextOnSurface else TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ── hh:mm picker ──────────────────────────────────────────────────────────────
@Composable
fun SimpleTimePicker(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgColor)
        .border(0.5.dp, Border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▲", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.clickable { onHour((hour+1)%24) })
            Text("%02d".format(hour), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("▼", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.clickable { onHour((hour+23)%24) })
        }
        Text(":", color = TextMuted, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▲", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.clickable { onMinute((minute+1)%60) })
            Text("%02d".format(minute), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("▼", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.clickable { onMinute((minute+59)%60) })
        }
    }
}