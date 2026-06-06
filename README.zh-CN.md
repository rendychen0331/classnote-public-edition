# ClassNote

[繁體中文](README.md) | **简体中文** | [English](README.en.md)

> [!NOTE]
> README 更新速度比不上 App 更新速度，实际功能以 App 为准

> [!IMPORTANT]
> - AI 功能需自行申请 API Key
> - 主要在 Xiaomi HyperOS 2 上测试

Made with Claude（代码）& Gemini（App 图标）& Google Stitch（UI）

如有功能建议或 Bug 反馈，请创建 [New Issue](https://github.com/rendychen0331/classnote-public-edition/issues/new)

## 1. 简介

这是一个高中生用 AI 辅助开发的 Android App，是首个能持续更新到现在并公开的个人项目。

最初目标是记录日程，功能逐渐扩展至课堂笔记、AI 整合、云端同步等。

## 2. 功能

App 底部导航有 4 个分页：**课表**、**提醒**、**上课笔记**、**更多**。

### 2.1 课表

* 周视图与日历视图
* 课程新增与编辑

### 2.2 提醒

* 提醒事项列表、详情、编辑
* 重复提醒（每天／每周／每月）
* 全屏提醒：类系统闹钟画面，强制覆盖最上层，配合震动
* 安静时段：设定闹钟与提醒不响起的时段，可选提前通知或延后
* 勿扰豁免：开启勿扰模式仍可收到提醒

### 2.3 上课笔记

* 支持录音、文字、手绘、拍照、相册导入
* AI 笔记摘要与问答：AI 总结课堂笔记，可进行进一步对话

### 2.4 更多

* 公式本：记录公式，支持 LaTeX 可视化输入
* 天气（模块）
* 设置

## 3. 模块系统

部分功能以可下载模块形式提供，可在「设置 → 功能模块管理」下载或删除。

| 模块 | 功能 |
|---|---|
| Google | Gmail／Classroom／Calendar／Tasks／Keep 同步 |
| Microsoft | OneDrive／Outlook Calendar／Teams／OneNote／To Do 同步 |
| AI | AI 通知识别、课堂笔记对话 |
| 助手 | 助手覆层功能 |
| 天气 | CWA 天气预报与每日推送 |

## 4. 同步与备份

### 4.1 Google 服务（需登录 Google 账号）

| 服务 | 说明 | 自动同步 |
|---|---|---|
| Google Drive 备份 | 备份设置、API Keys、笔记、提醒至云端，保留最多 3 份版本 | ✅ |
| Google Classroom 同步 | 同步 Classroom 作业至提醒事项 | ✅ |
| Gmail Classroom 消息同步 | 读取 Classroom 发出的 Gmail 邮件并同步 | ✅ |
| Google 日历同步 | 同步 Google 日历事项 | ✅ |
| Google Keep 同步 | 同步 Keep 笔记为提醒事项（仅支持教育／企业账号） | ✅ |
| Google Tasks 同步 | 同步 Google Tasks 待办事项 | ✅ |

### 4.2 Microsoft 服务（需登录 Microsoft 账号）

| 服务 | 说明 | 自动同步 |
|---|---|---|
| OneDrive 备份 | 备份至 OneDrive，保留最多 3 份版本 | ✅ |
| Microsoft Teams 同步 | 同步 Teams 作业至提醒事项（仅支持教育／企业账号） | ✅ |
| Outlook 日历同步 | 同步 Outlook 日历事项 | ✅ |
| OneNote 同步 | 同步 OneNote 笔记为提醒事项 | ✅ |
| Microsoft To Do 同步 | 同步 To Do 待办事项 | ✅ |

### 4.3 本地同步

读取设备日历（`READ_CALENDAR` 权限），支持大部分原厂日历 App，可自动同步并跳过节假日。

> [!NOTE]
> Google 与 Microsoft 同步功能需安装对应模块。支持多账号登录。可设定仅 WiFi 或移动数据同步。

## 5. AI 功能

### 5.1 支持的 AI Provider

| Provider | 状态 |
|---|---|
| Google Gemini | ✅ 默认启用 |
| Anthropic Claude | ✅ 支持 |
| OpenAI ChatGPT | ✅ 支持 |
| Xiaomi MiMo | ✅ 支持 |
| Groq | ✅ 支持 |
| DeepSeek | ✅ 支持 |
| 自定义 Anthropic 格式 | ✅ 支持 |
| 自定义 OpenAI 格式 | ✅ 支持 |

### 5.2 AI 通知识别（需 AI 模块）

AI 识别收到的通知是否为待办事项，是则自动加入提醒事项。

* 支持 App 白名单
* 频道黑白名单
* 敏感词过滤
* 默认禁用，可在「设置 → AI 设置」开启

## 6. 天气（需天气模块）

接入 CWA（中央气象署）API Key，支持 22 县市 36 小时预报。
可设定每日定时推送天气通知。

## 7. 自动更新

可自动从 GitHub 获取最新 APK，默认每 24 小时检查一次。

## 8. 语言支持

* 繁体中文（zh-TW）
* 简体中文（zh-CN）
* English

## 9. 说明

* 目前不打算上架 Google Play Store（$25 暂时付不起）
* 可能考虑 Samsung Galaxy Store，但目前更新频繁，不适合上架
* 不打算开发 iOS 版（无 Apple 设备且需年费），有需求欢迎自行 clone 改写

## 10. 特别感谢

* [svgl](https://svgl.app) — 提供 icon
* [lobehub](https://github.com/lobehub/lobe-icons)（[官网](https://lobehub.com)）— 提供 icon
* [Wikimedia Commons](https://commons.wikimedia.org) — 提供 icon 原图
* [Material Design 3](https://m3.material.io) — UI & icon

## 11. 授权

**MIT**
