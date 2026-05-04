package com.rendy.classnote.ui.classrecord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.rendy.classnote.R
import com.rendy.classnote.ui.MainActivity
import java.io.File

class AudioRecordService : Service() {

    companion object {
        const val ACTION_STOP_RECORD = "com.rendy.classnote.ACTION_STOP_RECORD"
        const val CHANNEL_ID = "recording"
        const val NOTIF_ID = 9901
    }

    inner class RecordBinder : Binder() {
        fun getService(): AudioRecordService = this@AudioRecordService
    }

    private val binder = RecordBinder()

    var isRecording = false; private set
    var savedAudioPath: String? = null; private set
    var savedDurationMs: Long = 0L; private set
    var elapsedMs: Long = 0L; private set

    var onStateChanged: (() -> Unit)? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var recordingStartMs: Long = 0L

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            elapsedMs = System.currentTimeMillis() - recordingStartMs
            updateNotification()
            onStateChanged?.invoke()
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RECORD) {
            stopRecording()
            onStateChanged?.invoke()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        if (isRecording) stopRecording()
        super.onDestroy()
    }

    fun startRecording() {
        val audioDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "ClassNote")
        audioDir.mkdirs()
        currentAudioFile = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentAudioFile!!.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        recordingStartMs = System.currentTimeMillis()
        elapsedMs = 0L
        savedAudioPath = null
        savedDurationMs = 0L
        startForeground(NOTIF_ID, buildNotification("00:00"))
        timerHandler.post(timerRunnable)
    }

    fun stopRecording() {
        timerHandler.removeCallbacks(timerRunnable)
        savedDurationMs = System.currentTimeMillis() - recordingStartMs
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        savedAudioPath = currentAudioFile?.absolutePath
        currentAudioFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val elapsed = elapsedMs / 1000
        val timeStr = "%02d:%02d".format(elapsed / 60, elapsed % 60)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(timeStr))
    }

    private fun buildNotification(timeStr: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, AudioRecordService::class.java).apply { action = ACTION_STOP_RECORD },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("錄音中")
            .setContentText(timeStr)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(R.drawable.ic_alarm, "停止", stopPi)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "錄音", NotificationManager.IMPORTANCE_LOW).apply {
            description = "上課錄音進行中"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
}
