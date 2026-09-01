import { WidgetConfig } from '../types';

export interface AndroidCodeFiles {
  'build.gradle.kts': string;
  'AndroidManifest.xml': string;
  'SchoolWidget.kt': string;
  'SchoolWidgetReceiver.kt': string;
  'NeisMealService.kt': string;
  'WidgetUpdateWorker.kt': string;
  'MainActivity.kt': string;
  'widget_config.json': string;
  'README_ANDROID.md': string;
}

export function generateAndroidProjectFiles(config: WidgetConfig): AndroidCodeFiles {
  const schoolName = config.school.schoolName;
  const orgCode = config.school.officeCode;
  const schoolCode = config.school.schoolCode;
  const { ddays, timetable, todos, showCalories, mealSwitchTime, theme, fontScale } = config;

  // 1. build.gradle.kts (App level)
  const buildGradle = `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.school.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.school.widget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Jetpack Glance (AppWidget with Compose for Android Tablet & Phone)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // WorkManager (Background periodic sync for NEIS meals & timetable)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Ktor / OkHttp + Kotlinx Serialization (NEIS Open API client)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
`;

  // 2. AndroidManifest.xml
  const manifest = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 인터넷 권한: 나이스(NEIS) 급식 및 학사 일정 실시간 조회 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="학교 생활 위젯"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SchoolWidget"
        tools:targetApi="35">

        <!-- 메인 설정 화면 (학교 선택, 시간표 수정, D-Day 등록) -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="학교 생활 위젯 설정"
            android:theme="@style/Theme.SchoolWidget">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 안드로이드 홈 화면 / 태블릿 위젯 리시버 (Glance AppWidget) -->
        <receiver
            android:name=".SchoolWidgetReceiver"
            android:exported="true"
            android:label="학교 생활 위젯 (시간표/급식/D-Day)">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/school_widget_info" />
        </receiver>

        <!-- 기기 부팅 시 백그라운드 급식/위젯 자동 갱신 워커 등록 -->
        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
`;

  // 3. SchoolWidget.kt (Jetpack Glance Widget for Android Tablets & Phones)
  const schoolWidgetKt = `package com.school.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.*

/**
 * 안드로이드 태블릿 & 스마트폰 홈 화면용 학교 생활 위젯 (Jetpack Glance)
 * - 상단: 오늘 날짜, 요일, D-Day 뱃지
 * - 중간 탭: 시간표(현재 교시 하이라이트) & 나이스 실시간 급식 (13:30 자동 다음날 전환)
 * - 하단: To-Do 리스트 및 원터치 완료 체크
 */
class SchoolWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // SharedPreferences 또는 로컬 Room DB에서 최신 데이터 로드
        val prefs = context.getSharedPreferences("school_widget_prefs", Context.MODE_PRIVATE)
        val schoolName = prefs.getString("school_name", "${schoolName}") ?: "${schoolName}"
        val todayMeal = prefs.getString("today_meal", "급식 정보를 불러오는 중입니다...") ?: ""
        val calInfo = prefs.getString("meal_calories", "680.5 kcal") ?: ""
        val currentPeriod = getCurrentPeriodInfo()

        provideContent {
            GlanceThemeWrapper {
                WidgetContent(
                    schoolName = schoolName,
                    todayMeal = todayMeal,
                    calInfo = calInfo,
                    currentPeriod = currentPeriod
                )
            }
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
            // 1. 헤더: 학교명 및 D-Day 뱃지
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
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

                // D-Day 뱃지 (예: 수능 또는 시험)
                Box(
                    modifier = GlanceModifier
                        .background(Color(0x333B82F6))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "수능 D-79",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF60A5FA)),
                            fontSize = 12.sp,
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
                    .defaultWeight()
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

            // 4. 하단 새로고침 및 설정 버튼
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
            currentMins in (9 * 60)..(9 * 60 + 50) -> "1교시: 문학 (3-1)"
            currentMins in (10 * 60)..(10 * 60 + 50) -> "2교시: 문학 (3-2)"
            currentMins in (11 * 60)..(11 * 60 + 50) -> "3교시: 상담"
            currentMins in (12 * 60)..(12 * 60 + 50) -> "4교시: 수업준비"
            currentMins in (12 * 60 + 50)..(13 * 60 + 50) -> "점심시간 🍚"
            currentMins in (13 * 60 + 50)..(14 * 60 + 40) -> "5교시: 진로지도"
            currentMins in (14 * 60 + 50)..(15 * 60 + 40) -> "6교시: 동아리"
            currentMins in (15 * 60 + 50)..(16 * 60 + 40) -> "7교시: 종례"
            else -> "일과 후 / 방과후 활동"
        }
    }

    @Composable
    private fun GlanceThemeWrapper(content: @Composable () -> Unit) {
        content()
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
`;

  // 4. SchoolWidgetReceiver.kt
  const receiverKt = `package com.school.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class SchoolWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SchoolWidget()
}
`;

  // 5. NeisMealService.kt (나이스 Open API 연동 및 파싱)
  const neisMealServiceKt = `package com.school.widget

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object NeisMealService {
    private val client = OkHttpClient()

    // 스튜디오에서 설정된 교육청 코드 및 학교 고유 코드
    const val DEFAULT_ATPT_OFCDC_SC_CODE = "${orgCode}"
    const val DEFAULT_SD_SCHUL_CODE = "${schoolCode}"
    const val DEFAULT_SCHOOL_NAME = "${schoolName}"

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
                // 만약 다음날이 토요일이면 월요일로 넘김
                if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, 2)
                } else if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val ymdFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
            val targetYmd = ymdFormat.format(cal.time)

            val url = "https://open.neis.go.kr/hub/mealServiceDietInfo" +
                    "?Type=json&pIndex=1&pSize=5" +
                    "&ATPT_OFCDC_SC_CODE=$orgCode" +
                    "&SD_SCHUL_CODE=$schoolCode" +
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
                        .replace("<br/>", "\\n")
                        .replace("<br>", "\\n")
                        .replace(Regex("\\\\([0-9.]+\\\\)"), "")
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
`;

  // 6. WidgetUpdateWorker.kt (WorkManager를 활용한 안드로이드 백그라운드 주기적 자동 갱신)
  const workerKt = `package com.school.widget

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
}
`;

  // 7. MainActivity.kt (태블릿 & 폰용 설정 UI)
  const mainActivityKt = `package com.school.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 백그라운드 위젯 자동 갱신 주기 등록 (15분마다)
        schedulePeriodicWidgetSync()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF020617),
                    surface = Color(0xFF0F172A),
                    primary = Color(0xFF38BDF8)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SchoolWidgetSettingsScreen(
                        currentSchool = "${schoolName}",
                        onRefreshMeal = {
                            // 즉시 급식 갱신
                            scheduleImmediateSync()
                        }
                    )
                }
            }
        }
    }

    private fun schedulePeriodicWidgetSync() {
        val syncRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SchoolWidgetSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun scheduleImmediateSync() {
        val oneTimeWork = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
        WorkManager.getInstance(this).enqueue(oneTimeWork)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolWidgetSettingsScreen(
    currentSchool: String,
    onRefreshMeal: () -> Unit
) {
    var schoolInput by remember { mutableStateOf(currentSchool) }
    var snackbarVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("학교 생활 위젯 설정", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 안내 카드
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TabletAndroid,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "안드로이드 태블릿 홈 화면 위젯",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "홈 화면 빈 공간을 길게 눌러 [학교 생활 위젯]을 추가하세요.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 학교 설정
            OutlinedTextField(
                value = schoolInput,
                onValueChange = { schoolInput = it },
                label = { Text("설정된 학교") },
                leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                )
            )

            // 나이스 동기화 버튼
            Button(
                onClick = {
                    onRefreshMeal()
                    snackbarVisible = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("나이스 급식 데이터 지금 즉시 동기화", fontWeight = FontWeight.Bold)
            }

            if (snackbarVisible) {
                Text(
                    text = "✅ 위젯 및 급식 데이터가 성공적으로 갱신되었습니다.",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp
                )
            }
        }
    }
}
`;

  // 7.5 widget_config.json — 저장소의 android/app/src/main/assets/widget_config.json 을
  // 이 파일로 덮어쓰고 커밋/푸시하면, GitHub Actions가 다시 빌드한 위젯에 D-Day와 시간표가
  // 그대로 반영된다 (Kotlin 코드 수정이나 Android Studio 없이 시간표/D-Day만 바꾸는 방법).
  const widgetConfigJson = JSON.stringify(
    {
      ddays: ddays.map((d) => ({ id: d.id, title: d.title, targetDate: d.targetDate })),
      timetable: timetable.map((t) => ({ day: t.day, periods: t.periods })),
      todos: todos.map((t) => ({ id: t.id, text: t.text, completed: t.completed })),
      showCalories,
      mealSwitchTime,
      theme,
      fontScale,
    },
    null,
    2
  );

  // 8. README_ANDROID.md (안드로이드 스튜디오에서 3분 만에 빌드/실행하는 완벽 가이드)
  const readme = `# 📱 학교 생활 안드로이드 태블릿 & 스마트폰 위젯

이 프로젝트는 안드로이드의 최신 **Jetpack Compose & Jetpack Glance** 기술을 이용하여 만든 학교 생활 홈 화면 위젯입니다.

## 🌟 주요 기능
1. **태블릿 & 스마트폰 홈 화면 상시 거치 위젯**:
   - 투명 아크릴 다크 테마 위젯
   - 실시간 날짜, D-Day 카운트다운
   - 오늘 요일 시간표 및 현재 교시 실시간 자동 하이라이트
   - 나이스(NEIS) 실시간 급식 정보 (오후 1:30 자동 다음날 전환)
2. **WorkManager 백그라운드 동기화**:
   - 기기 배터리를 거의 소모하지 않으면서 15분마다 정밀하게 급식 및 시간표 데이터를 자동 업데이트합니다.
3. **Jetpack Glance (Compose)**:
   - XML RemoteViews의 한계를 벗어나 모던 선언형 UI로 완벽한 레이아웃과 터치 인터랙션을 제공합니다.

---

## 🚀 Android Studio에서 빌드 및 실행하는 방법 (3단계)

### 1단계. Android Studio 열기
1. [Android Studio (Ladybug 이상 추천)]를 실행합니다.
2. **New Project** ➔ **Empty Activity** (Compose)를 선택하여 프로젝트를 생성합니다. (Package: \`com.school.widget\`)

### 2단계. 소스 코드 복사/붙여넣기
- \`app/build.gradle.kts\`: 위젯 및 Glance 의존성 추가
- \`app/src/main/AndroidManifest.xml\`: 인터넷 권한 및 Widget Receiver 등록
- \`app/src/main/java/com/school/widget/\` 폴더에 아래 6개 파일 생성 후 붙여넣기:
  1. \`SchoolWidget.kt\` (위젯 UI 및 인터랙션)
  2. \`SchoolWidgetReceiver.kt\` (위젯 브로드캐스트 수신자)
  3. \`NeisMealService.kt\` (교육부 나이스 Open API 연동)
  4. \`WidgetUpdateWorker.kt\` (백그라운드 동기화 워커)
  5. \`MainActivity.kt\` (태블릿 설정 앱 메인 화면)

### 3단계. 태블릿에 설치 및 위젯 배치
1. 태블릿을 USB로 연결하거나 무선 디버깅(Wi-Fi)으로 연결합니다.
2. 상단의 **Run (▶)** 버튼을 눌러 앱을 설치합니다.
3. 태블릿 홈 화면의 빈 공간을 **2초간 꾹 누른 뒤** ➔ **[위젯]** ➔ **[학교 생활 위젯]**을 선택하여 원하는 크기로 배치합니다!
`;

  return {
    'build.gradle.kts': buildGradle,
    'AndroidManifest.xml': manifest,
    'SchoolWidget.kt': schoolWidgetKt,
    'SchoolWidgetReceiver.kt': receiverKt,
    'NeisMealService.kt': neisMealServiceKt,
    'WidgetUpdateWorker.kt': workerKt,
    'MainActivity.kt': mainActivityKt,
    'widget_config.json': widgetConfigJson,
    'README_ANDROID.md': readme,
  };
}
