package com.rendy.classnote

import android.app.Application
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.repository.CourseRepository
import com.rendy.classnote.data.repository.ReminderRepository
import com.rendy.classnote.notification.NotificationHelper

class ClassNoteApplication : Application() {

    val database by lazy { ClassNoteDatabase.getDatabase(this) }

    val courseRepository by lazy {
        CourseRepository(
            courseDao = database.courseDao(),
            overrideDao = database.courseOverrideDao(),
            periodTimeDao = database.periodTimeDao()
        )
    }

    val reminderRepository by lazy {
        ReminderRepository(
            reminderDao = database.reminderDao(),
            notificationDao = database.reminderNotificationDao()
        )
    }

    val appPreferences by lazy { AppPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
