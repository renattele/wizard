package ${Package}.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class GeneratedSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.success()
}
