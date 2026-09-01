package com.school.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 1. 나이스 급식 최신 데이터 받아오기
        NeisMealService.syncMealData(appContext)

        // 2. 홈 화면에 배치된 모든 SchoolWidget 인스턴스 새로고침
        val glanceManager = GlanceAppWidgetManager(appContext)
        val glanceIds = glanceManager.getGlanceIds(SchoolWidget::class.java)

        glanceIds.forEach { glanceId ->
            SchoolWidget().update(appContext, glanceId)
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "SchoolWidgetSync"
    }
}
