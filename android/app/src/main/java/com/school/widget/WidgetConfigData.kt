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

private const val INTERNAL_CONFIG_FILENAME = "widget_config.json"

fun parseWidgetConfigJson(text: String): WidgetConfigData {
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

    return WidgetConfigData(ddays, timetable)
}

/**
 * 기기 내부 저장소(앱 전용 filesDir)에 이전에 저장된 설정이 있으면 그것을 우선 사용하고,
 * 없으면 APK에 번들된 기본값(app/src/main/assets/widget_config.json)을 사용한다.
 *
 * 내부 저장소 값은 두 가지 방법으로 채워진다:
 *  1) 스튜디오의 "widget_config.json 다운로드" 파일을 assets 폴더에 넣고 다시 빌드 (기존 방식)
 *  2) 스튜디오의 "이 기기에 적용하기" 버튼/QR코드 딥링크로 앱을 재빌드하지 않고 바로 전달
 *     (ConfigImportActivity/MainActivity에서 받아 saveWidgetConfig()로 저장, 아래 참고)
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
        WidgetConfigData(emptyList(), emptyList())
    }
}

/** 딥링크로 전달받은 시간표/D-Day JSON을 기기 내부 저장소에 저장해 다음 위젯 갱신부터 반영한다. */
fun saveWidgetConfig(context: Context, jsonText: String) {
    val internalFile = java.io.File(context.filesDir, INTERNAL_CONFIG_FILENAME)
    internalFile.writeText(jsonText)
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
