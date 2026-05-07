# ClassNote

歡迎，這是一個高中**牲**用 愛 + AI 做的Android App，是首個能持續更新到現在並公開的專案。

一開始做這個App是為了能記錄接下來的行程，漸漸的功能也越來越多，若這個App對你有幫助，我會很開心

如果你有任何功能提議或BUG想反饋，歡迎建立 [New Issues](https://github.com/rendychen0331/classnote-public-edition/issues/new)

Made with Claude(程式碼) & Gemini(App icon) & Google Stitch(UI) 
>[!IMPORTANT]
>API Key 需要自己申請
>
>大部分使用Xiaomi HyperOS 2 測試（主力機）
>
>天氣僅支援CWA API

## 特別感謝
- [svgl](https://svgl.app) - 提供icon
- [lobehub](https://github.com/lobehub/lobe-icons) ([官網](https://lobehub.com)) - 提供icon
- [Wikimedia Commons](https://commons.wikimedia.org)  - 提供icon原圖
- [Material Design 3](https://m3.material.io) - UI & icon
## 說明
>[!NOTE]
>因為敝人的懶散，README更新速度比不上App更新速度，所以直接看App比較準確

目前不打算上 Google Play Store ，因為 $25 真的暫時付不起

可能會上 Samsung Galaxy Store ，但App目前更新頻繁，不適合上架

應該不會做 ios 版，因為我沒再用Apple + 好像要付年費，有需求歡迎自行clone改寫

## 功能
- 課表紀錄
- 課堂筆記功能 : 支援錄音、文字、手繪、拍照、相簿匯入
- 公式本 : 可紀錄公式，支援LaTeX
- Google 服務 (需要登入Google帳號) :
  | 服務 | 說明 | 可自動執行 |
  |---|---|---|
  | Google Drive備份 | 將設定、API Keys、筆記、提醒事項備份到雲端硬碟或從雲端硬碟還原 | ✅ |
  | Google Classroom同步 | 可同步Google Classroom中的作業資訊至提醒事項 | ✅ |
  | Gmail Classroom訊息同步 | 同上，但是原理是讀取Google Classroom寄到Gmail的郵件 *(暫不支援AI辨識非Classroom郵件)* | ✅ |
  | Google 日曆同步 | 同步Google 日曆中的事項 | ✅ |
  | Google Keep同步 | 同步Google Keep中的筆記作為提醒事項 **(注意:僅支援教育/企業帳號，此功能可能被管理員禁用)** *(暫不支持匯入為筆記)* | ✅ |
  | Google Tasks同步 | 同步Google Tasks中的待辦事項 | ✅ |
- Microsoft 服務 (需要登入Microsoft帳號) :
  | 服務 | 說明 | 可自動執行 |
  |---|---|---|
  | Microsoft OneDrive備份 | 將設定、API Keys、筆記、提醒事項備份到OneDrive或從OneDrive還原，可設定自動備份 | ✅ |
  | Microsoft Teams同步 | 可同步Microsoft Teams中的作業資訊至提醒事項 **(注意:僅支援教育/企業帳號，此功能可能被管理員禁用)** | ✅ |
  | Microsoft Outlook行事曆同步 | 同步Microsoft Outlook行事曆中的事項 | ✅ |
  | Microsoft OneNote同步 | 同步Microsoft OneNote中的筆記作為提醒事項 *(暫不支持匯入為筆記)* | ✅ |
  | Microsoft To Do同步 | 同步Microsoft To Do中的待辦事項 | ✅ |
- 本地同步服務 (android.permission.READ_CALENDAR) : 讀取裝置日曆(READ_CALENDAR權限)，可同步大部分原廠日曆App，支援自動同步、跳過節假日
- 提醒與鬧鐘 :
  | 功能 | 說明 |
  |---|---|
  | 全頁提醒 | 會有類似系統鬧鐘的畫面，強制覆蓋在最上層，配合震動 |
  | 安靜時段 | 設定鬧中和提醒不會想起的時段，可選擇提前通知或延後 (主要是避免AI添加奇怪的時間) |
  | 勿擾豁免 | 就算開啟勿擾模式也可以收到提醒 |
- 天氣 : 可接入 CWA(中央氣象署) API Key，在 天氣頁 查看天氣，或設定每日定時傳送天氣訊息通知
- AI 功能 :
  | 功能 | 說明 |
  |---|---|
  | AI 通知辨識 | 由AI辨識收到的通知是否為待辦事項，如果是則自動加入提醒事項中，支援App白名單、頻道黑白名單、敏感詞過濾 |
  | AI 筆記摘要與問答 | 由AI總結課堂筆記，可進行進一步對話 |
  | Google Keep、Microsoft OneNote筆記辨識 | 由AI辨識筆記內容是否為待辦事項，如果是則自動加入提醒事項中 |
- 自動更新 : 可自動由Github抓取APK
- 支援的AI : (✅支援且已測試，❓支援但未測試，❌不支援)
  | 支援狀態 | AI |
  |---|---|
  | ✅ | Google Gemini |
  | ❓ | Anthropic Claude |
  | ❓ | OpenAI Chatgpt |
  | ❓ | Xiaomi MiMo (OpenAI API) |
  | ✅ | Groq (llama-3.3-70b-versatile) |
  | ❓ | DeepSeek |
  | ❌ | xAI Grok |
  | ❌ | Microsoft Copilot |
  | ❌ | Github Copilot |
  | ❌ | ByteDance Doubao / Dola |
  | ❌ | Alibaba Qwen |
  | ❌ | Perplexity |
  | ❌ | Meta AI |
  



