package com.rendy.classnote.data.repository

import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val notificationDao: ReminderNotificationDao
) {
    // ── 提醒事項 ──────────────────────────────────────────────────────────

    fun getActiveReminders(): Flow<List<ReminderEntity>> =
        reminderDao.getActiveReminders()

    fun getRemindersByCourse(courseId: Long): Flow<List<ReminderEntity>> =
        reminderDao.getRemindersByCourse(courseId)

    suspend fun getReminderById(id: Long): ReminderEntity? =
        reminderDao.getReminderById(id)

    suspend fun insertReminder(reminder: ReminderEntity): Long =
        reminderDao.insertReminder(reminder)

    suspend fun updateReminder(reminder: ReminderEntity) =
        reminderDao.updateReminder(reminder)

    suspend fun markCompleted(id: Long) =
        reminderDao.markCompleted(id)

    suspend fun deleteReminder(reminder: ReminderEntity) =
        reminderDao.deleteReminder(reminder)

    suspend fun cleanupCompleted() =
        reminderDao.deleteCompletedReminders()

    suspend fun getActiveRemindersOnce(): List<ReminderEntity> =
        reminderDao.getActiveRemindersOnce()

    // ── 通知時間 ──────────────────────────────────────────────────────────

    fun getNotificationsForReminder(reminderId: Long): Flow<List<ReminderNotificationEntity>> =
        notificationDao.getNotificationsForReminder(reminderId)

    suspend fun getNotificationsOnce(reminderId: Long): List<ReminderNotificationEntity> =
        notificationDao.getNotificationsOnce(reminderId)

    suspend fun getAllPendingNotifications(): List<ReminderNotificationEntity> =
        notificationDao.getAllPendingNotifications()

    suspend fun insertNotification(notification: ReminderNotificationEntity): Long =
        notificationDao.insertNotification(notification)

    suspend fun insertNotifications(notifications: List<ReminderNotificationEntity>): List<Long> =
        notificationDao.insertAll(notifications)

    /** 插入單筆通知，若 triggerAt 與現有待觸發通知衝突，自動往後延 1 分鐘直到空位。回傳含 id 與調整後時間的 entity。 */
    suspend fun insertNotificationDeduped(notification: ReminderNotificationEntity): ReminderNotificationEntity {
        val occupiedTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        var adjustedTime = notification.triggerAt
        while (occupiedTimes.contains(adjustedTime)) {
            adjustedTime += 60_000L
        }
        val adjusted = notification.copy(triggerAt = adjustedTime)
        val id = notificationDao.insertNotification(adjusted)
        return adjusted.copy(id = id)
    }

    /** 批次插入通知，每筆都做衝突避讓（同批內也互相避讓）。回傳含 id 與調整後時間的 entity 清單。 */
    suspend fun insertNotificationsDeduped(notifications: List<ReminderNotificationEntity>): List<ReminderNotificationEntity> {
        val occupiedTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        return notifications.map { notification ->
            var adjustedTime = notification.triggerAt
            while (occupiedTimes.contains(adjustedTime)) {
                adjustedTime += 60_000L
            }
            occupiedTimes.add(adjustedTime)
            val adjusted = notification.copy(triggerAt = adjustedTime)
            val id = notificationDao.insertNotification(adjusted)
            adjusted.copy(id = id)
        }
    }

    suspend fun markNotificationFired(id: Long) =
        notificationDao.markFired(id)

    suspend fun deleteNotificationsForReminder(reminderId: Long) =
        notificationDao.deleteNotificationsForReminder(reminderId)
}
