package com.school.widget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 폰에서 읽어온 세션 문서. 각 목록은 문서에 그 필드가 아예 없으면 null이다
 * (예: 이전 버전 폰 페이지가 만든 세션에는 ddays/timetable이 없음) - null인 항목은
 * 위젯의 기존 값을 건드리지 않고 그대로 두기 위해 "비어 있음"과 구분한다.
 * 목록은 모두 normalize*()를 거친 값이라, 위젯의 값도 같은 함수를 거치면 == 로 비교할 수 있다.
 */
data class LiveSessionSnapshot(
    val todos: List<TodoItemData>?,
    val ddays: List<DDayItem>?,
    val timetable: List<TimetableDay>?,
    val updatedAt: Long
)

/**
 * "실시간 편집" 기능(전자칠판이 QR을 보여주고, 폰으로 스캔해 할 일/D-Day/시간표를
 * 편하게 수정하는 기능)을 위한 최소한의 Firestore REST 클라이언트.
 *
 * Firebase Android SDK(google-services.json, Gradle 플러그인)를 추가하는 대신
 * 이미 있는 OkHttp로 Firestore의 공개 REST API만 직접 호출한다 - 문서가
 * "widgetSessions/{code}" 컬렉션에만 있고, 그 컬렉션의 규칙이 완전히 열려있어야
 * 인증 없이 동작한다 (android/README.md에 설정 방법 안내).
 *
 * 세션 코드는 짧고 무작위라 추측하기 어렵지만, 그 자체로 접근 제어는 아니므로
 * 여기서 읽어온 값도 딥링크 가져오기와 동일하게 개수/길이 제한을 적용해 방어한다.
 */
object FirestoreSessionClient {
    private const val PROJECT_ID = "school-widget-for-class"
    private const val MAX_TODOS = 200
    private const val MAX_DDAYS = 20
    private const val MAX_TIMETABLE_DAYS = 7
    private const val MAX_PERIODS_PER_DAY = 10
    private const val MAX_TEXT_LENGTH = 80

    private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private fun docUrl(sessionCode: String): String =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/widgetSessions/$sessionCode"

    // ---------- 정규화 ----------
    // 클라우드에서 읽을 때 적용하는 개수/길이 제한과 형식 검사를, 위젯 쪽 값에도 똑같이 적용해서
    // "양쪽이 같은 상태인가"를 == 로 정확히 비교할 수 있게 한다(예: 날짜 형식이 아닌 D-Day는
    // 클라우드에서 걸러지므로, 위젯 쪽에서도 비교 전에 같이 걸러야 영원히 "다르다"로 보이지 않는다).

    fun normalizeTodos(todos: List<TodoItemData>): List<TodoItemData> =
        todos.take(MAX_TODOS).map {
            TodoItemData(it.id.take(MAX_TEXT_LENGTH), it.text.take(MAX_TEXT_LENGTH), it.completed)
        }

    fun normalizeDDays(ddays: List<DDayItem>): List<DDayItem> =
        ddays.mapNotNull {
            val title = it.title.trim().take(MAX_TEXT_LENGTH)
            val date = it.targetDate.trim()
            // 날짜 형식이 아닌 값은 위젯이 어차피 표시하지 못하므로 걸러낸다
            if (title.isEmpty() || !DATE_REGEX.matches(date)) null else DDayItem(title, date)
        }.take(MAX_DDAYS)

    fun normalizeTimetable(timetable: List<TimetableDay>): List<TimetableDay> =
        timetable.take(MAX_TIMETABLE_DAYS).map { day ->
            TimetableDay(day.day.take(4), day.periods.take(MAX_PERIODS_PER_DAY).map { it.take(MAX_TEXT_LENGTH) })
        }

    // ---------- Firestore 값 변환 ----------

    private fun stringField(value: String) = JSONObject().put("stringValue", value.take(MAX_TEXT_LENGTH))

    private fun mapField(fields: JSONObject) = JSONObject().put("mapValue", JSONObject().put("fields", fields))

    private fun arrayField(values: JSONArray) = JSONObject().put("arrayValue", JSONObject().put("values", values))

    private fun todosToJson(todos: List<TodoItemData>): JSONObject {
        val array = JSONArray()
        normalizeTodos(todos).forEach { todo ->
            array.put(
                mapField(
                    JSONObject()
                        .put("id", stringField(todo.id))
                        .put("text", stringField(todo.text))
                        .put("completed", JSONObject().put("booleanValue", todo.completed))
                )
            )
        }
        return arrayField(array)
    }

    private fun ddaysToJson(ddays: List<DDayItem>): JSONObject {
        val array = JSONArray()
        normalizeDDays(ddays).forEach { dday ->
            array.put(
                mapField(
                    JSONObject()
                        .put("title", stringField(dday.title))
                        .put("targetDate", stringField(dday.targetDate))
                )
            )
        }
        return arrayField(array)
    }

    private fun timetableToJson(timetable: List<TimetableDay>): JSONObject {
        val array = JSONArray()
        normalizeTimetable(timetable).forEach { day ->
            val periods = JSONArray()
            day.periods.forEach { periods.put(stringField(it)) }
            array.put(
                mapField(
                    JSONObject()
                        .put("day", stringField(day.day))
                        .put("periods", arrayField(periods))
                )
            )
        }
        return arrayField(array)
    }

    /**
     * 세션 문서에 넘겨준(null이 아닌) 항목만 쓴다. updateMask로 그 필드들만 바꾸므로
     * 폰이 방금 저장한 다른 항목을 덮어쓰지 않는다. 문서가 없으면 새로 만든다.
     */
    suspend fun patchSession(
        sessionCode: String,
        todos: List<TodoItemData>? = null,
        ddays: List<DDayItem>? = null,
        timetable: List<TimetableDay>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fields = JSONObject()
            val maskNames = mutableListOf<String>()
            if (todos != null) { fields.put("todos", todosToJson(todos)); maskNames.add("todos") }
            if (ddays != null) { fields.put("ddays", ddaysToJson(ddays)); maskNames.add("ddays") }
            if (timetable != null) { fields.put("timetable", timetableToJson(timetable)); maskNames.add("timetable") }
            fields.put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            maskNames.add("updatedAt")

            val mask = maskNames.joinToString("&") { "updateMask.fieldPaths=$it" }
            val request = Request.Builder()
                .url("${docUrl(sessionCode)}?$mask")
                .patch(JSONObject().put("fields", fields).toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /** 세션 문서를 읽는다. 문서가 없거나 네트워크 오류면 null. */
    suspend fun readSession(sessionCode: String): LiveSessionSnapshot? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(docUrl(sessionCode)).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val json = JSONObject(response.body?.string() ?: return@withContext null)
                    val fields = json.optJSONObject("fields")
                        ?: return@withContext LiveSessionSnapshot(null, null, null, 0L)

                    val updatedAt = fields.optJSONObject("updatedAt")
                        ?.optString("integerValue")
                        ?.toLongOrNull() ?: 0L

                    LiveSessionSnapshot(
                        todos = parseTodos(fields),
                        ddays = parseDDays(fields),
                        timetable = parseTimetable(fields),
                        updatedAt = updatedAt
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

    /** 필드 자체가 없으면 null, 있으면(비어 있어도) 목록. Firestore는 빈 배열의 values를 생략한다. */
    private fun arrayValues(fields: JSONObject, name: String): JSONArray? {
        val field = fields.optJSONObject(name) ?: return null
        val arrayValue = field.optJSONObject("arrayValue") ?: return null
        return arrayValue.optJSONArray("values") ?: JSONArray()
    }

    private fun mapFields(element: JSONArray, index: Int): JSONObject? =
        element.optJSONObject(index)?.optJSONObject("mapValue")?.optJSONObject("fields")

    private fun JSONObject.stringOf(name: String): String =
        optJSONObject(name)?.optString("stringValue") ?: ""

    private fun parseTodos(fields: JSONObject): List<TodoItemData>? {
        val values = arrayValues(fields, "todos") ?: return null
        val todos = mutableListOf<TodoItemData>()
        for (i in 0 until minOf(values.length(), MAX_TODOS)) {
            val f = mapFields(values, i) ?: continue
            todos.add(
                TodoItemData(
                    id = f.stringOf("id").ifEmpty { i.toString() },
                    text = f.stringOf("text"),
                    completed = f.optJSONObject("completed")?.optBoolean("booleanValue") ?: false
                )
            )
        }
        return normalizeTodos(todos)
    }

    private fun parseDDays(fields: JSONObject): List<DDayItem>? {
        val values = arrayValues(fields, "ddays") ?: return null
        val ddays = mutableListOf<DDayItem>()
        for (i in 0 until minOf(values.length(), MAX_DDAYS)) {
            val f = mapFields(values, i) ?: continue
            ddays.add(DDayItem(f.stringOf("title"), f.stringOf("targetDate")))
        }
        return normalizeDDays(ddays)
    }

    private fun parseTimetable(fields: JSONObject): List<TimetableDay>? {
        val values = arrayValues(fields, "timetable") ?: return null
        val days = mutableListOf<TimetableDay>()
        for (i in 0 until minOf(values.length(), MAX_TIMETABLE_DAYS)) {
            val f = mapFields(values, i) ?: continue
            val periods = mutableListOf<String>()
            val periodValues = f.optJSONObject("periods")?.optJSONObject("arrayValue")?.optJSONArray("values")
            if (periodValues != null) {
                for (j in 0 until minOf(periodValues.length(), MAX_PERIODS_PER_DAY)) {
                    periods.add(periodValues.optJSONObject(j)?.optString("stringValue") ?: "")
                }
            }
            days.add(TimetableDay(f.stringOf("day"), periods))
        }
        return normalizeTimetable(days)
    }

    /** 세션이 더 이상 필요 없을 때(편집 종료) 문서를 지운다. 실패해도 무해하므로 결과를 무시해도 된다. */
    suspend fun deleteSession(sessionCode: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(docUrl(sessionCode)).delete().build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }

    /** 사람이 헷갈리기 쉬운 0/O, 1/I/l 을 뺀 문자셋으로 6자리 세션 코드를 만든다. */
    fun generateSessionCode(): String {
        val charset = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        return (1..6).map { charset.random() }.joinToString("")
    }
}
