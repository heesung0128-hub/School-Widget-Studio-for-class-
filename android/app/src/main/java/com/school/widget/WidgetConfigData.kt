package com.school.widget

import android.content.Context
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

data class WidgetConfigData(
    val ddays: List<DDayItem>,
    val timetable: List<TimetableDay>
)

/**
 * 스튜디오(웹 앱)의 [위젯 설정] 탭에서 시간표/D-Day를 바꾼 뒤 "widget_config.json 다운로드"로
 * 받은 파일을 이 경로(app/src/main/assets/widget_config.json)에 덮어쓰고 커밋/푸시하면,
 * 다음 자동 빌드부터 위젯에 바로 반영된다.
 */
fun loadWidgetConfig(context: Context): WidgetConfigData {
    return try {
        val text = context.assets.open("widget_config.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)

        val ddaysArray = root.optJSONArray("ddays")
        val ddays = mutableListOf<DDayItem>()
        if (ddaysArray != null) {
            for (i in 0 until ddaysArray.length()) {
                val obj = ddaysArray.getJSONObject(i)
                ddays.add(DDayItem(obj.optString("title"), obj.optString("targetDate")))
            }
        }

        val timetableArray = root.optJSONArray("timetable")
        val timetable = mutableListOf<TimetableDay>()
        if (timetableArray != null) {
            for (i in 0 until timetableArray.length()) {
                val obj = timetableArray.getJSONObject(i)
                val periodsArray = obj.optJSONArray("periods")
                val periods = mutableListOf<String>()
                if (periodsArray != null) {
                    for (j in 0 until periodsArray.length()) {
                        periods.add(periodsArray.optString(j))
                    }
                }
                timetable.add(TimetableDay(obj.optString("day"), periods))
            }
        }

        WidgetConfigData(ddays, timetable)
    } catch (e: Exception) {
        WidgetConfigData(emptyList(), emptyList())
    }
}

/**
 * 오늘 이후로 가장 가까운 D-Day 한 개를 골라 "제목"과 "D-n"(오늘이면 "D-DAY") 형태로 반환한다.
 * 위젯 공간이 좁아 여러 개를 동시에 보여주지 않고, 가장 임박한 일정 하나만 강조한다.
 */
fun nextDDayLabel(ddays: List<DDayItem>): Pair<String, String>? {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    sdf.isLenient = false
    val today = sdf.parse(sdf.format(java.util.Date())) ?: return null

    var best: Pair<String, Long>? = null
    for (item in ddays) {
        val target = try {
            sdf.parse(item.targetDate)
        } catch (e: Exception) {
            null
        } ?: continue

        val diffDays = ((target.time - today.time) / 86_400_000.0).roundToLong()
        if (diffDays < 0) continue
        if (best == null || diffDays < best!!.second) {
            best = item.title to diffDays
        }
    }

    val (title, diff) = best ?: return null
    val ddayText = if (diff == 0L) "D-DAY" else "D-$diff"
    return title to ddayText
}
