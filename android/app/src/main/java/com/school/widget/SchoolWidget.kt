package com.school.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 안드로이드 태블릿 & 스마트폰 홈 화면용 학교 생활 위젯 (Jetpack Glance)
 * - 상단: 오늘 날짜, 학교명
 * - 중간: 시간표(현재 교시 표시) & 나이스 실시간 급식 (13:30 자동 다음날 전환)
 * - 하단: 새로고침 버튼
 */
class SchoolWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("school_widget_prefs", Context.MODE_PRIVATE)
        val schoolName = prefs.getString("school_name", NeisMealService.DEFAULT_SCHOOL_NAME)
            ?: NeisMealService.DEFAULT_SCHOOL_NAME
        val todayMeal = prefs.getString("today_meal", "급식 정보를 불러오는 중입니다...") ?: ""
        val calInfo = prefs.getString("meal_calories", "") ?: ""
        val currentPeriod = getCurrentPeriodInfo()

        provideContent {
            WidgetContent(
                schoolName = schoolName,
                todayMeal = todayMeal,
                calInfo = calInfo,
                currentPeriod = currentPeriod
            )
        }
    }

    @Composable
    private fun WidgetContent(
        schoolName: String,
        todayMeal: String,
        calInfo: String,
        currentPeriod: String
    ) {
        val dateFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
        val todayStr = dateFormat.format(Date())

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xE60F172A)) // 반투명 슬레이트 다크 아크릴 테마
                .padding(16.dp)
        ) {
            // 1. 헤더: 날짜 및 학교명
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Column {
                    Text(
                        text = todayStr,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = schoolName,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // 2. 시간표 요약 (현재 교시 강조)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "현재 수업: ",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF38BDF8)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = currentPeriod,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // 3. 나이스 실시간 급식 섹션
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍱 오늘의 급식",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFCD34D)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = calInfo,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Text(
                    text = todayMeal,
                    maxLines = 4,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFE2E8F0)),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 4. 하단 새로고침 버튼
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔄 새로고침",
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF38BDF8)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    private fun getCurrentPeriodInfo(): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val currentMins = hour * 60 + min

        return when {
            currentMins < 9 * 60 -> "등교 / 수업 준비 시간"
            currentMins in (9 * 60)..(9 * 60 + 50) -> "1교시"
            currentMins in (10 * 60)..(10 * 60 + 50) -> "2교시"
            currentMins in (11 * 60)..(11 * 60 + 50) -> "3교시"
            currentMins in (12 * 60)..(12 * 60 + 50) -> "4교시"
            currentMins in (12 * 60 + 50)..(13 * 60 + 50) -> "점심시간 🍚"
            currentMins in (13 * 60 + 50)..(14 * 60 + 40) -> "5교시"
            currentMins in (14 * 60 + 50)..(15 * 60 + 40) -> "6교시"
            currentMins in (15 * 60 + 50)..(16 * 60 + 40) -> "7교시"
            else -> "일과 후 / 방과후 활동"
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // 나이스 API 즉시 동기화 실행
        NeisMealService.syncMealData(context)
        SchoolWidget().update(context, glanceId)
    }
}
