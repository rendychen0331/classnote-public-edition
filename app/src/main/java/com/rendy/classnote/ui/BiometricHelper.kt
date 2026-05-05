package com.rendy.classnote.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object BiometricHelper {

    /**
     * 檢查裝置是否支援生物辨識或螢幕鎖（PIN / 圖案 / 密碼）。
     */
    fun isAvailable(fragment: Fragment): Boolean {
        val manager = BiometricManager.from(fragment.requireContext())
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 顯示生物辨識/PIN 驗證提示。
     * @param fragment    呼叫端的 Fragment
     * @param title       提示標題
     * @param subtitle    提示副標題（可空）
     * @param onSuccess   驗證成功後執行
     * @param onCancel    使用者取消或驗證失敗後執行（可空）
     */
    fun authenticate(
        fragment: Fragment,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val manager = BiometricManager.from(fragment.requireContext())
        val canAuth = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }
        val executor = ContextCompat.getMainExecutor(fragment.requireContext())

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // 使用者取消（errorCode=10）或其他錯誤
                onCancel?.invoke()
            }

            override fun onAuthenticationFailed() {
                // 辨識失敗（生物特徵不符）— 讓系統自動重試，不需額外處理
            }
        }

        val prompt = BiometricPrompt(fragment, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}
