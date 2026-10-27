package com.example.nexonote.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.example.nexonote.reminder.WorkManagerEventScheduler
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventWorkManagerTest {
    @Test fun replacementAndCancellationLeaveAtMostOnePendingWorker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = WorkManagerEventScheduler(context)
        val manager = WorkManager.getInstance(context)
        val id = 9_999_991L
        val start = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        try {
            scheduler.schedule(id, 1, start, start)
            scheduler.schedule(id, 2, start + 1000, start + 1000)
            val active = manager.getWorkInfosForUniqueWork("event_reminder_$id").get(10, TimeUnit.SECONDS).filter { !it.state.isFinished }
            assertEquals(1, active.size)
            scheduler.cancel(id)
            assertTrue(manager.getWorkInfosForUniqueWork("event_reminder_$id").get(10, TimeUnit.SECONDS).all { it.state.isFinished })
        } finally { scheduler.cancel(id) }
    }
}
