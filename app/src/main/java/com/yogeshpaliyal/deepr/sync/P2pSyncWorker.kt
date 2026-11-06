package com.yogeshpaliyal.deepr.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic P2P background sync.
 * Runs only when the app is not in the foreground (no active sync controller).
 * The worker itself starts and stops the controller for each sync cycle.
 */
class P2pSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams),
    KoinComponent {
    private val controller: P2pSyncController by inject()

    override suspend fun doWork(): Result {
        // Skip if the app is in the foreground (controller already running) or there's nobody to sync with.
        if (controller.running.value || !controller.hasPeersOrInvite()) return Result.success()

        return try {
            controller.start()
            controller.node.syncAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        } finally {
            controller.stop()
        }
    }

    companion object {
        private const val WORK_NAME = "P2P_SYNC"
        private const val INTERVAL_MINUTES = 15L

        /**
         * Schedules the periodic P2P sync work.
         * Call once from [com.yogeshpaliyal.deepr.DeeprApplication.onCreate].
         */
        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<P2pSyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
