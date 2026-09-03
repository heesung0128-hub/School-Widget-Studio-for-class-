package com.school.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TodoIdKey = ActionParameters.Key<String>("todo_id")

/**
 * 안드로이드 태블릿 & 스마트폰 홈 화면용 학교 생활 위젯 (Jetpack Glance).
 * 데스크톱(PowerShell) 위젯과 같은 구성: 날짜, D-Day 전체, 오늘 시간표 전체,
 * 나이스 급식 전체 메뉴, 할 일 목록(체크/추가는 앱에서). 스튜디오에서 고른 테마와
 * 글씨 크기 배율도 데스크톱 위젯과 동일하게 반영된다.
 */
class SchoolWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("school_widget_prefs", Context.MODE_PRIVATE)
        val schoolName = prefs.getString("school_name", NeisMealService.DEFAULT_SCHOOL_NAME)
            ?: NeisMealService.DEFAULT_SCHOOL_NAME
        val todayMeal = prefs.getString("today_meal", "급식 정보를 불러오는 중입니다...") ?: ""
        val calInfo = prefs.getString("meal_calories", "") ?: ""
        val mealTitle = prefs.getString("meal_title", "오늘의 급식") ?: "오늘의 급식"
        val mealDateLabel = prefs.getString("meal_date_label", "") ?: ""

        val config = loadWidgetConfig(context)
        val palette = paletteFor(config.theme)
        val ddayLabels = allDDayLabels(config.ddays)
        val todayDay = todayKoreanDay()
        val todaySchedule = config.timetable.find { it.day == todayDay }

        provideContent {
            WidgetContent(
                context = context,
                palette = palette,
                fontScale = config.fontScale,
                schoolName = schoolName,
                todayMeal = todayMeal,
                calInfo = calInfo,
                mealTitle = mealTitle,
                mealDateLabel = mealDateLabel,
                showCalories = config.showCalories,
                ddayLabels = ddayLabels,
                todayDay = todayDay,
                todayPeriods = todaySchedule?.periods ?: emptyList(),
                todos = config.todos
            )
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        palette: WidgetPalette,
        fontScale: Float,
        schoolName: String,
        todayMeal: String,
        calInfo: String,
        mealTitle: String,
        mealDateLabel: String,
        showCalories: Boolean,
        ddayLabels: List<DDayLabel>,
        todayDay: String,
        todayPeriods: List<String>,
        todos: List<TodoItemData>
    ) {
        val fs: (Int) -> TextUnit = { base -> (base * fontScale).sp }
        val dateFormat = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)
        val todayStr = dateFormat.format(Date())
        val pendingCount = todos.count { !it.completed }

        // 일부 위젯 런처(이 전자칠판 포함)는 위젯 안쪽 스크롤 제스처를 지원하지 않아서,
        // 넘치는 내용이 스크롤 없이 그냥 잘려 보이지 않는 문제가 있었다. 그래서 스크롤에
        // 기대지 않고 애초에 위젯 최대 크기 안에 다 들어가도록 여백/항목 수를 줄였다.
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.containerBg)
                .padding(12.dp)
        ) {
            // 1. 헤더: 날짜 + 학교명 (한 줄)
            // 시간은 표시하지 않는다 - 위젯은 15분 주기로만 갱신되어 실시간 시계가 아니므로,
            // 마지막 갱신 시각을 시계처럼 보여주면 오해를 준다는 피드백에 따라 제거함.
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = todayStr,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.textPrimary),
                        fontSize = fs(17),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = schoolName,
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(palette.accent),
                        fontSize = fs(13),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 2. D-Day 배지 (전체 표시, 2열 칩 형태 - 데스크톱 위젯처럼 간결하게)
            if (ddayLabels.isNotEmpty()) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    ddayLabels.chunked(2).forEach { rowLabels ->
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            rowLabels.forEach { label ->
                                DDayChip(label, fs, modifier = GlanceModifier.defaultWeight())
                                if (label !== rowLabels.last()) {
                                    Spacer(modifier = GlanceModifier.width(4.dp))
                                }
                            }
                            if (rowLabels.size == 1) {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))
                    }
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
            }

            // 3. 오늘 시간표 (오전/오후 2열, 전체 교시)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(palette.cardBg)
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🕐 시간표 ($todayDay)",
                        style = TextStyle(
                            color = ColorProvider(palette.accent),
                            fontSize = fs(13),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    if (todayPeriods.isEmpty()) {
                        Text(
                            text = "오늘 등록된 시간표가 없습니다.",
                            style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = fs(12))
                        )
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                todayPeriods.take(4).forEachIndexed { idx, subject ->
                                    PeriodRow(idx + 1, subject, Color(0xFF60A5FA), palette.textPrimary, fs)
                                }
                            }
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                todayPeriods.drop(4).take(3).forEachIndexed { idx, subject ->
                                    PeriodRow(idx + 5, subject, Color(0xFF34D399), palette.textPrimary, fs)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 4. 나이스 실시간 급식 (전체 메뉴, 2열)
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(palette.cardBg)
                    .padding(8.dp)
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🍱 $mealTitle",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(palette.accent), fontSize = fs(13), fontWeight = FontWeight.Bold)
                    )
                    if (mealDateLabel.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(Color(0x3310B981))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = mealDateLabel,
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(Color(0xFF34D399)), fontSize = fs(10))
                            )
                        }
                    }
                    if (showCalories && calInfo.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = calInfo,
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = fs(12))
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                val dishes = todayMeal.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (dishes.isEmpty()) {
                    Text(
                        text = todayMeal,
                        style = TextStyle(color = ColorProvider(palette.textPrimary), fontSize = fs(12))
                    )
                } else {
                    dishes.chunked(2).forEach { rowDishes ->
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            rowDishes.forEach { dish ->
                                Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Top) {
                                    Text("• ", style = TextStyle(color = ColorProvider(Color(0xFF34D399)), fontSize = fs(13)))
                                    Text(
                                        text = dish,
                                        maxLines = 2,
                                        modifier = GlanceModifier.defaultWeight(),
                                        style = TextStyle(color = ColorProvider(palette.textPrimary), fontSize = fs(13))
                                    )
                                }
                            }
                            if (rowDishes.size == 1) {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 5. 할 일 목록 (체크로 완료 표시, 추가/삭제/순서변경은 앱에서)
            // 이 위젯 안에서 스크롤이 안 되는 런처도 있어서, 전부 다 보여주는 대신
            // 일부만 보여주고 "+ 할 일 추가/관리"로 앱에서 전체 목록을 다루게 한다.
            // 새로고침 버튼은 15분 자동 동기화 + 앱의 즉시 동기화 버튼과 중복이라 제거해
            // 그만큼 공간을 아꼈다.
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(palette.cardBg)
                    .padding(8.dp)
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✅ 할 일 목록",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(palette.accent), fontSize = fs(13), fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "${pendingCount}개 남음",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = fs(12))
                    )
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                if (todos.isEmpty()) {
                    Text(
                        text = "할 일이 없습니다.",
                        style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = fs(12))
                    )
                } else {
                    todos.take(10).forEach { todo ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .clickable(actionRunCallback<ToggleTodoAction>(actionParametersOf(TodoIdKey to todo.id))),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (todo.completed) "☑ " else "☐ ",
                                style = TextStyle(
                                    color = ColorProvider(if (todo.completed) Color(0xFF4ADE80) else palette.textSecondary),
                                    fontSize = fs(13)
                                )
                            )
                            Text(
                                text = todo.text,
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = ColorProvider(if (todo.completed) palette.textSecondary else palette.textPrimary),
                                    fontSize = fs(13),
                                    textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None
                                )
                            )
                        }
                    }
                    if (todos.size > 10) {
                        Text(
                            text = "+${todos.size - 10}개 더 (앱에서 확인)",
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(palette.textSecondary), fontSize = fs(11))
                        )
                    }
                }
                Text(
                    text = "+ 할 일 추가/관리",
                    maxLines = 1,
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(Intent(context, MainActivity::class.java))
                    ),
                    style = TextStyle(color = ColorProvider(palette.accent), fontSize = fs(12), fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    @Composable
    private fun DDayChip(label: DDayLabel, fs: (Int) -> TextUnit, modifier: GlanceModifier) {
        val (bg, fg) = ddayColors(label.urgency)
        Box(
            modifier = modifier
                .background(bg)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.title,
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(fg), fontSize = fs(12), fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = label.ddayText,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(fg), fontSize = fs(12), fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    @Composable
    private fun PeriodRow(periodNumber: Int, subject: String, accent: Color, textPrimary: Color, fs: (Int) -> TextUnit) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${periodNumber}교시 ",
                maxLines = 1,
                style = TextStyle(color = ColorProvider(accent), fontSize = fs(12), fontWeight = FontWeight.Bold)
            )
            Text(
                text = subject.ifBlank { "-" },
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(textPrimary), fontSize = fs(12), fontWeight = FontWeight.Medium)
            )
        }
    }

    private fun ddayColors(urgency: DDayUrgency): Pair<Color, Color> = when (urgency) {
        DDayUrgency.URGENT -> Color(0x33EF4444) to Color(0xFFFCA5A5)
        DDayUrgency.WARNING -> Color(0x33F59E0B) to Color(0xFFFCD34D)
        DDayUrgency.NORMAL -> Color(0x333B82F6) to Color(0xFF93C5FD)
        DDayUrgency.PASSED -> Color(0x33475569) to Color(0xFF94A3B8)
    }

    private fun todayKoreanDay(): String {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "월"
            Calendar.TUESDAY -> "화"
            Calendar.WEDNESDAY -> "수"
            Calendar.THURSDAY -> "목"
            Calendar.FRIDAY -> "금"
            Calendar.SATURDAY -> "토"
            else -> "일"
        }
    }
}

class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val todoId = parameters[TodoIdKey] ?: return
        toggleTodoCompleted(context, todoId)
        SchoolWidget().update(context, glanceId)
    }
}
