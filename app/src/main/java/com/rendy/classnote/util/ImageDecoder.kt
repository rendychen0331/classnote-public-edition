package com.rendy.classnote.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PERF-H3：統一的相片解碼點。所有從檔案載入縮圖 / 預覽的地方都必須走這裡，
 * 一律在 Dispatchers.IO 解碼，並依目標顯示尺寸算 inSampleSize 做降採樣，
 * 避免在主執行緒把數 MB 的相機相片整張 decode 成 ARGB_8888（造成卡頓 / OOM）。
 */
object ImageDecoder {

    /**
     * 依目標寬高降採樣解碼檔案。回傳 null 表示檔案不存在 / 損毀。
     * @param reqWidth  目標顯示寬（px）
     * @param reqHeight 目標顯示高（px）
     */
    suspend fun decodeSampled(path: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                // 先只讀邊界算出 inSampleSize，不真的配置像素
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                }
                BitmapFactory.decodeFile(path, opts)
            } catch (e: Exception) {
                null
            }
        }

    /**
     * 全解析度解碼（不降採樣），但仍在 IO 執行。
     * 用於需要保留完整品質的情境（例如可再編輯的手繪畫布），只把 decode 移出主執行緒。
     */
    suspend fun decodeFullOnIo(path: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                null
            }
        }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        if (reqW <= 0 || reqH <= 0) return sample
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) {
            sample *= 2
        }
        return sample
    }
}
