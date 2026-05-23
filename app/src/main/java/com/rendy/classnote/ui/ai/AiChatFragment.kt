package com.rendy.classnote.ui.ai

import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MatR
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.databinding.FragmentAiChatBinding
import com.rendy.classnote.databinding.ItemChatBubbleBinding
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.ui.classrecord.ChatMessage
import com.rendy.classnote.ui.classrecord.ClassRecordViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AiChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private val chatMessageDiffCallback = object : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old === new
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
    }

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon
    private var noteContext: String = ""

    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        markwon = Markwon.create(requireContext())

        chatAdapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChat.adapter = chatAdapter

        setupProviderChips()

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        binding.btnPickNote.setOnClickListener { showNotePickerDialog() }
        binding.btnClearContext.setOnClickListener { clearNoteContext() }
    }

    private fun setupProviderChips() {
        val prefs = AppPreferences(requireContext())
        val customAnthropicKey = AppPreferences.encodeCustomKey(
            prefs.customAnthropicEndpoint, prefs.customAnthropicModel, prefs.customAnthropicKey
        )
        val customAnthropicActive = prefs.customAnthropicEnabled &&
            prefs.customAnthropicEndpoint.isNotBlank() && prefs.customAnthropicModel.isNotBlank() && prefs.customAnthropicKey.isNotBlank()
        val customOpenaiKey = AppPreferences.encodeCustomKey(
            prefs.customOpenaiEndpoint, prefs.customOpenaiModel, prefs.customOpenaiKey
        )
        val customOpenaiActive = prefs.customOpenaiEnabled &&
            prefs.customOpenaiEndpoint.isNotBlank() && prefs.customOpenaiModel.isNotBlank()

        val chipMap = mapOf(
            binding.chipGemini          to ("gemini"           to (prefs.geminiApiKey   to prefs.geminiEnabled)),
            binding.chipMimo            to ("mimo"             to (prefs.mimoApiKey     to prefs.mimoEnabled)),
            binding.chipClaude          to ("claude"           to (prefs.claudeApiKey   to prefs.claudeEnabled)),
            binding.chipOpenai          to ("openai"           to (prefs.openaiApiKey   to prefs.openaiEnabled)),
            binding.chipGroq            to ("groq"             to (prefs.groqApiKey     to prefs.groqEnabled)),
            binding.chipDeepseek        to ("deepseek"         to (prefs.deepseekApiKey to prefs.deepseekEnabled)),
            binding.chipCustomAnthropic to ("custom-anthropic" to (customAnthropicKey   to customAnthropicActive)),
            binding.chipCustomOpenai    to ("custom-openai"    to (customOpenaiKey      to customOpenaiActive))
        )

        chipMap.forEach { (chip, pair) ->
            val (_, keyAndEnabled) = pair
            val active = keyAndEnabled.first.isNotBlank() && keyAndEnabled.second
            chip.isEnabled = active
            chip.alpha = if (active) 1f else 0.4f
        }

        val preferred = prefs.preferredChatProvider
        val preferredChip = chipMap.entries.firstOrNull { it.value.first == preferred }?.key
        val firstAvailable = chipMap.entries.firstOrNull { it.key.isEnabled }?.key
        (preferredChip?.takeIf { it.isEnabled } ?: firstAvailable)?.isChecked = true

        binding.chipGroupProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val provider = chipMap.entries.firstOrNull { it.key.id == id }?.value?.first
                ?: return@setOnCheckedStateChangeListener
            prefs.preferredChatProvider = provider
        }
    }

    private fun selectedProvider(): String = when (binding.chipGroupProvider.checkedChipId) {
        R.id.chipMimo            -> "mimo"
        R.id.chipClaude          -> "claude"
        R.id.chipOpenai          -> "openai"
        R.id.chipGroq            -> "groq"
        R.id.chipDeepseek        -> "deepseek"
        R.id.chipCustomAnthropic -> "custom-anthropic"
        R.id.chipCustomOpenai    -> "custom-openai"
        else                     -> "gemini"
    }

    private fun apiKeyForProvider(provider: String): String {
        val prefs = AppPreferences(requireContext())
        return when (provider) {
            "mimo"             -> prefs.mimoApiKey
            "claude"           -> prefs.claudeApiKey
            "openai"           -> prefs.openaiApiKey
            "groq"             -> prefs.groqApiKey
            "deepseek"         -> prefs.deepseekApiKey
            "custom-anthropic" -> AppPreferences.encodeCustomKey(
                prefs.customAnthropicEndpoint, prefs.customAnthropicModel, prefs.customAnthropicKey
            )
            "custom-openai"    -> AppPreferences.encodeCustomKey(
                prefs.customOpenaiEndpoint, prefs.customOpenaiModel, prefs.customOpenaiKey
            )
            else               -> prefs.geminiApiKey
        }
    }

    private fun showNotePickerDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val records = viewModel.records.first()
            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "尚無上課紀錄", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = records.map { r: ClassRecordEntity ->
                val title = r.title.ifBlank { r.date.ifBlank { "未命名" } }
                val timeLabel = r.timeLabel.ifBlank { "" }
                if (timeLabel.isNotBlank()) "$title｜$timeLabel" else title
            }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("選擇筆記作為 AI 背景")
                .setItems(labels) { _, idx ->
                    loadRecordAsContext(records[idx].id, labels[idx])
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadRecordAsContext(recordId: Long, label: String) {
        val ai = FeatureManager.getAi(requireContext()) ?: run {
            Toast.makeText(requireContext(), "請先下載 AI 功能模組", Toast.LENGTH_SHORT).show()
            return
        }
        val provider = selectedProvider()
        val apiKey = apiKeyForProvider(provider)
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "請先在設定頁輸入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressChat.visibility = View.VISIBLE
        binding.btnPickNote.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val record = viewModel.getById(recordId)
            val contentParts = mutableListOf<String>()

            record?.let {
                if (it.textNote.isNotBlank()) contentParts.add("【文字筆記】\n${it.textNote}")
                val mediaList = viewModel.getMediaOnce(recordId)
                for (audio in mediaList.filter { m -> m.type == "audio" }) {
                    if (record.aiSummary.isNotBlank()) {
                        contentParts.add("【錄音摘要】\n${record.aiSummary}")
                    } else {
                        ai.summarizeAudio(provider, apiKey, audio.filePath)
                            ?.takeIf { s -> s.isNotBlank() }
                            ?.let { s -> contentParts.add("【錄音摘要】\n$s") }
                    }
                }
                for (media in mediaList.filter { m -> m.type == "photo" || m.type == "drawing" }) {
                    val heading = if (media.type == "drawing") "手繪內容" else "照片內容"
                    if (media.aiSummary.isNotBlank()) {
                        contentParts.add("【$heading】\n${media.aiSummary}")
                    } else {
                        ai.summarizePhoto(provider, apiKey, media.filePath)
                            ?.takeIf { s -> s.isNotBlank() }
                            ?.let { s ->
                                viewModel.updateMediaAiSummary(media.id, s)
                                contentParts.add("【$heading】\n$s")
                            }
                    }
                }
            }

            binding.progressChat.visibility = View.GONE
            binding.btnPickNote.isEnabled = true

            if (contentParts.isEmpty()) {
                Toast.makeText(requireContext(), "這份筆記沒有可讀取的內容", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val summary = ai.summarizeSession(provider, apiKey, contentParts.joinToString("\n\n"))
            if (summary.isNullOrBlank()) {
                Toast.makeText(requireContext(), "總結失敗，請稍後再試", Toast.LENGTH_SHORT).show()
                return@launch
            }

            noteContext = summary
            binding.tvNoteContextLabel.text = label
            binding.rowNoteContext.visibility = View.VISIBLE
            messages.clear()
            chatAdapter.submitList(emptyList())
            addMessage(ChatMessage(summary, isUser = false))
        }
    }

    private fun clearNoteContext() {
        noteContext = ""
        binding.rowNoteContext.visibility = View.GONE
        messages.clear()
        chatAdapter.submitList(emptyList())
    }

    private fun sendMessage() {
        val text = binding.etChatInput.text?.toString()?.trim() ?: return
        if (text.isBlank()) return

        val provider = selectedProvider()
        val apiKey = apiKeyForProvider(provider)
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "請先在設定頁輸入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        binding.etChatInput.text?.clear()
        addMessage(ChatMessage(text, isUser = true))
        binding.progressChat.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        val history = messages.dropLast(1).map { it.text to it.isUser }

        viewLifecycleOwner.lifecycleScope.launch {
            val reply = FeatureManager.getAi(requireContext())
                ?.chatWithContext(provider, apiKey, noteContext, history, text)
            binding.progressChat.visibility = View.GONE
            binding.btnSend.isEnabled = true

            if (reply.isNullOrBlank()) {
                Toast.makeText(requireContext(), "回覆失敗，請稍後再試", Toast.LENGTH_SHORT).show()
            } else {
                addMessage(ChatMessage(reply, isUser = false))
            }
        }
    }

    private fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        chatAdapter.submitList(messages.toList())
        binding.rvChat.post { binding.rvChat.scrollToPosition(messages.size - 1) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.BubbleViewHolder>(chatMessageDiffCallback) {

        inner class BubbleViewHolder(private val b: ItemChatBubbleBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(msg: ChatMessage) {
                if (msg.isUser) b.tvBubbleText.text = msg.text
                else markwon.setMarkdown(b.tvBubbleText, msg.text)

                val margin48 = (48 * resources.displayMetrics.density).toInt()
                val flp = b.cardBubble.layoutParams as FrameLayout.LayoutParams
                if (msg.isUser) {
                    flp.gravity = Gravity.END
                    flp.marginStart = margin48
                    flp.marginEnd = 0
                    b.cardBubble.setCardBackgroundColor(resolveColor(MatR.attr.colorPrimaryContainer))
                } else {
                    flp.gravity = Gravity.START
                    flp.marginStart = 0
                    flp.marginEnd = margin48
                    b.cardBubble.setCardBackgroundColor(resolveColor(MatR.attr.colorSurfaceContainer))
                }
                b.cardBubble.layoutParams = flp
            }

            private fun resolveColor(attr: Int): Int {
                val tv = TypedValue()
                b.cardBubble.context.theme.resolveAttribute(attr, tv, true)
                return tv.data
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            BubbleViewHolder(ItemChatBubbleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: BubbleViewHolder, position: Int) =
            holder.bind(getItem(position))
    }
}
