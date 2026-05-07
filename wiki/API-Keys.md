# API Keys 設定

所有 API Key 在 App 內的**設定 → AI 模型設定**中輸入，不需寫死在程式碼裡。

---

## AI 辨識模型

ClassNote 支援多個 AI 模型進行通知辨識與課堂摘要，至少需設定其中一個。

### Google Gemini（推薦）

1. 前往 [Google AI Studio](https://aistudio.google.com/apikey)
2. 點 **Create API key**
3. 複製 key，貼到 App 設定的「Gemini API Key」欄位

免費額度充足，適合日常使用。

---

### Groq

1. 前往 [console.groq.com](https://console.groq.com/)
2. 登入後點 **API Keys → Create API Key**
3. 複製 key，貼到 App 設定的「Groq API Key」欄位

速度極快，免費額度足夠個人使用。

---

### OpenAI

1. 前往 [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. 點 **Create new secret key**
3. 複製 key，貼到 App 設定的「OpenAI API Key」欄位

需付費帳號（需綁定信用卡），模型選 `gpt-4o-mini` 最省錢。

---

### Anthropic Claude

1. 前往 [console.anthropic.com/settings/keys](https://console.anthropic.com/settings/keys)
2. 點 **Create Key**
3. 複製 key，貼到 App 設定的「Claude API Key」欄位

---

### Xiaomi MiMo

使用 OpenAI 相容格式，Endpoint：`https://api.xiaomimimo.com/v1/chat/completions`，模型：`mimo-v2.5`

1. 前往 [Xiaomi MiMo 開發者平台](https://xiaomimimo.com/) 申請 API Key
2. 貼到 App 設定的「Mimo API Key」欄位

---

## 天氣功能

使用中央氣象署（CWA）開放資料 API。

1. 前往 [CWA 氣象資料開放平臺](https://opendata.cwa.gov.tw/user/authkey)
2. 登入（或免費註冊）後點 **取得授權碼**
3. 複製 API Key，貼到 App 設定的「天氣 API Key」欄位

免費使用，僅支援台灣地區天氣預報。

---

## Microsoft 同步（選用）

同步 Microsoft To Do 任務、Outlook 行事曆事件、Teams 作業。

需在 [Microsoft Entra](https://portal.azure.com/) 建立 App Registration，詳見 [[Setup#3-建立-msal-設定檔microsoft-登入選用]]。

設定完成後，在 App 的**設定 → Microsoft 同步**頁面登入 Microsoft 帳號。

>[!IMPORTANT]
> ⚠️ Teams 作業同步僅支援教育版或企業版帳號（Microsoft 365 A1/A3/A5 或 E 系列）。

---

## Gmail / Google Classroom 同步（選用）

使用 Google Sign-In 登入，不需要額外 API Key。

需確認 Firebase 專案已啟用下列 API（Google Cloud Console）：

- Gmail API
- Google Classroom API
- Google Drive API（備份功能）

在 [Google Cloud Console](https://console.cloud.google.com/) → **API 和服務 → 已啟用的 API** 中確認並啟用。

---

## 安全性提醒

- App 使用 **EncryptedSharedPreferences** 儲存所有 API Key，key 不會以明文儲存在裝置上
- 查看 key 時需通過**生物辨識驗證**（指紋 / 臉部辨識）
- 所有 API Key 皆在裝置本地使用，**不會上傳至任何伺服器**
