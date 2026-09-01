package com.school.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToLong

data class DDayItem(
    val title: String,
    val targetDate: String // YYYY-MM-DD
)

data class TimetableDay(
    val day: String, // 월/화/수/목/금
    val periods: List<String>
)

data class TodoItemData(
    val id: String,
    val text: String,
    val completed: Boolean
)

enum class DDayUrgency { URGENT, WARNING, NORMAL, PASSED }

data class DDayLabel(
    val title: String,
    val ddayText: String,
    val urgency: DDayUrgency
)

data class WidgetConfigData(
    val ddays: List<DDayItem>,
    val timetable: List<TimetableDay>,
    val todos: List<TodoItemData>,
    val showCalories: Boolean,
    val mealSwitchTime: String,
    val theme: String,
    val fontScale: Float
)

data class WidgetPalette(
    val containerBg: Color,
    val cardBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
)

/**
 * 스튜디오의 6가지 테마와 동일한 배색 (색상 값은 powerShellGenerator.ts / Widget.tsx와 맞춤).
 * D-Day 긴급도 색상, 시간표 오전/오후 강조색, 급식 불릿/날짜 배지 색 등은 테마와 무관하게
 * 데스크톱 위젯과 마찬가지로 고정 색을 그대로 쓴다.
 */
fun paletteFor(theme: String): WidgetPalette = when (theme) {
    "light-acrylic" -> WidgetPalette(
        containerBg = Color(0xF2FFFFFF),
        cardBg = Color(0xE6F8FAFC),
        textPrimary = Color(0xFF1E293B),
        textSecondary = Color(0xFF64748B),
        accent = Color(0xFF2563EB)
    )
    "emerald-glass" -> WidgetPalette(
        containerBg = Color(0xE6022C22),
        cardBg = Color(0x99064E3B),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF6EE7B7),
        accent = Color(0xFF34D399)
    )
    "indigo-glass" -> WidgetPalette(
        containerBg = Color(0xE61E1B4B),
        cardBg = Color(0x99312E81),
        textPrimary = Color(0xFFEEF2FF),
        textSecondary = Color(0xFFA5B4FC),
        accent = Color(0xFF818CF8)
    )
    "slate-glass" -> WidgetPalette(
        containerBg = Color(0xD9334155),
        cardBg = Color(0x99475569),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFFCBD5E1),
        accent = Color(0xFF38BDF8)
    )
    "sakura-glass" -> WidgetPalette(
        containerBg = Color(0xE6500724),
        cardBg = Color(0x99831843),
        textPrimary = Color(0xFFFDF2F8),
        textSecondary = Color(0xFFF9A8D4),
        accent = Color(0xFFF472B6)
    )
    else -> WidgetPalette( // dark-acrylic (기본값)
        containerBg = Color(0xE60F172A),
        cardBg = Color(0xB31E293B),
        textPrimary = Color(0xFFF1F5F9),
        textSecondary = Color(0xFF94A3B8),
        accent = Color(0xFF60A5FA)
    )
}

// 스튜디오/딥링크로부터 받는 값이 악의적이거나 손상되었을 가능성을 대비한 안전 한도.
// (예: 위조된 schoolwidget://import 링크가 지나치게 큰 배열/문자열을 보내 위젯 렌더링을
//  느려지게 하거나 비정상적으로 만드는 것을 방지)
private const val MAX_DDAYS = 20
private const val MAX_TIMETABLE_DAYS = 7
private const val MAX_PERIODS_PER_DAY = 10
private const val MAX_TODOS = 200
private const val MAX_TITLE_LENGTH = 80

private const val INTERNAL_CONFIG_FILENAME = "widget_config.json"

fun parseWidgetConfigJson(text: String): WidgetConfigData {
    val root = JSONObject(text)

    val ddaysArray = root.optJSONArray("ddays")
    val ddays = mutableListOf<DDayItem>()
    if (ddaysArray != null) {
        for (i in 0 until minOf(ddaysArray.length(), MAX_DDAYS)) {
            val obj = ddaysArray.getJSONObject(i)
            ddays.add(
                DDayItem(
                    obj.optString("title").take(MAX_TITLE_LENGTH),
                    obj.optString("targetDate").take(20)
                )
            )
        }
    }

    val timetableArray = root.optJSONArray("timetable")
    val timetable = mutableListOf<TimetableDay>()
    if (timetableArray != null) {
        for (i in 0 until minOf(timetableArray.length(), MAX_TIMETABLE_DAYS)) {
            val obj = timetableArray.getJSONObject(i)
            val periodsArray = obj.optJSONArray("periods")
            val periods = mutableListOf<String>()
            if (periodsArray != null) {
                for (j in 0 until minOf(periodsArray.length(), MAX_PERIODS_PER_DAY)) {
                    periods.add(periodsArray.optString(j).take(MAX_TITLE_LENGTH))
                }
            }
            timetable.add(TimetableDay(obj.optString("day").take(4), periods))
        }
    }

    val todosArray = root.optJSONArray("todos")
    val todos = mutableListOf<TodoItemData>()
    if (todosArray != null) {
        for (i in 0 until minOf(todosArray.length(), MAX_TODOS)) {
            val obj = todosArray.getJSONObject(i)
            todos.add(
                TodoItemData(
                    id = obj.optString("id", i.toString()),
                    text = obj.optString("text").take(MAX_TITLE_LENGTH),
                    completed = obj.optBoolean("completed", false)
                )
            )
        }
    }

    // 이전 버전에서 저장된 설정 파일에는 이 필드들이 없을 수 있으므로 기본값을 둔다
    val showCalories = root.optBoolean("showCalories", true)
    val mealSwitchTime = root.optString("mealSwitchTime", "13:30").take(5)
    val theme = root.optString("theme", "dark-acrylic").take(20)
    // 스튜디오의 글씨 크기 슬라이더(0.85~1.3)와 같은 범위로 제한 - 손상된/악의적인 값이
    // 지나치게 큰 글씨로 위젯을 보기 흉하게 만들지 못하도록 방어
    val fontScale = root.optDouble("fontScale", 1.0).toFloat().coerceIn(0.85f, 1.3f)

    return WidgetConfigData(ddays, timetable, todos, showCalories, mealSwitchTime, theme, fontScale)
}

private fun WidgetConfigData.toJson(): JSONObject {
    val root = JSONObject()

    val ddaysArray = JSONArray()
    ddays.forEach { d ->
        ddaysArray.put(JSONObject().apply {
            put("title", d.title)
            put("targetDate", d.targetDate)
        })
    }
    root.put("ddays", ddaysArray)

    val timetableArray = JSONArray()
    timetable.forEach { t ->
        timetableArray.put(JSONObject().apply {
            put("day", t.day)
            put("periods", JSONArray(t.periods))
        })
    }
    root.put("timetable", timetableArray)

    val todosArray = JSONArray()
    todos.forEach { item ->
        todosArray.put(JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("completed", item.completed)
        })
    }
    root.put("todos", todosArray)

    root.put("showCalories", showCalories)
    root.put("mealSwitchTime", mealSwitchTime)
    root.put("theme", theme)
    root.put("fontScale", fontScale.toDouble())
    return root
}

/**
 * 기기 내부 저장소(앱 전용 filesDir)에 이전에 저장된 설정이 있으면 그것을 우선 사용하고,
 * 없으면 APK에 번들된 기본값(app/src/main/assets/widget_config.json)을 사용한다.
 *
 * 내부 저장소 값은 두 가지 방법으로 채워진다:
 *  1) 스튜디오의 "widget_config.json 다운로드" 파일을 assets 폴더에 넣고 다시 빌드 (기존 방식)
 *  2) 스튜디오의 "이 기기에 적용하기" 버튼/QR코드 딥링크로 앱을 재빌드하지 않고 바로 전달
 *     (MainActivity의 딥링크 처리에서 받아 saveWidgetConfig()로 저장)
 */
fun loadWidgetConfig(context: Context): WidgetConfigData {
    return try {
        val internalFile = java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME)
        val text = if (internalFile.exists()) {
            internalFile.readText()
        } else {
            context.assets.open(INTERNAL_CONFIG_FILENAME).bufferedReader().use { it.readText() }
        }
        parseWidgetConfigJson(text)
    } catch (e: Exception) {
        WidgetConfigData(emptyList(), emptyList(), emptyList(), true, "13:30", "dark-acrylic", 1.0f)
    }
}

/** 딥링크/스튜디오로부터 받은 시간표/D-Day/할일 JSON을 기기 내부 저장소에 저장한다. */
fun saveWidgetConfig(context: Context, jsonText: String) {
    // 저장 전에도 한 번 파싱→직렬화를 거쳐서 길이 제한이 항상 적용되도록 한다
    val sanitized = parseWidgetConfigJson(jsonText).toJson().toString()
    java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME).writeText(sanitized)
}

fun toggleTodoCompleted(context: Context, todoId: String) {
    val current = loadWidgetConfig(context)
    val updated = current.copy(
        todos = current.todos.map { if (it.id == todoId) it.copy(completed = !it.completed) else it }
    )
    java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME).writeText(updated.toJson().toString())
}

fun addTodo(context: Context, text: String) {
    if (text.isBlank()) return
    val current = loadWidgetConfig(context)
    val newItem = TodoItemData(
        id = System.currentTimeMillis().toString(),
        text = text.trim().take(MAX_TITLE_LENGTH),
        completed = false
    )
    val updated = current.copy(todos = (current.todos + newItem).takeLast(MAX_TODOS))
    java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME).writeText(updated.toJson().toString())
}

fun deleteTodo(context: Context, todoId: String) {
    val current = loadWidgetConfig(context)
    val updated = current.copy(todos = current.todos.filterNot { it.id == todoId })
    java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME).writeText(updated.toJson().toString())
}

/** 앱의 할 일 관리 화면에서 드래그로 바꾼 순서를 그대로 저장한다. */
fun reorderTodos(context: Context, newOrder: List<TodoItemData>) {
    val current = loadWidgetConfig(context)
    val updated = current.copy(todos = newOrder)
    java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME).writeText(updated.toJson().toString())
}

/**
 * D-Day 배지 목록을 데스크톱(PowerShell) 위젯과 동일한 규칙으로 계산한다:
 * 오늘이면 D-DAY(긴급), 7일 이내 남았으면 경고색, 그보다 많이 남았으면 기본색,
 * 이미 지난 일정이면 D+n으로 회색 표시. 입력 순서를 그대로 유지한다(데스크톱과 동일).
 */
fun allDDayLabels(ddays: List<DDayItem>): List<DDayLabel> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    sdf.isLenient = false
    val today = try {
        sdf.parse(sdf.format(java.util.Date()))
    } catch (e: Exception) {
        null
    } ?: return emptyList()

    return ddays.mapNotNull { item ->
        val target = try {
            sdf.parse(item.targetDate)
        } catch (e: Exception) {
            null
        } ?: return@mapNotNull null

        val diffDays = ((target.time - today.time) / 86_400_000.0).roundToLong()
        val (text, urgency) = when {
            diffDays == 0L -> "D-DAY" to DDayUrgency.URGENT
            diffDays > 0 -> "D-$diffDays" to (if (diffDays <= 7) DDayUrgency.WARNING else DDayUrgency.NORMAL)
            else -> "D+${-diffDays}" to DDayUrgency.PASSED
        }
        DDayLabel(item.title, text, urgency)
    }
}
