package com.school.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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

// 딥링크로 넘어온 값이 지나치게 크면(위조/손상 가능성) 아예 파싱을 시도하지 않는다.
private const val MAX_IMPORT_PAYLOAD_CHARS = 20_000

private data class PendingImport(
    val schoolName: String?,
    val orgCode: String?,
    val schoolCode: String?,
    val ddayCount: Int,
    val timetableDayCount: Int,
    val todoCount: Int,
    val configJson: String
)

class MainActivity : ComponentActivity() {
    private val schoolNameState = mutableStateOf("")
    private val statusMessageState = mutableStateOf<String?>(null)
    private val pendingImportState = mutableStateOf<PendingImport?>(null)
    private val todosState = mutableStateOf<List<TodoItemData>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        schedulePeriodicWidgetSync()
        refreshFromStorage()
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
                        pendingImport = pendingImportState.value,
                        todos = todosState.value,
                        onSaveSchoolName = { name ->
                            schoolNameState.value = name
                            getSharedPreferences("school_widget_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("school_name", name)
                                .apply()
                        },
                        onRefreshMeal = { scheduleImmediateSync() },
                        onConfirmImport = { applyPendingImport() },
                        onCancelImport = { pendingImportState.value = null },
                        onApplyPastedConfig = { pasted -> handlePastedConfig(pasted) },
                        onAddTodo = { text ->
                            addTodo(this, text)
                            refreshFromStorage()
                            scheduleImmediateSync()
                        },
                        onToggleTodo = { id ->
                            toggleTodoCompleted(this, id)
                            refreshFromStorage()
                            scheduleImmediateSync()
                        },
                        onDeleteTodo = { id ->
                            deleteTodo(this, id)
                            refreshFromStorage()
                            scheduleImmediateSync()
                        },
                        onReorderTodos = { newOrder ->
                            reorderTodos(this, newOrder)
                            refreshFromStorage()
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

    private fun refreshFromStorage() {
        val prefs = getSharedPreferences("school_widget_prefs", MODE_PRIVATE)
        schoolNameState.value = prefs.getString("school_name", NeisMealService.DEFAULT_SCHOOL_NAME)
            ?: NeisMealService.DEFAULT_SCHOOL_NAME
        todosState.value = loadWidgetConfig(this).todos
    }

    /**
     * 웹 스튜디오의 "이 기기에 적용하기" 버튼이나 QR코드가 여는
     * schoolwidget://import?config=<base64url(JSON)> 딥링크를 처리한다.
     *
     * 보안: 다른 앱/웹페이지도 이 딥링크를 열 수 있으므로(android:exported, BROWSABLE),
     * 내용을 절대 조용히 바로 적용하지 않고 먼저 요약을 보여준 뒤 사용자가 확인해야
     * 실제로 저장된다. 페이로드 크기도 제한해 손상/악의적인 링크로부터 보호한다.
     */
    private fun handleImportIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "schoolwidget" || data.host != "import") return false
        val encoded = data.getQueryParameter("config") ?: return false
        return stageImport(encoded)
    }

    /**
     * 딥링크를 탭할 수 없는 환경(카메라 위치가 불편한 전자칠판 등)을 위해, 스튜디오의
     * "링크만 복사"로 받은 전체 링크나 그 안의 config 값만 붙여넣어도 그대로 적용한다.
     */
    private fun handlePastedConfig(pasted: String) {
        val encoded = extractEncodedConfig(pasted)
        if (encoded == null) {
            statusMessageState.value = "⚠️ 붙여넣은 내용에서 설정을 찾지 못했습니다."
            return
        }
        stageImport(encoded)
    }

    private fun extractEncodedConfig(pasted: String): String? {
        val trimmed = pasted.trim()
        if (trimmed.isEmpty()) return null

        val uri = try {
            Uri.parse(trimmed)
        } catch (e: Exception) {
            null
        }
        if (uri != null && uri.scheme == "schoolwidget" && uri.host == "import") {
            return uri.getQueryParameter("config")
        }

        val markerIndex = trimmed.indexOf("config=")
        if (markerIndex != -1) {
            return trimmed.substring(markerIndex + "config=".length).trim()
        }

        // 링크 형식이 아니면, 붙여넣은 문자열 자체가 인코딩된 설정 값이라고 본다.
        return trimmed
    }

    /**
     * 딥링크(handleImportIntent)와 붙여넣기(handlePastedConfig) 두 경로 모두
     * 여기서 공통으로 파싱하고, 사용자가 확인해야만 적용되도록 대기 상태로 둔다.
     */
    private fun stageImport(encoded: String): Boolean {
        if (encoded.length > MAX_IMPORT_PAYLOAD_CHARS) {
            statusMessageState.value = "⚠️ 전달된 설정이 너무 커서 무시했습니다."
            return false
        }

        return try {
            val jsonText = decodeBase64UrlToString(encoded)
            val root = JSONObject(jsonText)
            val schoolObj = root.optJSONObject("school")

            val configPayload = JSONObject()
            configPayload.put("ddays", root.optJSONArray("ddays") ?: JSONArray())
            configPayload.put("timetable", root.optJSONArray("timetable") ?: JSONArray())
            configPayload.put("todos", root.optJSONArray("todos") ?: JSONArray())
            configPayload.put("showCalories", root.optBoolean("showCalories", true))
            configPayload.put("mealSwitchTime", root.optString("mealSwitchTime", "13:30"))
            configPayload.put("theme", root.optString("theme", "dark-acrylic"))
            configPayload.put("fontScale", root.optDouble("fontScale", 1.0))

            // 안전 한도(길이/개수 제한)를 적용한 뒤의 실제 값으로 요약을 보여준다
            val parsed = parseWidgetConfigJson(configPayload.toString())

            pendingImportState.value = PendingImport(
                schoolName = schoolObj?.optString("schoolName")?.take(60),
                orgCode = schoolObj?.optString("officeCode")?.take(10),
                schoolCode = schoolObj?.optString("schoolCode")?.take(10),
                ddayCount = parsed.ddays.size,
                timetableDayCount = parsed.timetable.size,
                todoCount = parsed.todos.size,
                configJson = configPayload.toString()
            )
            true
        } catch (e: Exception) {
            statusMessageState.value = "⚠️ 설정을 불러오지 못했습니다. 링크가 손상되었을 수 있습니다."
            false
        }
    }

    private fun applyPendingImport() {
        val pending = pendingImportState.value ?: return
        val prefsEditor = getSharedPreferences("school_widget_prefs", MODE_PRIVATE).edit()
        if (!pending.schoolName.isNullOrBlank()) {
            prefsEditor.putString("school_name", pending.schoolName)
        }
        if (!pending.orgCode.isNullOrBlank()) {
            prefsEditor.putString("org_code", pending.orgCode)
        }
        if (!pending.schoolCode.isNullOrBlank()) {
            prefsEditor.putString("school_code", pending.schoolCode)
        }
        prefsEditor.apply()

        saveWidgetConfig(this, pending.configJson)
        refreshFromStorage()
        scheduleImmediateSync()

        statusMessageState.value = "✅ 스튜디오에서 보낸 설정을 적용했습니다."
        Toast.makeText(this, "학교 설정을 적용했습니다", Toast.LENGTH_LONG).show()
        pendingImportState.value = null
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
private fun SchoolWidgetSettingsScreen(
    currentSchool: String,
    statusMessage: String?,
    pendingImport: PendingImport?,
    todos: List<TodoItemData>,
    onSaveSchoolName: (String) -> Unit,
    onRefreshMeal: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onApplyPastedConfig: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onReorderTodos: (List<TodoItemData>) -> Unit
) {
    var schoolInput by remember(currentSchool) { mutableStateOf(currentSchool) }
    var snackbarVisible by remember { mutableStateOf(false) }
    var newTodoText by remember { mutableStateOf("") }
    var pasteInput by remember { mutableStateOf("") }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("이 설정을 적용할까요?") },
            text = {
                Column {
                    if (!pendingImport.schoolName.isNullOrBlank()) {
                        Text("학교: ${pendingImport.schoolName}")
                    }
                    Text("시간표: ${pendingImport.timetableDayCount}일치")
                    Text("D-Day: ${pendingImport.ddayCount}개")
                    Text("할 일: ${pendingImport.todoCount}개")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "웹 스튜디오에서 보낸 링크가 아니라면 취소하세요.",
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmImport) { Text("적용") }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) { Text("취소") }
            }
        )
    }

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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            text = "이 기기 브라우저에서 웹 스튜디오를 열고 [이 기기에 적용하기]를 누르면 여기서 확인 후 반영됩니다.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 카메라로 QR을 찍기 불편한 기기(전자칠판 등)를 위한 대안: 스튜디오의
            // "링크만 복사"로 받은 링크(또는 그 안의 설정 값)를 여기에 그대로 붙여넣으면
            // 딥링크를 탭한 것과 동일하게 적용된다.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "또는, 링크 붙여넣기로 적용",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Text(
                    text = "스튜디오의 [링크만 복사]로 받은 링크를 아래에 붙여넣으세요. 카메라로 QR을 찍기 어려운 기기에서 유용합니다.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pasteInput,
                        onValueChange = { pasteInput = it },
                        label = { Text("schoolwidget://import?config=... 붙여넣기") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (pasteInput.isNotBlank()) {
                            onApplyPastedConfig(pasteInput)
                            pasteInput = ""
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "붙여넣은 링크 적용", tint = Color(0xFF34D399))
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

            // 할 일 목록 관리 (위젯에서는 체크만 가능, 추가/삭제는 여기서)
            Text(
                text = "할 일 목록 (${todos.count { !it.completed }}개 남음)",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTodoText,
                    onValueChange = { newTodoText = it },
                    label = { Text("새 할 일") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (newTodoText.isNotBlank()) {
                        onAddTodo(newTodoText)
                        newTodoText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "추가", tint = Color(0xFF38BDF8))
                }
            }

            Text(
                text = "항목을 길게 눌러 위아래로 끌면 순서를 바꿀 수 있습니다.",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )

            ReorderableTodoList(
                todos = todos,
                onToggle = onToggleTodo,
                onDelete = onDeleteTodo,
                onReorder = onReorderTodos
            )
        }
    }
}

/**
 * 길게 누른 뒤 위/아래로 끌면 순서가 바뀌는 간단한 할 일 목록.
 * 드래그 중에는 로컬 상태로만 순서를 미리 반영하다가, 손을 떼는 순간 저장소에 반영한다.
 */
@Composable
private fun ReorderableTodoList(
    todos: List<TodoItemData>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<TodoItemData>) -> Unit
) {
    var localTodos by remember(todos) { mutableStateOf(todos) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 48.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        localTodos.forEach { todo ->
            val isDragging = todo.id == draggedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = with(density) { (if (isDragging) dragOffsetPx else 0f).toDp() })
                    .pointerInput(todo.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = todo.id
                                dragOffsetPx = 0f
                            },
                            onDragEnd = {
                                draggedId = null
                                dragOffsetPx = 0f
                                onReorder(localTodos)
                            },
                            onDragCancel = {
                                draggedId = null
                                dragOffsetPx = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx += dragAmount.y
                                val currentIndex = localTodos.indexOfFirst { it.id == todo.id }
                                if (currentIndex != -1) {
                                    if (dragOffsetPx > rowHeightPx / 2 && currentIndex < localTodos.size - 1) {
                                        localTodos = localTodos.toMutableList().apply {
                                            add(currentIndex + 1, removeAt(currentIndex))
                                        }
                                        dragOffsetPx -= rowHeightPx
                                    } else if (dragOffsetPx < -rowHeightPx / 2 && currentIndex > 0) {
                                        localTodos = localTodos.toMutableList().apply {
                                            add(currentIndex - 1, removeAt(currentIndex))
                                        }
                                        dragOffsetPx += rowHeightPx
                                    }
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "길게 눌러 순서 변경",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = { onToggle(todo.id) }) {
                    Icon(
                        if (todo.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (todo.completed) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                    )
                }
                Text(
                    text = todo.text,
                    modifier = Modifier.weight(1f),
                    color = if (todo.completed) Color(0xFF64748B) else Color(0xFFE2E8F0),
                    textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                    fontSize = 14.sp
                )
                IconButton(onClick = { onDelete(todo.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color(0xFF94A3B8))
                }
            }
        }
    }
}
