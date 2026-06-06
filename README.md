# ClassNote

> [!NOTE]
> README 更新速度比不上 App 更新速度，實際功能以 App 為準

> [!WARNING]
> v3.xx 目前有許多影響使用的 bug（v3.40 亦然），建議先使用 v2.xx

## 1. 簡介

這是一個高中生用 AI 輔助開發的 Android App，是首個能持續更新到現在並公開的個人專案。

最初目標是記錄行程，功能逐漸擴展至課堂筆記、AI 整合、雲端同步等。若這個 App 對你有幫助，歡迎提供回饋。

如有功能建議或 Bug 回報，請建立 [New Issue](https://github.com/rendychen0331/classnote-public-edition/issues/new)

> [!IMPORTANT]
> - AI 功能需自行申請 API Key
> - 主要在 Xiaomi HyperOS 2 上測試

Made with Claude（程式碼）& Gemini（App icon）& Google Stitch（UI）

## 2. 功能

### 2.1 課堂筆記

* 課表紀錄
* 課堂筆記：支援錄音、文字、手繪、拍照、相簿匯入
* 公式本：可記錄公式，支援 LaTeX

### 2.2 Google 服務

需要登入 Google 帳號。

| 服務 | 說明 | 自動同步 |
|---|---|---|
| Google Drive 備份 | 將設定、API Keys、筆記、提醒備份至雲端或還原 | ✅ |
| Google Classroom 同步 | 同步 Classroom 作業至提醒事項 | ✅ |
| Gmail Classroom 訊息同步 | 讀取 Classroom 寄出的 Gmail 郵件並同步 | ✅ |
| Google 日曆同步 | 同步 Google 日曆事項 | ✅ |
| Google Keep 同步 | 同步 Keep 筆記為提醒事項（僅支援教育／企業帳號） | ✅ |
| Google Tasks 同步 | 同步 Google Tasks 待辦事項 | ✅ |

### 2.3 Microsoft 服務

需要登入 Microsoft 帳號。

| 服務 | 說明 | 自動同步 |
|---|---|---|
| OneDrive 備份 | 備份至 OneDrive 或還原，可設定自動備份 | ✅ |
| Microsoft Teams 同步 | 同步 Teams 作業至提醒事項（僅支援教育／企業帳號） | ✅ |
| Outlook 行事曆同步 | 同步 Outlook 行事曆事項 | ✅ |
| OneNote 同步 | 同步 OneNote 筆記為提醒事項 | ✅ |
| Microsoft To Do 同步 | 同步 To Do 待辦事項 | ✅ |

### 2.4 本地同步

讀取裝置日曆（`READ_CALENDAR` 權限），支援大部分原廠日曆 App，可自動同步並跳過節假日。

### 2.5 提醒與鬧鐘

| 功能 | 說明 |
|---|---|
| 全頁提醒 | 類系統鬧鐘畫面，強制覆蓋最上層，配合震動 |
| 安靜時段 | 設定鬧鐘與提醒不響起的時段，可選提前通知或延後 |
| 勿擾豁免 | 開啟勿擾模式仍可收到提醒 |

### 2.6 天氣

接入 CWA（中央氣象署）API Key，可在天氣頁查看天氣，或設定每日定時推送天氣通知。
亦支援 open-meteo 及 weatherapi.com（尚未完整測試）。

### 2.7 AI 功能

| 功能 | 說明 |
|---|---|
| AI 通知辨識 | AI 辨識通知是否為待辦事項並自動加入提醒，支援 App 白名單、頻道黑白名單、敏感詞過濾 |
| AI 筆記摘要與問答 | AI 總結課堂筆記，可進行進一步對話 |
| Keep／OneNote 筆記辨識 | AI 辨識筆記內容並自動加入提醒事項 |

### 2.8 自動更新

可自動從 GitHub 抓取最新 APK。

## 3. 支援的 AI

| 狀態 | AI |
|---|---|
| ✅ 支援已測試 | Google Gemini |
| ✅ 支援已測試 | Groq（llama-3.3-70b-versatile）|
| ❓ 支援未測試 | Anthropic Claude |
| ❓ 支援未測試 | OpenAI ChatGPT |
| ❓ 支援未測試 | Xiaomi MiMo（OpenAI API）|
| ❓ 支援未測試 | DeepSeek |

## 4. 說明

* 目前不打算上架 Google Play Store（$25 暫時付不起）
* 可能考慮 Samsung Galaxy Store，但目前更新頻繁，不適合上架
* 不打算開發 iOS 版（無 Apple 設備且需年費），有需求歡迎自行 clone 改寫

## 5. 特別感謝

* [svgl](https://svgl.app) — 提供 icon
* [lobehub](https://github.com/lobehub/lobe-icons)（[官網](https://lobehub.com)）— 提供 icon
* [Wikimedia Commons](https://commons.wikimedia.org) — 提供 icon 原圖
* [Material Design 3](https://m3.material.io) — UI & icon

## 6. 授權

**MIT**
