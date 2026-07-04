package com.rendy.classnote.widget

import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity

/**
 * PERF-H1：一次 widget 刷新裡，provider 的 populateOverview 與 RemoteViewsFactory 的
 * onDataSetChanged 會各自對 reminders / reminder_notifications 做一次全表查詢（兩次 runBlocking）。
 * 兩者同一 app process，這裡提供極短命的快照快取：第一次查詢後在極短 TTL 內，第二個讀者直接複用，
 * 消掉重複掃描。TTL 很短，確保跨刷新一定重查，不會拿到過期資料。
 *
 * 注意：不能用 Date.now 以外的隨機性；這裡只用 System.nanoTime 做單調 TTL 比較（非時鐘）。
 */
object WidgetDataCache {

    data class Snapshot(
        val reminders: List<ReminderEntity>,
        val notifications: List<ReminderNotificationEntity>,
        val capturedAtNanos: Long
    )

    private const val TTL_NANOS = 2_000_000_000L  // 2 秒

    @Volatile
    private var cached: Snapshot? = null

    /** 取得仍在 TTL 內的快照，否則 null（呼叫端須自行查詢並 put）。 */
    fun get(nowNanos: Long): Snapshot? {
        val c = cached ?: return null
        return if (nowNanos - c.capturedAtNanos <= TTL_NANOS) c else null
    }

    fun put(snapshot: Snapshot) {
        cached = snapshot
    }

    /** 資料變更（完成提醒、同步寫入）後主動失效，確保下次刷新一定重查。 */
    fun invalidate() {
        cached = null
    }
}
