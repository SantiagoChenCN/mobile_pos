# 电脑端鸣盛数据库只读同步工具与手机端 HTTP 同步接入方案

更新时间：2026-07-08  
适用项目：`android-emergency-pos` + 新增 `pc-sync-tool`  
目标读者：电脑端 agent、手机端后端 agent、手机端前端 agent、验收 agent

## 1. 本轮目标

实现一套“电脑端只读备份 + 手机端主动拉取”的商品库同步方案。

核心目标：

1. 电脑端小工具定时只读复制鸣盛商品数据库 `.db`。
2. 电脑端小工具提供局域网 HTTP 服务。
3. 电脑端小工具显示二维码，手机扫码后自动配置连接。
4. 手机端从电脑端 HTTP 服务下载最新 `.db` 副本。
5. 手机端校验 hash 后复用现有鸣盛 `.db` 导入流程。
6. 手机端导入前提示确认；如果有本地手动修改，必须二次确认。

第一版分两个阶段开发：

- 阶段 A：电脑端同步工具。
- 阶段 B：手机端同步接入。

## 2. 已确认决策

### 2.1 电脑端工具运行方式

- Windows 桌面工具。
- 开机后自动后台运行。
- 有托盘图标。
- 点击窗口右上角关闭时不退出，只最小化到托盘。
- 只有托盘菜单“退出”才真正退出。

### 2.2 数据库来源选择

支持两种来源：

1. 选择具体 `.db` 文件。
2. 选择鸣盛软件文件夹，工具自动寻找 `.db`。

自动寻找规则：

1. 优先文件名：
   - `AGT_MAIN.db`
   - `AGT_MAIN_*.db`
   - 其他 `.db`
2. 对候选 `.db` 做只读 SQLite 表结构校验：

```sql
SELECT name FROM sqlite_master WHERE type='table' AND name='CJQ_GOODLIST';
```

3. 找到 `CJQ_GOODLIST` 表才认为是鸣盛商品库。
4. 如果找到多个，优先：
   - `AGT_MAIN.db`
   - 最新修改时间
   - 文件最大

### 2.3 安全

- HTTP 访问必须带 token。
- token 由电脑端工具首次启动时自动生成。
- 电脑端界面显示 token 和二维码。
- 手机扫码后自动保存 host、port、token。

### 2.4 二维码协议

二维码内容使用自定义协议：

```text
mobilepos-sync://setup?host=192.168.1.25&port=8765&token=A7K9Q2M4
```

手机端扫码后解析该协议并自动填入同步配置。

### 2.5 备份间隔

第一版只做：

- 固定间隔自动备份。
- 手动立即备份。

不做实时文件监控。

备份间隔档位：

- 关闭
- 5 分钟
- 15 分钟
- 30 分钟
- 60 分钟

默认：15 分钟。

### 2.6 备份保留数量

- 电脑端保留最近 5 个备份。
- 手机端继续使用现有最近 5 次导入快照。

### 2.7 手机端同步策略

- 手机端主动拉取，不让电脑主动推送。
- 第一版做：
  - 手动立即同步。
  - 打开 App 时检查新版本。
- 不做 Android 后台定时同步。
- 发现新版本后先提示确认。
- 本地有手动修改时必须二次确认。

### 2.8 电脑端不解析业务数据

电脑端第一版只做：

- 复制数据库副本。
- 计算 SHA-256。
- 生成 manifest。
- 提供 HTTP 下载。

电脑端不读取商品数量、不解析促销、不做业务映射。

商品数量、促销数量、warning 由手机端导入后显示。

### 2.9 技术栈

电脑端工具：

- Python
- PySide6 / Qt
- 源码运行验收通过后，再用 PyInstaller 打包 `.exe`

项目目录：

```text
E:\手机收银软件开发\pc-sync-tool
```

### 2.10 配置和备份目录

配置文件：

```text
C:\Users\<用户名>\AppData\Roaming\MobilePosSync\config.json
```

备份目录：

```text
C:\Users\<用户名>\AppData\Local\MobilePosSync\backups\
```

开机启动：

- 默认开启，可关闭。
- 使用当前用户 Run 注册表项：

```text
HKCU\Software\Microsoft\Windows\CurrentVersion\Run
```

## 3. 硬性安全边界

这一节是强约束。任何 agent 实现时必须遵守。

电脑端同步工具只允许：

- 只读打开源 `.db`。
- 复制源 `.db` 到同步工具自己的 AppData 备份目录。
- 在 AppData 下写入：
  - `latest.db`
  - `manifest.json`
  - 历史备份
  - 日志
  - 配置

电脑端同步工具禁止：

- 禁止写入、修改、删除、移动、重命名鸣盛软件目录下任何文件。
- 禁止修改 `AGT_MAIN.db` 原文件。
- 禁止修改 `AGT_REPORT.db`、`AGT_PRINT.db`。
- 禁止修改 `MS2011.zip`、`MS2011BAK.zip` 等加密备份。
- 禁止解压、破解、修复、重打包鸣盛备份文件。
- 禁止连接 SQL Server 执行写操作。
- 禁止修改鸣盛程序、配置、授权、模板、报表、打印文件。
- 禁止把手机端数据写回鸣盛数据库。

正确数据流：

```text
鸣盛现有 db 文件，只读
→ 复制到临时文件
→ 写入同步工具 AppData backups 目录
→ 生成 latest.db / manifest.json
→ 手机通过 HTTP 下载副本
→ 手机导入自己的本地商品库
```

错误数据流，禁止实现：

```text
手机或同步工具
→ 写回鸣盛数据库
```

## 4. 本地资料检查结论

已检查当前工作区已有分析资料：

- 可读商品库是 `AGT_MAIN.db / AGT_MAIN_20260705.db`。
- 商品主表是 `CJQ_GOODLIST`。
- `CJQ_GOODLIST` 包含完整条码、商品名、售价、分类、单位等字段。
- `AGT_MAIN.db` 更像本地商品/配置/缓存库，不是完整业务流水主库。
- 完整业务主库更可能在 SQL Server 或加密备份里。
- `MS2011.zip`、`MS2011BAK.zip` 是加密 ZIP，像主库备份，不能碰。
- 手册目录中存在“数据库备份和数据库导入教程”“数据库清理教程”等数据库维护章节，但同步工具不介入这些系统维护操作。

相关资料：

```text
ESpsa_analysis/notes/initial_analysis.md
ESpsa_analysis/notes/new_drive_database_findings.md
ESpsa_analysis/notes/sqlite_summary.json
ESpsa_analysis/manual_web/webhelpcontents.htm
```

## 5. 总体架构

```text
电脑端 pc-sync-tool
  ├─ GUI/托盘
  ├─ 配置管理
  ├─ 源数据库定位
  ├─ 只读稳定性检查
  ├─ 原子备份
  ├─ manifest/hash
  ├─ HTTP 服务
  └─ 二维码

手机端 android-emergency-pos
  ├─ 导入页电脑同步卡片
  ├─ 二维码扫描/解析
  ├─ 同步配置保存
  ├─ health/manifest 请求
  ├─ latest.db 下载
  ├─ sha256 校验
  ├─ 导入确认/二次确认
  └─ 复用现有 MINGSHENG_DB 导入
```

## 6. 电脑端前后端分离

电脑端虽然是桌面工具，也必须分层。

### 6.1 电脑端后端模块

负责：

- 配置读写。
- 数据库路径解析。
- 文件稳定性检查。
- 只读复制。
- SHA-256。
- manifest。
- HTTP 服务。
- 日志。
- 开机启动注册表。

不负责：

- GUI 布局。
- 手机端导入。
- 鸣盛数据库业务解析。

### 6.2 电脑端前端模块

负责：

- PySide6 界面。
- 托盘菜单。
- 二维码展示。
- 配置表单。
- 状态展示。
- 日志列表。

不负责：

- 直接复制文件。
- 直接启动 socket 细节。
- 直接写 manifest。

## 7. 手机端前后端分离

### 7.1 手机端后端/app-service 层

负责：

- 同步配置保存。
- HTTP 请求。
- manifest 解析。
- 文件下载。
- SHA-256 校验。
- 调用现有 `AppServices.importProducts(..., ImportFormat.MINGSHENG_DB)`。

不负责：

- UI 卡片布局。
- 弹窗文案。
- 二维码 UI。

### 7.2 手机端前端/UI 层

负责：

- 导入页新增“电脑同步”卡片。
- 扫码连接按钮。
- 测试连接按钮。
- 检查新版本按钮。
- 立即同步按钮。
- 确认弹窗。
- 本地修改二次确认弹窗。
- 同步状态展示。

不负责：

- 直接解析 DB。
- 直接写商品库。
- 直接操作 HTTP socket 细节。

## 8. 电脑端项目结构

建议新增：

```text
pc-sync-tool/
  README.md
  requirements.txt
  src/
    app.py
    config.py
    paths.py
    source_locator.py
    backup_worker.py
    manifest.py
    file_hash.py
    http_server.py
    qr_code.py
    tray.py
    startup.py
    event_log.py
    ui/
      main_window.py
      status_panel.py
      settings_panel.py
      log_panel.py
  tests/
    test_source_locator.py
    test_manifest.py
    test_file_hash.py
    test_backup_worker.py
  scripts/
    run_dev.ps1
    build_exe.ps1
```

## 9. 电脑端配置格式

配置文件：

```json
{
  "sourceMode": "file",
  "dbFilePath": "D:\\MS2011\\AGT_MAIN.db",
  "dbFolderPath": "",
  "backupIntervalMinutes": 15,
  "retentionCount": 5,
  "port": 8765,
  "token": "A7K9Q2M4",
  "selectedHost": "192.168.1.25",
  "startOnBoot": true
}
```

字段说明：

- `sourceMode`: `file` 或 `folder`
- `dbFilePath`: 具体 `.db` 文件路径
- `dbFolderPath`: 鸣盛软件文件夹路径
- `backupIntervalMinutes`: `0, 5, 15, 30, 60`
- `retentionCount`: 第一版固定 5
- `port`: 默认 8765
- `token`: 自动生成，可重新生成
- `selectedHost`: 用于二维码和手机连接的局域网 IP
- `startOnBoot`: 是否开机启动

## 10. 电脑端备份流程

备份必须使用稳定性检查 + 原子写入。

流程：

```text
1. 定位源 db
2. 只读读取源文件大小和修改时间
3. 等待 2 秒
4. 再次读取源文件大小和修改时间
5. 如果大小或修改时间变化，跳过本轮，不覆盖 latest
6. 复制到 AppData backups/latest.tmp
7. 计算 latest.tmp 的 sha256
8. 原子重命名 latest.tmp -> latest.db
9. 写 manifest.tmp
10. 原子重命名 manifest.tmp -> manifest.json
11. 复制一份到 history 目录
12. 删除超过最近 5 个的历史备份
13. 写事件日志
```

失败规则：

- 任何失败都不能覆盖现有 `latest.db`。
- 任何失败都不能覆盖现有 `manifest.json`。
- 本轮失败时，手机继续看到上一次成功备份。

## 11. manifest 格式

```json
{
  "ok": true,
  "version": "2026-07-08T10:45:00Z",
  "fileName": "AGT_MAIN.db",
  "sizeBytes": 18874368,
  "sha256": "abc123...",
  "createdAt": "2026-07-08T10:45:00Z",
  "downloadPath": "/latest.db"
}
```

没有可用备份：

```json
{
  "ok": false,
  "error": "NO_BACKUP_READY"
}
```

## 12. HTTP API

第一版只提供 3 个接口。

### 12.1 GET /health

请求：

```text
GET /health?token=xxxx
```

响应：

```json
{
  "ok": true,
  "app": "MobilePosSync",
  "version": "1.0",
  "host": "192.168.1.25",
  "port": 8765
}
```

### 12.2 GET /manifest.json

请求：

```text
GET /manifest.json?token=xxxx
```

响应：见 manifest 格式。

### 12.3 GET /latest.db

请求：

```text
GET /latest.db?token=xxxx
```

响应：

- body：数据库文件二进制。
- headers：

```text
Content-Type: application/octet-stream
Content-Length: <size>
X-File-Sha256: <sha256>
```

### 12.4 错误规则

token 错误：

```text
403 Forbidden
```

没有备份：

```json
{
  "ok": false,
  "error": "NO_BACKUP_READY"
}
```

端口被占用：

- GUI 显示错误。
- HTTP 服务不启动。
- 用户可修改端口。

## 13. 二维码

二维码内容：

```text
mobilepos-sync://setup?host=192.168.1.25&port=8765&token=A7K9Q2M4
```

电脑端界面显示：

- 服务状态
- 选择的局域网 IP
- 端口
- token
- 二维码
- “复制同步地址”按钮

如果电脑有多个 IP：

- 列出候选 IP。
- 用户选择一个作为 `selectedHost`。
- 二维码使用该 IP。

## 14. 电脑端 GUI 要求

主界面建议包含：

### 14.1 状态区域

```text
状态：运行中 / 停止 / 错误
最近备份：2026-07-08 10:45 成功
最近请求：2026-07-08 11:21 手机下载 latest.db
错误：无
```

### 14.2 数据库来源区域

```text
来源模式：[选择文件] [选择文件夹自动寻找]
数据库文件：D:\MS2011\AGT_MAIN.db [选择文件]
鸣盛文件夹：D:\MS2011 [选择文件夹]
检测结果：找到 CJQ_GOODLIST
```

### 14.3 备份设置区域

```text
自动备份间隔：[关闭 / 5 / 15 / 30 / 60 分钟]
保留历史备份：最近 5 个
[立即备份一次]
```

### 14.4 HTTP 设置区域

```text
端口：8765
局域网 IP：192.168.1.25
Token：A7K9Q2M4 [重新生成]
[复制同步地址]
```

### 14.5 二维码区域

```text
手机 App 扫描二维码连接电脑同步工具
[二维码]
```

### 14.6 日志区域

保留最近 200 条事件。

示例：

```text
2026-07-08 10:45:00 备份成功 AGT_MAIN.db 18MB sha256=...
2026-07-08 11:00:00 跳过备份：源文件正在变化
2026-07-08 11:15:00 备份失败：找不到数据库文件
2026-07-08 11:20:00 HTTP 服务启动 192.168.1.25:8765
2026-07-08 11:21:00 手机下载 latest.db 成功
```

## 15. 托盘行为

点击窗口右上角 X：

```text
隐藏窗口
托盘继续运行
HTTP 服务继续运行
定时备份继续运行
```

托盘菜单：

```text
打开同步工具
立即备份
复制同步地址
退出
```

退出时：

```text
停止定时器
停止 HTTP 服务
保存配置
退出程序
```

## 16. 手机端项目结构建议

新增或修改：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncConfig.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncStore.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncClient.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncManifest.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncService.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ImportScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
```

## 17. 手机端同步配置

保存到 app SharedPreferences 或独立 JSON 均可，但必须集中在 `ComputerSyncStore`。

建议结构：

```json
{
  "host": "192.168.1.25",
  "port": 8765,
  "token": "A7K9Q2M4",
  "lastSeenSha256": "abc...",
  "lastSyncedSha256": "abc...",
  "lastCheckedAt": "2026-07-08T11:20:00",
  "lastSyncedAt": "2026-07-08T11:21:00"
}
```

## 18. 手机端同步流程

### 18.1 扫码连接

```text
导入页
→ 电脑同步卡片
→ 扫码连接电脑工具
→ 扫描 mobilepos-sync://setup?host=...&port=...&token=...
→ 解析并保存配置
→ 自动请求 /health 测试连接
```

### 18.2 检查新版本

```text
请求 /manifest.json
→ token 错误：提示重新扫码
→ 无备份：提示电脑端先立即备份
→ sha256 和 lastSyncedSha256 相同：提示已是最新
→ sha256 不同：显示新版本确认弹窗
```

### 18.3 立即同步

```text
请求 /manifest.json
→ 下载 /latest.db
→ 计算 sha256
→ 与 manifest.sha256 比对
→ 校验通过
→ 确认导入
→ 调用现有 ImportFormat.MINGSHENG_DB 导入
→ 导入成功后记录 lastSyncedSha256 / lastSyncedAt
```

### 18.4 打开 App 时检查

第一版只检查，不自动导入：

```text
App 打开
→ 如果已配置电脑同步
→ 请求 manifest
→ 如果有新版本
→ 在导入页或 toast/状态中提示
```

不要在用户不知情时自动覆盖商品库。

## 19. 手机端确认弹窗

普通情况：

```text
发现新的电脑商品库
文件：AGT_MAIN.db
时间：2026-07-08 10:45
大小：18 MB

导入后会替换当前手机商品库。

[取消] [导入]
```

有本地手动修改时：

```text
发现新的电脑商品库

注意：手机上有本地手动修改或自建商品。
导入后这些修改会被电脑商品库覆盖。

[取消] [继续覆盖并导入]
```

导入成功：

```text
同步完成
商品：xxxx
促销：xxx
警告：x
```

## 20. 手机端导入页 UI

同步入口放在“导入商品库”页面，不放设置页，不新建首页一级入口。

导入页结构：

```text
当前商品库

鸣盛数据库 .db
[选择 .db 文件]

通用 CSV 商品表
[选择 .csv 文件]

电脑同步
从收银电脑自动获取最新鸣盛数据库副本
状态：未配置 / 已连接 / 上次同步时间
[扫码连接电脑工具]
[测试连接]
[检查新版本]
[立即同步]

最近 5 次导入
...
```

## 21. 手机端错误提示

连接不上电脑：

```text
无法连接电脑同步工具。请确认电脑和手机在同一 Wi-Fi，工具正在运行。
```

token 错误：

```text
访问令牌不正确。请重新扫描电脑端二维码。
```

没有备份：

```text
电脑端还没有生成可同步的数据库，请先点击立即备份。
```

hash 不匹配：

```text
下载文件校验失败，请重新同步。
```

导入失败：

```text
下载成功，但不是支持的鸣盛商品数据库。
```

## 22. 阶段 A：电脑端开发任务

### 22.1 后端任务

1. 创建 `pc-sync-tool` 项目。
2. 实现 AppData 路径管理。
3. 实现配置读写。
4. 实现 token 生成。
5. 实现源 DB 文件模式。
6. 实现源文件夹自动寻找模式。
7. 实现 `CJQ_GOODLIST` 只读表校验。
8. 实现稳定性检查。
9. 实现原子复制。
10. 实现 SHA-256。
11. 实现 manifest。
12. 实现最近 5 个历史备份保留。
13. 实现 HTTP API。
14. 实现最近 200 条事件日志。
15. 实现开机启动注册表开关。
16. 增加单元测试。

### 22.2 前端任务

1. 实现 PySide6 主窗口。
2. 实现托盘图标和菜单。
3. 实现来源选择 UI。
4. 实现备份间隔 UI。
5. 实现端口、IP、token UI。
6. 实现二维码展示。
7. 实现状态面板。
8. 实现日志列表。
9. 实现关闭窗口最小化到托盘。

### 22.3 阶段 A 验收

源码运行：

```powershell
cd E:\手机收银软件开发\pc-sync-tool
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\python src\app.py
```

验收点：

- 可以选择具体 `.db`。
- 可以选择文件夹并自动找到含 `CJQ_GOODLIST` 的 `.db`。
- 可以立即备份。
- 备份只写入 AppData。
- 鸣盛原目录没有任何文件被修改。
- `/health?token=...` 可访问。
- `/manifest.json?token=...` 可访问。
- `/latest.db?token=...` 可下载。
- token 错误返回 403。
- 二维码内容正确。
- 关闭窗口后托盘继续运行。
- 托盘退出能正常停止服务。

## 23. 阶段 B：手机端开发任务

### 23.1 后端/app-service 任务

1. 新增 `ComputerSyncConfig`。
2. 新增 `ComputerSyncStore`。
3. 新增 `ComputerSyncManifest`。
4. 新增 `ComputerSyncClient`。
5. 新增 `ComputerSyncService`。
6. 实现 `/health` 测试连接。
7. 实现 `/manifest.json` 检查新版本。
8. 实现 `/latest.db` 下载到临时文件。
9. 实现 SHA-256 校验。
10. 调用现有 `AppServices.importProducts(..., ImportFormat.MINGSHENG_DB)`。
11. 导入成功后保存同步状态。

### 23.2 前端/UI 任务

1. 导入页新增“电脑同步”卡片。
2. 新增扫码连接入口。
3. 解析 `mobilepos-sync://setup?...`。
4. 显示同步配置状态。
5. 显示测试连接按钮。
6. 显示检查新版本按钮。
7. 显示立即同步按钮。
8. 新版本确认弹窗。
9. 本地手动修改二次确认弹窗。
10. 错误提示。

### 23.3 阶段 B 验收

- 手机扫码二维码后自动保存 host/port/token。
- 测试连接成功。
- token 错误时提示重新扫码。
- 电脑端无备份时提示先备份。
- 有新 manifest 时提示确认导入。
- 本地有手动修改时二次确认。
- 下载 `.db` 后 hash 校验通过才导入。
- 导入成功后商品库更新。
- 导入成功后保留手机端快照。
- 现有手动 `.db` 导入、CSV 导入、商品编辑、收银不回退。

## 24. 多 agent 提示词

### 24.1 电脑端后端 agent

```text
你负责 pc-sync-tool 的后端模块。

必须遵守硬性安全边界：
- 只能只读打开鸣盛源 db。
- 只能写入 pc-sync-tool 自己的 AppData 配置、备份、日志目录。
- 禁止修改、删除、移动、重命名任何鸣盛目录文件。
- 禁止修改 AGT_MAIN.db 原文件。
- 禁止碰 MS2011 / MS2011BAK 加密备份。

任务：
- 配置读写
- DB 来源定位
- CJQ_GOODLIST 只读表校验
- 稳定性检查
- 原子复制
- sha256
- manifest
- HTTP API
- token 校验
- 最近 5 个备份
- 最近 200 条事件日志

保持模块化和函数拆分。不要把 GUI、HTTP、复制、配置写在一个文件里。
完成后提供单元测试和手工验收说明。
```

### 24.2 电脑端前端 agent

```text
你负责 pc-sync-tool 的 PySide6/Qt 前端。

只调用后端服务接口，不直接复制文件、不直接写 manifest、不直接访问鸣盛源文件。

任务：
- 主窗口
- 托盘菜单
- 来源选择 UI
- 备份间隔 UI
- 端口/IP/token UI
- 二维码展示
- 状态面板
- 日志列表
- 关闭窗口最小化到托盘

保持 UI 和业务逻辑分离。不要把备份逻辑写进按钮回调。
```

### 24.3 手机端后端 agent

```text
你负责 android-emergency-pos 手机端同步后端。

任务：
- ComputerSyncConfig
- ComputerSyncStore
- ComputerSyncClient
- ComputerSyncManifest
- ComputerSyncService
- health/manifest/latest.db 请求
- sha256 校验
- 下载临时 db
- 调用现有 ImportFormat.MINGSHENG_DB 导入
- 保存 lastSeenSha256 / lastSyncedSha256 / lastCheckedAt / lastSyncedAt

禁止修改鸣盛导入 mapper 的既有字段规则。
禁止改变收银、商品编辑、CSV 导入逻辑。
保持模块化和函数拆分。
```

### 24.4 手机端前端 agent

```text
你负责 android-emergency-pos 手机端同步 UI。

任务：
- 导入页新增电脑同步卡片
- 扫码连接电脑工具
- 解析 mobilepos-sync://setup?host=...&port=...&token=...
- 显示连接状态
- 测试连接
- 检查新版本
- 立即同步
- 导入确认弹窗
- 本地手动修改二次确认弹窗
- 错误提示

不要把 HTTP 请求、hash 校验、DB 导入写进 ImportScreen。
UI 只调用 ComputerSyncService。
```

### 24.5 验收 agent

```text
你负责验收电脑端同步工具和手机端同步接入，不做实现。

重点检查：
- 电脑端是否绝对不修改鸣盛原文件。
- 电脑端是否只写 AppData 自己目录。
- 备份是否原子写入。
- token 错误是否返回 403。
- manifest/hash 是否正确。
- latest.db 下载文件 hash 是否匹配。
- 手机扫码是否能保存配置。
- 手机是否先确认再导入。
- 本地手动修改是否二次确认。
- 现有手动导入、CSV 导入、收银、商品编辑是否无回归。
```

## 25. 不做范围

第一版不做：

- 双向同步。
- 手机写回电脑数据库。
- 修改鸣盛数据库。
- 解析 SQL Server 主库。
- 解密 `MS2011.zip` / `MS2011BAK.zip`。
- 公网同步。
- 内网穿透。
- 多手机设备管理。
- 电脑端商品数量解析。
- Android 后台定时同步。
- Excel 导入扩展。

## 26. 完成定义

阶段 A 完成：

- 电脑端工具源码可运行。
- 可配置 DB 文件或文件夹。
- 可只读备份到 AppData。
- latest/manifest/history 正常。
- HTTP API 正常。
- 二维码正常。
- token 安全校验正常。
- 日志正常。
- 托盘正常。
- 不修改鸣盛任何已有文件。

阶段 B 完成：

- 手机端可扫码配置。
- 可测试连接。
- 可检查新版本。
- 可下载 latest.db。
- 可校验 SHA-256。
- 可确认后导入。
- 本地修改时二次确认。
- 同步状态保存。
- 现有功能无回归。
