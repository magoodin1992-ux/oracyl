package com.oracyl.workstation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Durable Oracyl UI/work records. Victor state and Qwen model files stay separate. */
class OracylDatabase(context: Context) : SQLiteOpenHelper(context, "oracyl_workstation.sqlite3", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging()
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chats(id TEXT PRIMARY KEY,title TEXT NOT NULL,pinned INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,chat_id TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,role TEXT NOT NULL,content TEXT NOT NULL,status TEXT NOT NULL,semantic_json TEXT,victor_json TEXT,created_at TEXT NOT NULL)")
        db.execSQL("CREATE INDEX messages_chat_time ON messages(chat_id,created_at)")
        db.execSQL("CREATE TABLE drafts(chat_id TEXT PRIMARY KEY REFERENCES chats(id) ON DELETE CASCADE,text TEXT NOT NULL,updated_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY,title TEXT NOT NULL,spec_json TEXT NOT NULL,status TEXT NOT NULL,result_json TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE operator_questions(id TEXT PRIMARY KEY,question TEXT NOT NULL,context_json TEXT NOT NULL,status TEXT NOT NULL,answer TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE saved_tabs(id TEXT PRIMARY KEY,url TEXT NOT NULL,title TEXT NOT NULL,session_state TEXT,saved INTEGER NOT NULL,pinned INTEGER NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE tasks ADD COLUMN result_json TEXT")
    }

    @Synchronized fun ensureChat(chatId: String?, firstText: String? = null): String {
        if (chatId != null && chatExists(chatId)) return chatId
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        val title = firstText?.trim()?.replace(Regex("\\s+"), " ")?.take(64)?.ifBlank { "New chat" } ?: "New chat"
        writableDatabase.insertOrThrow("chats", null, ContentValues().apply {
            put("id", id); put("title", title); put("created_at", now); put("updated_at", now)
        }); return id
    }

    @Synchronized fun appendMessage(chatId: String, role: String, content: String, status: String,
                                    semanticJson: String? = null, victorJson: String? = null): String {
        require(role in setOf("operator", "victor", "system")); require(content.length <= 64_000)
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow("messages", null, ContentValues().apply {
                put("id", id); put("chat_id", chatId); put("role", role); put("content", content)
                put("status", status); put("semantic_json", semanticJson); put("victor_json", victorJson); put("created_at", now)
            })
            writableDatabase.execSQL("UPDATE chats SET updated_at=? WHERE id=?", arrayOf(now, chatId))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        return id
    }

    @Synchronized fun recentMessages(chatId: String, limit: Int = 12): JSONArray {
        require(limit in 1..500); val rows = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT role,content,status,created_at FROM messages WHERE chat_id=? ORDER BY created_at DESC LIMIT ?",
            arrayOf(chatId, limit.toString())).use { cursor ->
            while (cursor.moveToNext()) rows += JSONObject().put("role", cursor.getString(0))
                .put("content", cursor.getString(1)).put("status", cursor.getString(2)).put("created_at", cursor.getString(3))
        }
        return JSONArray(rows.asReversed())
    }
    @Synchronized fun messages(chatId: String, limit: Int = 500): JSONArray = recentMessages(chatId, limit.coerceIn(1, 500))

    @Synchronized fun chats(): JSONArray {
        val result = JSONArray()
        readableDatabase.rawQuery("SELECT id,title,pinned,updated_at FROM chats ORDER BY pinned DESC,updated_at DESC LIMIT 200", null).use { cursor ->
            while (cursor.moveToNext()) result.put(JSONObject().put("id", cursor.getString(0)).put("title", cursor.getString(1))
                .put("pinned", cursor.getInt(2) != 0).put("updated_at", cursor.getString(3)))
        }; return result
    }

    @Synchronized fun saveDraft(chatId: String, text: String) {
        require(text.length <= 64_000); val now = Instant.now().toString()
        writableDatabase.execSQL("INSERT INTO drafts(chat_id,text,updated_at) VALUES(?,?,?) ON CONFLICT(chat_id) DO UPDATE SET text=excluded.text,updated_at=excluded.updated_at",
            arrayOf(chatId, text, now))
    }
    @Synchronized fun draft(chatId: String): String = readableDatabase.rawQuery("SELECT text FROM drafts WHERE chat_id=?", arrayOf(chatId)).use {
        if (it.moveToFirst()) it.getString(0) else ""
    }

    @Synchronized fun createTab(url: String): String {
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        writableDatabase.insertOrThrow("saved_tabs", null, ContentValues().apply {
            put("id", id); put("url", url); put("title", url.take(160)); putNull("session_state")
            put("saved", 0); put("pinned", 0); put("created_at", now); put("updated_at", now)
        }); return id
    }
    @Synchronized fun tab(id: String): JSONObject? = readableDatabase.rawQuery(
        "SELECT id,url,title,session_state,saved,pinned FROM saved_tabs WHERE id=?", arrayOf(id)).use { cursor ->
        if (!cursor.moveToFirst()) null else JSONObject().put("id", cursor.getString(0)).put("url", cursor.getString(1))
            .put("title", cursor.getString(2)).put("session_state", if (cursor.isNull(3)) JSONObject.NULL else cursor.getString(3))
            .put("saved", cursor.getInt(4) != 0).put("pinned", cursor.getInt(5) != 0)
    }
    @Synchronized fun updateTab(id: String, url: String, title: String?, sessionState: String?) {
        writableDatabase.update("saved_tabs", ContentValues().apply {
            put("url", url); put("title", title?.take(300) ?: url.take(300)); put("session_state", sessionState)
            put("updated_at", Instant.now().toString())
        }, "id=?", arrayOf(id))
    }
    @Synchronized fun setTabSaved(id: String, saved: Boolean) {
        writableDatabase.update("saved_tabs", ContentValues().apply { put("saved", if (saved) 1 else 0); put("updated_at", Instant.now().toString()) }, "id=?", arrayOf(id))
    }
    @Synchronized fun tabs(): JSONArray {
        val result=JSONArray(); readableDatabase.rawQuery("SELECT id,url,title,saved,pinned,updated_at FROM saved_tabs WHERE saved=1 ORDER BY updated_at DESC",null).use { c ->
            while(c.moveToNext()) result.put(JSONObject().put("id",c.getString(0)).put("url",c.getString(1)).put("title",c.getString(2))
                .put("saved",c.getInt(3)!=0).put("pinned",c.getInt(4)!=0).put("updated_at",c.getString(5)))
        }; return result
    }

    @Synchronized fun createTask(title: String, spec: JSONObject): JSONObject {
        val clean=title.trim();require(clean.isNotEmpty()&&clean.length<=160)
        val id=UUID.randomUUID().toString();val now=Instant.now().toString()
        writableDatabase.insertOrThrow("tasks",null,ContentValues().apply{
            put("id",id);put("title",clean);put("spec_json",spec.toString());put("status","SCHEDULED");putNull("result_json")
            put("created_at",now);put("updated_at",now)
        });return JSONObject().put("id",id).put("title",clean).put("status","SCHEDULED")
    }
    @Synchronized fun tasks(): JSONArray {
        val out=JSONArray();readableDatabase.rawQuery("SELECT id,title,spec_json,status,result_json,updated_at FROM tasks ORDER BY updated_at DESC LIMIT 500",null).use{c->
            while(c.moveToNext())out.put(JSONObject().put("id",c.getString(0)).put("title",c.getString(1))
                .put("spec",JSONObject(c.getString(2))).put("status",c.getString(3))
                .put("result",if(c.isNull(4))JSONObject.NULL else JSONObject(c.getString(4))).put("updated_at",c.getString(5)))
        };return out
    }
    @Synchronized fun setTaskStatus(id:String,status:String):JSONObject{
        require(status in setOf("SCHEDULED","RUNNING","PAUSED","CANCELLED","COMPLETED","FAILED_RETRYABLE","FAILED"));val now=Instant.now().toString()
        val changed=writableDatabase.update("tasks",ContentValues().apply{put("status",status);put("updated_at",now)},"id=?",arrayOf(id))
        check(changed==1){"Unknown task"};return JSONObject().put("id",id).put("status",status)
    }
    @Synchronized fun task(id:String):JSONObject?=readableDatabase.rawQuery("SELECT id,title,spec_json,status,result_json FROM tasks WHERE id=?",arrayOf(id)).use{c->
        if(!c.moveToFirst())null else JSONObject().put("id",c.getString(0)).put("title",c.getString(1)).put("spec",JSONObject(c.getString(2)))
            .put("status",c.getString(3)).put("result",if(c.isNull(4))JSONObject.NULL else JSONObject(c.getString(4)))
    }
    @Synchronized fun finishTask(id:String,status:String,result:JSONObject):JSONObject{
        require(status in setOf("COMPLETED","FAILED_RETRYABLE","FAILED"));val now=Instant.now().toString()
        val changed=writableDatabase.update("tasks",ContentValues().apply{put("status",status);put("result_json",result.toString());put("updated_at",now)},"id=?",arrayOf(id))
        check(changed==1){"Unknown task"};return JSONObject().put("id",id).put("status",status).put("result",result)
    }
    @Synchronized fun createOperatorQuestion(question:String,context:JSONObject):JSONObject{
        val clean=question.trim();require(clean.isNotEmpty()&&clean.length<=1000)
        val id=UUID.randomUUID().toString();val now=Instant.now().toString()
        writableDatabase.insertOrThrow("operator_questions",null,ContentValues().apply{put("id",id);put("question",clean)
            put("context_json",context.toString());put("status","OPEN");put("created_at",now);put("updated_at",now)})
        return JSONObject().put("id",id).put("question",clean).put("status","OPEN")
    }
    @Synchronized fun operatorQuestions():JSONArray{
        val out=JSONArray();readableDatabase.rawQuery("SELECT id,question,context_json,status,answer,updated_at FROM operator_questions ORDER BY updated_at DESC LIMIT 500",null).use{c->
            while(c.moveToNext())out.put(JSONObject().put("id",c.getString(0)).put("question",c.getString(1))
                .put("context",JSONObject(c.getString(2))).put("status",c.getString(3))
                .put("answer",if(c.isNull(4))JSONObject.NULL else c.getString(4)).put("updated_at",c.getString(5)))
        };return out
    }
    @Synchronized fun answerOperatorQuestion(id:String,answer:String):JSONObject{
        val clean=answer.trim();require(clean.isNotEmpty()&&clean.length<=8000);val now=Instant.now().toString()
        val changed=writableDatabase.update("operator_questions",ContentValues().apply{put("answer",clean);put("status","ANSWERED");put("updated_at",now)},"id=? AND status='OPEN'",arrayOf(id))
        check(changed==1){"Operator question is missing or already answered"}
        return JSONObject().put("id",id).put("status","ANSWERED")
    }
    private fun chatExists(id: String) = readableDatabase.rawQuery("SELECT 1 FROM chats WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
}