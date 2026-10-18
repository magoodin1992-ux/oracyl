package com.oracyl.workstation

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject

class OracylTaskWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id=inputData.getString("task_id")?:return Result.failure()
        val database=OracylDatabase(applicationContext)
        val task=database.task(id)?:return Result.failure()
        if(task.getString("status") in setOf("PAUSED","CANCELLED"))return Result.success()
        database.setTaskStatus(id,"RUNNING")
        return try{
            val spec=task.getJSONObject("spec")
            val victor=VictorPythonRuntime(applicationContext,VictorStateManager(applicationContext))
            val request=JSONObject().put("version","VICTOR_ARM64_RPC_V1").put("action","research_request")
                .put("question",spec.getString("question")).put("cycle_id",id)
            val response=victor.dispatch(request)
            check(response.optBoolean("ok")){response.optString("message","Victor task failed")}
            database.finishTask(id,"COMPLETED",response.getJSONObject("result"));Result.success()
        }catch(failure:Throwable){
            val record=JSONObject().put("error",failure.javaClass.simpleName).put("message",(failure.message?:"Task failed").take(300))
            if(runAttemptCount<2){database.finishTask(id,"FAILED_RETRYABLE",record);Result.retry()}
            else{database.finishTask(id,"FAILED",record);Result.failure()}
        }
    }
}