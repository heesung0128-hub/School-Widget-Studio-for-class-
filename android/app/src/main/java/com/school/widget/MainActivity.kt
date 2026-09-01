package com.school.widget

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    // 클래스 필드에 Compose State로 보관해두면, 앱이 이미 켜져 있는 상태에서
    // 딥링크(onNewIntent)로 설정이 들어와도 액티비티를 재시작하지 않고 화면이 바로 갱신된다.
    private val schoolNameState = mutableStateOf("")
    private val statusMessageState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        schedulePeriodicWidgetSync()

        val prefs = getSharedPreferences("school_widget_prefs", MODE_PRIVATE)
        schoolNameState.value = prefs.getString("school_name", NeisMealService.DEFAULT_SCHOOL_NAME)
            ?: NeisMealService.DEFAULT_SCHOOL_NAME

        handleImportIntent(intent)

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
                        currentSchool = schoolNameState.value,
                        statusMessage = statusMessageState.value,
                        onSaveSchoolName = { name ->
                            schoolNameState.value = name
                            getSharedPreferences("school_widget_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("school_name", name)
                                .apply()
                        },
                        onRefreshMeal = {
                            scheduleImmediateSync()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    /**
     * 웹 스튜디오의 "이 기기에 적용하기" 버튼이나 QR코드가 여는
     * schoolwidget://import?config=<base64url(JSON)> 딥링크를 처리한다.
     * JSON 형태: { "school": {officeCode, schoolCode, schoolName}, "ddays": [...], "timetable": [...] }
     */
    private fun handleImportIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "schoolwidget" || data.host != "import") return false
        val encoded = data.getQueryParameter("config") ?: return false

        return try {
            val jsonText = decodeBase64UrlToString(encoded)
            val root = JSONObject(jsonText)

            val schoolObj = root.optJSONObject("school")
            if (schoolObj != null) {
                val newSchoolName = schoolObj.optString("schoolName", NeisMealService.DEFAULT_SCHOOL_NAME)
                getSharedPreferences("school_widget_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("school_name", newSchoolName)
                    .putString("org_code", schoolObj.optString("officeCode", NeisMealService.DEFAULT_ATPT_OFCDC_SC_CODE))
                    .putString("school_code", schoolObj.optString("schoolCode", NeisMealService.DEFAULT_SD_SCHUL_CODE))
                    .apply()
                schoolNameState.value = newSchoolName
            }

            val configPayload = JSONObject()
            configPayload.put("ddays", root.optJSONArray("ddays") ?: JSONArray())
            configPayload.put("timetable", root.optJSONArray("timetable") ?: JSONArray())
            saveWidgetConfig(this, configPayload.toString())

            // 위젯 및 급식 정보 즉시 갱신
            scheduleImmediateSync()

            statusMessageState.value = "✅ 스튜디오에서 보낸 학교 정보/시간표/D-Day를 적용했습니다."
            Toast.makeText(this, "학교 설정을 적용했습니다", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            statusMessageState.value = "⚠️ 설정을 적용하지 못했습니다. 링크가 손상되었을 수 있습니다."
            false
        }
    }

    private fun decodeBase64UrlToString(encoded: String): String {
        var s = encoded.replace('-', '+').replace('_', '/')
        while (s.length % 4 != 0) s += "="
        val bytes = Base64.decode(s, Base64.DEFAULT)
        return String(bytes, Charsets.UTF_8)
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
            WidgetUpdateWorker.UNIQUE_WORK_NAME,
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
    statusMessage: String?,
    onSaveSchoolName: (String) -> Unit,
    onRefreshMeal: () -> Unit
) {
    var schoolInput by remember(currentSchool) { mutableStateOf(currentSchool) }
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
                        modifier = Modifier.width(36.dp)
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

            // 딥링크로 시간표/D-Day를 가져올 수 있다는 안내
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.width(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "시간표/D-Day 가져오기",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "이 기기 브라우저에서 웹 스튜디오를 열고 [이 기기에 적용하기]를 누르면, 다시 앱을 설치하지 않고도 시간표와 D-Day가 여기 바로 반영됩니다.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = if (statusMessage.startsWith("✅")) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                    fontSize = 13.sp
                )
            }

            // 학교 표시 이름 설정
            OutlinedTextField(
                value = schoolInput,
                onValueChange = {
                    schoolInput = it
                    onSaveSchoolName(it)
                },
                label = { Text("위젯에 표시할 학교 이름") },
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
                    text = "✅ 동기화 요청을 예약했습니다. 잠시 후 위젯이 갱신됩니다.",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp
                )
            }
        }
    }
}
