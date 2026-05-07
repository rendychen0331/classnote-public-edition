>[!NOTE]
>這是給想自行Build APK的使用者看的，想直接用可下載[最新的APK版本](https://github.com/rendychen0331/classnote-public-edition/releases)


# 設定與安裝

## 環境需求

- Android Studio Hedgehog 或更新版本
- Android SDK 34+（compileSdk / targetSdk）
- minSdk 26（Android 8.0）
- JDK 17（Android Studio 內建）

## 步驟

### 1. Clone 專案

```bash
git clone https://github.com/rendychen0331/classnote-public-edition.git
cd classnote-public-edition
```

### 2. 建立 Google OAuth (Google 登入，選用)

1. 前往 [Google Cloud Console](https://console.cloud.google.com)
2. 建立新專案（或使用現有專案）
3. 設定 OAuth同意畫面
4. 新增憑證
5. 啟用 API
6. 前往 [Firebase Console](https://console.firebase.google.com/)
7. 建立新專案（或使用現有專案），匯入 Google Cloud 專案
8. 新增 Android App，Package name 填 `com.rendy.classnote`
9. 下載 `google-services.json`，放到 `app/` 目錄

### 3. 建立 MSAL 設定檔（Microsoft 登入，選用）

若需要 Microsoft To Do / Outlook / Teams 同步：

1. 前往 [Microsoft Entra（Azure AD）](https://portal.azure.com/) → App registrations → New registration
2. Redirect URI 類型選 **Public client/native (mobile & desktop)**，填入 `msauth://com.rendy.classnote/你的Signature`
3. 建立 `app/src/main/res/values/msal_auth_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="msal_client_id">你的 Application (client) ID</string>
    <string name="msal_redirect_host">com.rendy.classnote</string>
    <string name="msal_redirect_path">/你的Signature</string>
</resources>
```

4. 建立 `app/src/main/assets/msal_config.json`：

```json
{
  "client_id": "你的 Application (client) ID",
  "authorization_user_agent": "DEFAULT",
  "redirect_uri": "msauth://com.rendy.classnote/你的Signature",
  "account_mode": "SINGLE",
  "authorities": [
    {
      "type": "AAD",
      "audience": {
        "type": "AzureADandPersonalMicrosoftAccount"
      }
    }
  ]
}
```

> ⚠️ 這兩個檔案已加入 `.gitignore`，不會被 commit。

取得 Signature Hash 的方式：

```bash
# Debug keystore（開發用）
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore \
  | openssl sha1 -binary | openssl base64
```

### 4. 設定 API Keys（選用）

AI 辨識、天氣、Gmail 同步等功能需要個別 API Key。

詳見 → [[API Keys]]

### 5. Build 並安裝

在 Android Studio 中選擇 **Run 'app'**，或使用命令列：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 權限

首次啟動時 App 會引導你開啟下列權限：

| 權限 | 用途 |
|------|------|
| 通知存取 | 讀取 LINE/WhatsApp 等通知並辨識作業 |
| POST_NOTIFICATIONS | 顯示 AI 辨識結果通知 |
| SCHEDULE_EXACT_ALARM | 準時觸發提醒鬧鐘 |
| USE_FULL_SCREEN_INTENT | 鎖屏全螢幕提醒（類鬧鐘效果） |
| 顯示在其他 App 上方 | 提醒視窗懸浮顯示 |

小米 / HyperOS 裝置需額外手動開啟「鎖定螢幕顯示」（App 會引導前往設定）。
