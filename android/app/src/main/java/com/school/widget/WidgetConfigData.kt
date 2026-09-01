package com.school.widget

import android.content.Context
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
    val mealSwitchTime: String
)

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

    return WidgetConfigData(ddays, timetable, todos, showCalories, mealSwitchTime)
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
        WidgetConfigData(emptyList(), emptyList(), emptyList(), true, "13:30")
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
