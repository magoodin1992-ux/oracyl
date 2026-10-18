package com.oracyl.workstation

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Schedules only bounded native Victor work; no external action authority. */
class TaskScheduler(private val context: Context, private val database: OracylDatabase) {
    fun create(title: String, spec: JSONObject): JSONObject {
        validate(spec)
        val task = database.createTask(title, spec)
        enqueue(task.getString("id"), spec)
        return task
    }
    fun pause(id: String): JSONObject {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        return database.setTaskStatus(id, "PAUSED")
    }
    fun resume(id: String): JSONObject {
        val task=database.task(id)?:error("Unknown task");check(task.getString("status")=="PAUSED"){"Task is not paused"}
        val spec=task.getJSONObject("spec");validate(spec);enqueue(id,spec)
        return database.setTaskStatus(id,"SCHEDULED")
    }
    fun cancel(id: String): JSONObject {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        return database.setTaskStatus(id, "CANCELLED")
    }
    private fun enqueue(id:String,spec:JSONObject){
        val runAt=spec.optLong("run_at_epoch_ms",System.currentTimeMillis())
        val delay=(runAt-System.currentTimeMillis()).coerceAtLeast(0)
        val request=OneTimeWorkRequestBuilder<OracylTaskWorker>().setInitialDelay(delay,TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("task_id",id).build()).addTag("oracyl-task").build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id),ExistingWorkPolicy.REPLACE,request)
    }
    private fun validate(spec:JSONObject){
        check(spec.keys().asSequence().toSet()==setOf("kind","question","run_at_epoch_ms")){"Task specification schema mismatch"}
        check(spec.getString("kind")=="VICTOR_RESEARCH_REQUEST"){"Unsupported task kind"}
        check(spec.getString("question").trim().length in 1..512){"Invalid research question"}
        check(spec.getLong("run_at_epoch_ms")>=0){"Invalid task time"}
    }
    private fun workName(id:String)="oracyl-task-$id"
}