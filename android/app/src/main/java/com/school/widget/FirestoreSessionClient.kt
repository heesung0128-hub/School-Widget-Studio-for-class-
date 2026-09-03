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
 * "실시간 편집" 기능(전자칠판이 QR을 보여주고, 폰으로 스캔해 할 일 목록만 편하게
 * 수정하는 기능)을 위한 최소한의 Firestore REST 클라이언트.
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
    private const val MAX_TEXT_LENGTH = 80

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private fun docUrl(sessionCode: String): String =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/widgetSessions/$sessionCode"

    /** 세션 문서를 새로 만들거나 완전히 덮어쓴다 (todos + updatedAt). */
    suspend fun writeTodos(sessionCode: String, todos: List<TodoItemData>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val todosArray = JSONArray()
                todos.take(MAX_TODOS).forEach { todo ->
                    todosArray.put(
                        JSONObject().put(
                            "mapValue",
                            JSONObject().put(
                                "fields",
                                JSONObject()
                                    .put("id", JSONObject().put("stringValue", todo.id))
                                    .put("text", JSONObject().put("stringValue", todo.text.take(MAX_TEXT_LENGTH)))
                                    .put("completed", JSONObject().put("booleanValue", todo.completed))
                            )
                        )
                    )
                }

                val body = JSONObject().put(
                    "fields",
                    JSONObject()
                        .put("todos", JSONObject().put("arrayValue", JSONObject().put("values", todosArray)))
                        .put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
                )

                val request = Request.Builder()
                    .url(docUrl(sessionCode))
                    .patch(body.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }

    /** 세션 문서를 읽어 (할일 목록, updatedAt) 을 반환한다. 문서가 없거나 실패하면 null. */
    suspend fun readSession(sessionCode: String): Pair<List<TodoItemData>, Long>? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(docUrl(sessionCode)).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val json = JSONObject(response.body?.string() ?: return@withContext null)
                    val fields = json.optJSONObject("fields") ?: return@withContext (emptyList<TodoItemData>() to 0L)

                    val todos = mutableListOf<TodoItemData>()
                    val todosArray = fields.optJSONObject("todos")
                        ?.optJSONObject("arrayValue")
                        ?.optJSONArray("values")
                    if (todosArray != null) {
                        for (i in 0 until minOf(todosArray.length(), MAX_TODOS)) {
                            val f = todosArray.getJSONObject(i).optJSONObject("mapValue")?.optJSONObject("fields")
                                ?: continue
                            todos.add(
                                TodoItemData(
                                    id = f.optJSONObject("id")?.optString("stringValue") ?: i.toString(),
                                    text = (f.optJSONObject("text")?.optString("stringValue") ?: "").take(MAX_TEXT_LENGTH),
                                    completed = f.optJSONObject("completed")?.optBoolean("booleanValue") ?: false
                                )
                            )
                        }
                    }

                    val updatedAt = fields.optJSONObject("updatedAt")
                        ?.optString("integerValue")
                        ?.toLongOrNull() ?: 0L

                    todos to updatedAt
                }
            } catch (e: Exception) {
                null
            }
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
