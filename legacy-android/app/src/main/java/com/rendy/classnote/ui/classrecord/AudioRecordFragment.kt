package com.rendy.classnote.ui.classrecord

import android.Manifest
import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.databinding.FragmentAudioRecordBinding
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AudioRecordFragment : Fragment() {

    private var _binding: FragmentAudioRecordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var selectedDate: String = LocalDate.now().format(dateFormatter)

    // Service binding
    private var audioService: AudioRecordService? = null
    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            audioService = (service as AudioRecordService.RecordBinder).getService()
            isBound = true
            audioService?.onStateChanged = { requireActivity().runOnUiThread { updateUiFromService() } }
            updateUiFromService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            audioService = null
            isBound = false
        }
    }

    // Photos
    private var pendingPhotoPath: String? = null
    private val photoPathList = mutableListOf<String>()

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doStartRecording()
        else Toast.makeText(requireContext(), "需要錄音權限", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (success && path != null) {
            photoPathList.add(path)
            addPhotoThumbnail(path)
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(requireContext(), "需要相機權限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAudioRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAudioDate.text = selectedDate
        binding.tvAudioDate.setOnClickListener { pickDate() }
        binding.tilAudioTimeLabel.setEndIconOnClickListener { pickTime() }

        val t = java.time.LocalTime.now()
        val label = ClassPeriodUtils.detect(t.hour, t.minute)
            ?: t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        binding.etAudioTimeLabel.setText(label)

        binding.btnRecord.setOnClickListener {
            val svc = audioService
            if (svc != null && svc.isRecording) doStopRecording()
            else checkPermissionAndRecord()
        }
        binding.btnTakePhoto.setOnClickListener { checkCameraAndLaunch() }
        binding.btnAudioCancel.setOnClickListener { cancelAndPop() }
        binding.btnAudioSave.setOnClickListener { saveRecord() }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), AudioRecordService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            audioService?.onStateChanged = null
            requireContext().unbindService(serviceConnection)
            isBound = false
            audioService = null
        }
    }

    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            doStartRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun doStartRecording() {
        val svc = audioService ?: return
        ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), AudioRecordService::class.java))
        svc.startRecording()
        updateUiFromService()
    }

    private fun doStopRecording() {
        audioService?.stopRecording()
        updateUiFromService()
    }

    private fun cancelAndPop() {
        val svc = audioService
        if (svc != null && svc.isRecording) {
            svc.stopRecording()
            svc.savedAudioPath?.let { File(it).delete() }
        }
        requireContext().stopService(Intent(requireContext(), AudioRecordService::class.java))
        findNavController().popBackStack()
    }

    private fun updateUiFromService() {
        val svc = audioService ?: return
        if (svc.isRecording) {
            val elapsed = svc.elapsedMs / 1000
            binding.tvTimer.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
            binding.tvRecordingStatus.text = "錄音中..."
            binding.btnRecord.setIconResource(android.R.drawable.ic_media_pause)
            binding.btnAudioSave.isEnabled = false
        } else if (svc.savedAudioPath != null) {
            val elapsed = svc.savedDurationMs / 1000
            binding.tvTimer.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
            binding.tvRecordingStatus.text = "錄音完成，點擊儲存"
            binding.btnRecord.setIconResource(android.R.drawable.ic_btn_speak_now)
            binding.btnAudioSave.isEnabled = true
        }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day).format(dateFormatter)
            binding.tvAudioDate.text = selectedDate
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        val cal = Calendar.getInstance()
        android.app.TimePickerDialog(requireContext(), { _, hour, minute ->
            val timeStr = "%02d:%02d".format(hour, minute)
            binding.etAudioTimeLabel.setText(timeStr)
            binding.etAudioTimeLabel.setSelection(timeStr.length)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun checkCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoDir = File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "ClassNote")
        photoDir.mkdirs()
        val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
        pendingPhotoPath = photoFile.absolutePath
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", photoFile
        )
        takePictureLauncher.launch(uri)
    }

    private fun addPhotoThumbnail(path: String) {
        binding.scrollAudioPhotos.visibility = View.VISIBLE
        val sizePx = (60 * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return
        val iv = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = marginPx }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bmp)
        }
        binding.layoutAudioPhotos.addView(iv)
    }

    private fun saveRecord() {
        val svc = audioService
        val audioPath = svc?.savedAudioPath ?: run {
            Toast.makeText(requireContext(), "請先錄音", Toast.LENGTH_SHORT).show()
            return
        }
        val record = ClassRecordEntity(
            date = selectedDate,
            timeLabel = binding.etAudioTimeLabel.text.toString().trim()
        )
        val media = mutableListOf(
            ClassRecordMediaEntity(recordId = 0, type = "audio", filePath = audioPath, durationMs = svc.savedDurationMs)
        )
        photoPathList.forEach { path ->
            media.add(ClassRecordMediaEntity(recordId = 0, type = "photo", filePath = path))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.save(record, media)
            requireContext().stopService(Intent(requireContext(), AudioRecordService::class.java))
            if (isAdded) findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
