# ClassNote

**繁體中文** | [简体中文](README.zh-CN.md) | [English](README.en.md)

> [!NOTE]
> README 更新速度比不上 App 更新速度，實際功能以 App 為準

> [!IMPORTANT]
> - AI 功能需自行申請 API Key
> - 主要在 Xiaomi HyperOS 2 上測試

Made with Claude（程式碼）& Gemini（App icon）& Google Stitch（UI）

如有功能建議或 Bug 回報，請建立 [New Issue](https://github.com/rendychen0331/classnote-public-edition/issues/new)

## 1. 簡介

這是一個高中生用 AI 輔助開發的 Android App，是首個能持續更新到現在並公開的個人專案。

最初目標是記錄行程，功能逐漸擴展至課堂筆記、AI 整合、雲端同步等。

## 2. 功能

App 底部導航有 4 個分頁：**課表**、**提醒**、**上課筆記**、**更多**。

### 2.1 課表

* 週檢視與日曆檢視
* 課程新增與編輯

### 2.2 提醒

* 提醒事項列表、詳情、編輯
* 重複提醒（每天／每週／每月）
* 全頁提醒：類系統鬧鐘畫面，強制覆蓋最上層，配合震動
* 安靜時段：設定鬧鐘與提醒不響起的時段，可選提前通知或延後
* 勿擾豁免：開啟勿擾模式仍可收到提醒

### 2.3 上課筆記

* 支援錄音、文字、手繪、拍照、相簿匯入
* AI 筆記摘要與問答：AI 總結課堂筆記，可進行進一步對話

### 2.4 更多

* 公式本：記錄公式，支援 LaTeX 視覺化輸入
* 天氣（模組）
* 設定

## 3. 模組系統

部分功能以可下載模組形式提供，可在「設定 → 功能模組管理」下載或刪除。

| 模組 | 功能 |
|---|---|
| Google | Gmail／Classroom／Calendar／Tasks／Keep 同步 |
| Microsoft | OneDrive／Outlook Calendar／Teams／OneNote／To Do 同步 |
| AI | AI 通知辨識、課堂筆記對話 |
| 助手 | 助手覆層功能 |
| 天氣 | CWA 天氣預報與每日推播 |

## 4. 同步與備份

### 4.1 Google 服務（需登入 Google 帳號）

| 服務 | 說明 | 自動同步 |
|---|---|---|
| Google Drive 備份 | 備份設定、API Keys、筆記、提醒至雲端，保留最多 3 份版本 | ✅ |
| Google Classroom 同步 | 同步 Classroom 作業至提醒事項 | ✅ |
| Gmail Classroom 訊息同步 | 讀取 Classroom 寄出的 Gmail 郵件並同步 | ✅ |
| Google 日曆同步 | 同步 Google 日曆事項 | ✅ |
| Google Keep 同步 | 同步 Keep 筆記為提醒事項（僅支援教育／企業帳號） | ✅ |
| Google Tasks 同步 | 同步 Google Tasks 待辦事項 | ✅ |

### 4.2 Microsoft 服務（需登入 Microsoft 帳號）

| 服務 | 說明 | 自動同步 |
|---|---|---|
| OneDrive 備份 | 備份至 OneDrive，保留最多 3 份版本 | ✅ |
| Microsoft Teams 同步 | 同步 Teams 作業至提醒事項（僅支援教育／企業帳號） | ✅ |
| Outlook 行事曆同步 | 同步 Outlook 行事曆事項 | ✅ |
| OneNote 同步 | 同步 OneNote 筆記為提醒事項 | ✅ |
| Microsoft To Do 同步 | 同步 To Do 待辦事項 | ✅ |

### 4.3 本地同步

讀取裝置日曆（`READ_CALENDAR` 權限），支援大部分原廠日曆 App，可自動同步並跳過節假日。

> [!NOTE]
> Google 與 Microsoft 同步功能需安裝對應模組。支援多帳號登入。可設定僅 WiFi 或行動數據同步。

## 5. AI 功能

### 5.1 支援的 AI Provider

| Provider | 狀態 |
|---|---|
| Google Gemini | ✅ 預設啟用 |
| Anthropic Claude | ✅ 支援 |
| OpenAI ChatGPT | ✅ 支援 |
| Xiaomi MiMo | ✅ 支援 |
| Groq | ✅ 支援 |
| DeepSeek | ✅ 支援 |
| 自訂 Anthropic 格式 | ✅ 支援 |
| 自訂 OpenAI 格式 | ✅ 支援 |

### 5.2 AI 通知辨識（需 AI 模組）

AI 辨識收到的通知是否為待辦事項，是則自動加入提醒事項。

* 支援 App 白名單
* 頻道黑白名單
* 敏感詞過濾
* 預設禁用，可在「設定 → AI 設定」開啟

## 6. 天氣（需天氣模組）

接入 CWA（中央氣象署）API Key，支援 22 縣市 36 小時預報。
可設定每日定時推送天氣通知。

## 7. 自動更新

可自動從 GitHub 抓取最新 APK，預設每 24 小時檢查一次。

## 8. 語言支援

* 繁體中文（zh-TW）
* 簡體中文（zh-CN）
* English

## 9. 說明

* 目前不打算上架 Google Play Store（$25 暫時付不起）
* 可能考慮 Samsung Galaxy Store，但目前更新頻繁，不適合上架
* 不打算開發 iOS 版（無 Apple 設備且需年費），有需求歡迎自行 clone 改寫

## 10. 特別感謝

* [svgl](https://svgl.app) — 提供 icon
* [lobehub](https://github.com/lobehub/lobe-icons)（[官網](https://lobehub.com)）— 提供 icon
* [Wikimedia Commons](https://commons.wikimedia.org) — 提供 icon 原圖
* [Material Design 3](https://m3.material.io) — UI & icon

## 11. 授權

**MIT**
