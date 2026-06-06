# ClassNote

[繁體中文](README.md) | [简体中文](README.zh-CN.md) | **English**

> [!NOTE]
> The README may lag behind app updates. Check the app for the latest features.

> [!IMPORTANT]
> - AI features require your own API Key
> - Primarily tested on Xiaomi HyperOS 2

Made with Claude (code) & Gemini (App icon) & Google Stitch (UI)

For feature requests or bug reports, please open a [New Issue](https://github.com/rendychen0331/classnote-public-edition/issues/new)

## 1. Introduction

An Android app built by a high school student with AI assistance — the first personal project to be continuously updated and made public.

Originally designed to track schedules, it has since expanded to include class notes, AI integration, cloud sync, and more.

## 2. Features

The app has 4 bottom navigation tabs: **Schedule**, **Reminders**, **Class Notes**, **More**.

### 2.1 Schedule

* Weekly and calendar view
* Add and edit courses

### 2.2 Reminders

* Reminder list, details, and editing
* Recurring reminders (daily / weekly / monthly)
* Full-screen alert: system alarm-style overlay with vibration
* Quiet hours: set time ranges when alarms won't trigger, with early or delayed notification options
* Do Not Disturb bypass: receive reminders even when DND is active

### 2.3 Class Notes

* Supports audio recording, text, drawing, photos, and gallery import
* AI summary and Q&A: AI summarizes class notes and supports follow-up conversation

### 2.4 More

* Formula book: record formulas with LaTeX visual input
* Weather (module)
* Settings

## 3. Module System

Some features are available as downloadable modules. Manage them under **Settings → Feature Modules**.

| Module | Features |
|---|---|
| Google | Gmail / Classroom / Calendar / Tasks / Keep sync |
| Microsoft | OneDrive / Outlook Calendar / Teams / OneNote / To Do sync |
| AI | AI notification recognition, class note chat |
| Assistant | Assistant overlay |
| Weather | CWA weather forecast and daily push notifications |

## 4. Sync & Backup

### 4.1 Google Services (requires Google account)

| Service | Description | Auto Sync |
|---|---|---|
| Google Drive Backup | Backup settings, API Keys, notes, reminders to Drive; retains up to 3 versions | ✅ |
| Google Classroom Sync | Sync Classroom assignments to reminders | ✅ |
| Gmail Classroom Sync | Read Classroom emails sent via Gmail and sync | ✅ |
| Google Calendar Sync | Sync Google Calendar events | ✅ |
| Google Keep Sync | Sync Keep notes as reminders (education/enterprise accounts only) | ✅ |
| Google Tasks Sync | Sync Google Tasks to-do items | ✅ |

### 4.2 Microsoft Services (requires Microsoft account)

| Service | Description | Auto Sync |
|---|---|---|
| OneDrive Backup | Backup to OneDrive; retains up to 3 versions | ✅ |
| Microsoft Teams Sync | Sync Teams assignments to reminders (education/enterprise accounts only) | ✅ |
| Outlook Calendar Sync | Sync Outlook Calendar events | ✅ |
| OneNote Sync | Sync OneNote notes as reminders | ✅ |
| Microsoft To Do Sync | Sync To Do items | ✅ |

### 4.3 Local Sync

Reads device calendar (`READ_CALENDAR` permission). Supports most stock calendar apps with auto-sync and holiday filtering.

> [!NOTE]
> Google and Microsoft sync features require the respective module. Supports multiple accounts. Sync can be restricted to Wi-Fi or mobile data.

## 5. AI Features

### 5.1 Supported AI Providers

| Provider | Status |
|---|---|
| Google Gemini | ✅ Enabled by default |
| Anthropic Claude | ✅ Supported |
| OpenAI ChatGPT | ✅ Supported |
| Xiaomi MiMo | ✅ Supported |
| Groq | ✅ Supported |
| DeepSeek | ✅ Supported |
| Custom Anthropic format | ✅ Supported |
| Custom OpenAI format | ✅ Supported |

### 5.2 AI Notification Recognition (requires AI module)

AI detects whether incoming notifications are to-do items and adds them to reminders automatically.

* App whitelist
* Channel blacklist / whitelist
* Sensitive word filtering
* Disabled by default — enable under **Settings → AI Settings**

## 6. Weather (requires Weather module)

Connects to CWA (Central Weather Administration) API Key. Supports 36-hour forecasts for 22 counties/cities in Taiwan. Daily scheduled weather push notifications available.

## 7. Auto Update

Automatically fetches the latest APK from GitHub. Checks every 24 hours by default.

## 8. Language Support

* Traditional Chinese (zh-TW)
* Simplified Chinese (zh-CN)
* English

## 9. Notes

* Not planning to publish on Google Play Store ($25 fee is out of budget for now)
* Samsung Galaxy Store is possible, but frequent updates make it unsuitable for now
* No iOS version planned (no Apple device + annual fee required); feel free to fork and port

## 10. Credits

* [svgl](https://svgl.app) — icons
* [lobehub](https://github.com/lobehub/lobe-icons) ([website](https://lobehub.com)) — icons
* [Wikimedia Commons](https://commons.wikimedia.org) — icon source images
* [Material Design 3](https://m3.material.io) — UI & icons

## 11. License

**MIT**
