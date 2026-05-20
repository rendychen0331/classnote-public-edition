package com.rendy.classnote.data

import com.rendy.classnote.data.local.dao.ErrorLogDao
import com.rendy.classnote.data.local.entity.ErrorLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ErrorLogger {

    private var dao: ErrorLogDao? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(errorLogDao: ErrorLogDao) {
        dao = errorLogDao
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                runBlocking(Dispatchers.IO) {
                    errorLogDao.insert(
                        ErrorLogEntity(
                            tag = "CRASH[${thread.name}]",
                            message = throwable.message ?: throwable.javaClass.simpleName,
                            stacktrace = throwable.stackTraceToString().take(3000)
                        )
                    )
                }
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val d = dao ?: return
        CoroutineScope(Dispatchers.IO).launch {
            d.insert(
                ErrorLogEntity(
                    tag = tag,
                    message = message,
                    stacktrace = throwable?.stackTraceToString()?.take(3000) ?: ""
                )
            )
            d.pruneOldLogs()
        }
    }
}
