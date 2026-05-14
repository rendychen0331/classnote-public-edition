package com.rendy.classnote.ui.classrecord

import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MatR
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.FeatureManager
import com.rendy.classnote.databinding.FragmentClassRecordSummaryBinding
import com.rendy.classnote.databinding.ItemChatBubbleBinding
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

class ClassRecordSummaryFragment : Fragment() {

    private var _binding: FragmentClassRecordSummaryBinding? = null
    private val binding get() = _binding!!

    private val chatMessageDiffCallback = object : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old === new
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
    }

    private val args: ClassRecordSummaryFragmentArgs by navArgs()
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon

    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClassRecordSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        markwon = Markwon.create(requireContext())
        binding.tvSummarySession.text = args.sessionLabel

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

        when {
            args.summary.isNotBlank() -> addMessage(ChatMessage(args.summary, isUser = false))
            args.recordIds.isNotBlank() -> loadOrGenerateSummary()
        }
    }

    private fun loadOrGenerateSummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cached = viewModel.getSessionSummary(args.sessionLabel)
            if (!cached.isNullOrBlank()) {
                addMessage(ChatMessage(cached, isUser = false))
                return@launch
            }
            generateSummary()
        }
    }

    private fun generateSummary() {
        val prefs = AppPreferences(requireContext())
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            addMessage(ChatMessage("請先在設定頁輸入 Gemini API Key", isUser = false))
            return
        }
        val ai = FeatureManager.getAi(requireContext()) ?: run {
            addMessage(ChatMessage("請先下載 AI 功能模組", isUser = false))
            return
        }

        val recordIds = args.recordIds.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (recordIds.isEmpty()) return

        binding.progressChat.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val contentParts = mutableListOf<String>()
            for (id in recordIds) {
                val record = viewModel.getById(id) ?: continue
                if (record.textNote.isNotBlank()) {
                    contentParts.add("【文字筆記】\n${record.textNote}")
                }
                val mediaList = viewModel.getMediaOnce(id)
                for (audio in mediaList.filter { it.type == "audio" }) {
                    if (record.aiSummary.isNotBlank()) {
                        contentParts.add("【錄音摘要】\n${record.aiSummary}")
                    } else {
                        ai.summarizeAudio(apiKey, audio.filePath)?.takeIf { it.isNotBlank() }
                            ?.let { contentParts.add("【錄音摘要】\n$it") }
                    }
                }
                for (photo in mediaList.filter { it.type == "photo" || it.type == "drawing" }) {
                    val label = if (photo.type == "drawing") "手繪內容" else "照片內容"
                    if (photo.aiSummary.isNotBlank()) {
                        contentParts.add("【$label】\n${photo.aiSummary}")
                    } else {
                        ai.summarizePhoto(apiKey, photo.filePath)?.takeIf { it.isNotBlank() }
                            ?.let {
                                viewModel.updateMediaAiSummary(photo.id, it)
                                contentParts.add("【$label】\n$it")
                            }
                    }
                }
            }

            if (contentParts.isEmpty()) {
                binding.progressChat.visibility = View.GONE
                binding.btnSend.isEnabled = true
                addMessage(ChatMessage("這堂課沒有可總結的內容（文字、錄音、照片）", isUser = false))
                return@launch
            }

            val summary = ai.summarizeSession(apiKey, contentParts.joinToString("\n\n"))
            binding.progressChat.visibility = View.GONE
            binding.btnSend.isEnabled = true

            if (summary.isNullOrBlank()) {
                addMessage(ChatMessage("總結失敗，請稍後再試", isUser = false))
            } else {
                viewModel.saveSessionSummary(args.sessionLabel, summary)
                addMessage(ChatMessage(summary, isUser = false))
            }
        }
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
            binding.chipGemini        to ("gemini"           to (prefs.geminiApiKey   to prefs.geminiEnabled)),
            binding.chipMimo          to ("mimo"             to (prefs.mimoApiKey     to prefs.mimoEnabled)),
            binding.chipClaude        to ("claude"           to (prefs.claudeApiKey   to prefs.claudeEnabled)),
            binding.chipOpenai        to ("openai"           to (prefs.openaiApiKey   to prefs.openaiEnabled)),
            binding.chipGroq          to ("groq"             to (prefs.groqApiKey     to prefs.groqEnabled)),
            binding.chipDeepseek      to ("deepseek"         to (prefs.deepseekApiKey to prefs.deepseekEnabled)),
            binding.chipCustomAnthropic to ("custom-anthropic" to (customAnthropicKey to customAnthropicActive)),
            binding.chipCustomOpenai    to ("custom-openai"    to (customOpenaiKey    to customOpenaiActive))
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
            val provider = chipMap.entries.firstOrNull { it.key.id == id }?.value?.first ?: return@setOnCheckedStateChangeListener
            prefs.preferredChatProvider = provider
        }
    }

    private fun selectedProvider(): String {
        val checkedId = binding.chipGroupProvider.checkedChipId
        return when (checkedId) {
            R.id.chipMimo           -> "mimo"
            R.id.chipClaude         -> "claude"
            R.id.chipOpenai         -> "openai"
            R.id.chipGroq           -> "groq"
            R.id.chipDeepseek       -> "deepseek"
            R.id.chipCustomAnthropic -> "custom-anthropic"
            R.id.chipCustomOpenai    -> "custom-openai"
            else                     -> "gemini"
        }
    }

    private fun currentSummaryContext(): String =
        messages.firstOrNull { !it.isUser }?.text ?: args.summary

    private fun sendMessage() {
        val text = binding.etChatInput.text?.toString()?.trim() ?: return
        if (text.isBlank()) return

        val prefs = AppPreferences(requireContext())
        val provider = selectedProvider()
        val apiKey = when (provider) {
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
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "請先在設定頁輸入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        binding.etChatInput.text?.clear()
        addMessage(ChatMessage(text, isUser = true))
        binding.progressChat.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        val noteContext = currentSummaryContext()
        val history = messages.dropLast(1).drop(1).map { it.text to it.isUser }

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

    // ── Adapter ──────────────────────────────────────────────────────────────

    inner class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.BubbleViewHolder>(chatMessageDiffCallback) {

        inner class BubbleViewHolder(private val binding: ItemChatBubbleBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(msg: ChatMessage) {
                if (msg.isUser) {
                    binding.tvBubbleText.text = msg.text
                } else {
                    markwon.setMarkdown(binding.tvBubbleText, msg.text)
                }
                val density = resources.displayMetrics.density
                val margin48 = (48 * density).toInt()
                val flp = binding.cardBubble.layoutParams as FrameLayout.LayoutParams

                if (msg.isUser) {
                    flp.gravity = Gravity.END
                    flp.marginStart = margin48
                    flp.marginEnd = 0
                    binding.cardBubble.setCardBackgroundColor(resolveAttrColor(MatR.attr.colorPrimaryContainer))
                } else {
                    flp.gravity = Gravity.START
                    flp.marginStart = 0
                    flp.marginEnd = margin48
                    binding.cardBubble.setCardBackgroundColor(resolveAttrColor(MatR.attr.colorSurfaceContainer))
                }
                binding.cardBubble.layoutParams = flp
            }

            private fun resolveAttrColor(attr: Int): Int {
                val tv = TypedValue()
                binding.cardBubble.context.theme.resolveAttribute(attr, tv, true)
                return tv.data
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            BubbleViewHolder(
                ItemChatBubbleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        override fun onBindViewHolder(holder: BubbleViewHolder, position: Int) =
            holder.bind(getItem(position))
    }
}
