package com.school.widget

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object NeisMealService {
    private val client = OkHttpClient()

    // 스튜디오(웹 앱)의 [위젯 설정] 탭에서 학교를 바꾸면, 앱의 설정 화면(MainActivity)에서
    // 아래 기본값 대신 SharedPreferences("org_code"/"school_code")에 저장된 값을 우선 사용한다.
    const val DEFAULT_ATPT_OFCDC_SC_CODE = "B10"
    const val DEFAULT_SD_SCHUL_CODE = "7010152"
    const val DEFAULT_SCHOOL_NAME = "동덕여자고등학교"

    suspend fun syncMealData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("school_widget_prefs", Context.MODE_PRIVATE)
            val orgCode = prefs.getString("org_code", DEFAULT_ATPT_OFCDC_SC_CODE) ?: DEFAULT_ATPT_OFCDC_SC_CODE
            val schoolCode = prefs.getString("school_code", DEFAULT_SD_SCHUL_CODE) ?: DEFAULT_SD_SCHUL_CODE

            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)

            // 오후 1:30 (13:30) 이후에는 다음 날 급식을 미리 보여주는 스마트 스위칭
            if (hour > 13 || (hour == 13 && min >= 30)) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                // 다음날이 토요일이면 월요일로, 일요일이면 월요일로 넘김
                when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_YEAR, 2)
                    Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val ymdFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
            val targetYmd = ymdFormat.format(cal.time)

            // orgCode/schoolCode는 딥링크로 외부에서 전달될 수 있는 값이므로, 요청 URL에
            // 그대로 이어붙이지 않고 인코딩한다 (쿼리 문자열 조작 방지).
            val url = "https://open.neis.go.kr/hub/mealServiceDietInfo" +
                "?Type=json&pIndex=1&pSize=5" +
                "&ATPT_OFCDC_SC_CODE=${android.net.Uri.encode(orgCode)}" +
                "&SD_SCHUL_CODE=${android.net.Uri.encode(schoolCode)}" +
                "&MLSV_YMD=$targetYmd"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            val json = JSONObject(responseBody)
            if (json.has("mealServiceDietInfo")) {
                val array = json.getJSONArray("mealServiceDietInfo")
                val rowArray = array.getJSONObject(1).getJSONArray("row")
                if (rowArray.length() > 0) {
                    val row = rowArray.getJSONObject(0)
                    val rawDish = row.optString("DDISH_NM", "")
                    val calInfo = row.optString("CAL_INFO", "")

                    // 알레르기 번호 (1.2.5.6) 및 HTML 태그 정제
                    val cleanDish = rawDish
                        .replace("<br/>", "\n")
                        .replace("<br>", "\n")
                        .replace(Regex("\\([0-9.]+\\)"), "")
                        .trim()

                    prefs.edit()
                        .putString("today_meal", cleanDish)
                        .putString("meal_calories", calInfo)
                        .putLong("last_meal_sync", System.currentTimeMillis())
                        .apply()

                    return@withContext true
                }
            }

            prefs.edit()
                .putString("today_meal", "해당 날짜에 등록된 급식 정보가 없습니다.")
                .putString("meal_calories", "-")
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
