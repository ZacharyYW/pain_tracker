package com.example.pain_tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.pain_tracker.R

@Composable
fun getStatusImage(score: Int?): Int {
    return when {
        score == null -> R.drawable.status_empty
        score >= 85 -> R.drawable.status_happiest
        score >= 70 -> R.drawable.status_smile
        score >= 55 -> R.drawable.status_meh
        score >= 40 -> R.drawable.status_sad
        score >= 25 -> R.drawable.status_tears
        else -> R.drawable.status_saddest
    }
}

// ── colour palette ────────────────────────────────────────────────────────────
private val BgColor      = Color(0xFFFCF4EC) //cream 0xFFFCF4EC
private val Brown       = Color(0xFF6B3820)  // alias used by FAB
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
    val weekOffset        by vm.weekOffset.collectAsState()
    val selectedYear      by vm.selectedYear.collectAsState()
    val selectedMonth     by vm.selectedMonth.collectAsState()
    val selectedDay       by vm.selectedDay.collectAsState()

    val monthScores       by vm.monthScores.collectAsState()
    val displayedSessions by vm.displayedSessions.collectAsState()
    val displayedScore    by vm.displayedScore.collectAsState()
    val showAddSheet      by vm.showAddSheet.collectAsState()
    val expandedId        by vm.expandedId.collectAsState()

    // Pager State for swipeable weeks (starts at 5000 to allow infinite swiping both ways)
    val pagerState = rememberPagerState(initialPage = 5000, pageCount = { 10000 })
    val coroutineScope = rememberCoroutineScope()

    // NEW SMART CALENDAR LOGIC:
    // Prioritizes showing the month of the selected day if it falls in the current week!
    val currentCal = remember(weekOffset, selectedYear, selectedMonth, selectedDay) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.SUNDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }

        val weekStart = cal.clone() as Calendar
        while (weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            weekStart.add(Calendar.DAY_OF_MONTH, -1)
        }
        val weekEnd = weekStart.clone() as Calendar
        weekEnd.add(Calendar.DAY_OF_MONTH, 6)

        val targetCal = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }

        if (targetCal.timeInMillis in weekStart.timeInMillis..weekEnd.timeInMillis) {
            targetCal // Show the month of our selected day
        } else {
            weekStart.add(Calendar.DAY_OF_MONTH, 3) // Fallback to Wednesday rule
            weekStart
        }
    }

    val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentCal.time).lowercase()

    // Generate dropdown options
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
            if (abs(pagerState.currentPage - targetPage) > 3) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    // Sync Pager -> ViewModel (When swiping left/right)
    LaunchedEffect(pagerState.settledPage) {
        val targetOffset = pagerState.settledPage - 5000
        if (weekOffset != targetOffset) {
            vm.setWeekOffset(targetOffset)
        }
    }

    val isToday = selectedYear == Calendar.getInstance().get(Calendar.YEAR) &&
            selectedMonth == Calendar.getInstance().get(Calendar.MONTH) &&
            selectedDay == Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.MONTH, selectedMonth) }.time).lowercase()
    val dateLabel = if (isToday) "today" else "$monthName $selectedDay"

    val showPeriodSheet by vm.showPeriodSheet.collectAsState()
    val periodDays      by vm.periodDays.collectAsState()
    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(containerColor = BgColor,
        floatingActionButton = {
            ExpandableFab(
                expanded   = fabExpanded,
                onToggle   = { fabExpanded = !fabExpanded },
                onPain     = { fabExpanded = false; vm.openAddSheet() },
                onPeriod   = { fabExpanded = false; vm.openPeriodSheet() }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {

            item {
                MonthCalendarSection(
                    monthScores = monthScores,
                    selectedYear = selectedYear,
                    selectedMonth = selectedMonth,
                    selectedDay = selectedDay,
                    currentMonthYear = currentMonthYear,
                    currentCal = currentCal,
                    monthOptions = monthOptions,
                    periodDays = periodDays,
                    onMonthSelect = { y, m -> vm.setMonth(y, m) },
                    onDayClick = { y, m, d, score -> vm.selectDay(y, m, d, score) }
                )
            }
            item { HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            item {
                WeekStripSection(
                    scoresMap = monthScores,
                    selectedYear = selectedYear,
                    selectedMonth = selectedMonth,
                    selectedDay = selectedDay,
                    weekOffset = weekOffset,
                    pagerState = pagerState,
                    coroutineScope = coroutineScope,
                    periodDays = periodDays,
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
                    onEdit = { vm.openAddSheet(session) },
                    onToggleSx = { sx -> vm.toggleSymptom(session.id, sx) },
                    onNotes = { notes -> vm.updateNotes(session.id, notes) }
                )
            }
            item { displayedScore?.let { DayScoreSummary(it) } }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val periodSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editingSession by vm.editingSession.collectAsState()

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.closeAddSheet() },
            sheetState = sheetState,
            containerColor = BgColor
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
                onDelete = { session -> vm.deleteSession(session.id) },
                onDismiss = { vm.closeAddSheet() }
            )
        }
    }

    if (showPeriodSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.closePeriodSheet() },
            sheetState = periodSheetState,
            containerColor = BgColor
        ) {
            PeriodLogSheet(
                onSave    = { y, m, d, flow, sx, notes -> vm.logPeriod(y, m, d, flow, sx, notes) },
                onDismiss = { vm.closePeriodSheet() }
            )
        }
    }
}

// ── expandable FAB ────────────────────────────────────────────────────────────
@Composable
fun ExpandableFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onPain:   () -> Unit,
    onPeriod: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit    = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick          = onPeriod,
                    containerColor   = PinkAccent,
                    contentColor     = Color.White,
                    shape            = RoundedCornerShape(24.dp)
                ) {
                    Text("log period", fontSize = 14.sp)
                }
                ExtendedFloatingActionButton(
                    onClick          = onPain,
                    containerColor   = PinkAccent,
                    contentColor     = Color.White,
                    shape            = RoundedCornerShape(24.dp)
                ) {
                    Text("log pain session", fontSize = 14.sp)
                }
            }
        }

        FloatingActionButton(
            onClick        = onToggle,
            containerColor = PinkAccent,
            contentColor   = Color.White,
            shape          = CircleShape
        ) {
            val rotation by animateFloatAsState(
                targetValue   = if (expanded) 45f else 0f,
                animationSpec = tween(200),
                label         = "fab_rotation"
            )
            Icon(
                Icons.Default.Add,
                contentDescription = if (expanded) "close" else "add",
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

// ── period log sheet ─────────────────────────────────────────────────────────
@Composable
fun PeriodLogSheet(
    onSave:    (Int, Int, Int, Int, Set<String>, String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("log period", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)

        val today = remember { Calendar.getInstance() }
        var selectedYear   by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
        var selectedMonth  by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
        var selectedDay2   by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }
        val monthNames = listOf("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec")
        val daysInMonth = remember(selectedYear, selectedMonth) {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, selectedYear); set(Calendar.MONTH, selectedMonth)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        if (selectedDay2 > daysInMonth) selectedDay2 = daysInMonth

        Text("date", color = TextPrimary, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEDD9CC))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(2f)) {
                Text("▲", color = TextPrimary, fontSize = 10.sp,
                    modifier = Modifier.clickable { selectedMonth = (selectedMonth + 1) % 12 })
                Text(monthNames[selectedMonth], color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("▼", color = TextPrimary, fontSize = 10.sp,
                    modifier = Modifier.clickable { selectedMonth = (selectedMonth + 11) % 12 })
            }
            Text("/", color = TextPrimary, fontSize = 18.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.5f)) {
                Text("▲", color = TextPrimary, fontSize = 10.sp,
                    modifier = Modifier.clickable { selectedDay2 = selectedDay2 % daysInMonth + 1 })
                Text("%02d".format(selectedDay2), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("▼", color = TextPrimary, fontSize = 10.sp,
                    modifier = Modifier.clickable { selectedDay2 = (selectedDay2 - 2 + daysInMonth) % daysInMonth + 1 })
            }
            Text("/", color = TextPrimary, fontSize = 18.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(2f)) {
                Text("▲", color = TextPrimary, fontSize = 10.sp,
                    modifier = Modifier.clickable { selectedYear++ })
                Text("$selectedYear", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("▼", color = TextPrimary, fontSize = 10.sp,
                    modifier = Modifier.clickable { selectedYear-- })
            }
        }

        Text("flow", color = TextPrimary, fontSize = 13.sp)
        var flow by remember { mutableIntStateOf(1) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("spotting", "light", "medium", "heavy").forEachIndexed { i, label ->
                val sel = flow == i
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) PinkAccent else Color(0xFFEDD9CC))
                        .clickable { flow = i }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (sel) Color.White else TextPrimary, fontSize = 12.sp)
                }
            }
        }

        Text("symptoms", color = TextPrimary, fontSize = 13.sp)
        var periodSymptoms by remember { mutableStateOf(setOf<String>()) }
        val options = listOf("cramping", "bloating", "headache", "fatigue", "mood changes", "back pain")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { opt ->
                        val sel = opt in periodSymptoms
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) PinkAccent else Color(0xFFEDD9CC))
                                .clickable { periodSymptoms = if (sel) periodSymptoms - opt else periodSymptoms + opt }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(opt, color = if (sel) Color.White else TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Text("notes", color = TextPrimary, fontSize = 13.sp)
        var notes by remember { mutableStateOf("") }
        OutlinedTextField(
            value         = notes,
            onValueChange = { notes = it },
            placeholder   = { Text("optional notes...", fontSize = 12.sp) },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 2,
            maxLines      = 4,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = PinkAccent,
                unfocusedBorderColor    = Border,
                focusedTextColor        = TextPrimary,
                unfocusedTextColor      = TextPrimary,
                cursorColor             = PinkAccent,
                focusedContainerColor   = BgColor,
                unfocusedContainerColor = BgColor
            )
        )

        Button(
            onClick   = { onSave(selectedYear, selectedMonth, selectedDay2, flow, periodSymptoms, notes) },
            modifier  = Modifier.fillMaxWidth().height(50.dp),
            shape     = RoundedCornerShape(12.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = PinkAccent, contentColor = Color.White)
        ) {
            Text("save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── monthly calendar ──────────────────────────────────────────────────────────
@Composable
fun MonthCalendarSection(
    monthScores: Map<Int, DayScore>,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    currentMonthYear: String,
    currentCal: Calendar,
    monthOptions: List<Pair<String, Pair<Int, Int>>>,
    periodDays: Set<String>,
    onMonthSelect: (Int, Int) -> Unit,
    onDayClick: (Int, Int, Int, DayScore?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var expandedMonthMenu by remember { mutableStateOf(false) }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
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
                        .heightIn(max = 240.dp)
                ) {
                    monthOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.first, color = TextOnSurface) },
                            onClick = {
                                expandedMonthMenu = false
                                onDayClick(option.second.first, option.second.second, 1, null)
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
                    listOf("s","m","t","w","t","f","s").forEach { d ->
                        Text(d, color = TextPrimary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    }
                }

                val currentYear = currentCal.get(Calendar.YEAR)
                val currentMonth = currentCal.get(Calendar.MONTH)

                val daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val firstDayCal = currentCal.clone() as Calendar
                firstDayCal.set(Calendar.DAY_OF_MONTH, 1)
                val offset = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1
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
                                // FULL DATE CHECK
                                val isSelected = currentYear == selectedYear && currentMonth == selectedMonth && day == selectedDay

                                Column(
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val periodKey = "%04d-%02d-%02d".format(currentYear, currentMonth + 1, day)
                                    val isPeriod = periodKey in periodDays
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .padding(1.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPeriod) PinkAccent.copy(alpha = 0.15f)
                                                else Surface2.copy(alpha = 0.04f)
                                            )
                                            .then(
                                                if (isPeriod) Modifier.border(3.dp, PinkAccent, CircleShape)
                                                else Modifier
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(1.5.dp, Surface1, CircleShape)
                                                else Modifier
                                            )
                                            .clickable { onDayClick(currentYear, currentMonth, day, ds) }
                                    ) {
                                        Image(
                                            painter = painterResource(id = getStatusImage(ds?.score)),
                                            contentDescription = "Pain status",
                                            modifier = Modifier.size(30.dp),
                                        )
                                        if (isPeriod) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 2.dp)
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(PinkAccent)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "$day",
                                        color = if (isSelected) PinkAccent else TextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
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
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    weekOffset: Int,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    periodDays: Set<String>,
    onDayClick: (Int, Int, Int, DayScore?) -> Unit,
    onTodayClick: () -> Unit
) {
    val labels = listOf("sun","mon","tue","wed","thu","fri","sat")

    val weekLabel = when (weekOffset) {
        0 -> "this week"
        -1 -> "last week"
        1 -> "next week"
        else -> if (weekOffset < 0) "${-weekOffset} weeks ago" else "in $weekOffset weeks"
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(weekLabel, color = TextPrimary, fontSize = 11.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    // FULL DATE CHECK
                    val isSelected = itemYear == selectedYear && itemMonth == selectedMonth && itemDay == selectedDay
                    val ds = scoresMap[itemDay]

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                    ) {
                        val periodKey = "%04d-%02d-%02d".format(itemYear, itemMonth + 1, itemDay)
                        val isPeriod = periodKey in periodDays
                        Text(labels[i], color = if (isSelected) PinkAccent else TextPrimary, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))

                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPeriod) PinkAccent.copy(alpha = 0.15f)
                                    else Surface2.copy(alpha = 0.04f)
                                )
                                .then(
                                    if (isPeriod) Modifier.border(3.dp, PinkAccent, CircleShape)
                                    else Modifier
                                )
                                .then(
                                    if (isSelected) Modifier.border(1.5.dp, Surface1, CircleShape)
                                    else Modifier
                                )
                                .clickable { onDayClick(itemYear, itemMonth, itemDay, ds) }
                        ) {
                            Image(
                                painter = painterResource(id = getStatusImage(ds?.score)),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                            )
                            if (isPeriod) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(PinkAccent)
                                )
                            }
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

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
        .clip(RoundedCornerShape(14.dp)).background(Surface1)
        .border(0.5.dp, if (expanded) PinkAccent.copy(0.4f) else Border, RoundedCornerShape(14.dp))
        .clickable { onToggle() }.padding(14.dp)) {

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
                Spacer(Modifier.height(12.dp))
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
    fun getTimePart(ts: Long, field: Int): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(field)

    var startHour by remember { mutableIntStateOf(existingSession?.let { getTimePart(it.startTime, Calendar.HOUR_OF_DAY) } ?: 14) }
    var startMinute by remember { mutableIntStateOf(existingSession?.let { getTimePart(it.startTime, Calendar.MINUTE) } ?: 30) }
    var endHour by remember { mutableIntStateOf(existingSession?.let { getTimePart(it.endTime, Calendar.HOUR_OF_DAY) } ?: 15) }
    var endMinute by remember { mutableIntStateOf(existingSession?.let { getTimePart(it.endTime, Calendar.MINUTE) } ?: 15) }

    var peakLevel by remember { mutableFloatStateOf(existingSession?.peakLevel ?: 5f) }
    var symptoms by remember { mutableStateOf(existingSession?.symptoms ?: emptySet()) }
    var notes by remember { mutableStateOf(existingSession?.notes ?: "") }

    val peakColor = if (peakLevel >= 7f) PinkAccent else if (peakLevel >= 4f) AmberAccent else GreenAccent

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

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("start time", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                SimpleTimePicker(startHour, startMinute, { startHour = it }, { startMinute = it })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("end time", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
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
        Text("symptoms", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        SymptomChips(
            symptoms = Symptom.entries.toList(),
            isActive = { it in symptoms },
            onToggle = { sx -> symptoms = if (sx in symptoms) symptoms - sx else symptoms + sx }
        )

        Spacer(Modifier.height(16.dp))
        Text("notes", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
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

        Button(
            onClick = { onSave(startHour, startMinute, endHour, endMinute, peakLevel, symptoms, notes) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Surface1)
        ) {
            Text(if (existingSession != null) "save changes" else "save session", color = Color.White)
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

// ── symptom chips ────────────────────────────────────────────────────────────
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