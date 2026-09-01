package com.school.widget

import android.os.Bundle
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        schedulePeriodicWidgetSync()

        val prefs = getSharedPreferences("school_widget_prefs", MODE_PRIVATE)

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
                        currentSchool = prefs.getString("school_name", NeisMealService.DEFAULT_SCHOOL_NAME)
                            ?: NeisMealService.DEFAULT_SCHOOL_NAME,
                        onSaveSchoolName = { name ->
                            prefs.edit().putString("school_name", name).apply()
                        },
                        onRefreshMeal = {
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
    onSaveSchoolName: (String) -> Unit,
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

            // 학교 표시 이름 설정 (급식 조회에 사용되는 교육청/학교 코드는
            // NeisMealService.kt의 기본값을 바꾼 뒤 앱을 다시 빌드해야 변경됨)
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
