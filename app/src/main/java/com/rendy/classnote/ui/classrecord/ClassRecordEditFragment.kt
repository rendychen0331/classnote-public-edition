package com.rendy.classnote.ui.classrecord

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.databinding.FragmentClassRecordEditBinding
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class ClassRecordEditFragment : Fragment() {

    private var _binding: FragmentClassRecordEditBinding? = null
    private val binding get() = _binding!!

    private val args: ClassRecordEditFragmentArgs by navArgs()
    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var selectedDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private var currentRecordId: Long = -1L

    // Existing media loaded from DB (read-only display)
    private val existingMediaItems = mutableListOf<ClassRecordMediaEntity>()

    // New media added this session
    private val newPhotoPaths = mutableListOf<String>()
    private val newAudioItems = mutableListOf<Pair<String, Long>>() // filePath to durationMs

    // Audio recording
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null
    private var recordingStartMs: Long = 0L

    // Camera
    private var pendingPhotoPath: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoPath != null) {
            newPhotoPaths.add(pendingPhotoPath!!)
            addPhotoThumbnail(pendingPhotoPath!!)
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(requireContext(), "需要相機權限", Toast.LENGTH_SHORT).show()
    }

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(requireContext(), "需要錄音權限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassRecordEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvRecordDate.text = selectedDate
        binding.tvRecordDate.setOnClickListener { pickDate() }
        binding.btnTakePhoto.setOnClickListener { checkCameraAndLaunch() }
        binding.btnRecord.setOnClickListener { toggleRecording() }
        binding.btnAiSummary.setOnClickListener { runAiSummary() }
        binding.btnSaveRecord.setOnClickListener { saveRecord() }

        if (args.recordId > 0) {
            currentRecordId = args.recordId
            loadRecord(args.recordId)
        }
    }

    private fun loadRecord(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ClassNoteApplication
            val record = viewModel.getById(id) ?: return@launch
            selectedDate = record.date
            binding.tvRecordDate.text = record.date
            binding.etTimeLabel.setText(record.timeLabel)
            binding.etTextNote.setText(record.textNote)
            if (record.aiSummary.isNotBlank()) {
                binding.tvAiSummary.text = record.aiSummary
                binding.cardAiSummary.visibility = View.VISIBLE
            }
            val mediaItems = app.classRecordRepository.getMediaForRecordOnce(id)
            existingMediaItems.addAll(mediaItems)
            mediaItems.filter { it.type == "photo" }.forEach { addPhotoThumbnail(it.filePath) }
            mediaItems.filter { it.type == "audio" }.forEach { addAudioRow(it.filePath, it.durationMs) }
            updateAiButton()
        }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day).format(dateFormatter)
            binding.tvRecordDate.text = selectedDate
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun checkCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ClassNote")
        photoDir.mkdirs()
        val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
        pendingPhotoPath = photoFile.absolutePath
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", photoFile
        )
        takePictureLauncher.launch(uri)
    }

    private fun addPhotoThumbnail(path: String) {
        val sizePx = (80 * resources.displayMetrics.density).toInt()
        val iv = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap != null) iv.setImageBitmap(bitmap)
        binding.layoutPhotos.addView(iv)
        binding.scrollPhotos.visibility = View.VISIBLE
    }

    private fun toggleRecording() {
        if (isRecording) stopRecording() else checkAudioAndRecord()
    }

    private fun checkAudioAndRecord() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val audioDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "ClassNote")
        audioDir.mkdirs()
        currentAudioFile = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
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
        binding.btnRecord.text = getString(R.string.class_record_stop_audio)
    }

    private fun stopRecording() {
        val durationMs = System.currentTimeMillis() - recordingStartMs
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        binding.btnRecord.text = getString(R.string.class_record_record_audio)
        currentAudioFile?.let { file ->
            newAudioItems.add(Pair(file.absolutePath, durationMs))
            addAudioRow(file.absolutePath, durationMs)
            updateAiButton()
        }
        currentAudioFile = null
    }

    private fun addAudioRow(path: String, durationMs: Long) {
        val mins = durationMs / 60000
        val secs = (durationMs % 60000) / 1000
        val tv = TextView(requireContext()).apply {
            text = "🎙  ${File(path).name}  (${mins}:${"%02d".format(secs)})"
            textSize = 13f
            setTextColor(resources.getColor(android.R.color.tab_indicator_text, requireContext().theme))
            val padV = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, padV, 0, padV)
        }
        binding.layoutRecordings.addView(tv)
    }

    private fun updateAiButton() {
        val hasAudio = existingMediaItems.any { it.type == "audio" } || newAudioItems.isNotEmpty()
        binding.btnAiSummary.visibility = if (hasAudio) View.VISIBLE else View.GONE
    }

    private fun runAiSummary() {
        val audioPath = existingMediaItems.firstOrNull { it.type == "audio" }?.filePath
            ?: newAudioItems.firstOrNull()?.first ?: return

        val apiKey = (requireActivity().application as ClassNoteApplication).appPreferences.geminiApiKey
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "請先在設定中輸入 Gemini API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(audioPath)
        if (file.length() > 15 * 1024 * 1024L) {
            Toast.makeText(requireContext(), "錄音檔案過大（> 15 MB），請分段錄音", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnAiSummary.isEnabled = false
        binding.btnAiSummary.text = getString(R.string.class_record_summarizing)

        viewLifecycleOwner.lifecycleScope.launch {
            val summary = GeminiApi.summarizeAudio(apiKey, audioPath)
            if (summary != null) {
                binding.tvAiSummary.text = summary
                binding.cardAiSummary.visibility = View.VISIBLE
            } else {
                Toast.makeText(requireContext(), "AI 總結失敗，請稍後再試", Toast.LENGTH_SHORT).show()
            }
            binding.btnAiSummary.isEnabled = true
            binding.btnAiSummary.text = getString(R.string.class_record_ai_summary)
        }
    }

    private fun saveRecord() {
        val record = ClassRecordEntity(
            id = if (currentRecordId > 0) currentRecordId else 0,
            date = selectedDate,
            timeLabel = binding.etTimeLabel.text.toString().trim(),
            textNote = binding.etTextNote.text.toString().trim(),
            aiSummary = binding.tvAiSummary.text.toString().trim()
        )

        val newMediaItems = newPhotoPaths.map {
            ClassRecordMediaEntity(recordId = 0, type = "photo", filePath = it)
        } + newAudioItems.map { (path, duration) ->
            ClassRecordMediaEntity(recordId = 0, type = "audio", filePath = path, durationMs = duration)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.save(record, newMediaItems)
            if (isAdded) findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRecording) stopRecording()
        _binding = null
    }
}
