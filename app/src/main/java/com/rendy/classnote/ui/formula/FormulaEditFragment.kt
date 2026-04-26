package com.rendy.classnote.ui.formula

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.local.entity.FormulaEntity
import com.rendy.classnote.databinding.FragmentFormulaEditBinding
import kotlinx.coroutines.launch

class FormulaEditFragment : Fragment() {

    private var _binding: FragmentFormulaEditBinding? = null
    private val binding get() = _binding!!

    private val args: FormulaEditFragmentArgs by navArgs()
    private val viewModel: FormulaViewModel by viewModels {
        FormulaViewModel.Factory((requireActivity().application as ClassNoteApplication).formulaRepository)
    }

    private var existingFormula: FormulaEntity? = null
    private var currentLatex: String = ""
    private var editorReady = false
    private val keyboardPages = mutableListOf<View>()

    private var clrPrimary = 0
    private var clrError = 0
    private var clrSecondary = 0

    // ── 按鍵動作 ──────────────────────────────────────────────────────────────

    private sealed class KA {
        data class Wrt(val s: String) : KA()   // field.write()
        data class Cmd(val s: String) : KA()   // field.cmd()
        data class Ks(val s: String) : KA()    // field.keystroke()
    }

    private enum class KS { NUM, OP, STRUCT, NAV, BACK }

    private data class Key(val lbl: String, val action: KA, val style: KS = KS.OP)

    // 按鍵定義快捷語法
    private fun n(l: String, s: String) = Key(l, KA.Wrt(s), KS.NUM)
    private fun o(l: String, s: String) = Key(l, KA.Wrt(s), KS.OP)
    private fun st(l: String, s: String) = Key(l, KA.Wrt(s), KS.STRUCT)
    private fun kcmd(l: String, s: String) = Key(l, KA.Cmd(s), KS.STRUCT)
    private val bk get() = Key("⌫", KA.Ks("Backspace"), KS.BACK)
    private val lf get() = Key("←", KA.Ks("Left"), KS.NAV)
    private val rt get() = Key("→", KA.Ks("Right"), KS.NAV)

    // ── 鍵盤頁定義 ────────────────────────────────────────────────────────────

    // 基本（數字 + 運算子 + 結構）
    private val basicKeys get() = listOf(
        n("7","7"),        n("8","8"),        n("9","9"),        o("÷","\\div "),     bk,
        n("4","4"),        n("5","5"),        n("6","6"),        o("×","\\times "),   kcmd("√","\\sqrt"),
        n("1","1"),        n("2","2"),        n("3","3"),        o("−","-"),           kcmd("^","^"),
        n("0","0"),        o(".","."),        o("=","="),        o("+","+"),           kcmd("a/b","\\frac"),
        lf,                rt,                kcmd("( )","("),   o(",",","),           st("ⁿ√","\\sqrt[n]{}")
    )

    // 函數（三角、對數、微積分）
    private val functionKeys get() = listOf(
        st("sin","\\sin"),        st("cos","\\cos"),        st("tan","\\tan"),        kcmd("|x|","|"),     bk,
        st("sin⁻¹","\\sin^{-1}"),st("cos⁻¹","\\cos^{-1}"),st("tan⁻¹","\\tan^{-1}"),kcmd("( )","("),    kcmd("^","^"),
        st("ln","\\ln"),          st("log","\\log"),         n("e","e"),               o("x!","!"),         o("∞","\\infty "),
        st("∫","\\int_{}^{}"),    st("Σ","\\sum_{}^{}"),     st("∏","\\prod_{}^{}"),   st("lim","\\lim_{}"), st("d/dx","\\frac{d}{dx}"),
        lf,                       rt,                         kcmd("x̄","\\bar"),       kcmd("x⃗","\\vec"),  kcmd("x̂","\\hat")
    )

    // 希臘字母 + 關係符號
    private val greekKeys get() = listOf(
        o("α","\\alpha "),  o("β","\\beta "),  o("γ","\\gamma "), o("δ","\\delta "), o("ε","\\epsilon "),
        o("θ","\\theta "),  o("λ","\\lambda "), o("μ","\\mu "),   o("π","\\pi "),    o("σ","\\sigma "),
        o("τ","\\tau "),    o("φ","\\phi "),    o("ω","\\omega "), o("Γ","\\Gamma "), o("Δ","\\Delta "),
        o("Σ","\\Sigma "),  o("Ω","\\Omega "),  o("≤","\\leq "),  o("≥","\\geq "),   o("≠","\\neq "),
        o("≈","\\approx "), o("∞","\\infty "),  o("∈","\\in "),   o("∉","\\notin "), bk
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFormulaEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        clrPrimary   = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        clrError     = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorError, Color.RED)
        clrSecondary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSecondary, Color.GRAY)

        setupEditor()
        setupKeyboard()
        loadExistingOrNew()

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener {
            existingFormula?.let { formula ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.confirm_delete)
                    .setMessage(R.string.confirm_delete_message)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        viewModel.delete(formula)
                        findNavController().navigateUp()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    // ── 公式編輯器 WebView ────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEditor() {
        val wv = binding.webViewEditor
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        wv.addJavascriptInterface(MathInterface(), "Android")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                editorReady = true
                view?.evaluateJavascript("suppressKeyboard()", null)
                existingFormula?.let { f ->
                    view?.evaluateJavascript("setLatex('${esc(f.latex)}')", null)
                }
            }
        }
        wv.loadUrl("file:///android_asset/mathquill/editor.html")
    }

    // ── 自訂鍵盤 ──────────────────────────────────────────────────────────────

    private fun setupKeyboard() {
        val pages = listOf(basicKeys, functionKeys, greekKeys)
        listOf("基本", "函數", "希臘").forEachIndexed { idx, name ->
            binding.keyboardTabs.addTab(binding.keyboardTabs.newTab().setText(name))
            val page = buildPage(pages[idx])
            page.visibility = if (idx == 0) View.VISIBLE else View.GONE
            binding.keyboardContainer.addView(page)
            keyboardPages.add(page)
        }
        binding.keyboardTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                keyboardPages.forEachIndexed { i, p -> p.visibility = if (i == tab.position) View.VISIBLE else View.GONE }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun buildPage(keys: List<Key>): LinearLayout {
        val dp = resources.displayMetrics.density
        val rowH = (44 * dp).toInt()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            keys.chunked(5).forEach { row ->
                addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, rowH)
                    row.forEach { addView(buildBtn(it, rowH)) }
                })
            }
        }
    }

    private fun buildBtn(key: Key, height: Int): MaterialButton =
        MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = key.lbl
            textSize = when {
                key.lbl.length <= 1 -> 18f
                key.lbl.length == 2 -> 15f
                key.lbl.length == 3 -> 13f
                key.lbl.length <= 5 -> 10f
                else -> 8f
            }
            setPadding(0, 0, 0, 0)
            insetTop = 0; insetBottom = 0
            minWidth = 0; minimumWidth = 0
            minHeight = 0; minimumHeight = 0
            layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply { setMargins(1, 1, 1, 1) }
            when (key.style) {
                KS.STRUCT -> setTextColor(clrPrimary)
                KS.BACK   -> setTextColor(clrError)
                KS.NAV    -> setTextColor(clrSecondary)
                else -> {}
            }
            setOnClickListener { if (editorReady) exec(key.action) }
        }

    private fun exec(action: KA) {
        val js = when (action) {
            is KA.Wrt -> "writeLatex('${esc(action.s)}')"
            is KA.Cmd -> "insertCmd('${esc(action.s)}')"
            is KA.Ks  -> "keystroke('${action.s}')"
        }
        binding.webViewEditor.evaluateJavascript(js, null)
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    // ── 載入 / 儲存 ───────────────────────────────────────────────────────────

    private fun loadExistingOrNew() {
        if (args.formulaId == -1L) {
            binding.btnDelete.visibility = View.GONE
            binding.tvTitle.setText(R.string.formula_add)
            return
        }
        binding.tvTitle.setText(R.string.formula_edit)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getById(args.formulaId)?.let { f ->
                existingFormula = f
                binding.etFormulaTitle.setText(f.title)
                binding.etExplanation.setText(f.explanation)
                binding.etSubject.setText(f.subject)
                currentLatex = f.latex
                if (editorReady) binding.webViewEditor.evaluateJavascript("setLatex('${esc(f.latex)}')", null)
            }
        }
    }

    private fun save() {
        val title = binding.etFormulaTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) { binding.etFormulaTitle.error = getString(R.string.formula_title_required); return }
        if (currentLatex.isBlank()) return
        val formula = existingFormula?.copy(
            title = title, latex = currentLatex,
            explanation = binding.etExplanation.text?.toString()?.trim().orEmpty(),
            subject = binding.etSubject.text?.toString()?.trim().orEmpty()
        ) ?: FormulaEntity(
            title = title, latex = currentLatex,
            explanation = binding.etExplanation.text?.toString()?.trim().orEmpty(),
            subject = binding.etSubject.text?.toString()?.trim().orEmpty()
        )
        if (existingFormula == null) viewModel.insert(formula) else viewModel.update(formula)
        findNavController().navigateUp()
    }

    inner class MathInterface {
        @JavascriptInterface
        fun onLatexChange(latex: String) { currentLatex = latex }
    }

    override fun onDestroyView() {
        binding.webViewEditor.destroy()
        super.onDestroyView()
        _binding = null
    }
}
