# 项目日志

## 2026-07-07

### 精简收银页商品行 UI

- 问题：收银页中每个购物车商品行直接显示两排操作按钮，多个商品时页面高度增长过快，影响收银操作效率。
- 修复：将每行商品下方的 `-`、`+`、改价、删除、折扣%、减价、撤回改动，移动到商品行右侧“操作 / Mas”按钮打开的二级操作弹窗。
- UI 变化：购物车商品行现在只保留商品名、条码、数量、单价、小计，以及必要的已改价、已优惠、促销状态标记。
- 影响范围：仅修改 `CheckoutScreen` 的渲染和菜单交互，不改变购物车、优惠、价格计算或销售记录核心逻辑。
- 验证：
  - `CoreSmokeTest` 通过。
  - 完整 debug APK Gradle 构建成功。
  - 新 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`
  - APK 大小：`876821 bytes`。
  - 构建时间：`2026-07-07 18:11:54`。

### 同步项目进度文档

- 对照 `docs/PROJECT_STATUS.md` 和 `docs/PROJECT_LOG.md`，确认进度文档已包含 2026-07-06 的“修复收银/交易明细 tab 无法返回”记录。
- 将 `docs/PROJECT_STATUS.md` 更新日期改为 2026-07-07。
- 在进度文档“已验证”中补充已同步到开发日志记录。
- 修正“建议下一步”中的重复编号。
- 同步更新 `mobile_pos_publish/docs/PROJECT_STATUS.md`。

### 同步收银商品行 UI 更新和搜索性能问题

- 将进度文档底部散落的“2026-07-07 收银商品行 UI 更新”整合进“已实现”和“已验证”章节。
- 将最新 APK 路径、大小和构建时间更新为 `E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`、`876821 bytes`、`2026-07-07 18:11:54`。
- 记录当前体验问题：收银和商品编辑中的关键词搜索在弹出结果前有短暂停顿，需要优化搜索索引、结果数量限制或异步搜索体验。
- 同步更新 `mobile_pos_publish/docs/PROJECT_STATUS.md` 和 `mobile_pos_publish/docs/PROJECT_LOG.md`。

## 2026-07-06

### 修复收银/交易明细 tab 无法返回

- 问题：在收银页进入“交易明细”后，无法再切回“收银”。
- 原因：`CheckoutSectionScreen` 的 tab 按钮只在初次渲染时设置启用状态。初始状态下“收银”按钮被禁用；切换到交易明细后，内容区刷新了，但 tab 按钮状态没有刷新，所以“收银”按钮仍然不可点。
- 修复：为“收银”和“交易明细”按钮保留引用，在 `switchTo(...)` 时同步调用 `updateTabButtons()`，确保当前 tab 禁用、另一个 tab 可点击。
- 影响范围：只影响收银页内部 tab 导航，不改变购物车、销售记录、价格计算或商品库逻辑。
- 验证：
  - `CoreSmokeTest` 通过。
  - debug APK Gradle 构建成功。
  - 新 APK：`E:\AndroidEmergencyPos\app\build\outputs\apk\debug\app-debug.apk`
  - APK 大小：874181 bytes。
  - 构建时间：2026-07-06 20:06:46。

### 文档同步

- 更新 `docs/PROJECT_STATUS.md`，记录 tab 修复、最新 APK 大小和构建时间。
- 从本次开始维护 `docs/PROJECT_LOG.md` 作为持续项目日志。
## 2026-07-07

### 搜索无卡顿优化开发验收完成

- 依据：`修改方案/search_optimization_plan.md`。
- 后端完成项：
  - 新增 `ProductSearchEntry` 保存商品预处理搜索字段和 token。
  - `InMemoryProductRepository` 维护搜索索引，避免每次搜索重复 normalize 每个商品字段。
  - `replaceAll()`、`upsert()`、`deleteById()` 同步维护搜索索引。
  - 搜索结果继续支持全部匹配返回，不做固定 10 条上限。
- 前端完成项：
  - 新增 `SearchTaskRunner`，将收银页和商品编辑页搜索移到后台线程。
  - 使用 latest request 防护，避免连续搜索时旧结果覆盖新结果。
  - 新增 `ProductSearchResultAdapter` 和 `ProductSearchResultDialog`，用 `ListView` 渲染搜索结果，避免一次性创建大量按钮。
  - `CheckoutScreen` 搜索结果点击后加入购物车；`ProductEditScreen` 搜索结果点击后进入商品编辑。
- 影响范围：不改变购物车、价格计算、促销计算、销售记录、商品导入字段映射。
- 验收结果：
  - `CoreSmokeTest` 通过。
  - 完整 debug APK Gradle 构建成功。
  - 新 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`
  - APK 大小：`889497 bytes`。
  - 构建时间：`2026-07-07 19:52:44`。
## 2026-07-08

### UI/收银/字体适配改进验收完成

- 依据：
  - `修改方案/ui_checkout_search_font_improvement_plan.md`
  - `修改方案/text_scale_ui_controls_fix_plan.md`
- 搜索交互：
  - 页面切换/重绘时统一取消 pending 搜索回调，避免旧搜索结果弹到新页面。
  - 收银页输入框支持回车触发关键词搜索。
  - 商品编辑页关键词输入框支持回车搜索；条码输入框回车仍走条码查找/新建。
- 现金结账：
  - 新增 core 层 `CashChangeCalculator` / `CashChangeResult`。
  - 新增 `CashPaymentDialog`，现金支付时先输入客户付款金额，金额不足不保存，金额足够显示找零并确认结账。
  - 非现金支付流程不变。
- 字体大小：
  - 新增 `TextScale` 档位和偏好保存/读取。
  - `StyleGuide` / `Views` 统一控制文本、按钮、输入框、下拉框字体缩放。
  - 设置页支持小、标准、大、特大，切换后重新渲染并持久化。
  - 补齐 `EditText` 与 `Spinner` 字号适配遗漏，商品表单、收银输入框、现金付款输入框、支付方式下拉框均跟随字体档位变化。
- 验收：
  - `rg -n "new EditText\(|new Spinner\(|new ArrayAdapter" android-emergency-pos\app\src\main\java\com\espsa\mobilepos` 检查通过；页面中直接 `Spinner` 创建仅保留控件本体，adapter 已统一走 `Views.spinnerAdapter(...)`。
  - `CoreSmokeTest` 通过。
  - 完整 debug APK Gradle 构建成功。
  - 新 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`
  - APK 大小：`904393 bytes`。
  - 构建时间：`2026-07-08 01:18:20`。
## 2026-07-08

### 卡片式 UI 与多格式导入验收完成

- 依据：`修改方案/ui_cards_and_multi_format_import_plan.md`。
- UI 改动：
  - 主页从简单按钮列表改为卡片式一级导航。
  - 导入页改为格式选择卡片，支持鸣盛数据库 `.db` 和通用 CSV 商品表 `.csv`。
  - 设置页、收银入口页等一级界面同步使用统一卡片风格。
  - 卡片样式集中在 `Views` / `StyleGuide`，没有在各 screen 中重复散落样式。
- 导入架构：
  - 新增 `ImportFormat`、`ProductImportAdapter`、`ImportFormatRegistry`。
  - 保留鸣盛 `.db` 导入能力，文件选择和导入分发按格式处理。
  - 新增 `CsvProductImportAdapter`，支持常见字段别名：条码、名称、售价、分类、单位。
  - CSV 导入会处理重复条码、缺少必填字段、空行、无有效商品等情况，并通过 warning/exception 输出清晰结果。
  - `AppServices.importProducts(...)` 统一导入入口，导入成功后继续复用商品库覆盖、快照和 metadata 保存逻辑。
- 回归范围：
  - 不改变收银、商品编辑、搜索、现金找零、字体大小设置、销售记录逻辑。
- 验收：
  - `CoreSmokeTest` 通过。
  - 完整 debug APK Gradle 构建成功。
  - 新 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`
  - APK 大小：`931969 bytes`。
  - 构建时间：`2026-07-08 02:00:36`。
## 2026-07-09

### 电脑端同步工具后端完成

- 依据：`修改方案/pc_sync_http_tool_plan.md`。
- 新增目录：`E:\手机收银软件开发\pc-sync-tool`。
- 后端完成项：
  - `paths.py`：统一 `%APPDATA%` / `%LOCALAPPDATA%` 下的 `MobilePosSync` 路径。
  - `config.py`：配置读写、默认值、token 生成和校验。
  - `source_locator.py`：文件模式和文件夹自动寻找模式，SQLite 只读校验 `CJQ_GOODLIST`。
  - `backup_worker.py`：稳定性检查、临时文件复制、SHA-256、latest/manifest 原子替换、历史备份保留。
  - `http_server.py`：实现 `/health`、`/manifest.json`、`/latest.db`，token 错误返回 403。
  - `event_log.py`：最近 200 条事件日志。
  - `startup.py`：当前用户 HKCU Run 开机启动封装。
  - `qr_code.py`：生成 `mobilepos-sync://setup?...` 同步地址。
- 安全边界：
  - 只读打开鸣盛源 `.db`。
  - 只写工具自己的 AppData 配置、备份和日志目录。
  - 不修改、删除、移动、重命名鸣盛原目录任何文件。
- 验收：
  - `python -m unittest discover -s tests` 通过，12 个测试 OK。
  - `python -m compileall src tests` 通过。

### 电脑端同步工具后端一致性修复

- 修复 HTTP 服务默认绑定地址未使用 `selectedHost` 的问题；未显式传入 `bind_host` 时现在绑定 `config.selected_host`。
- 修复 `latest.db` 和 `manifest.json` 发布一致性风险；`/latest.db` 下载前必须验证 manifest 存在、`ok=true`、`sizeBytes` 匹配、`sha256` 匹配。
- 修复复制数据库后缺少二次稳定校验的问题；复制完成后会再次检查源文件 size/mtime，复制期间变化则跳过本轮且不发布。
- 修复无有效 manifest/hash 时仍可能下载 `latest.db` 的问题；manifest 缺失返回 `NO_BACKUP_READY`，hash/size 不匹配返回错误并拒绝发送 DB。
- 回归测试已新增对应覆盖；`python -m unittest discover -s tests` 通过，16 个测试 OK。

### 电脑端同步工具发布/下载竞态修复

- 修复 `latest.db` 下载和备份发布之间没有共享锁的问题。
- 新增 `publish_lock.py`，同一套 `AppPaths` 会按备份目录复用同一把 `RLock`。
- `BackupWorker` 发布 `latest.db` 和 `manifest.json` 时持有发布锁。
- `SyncHttpService` 对 `/latest.db` 执行“校验 manifest/hash + 发送文件”时持有同一把发布锁，避免出现旧 hash header 发送新 DB 文件的竞态。
- 新增回归测试确认：
  - `BackupWorker` 与 `SyncHttpService` 共享同一把发布锁。
  - `/latest.db` 请求会等待发布锁释放后才校验并发送文件。
- 验收：`python -m unittest discover -s tests` 通过，20 个测试 OK；`python -m compileall src tests` 通过。

### 电脑端同步工具前端接入

- 依据：`修改方案/pc_sync_http_tool_plan.md`。
- 新增 `pc-sync-tool/src/ui/`：
  - `main_window.py`：PySide6 主窗口、托盘菜单、二维码显示、状态刷新、日志列表、关闭窗口最小化到托盘。
  - `controller.py`：UI 到后端模块的薄控制器，负责保存配置、启动/停止 HTTP、立即备份、读取状态和同步地址。
  - `network.py`：列出可选局域网 IPv4 地址。
- 更新 `pc-sync-tool/src/app.py`：
  - 无参数默认启动桌面 GUI。
  - 保留 `--backup-once`、`--serve`、`--print-setup-url` 后端命令。
  - 修复 `--print-setup-url` 打印后继续输出 help 的问题。
- 更新 `pc-sync-tool/requirements.txt`，补充 `PySide6` 和 `qrcode[pil]`。
- 安全边界：
  - 前端不直接复制文件、不直接写 manifest、不直接操作 HTTP socket 细节。
  - 来源检测、备份、manifest、HTTP、事件日志均调用既有后端模块。
- 验收：
  - `python -m unittest discover -s tests` 通过，12 个测试 OK。
  - `python -m compileall src tests` 通过。
  - 使用工作区临时 AppData 验证 `python src\app.py --print-setup-url` 正常输出同步地址。
- 后续验收：
  - GUI 依赖和 offscreen 主窗口 smoke test 已在后续修复段落完成；正式打包前仍建议在 Windows 桌面真实打开一次窗口确认托盘菜单和关闭最小化交互。

### 电脑端同步工具前端修复

- 修复设置页里选择的局域网 IP 没有显式传给 HTTP 服务的问题：
  - UI 控制器启动服务时将 `config.selected_host` 作为 `bind_host` 传入 `SyncHttpService`。
  - CLI `--serve` 同样按配置的 `selected_host` 绑定。
- 补充 UI 状态：
  - 状态区新增“服务绑定”，显示当前实际监听的 IP:端口。
  - 状态区新增“二维码”，显示二维码指向地址，并提示是否与当前服务绑定一致。
- 修复开机自启动命令：
  - 源码运行时注册为 `python.exe src\app.py --gui`。
  - 打包运行时注册为 exe 本身。
- 验收：
  - 新增 UI 控制器绑定 IP 测试。
  - 新增源码/打包自启动命令测试。
  - `python -m unittest discover -s tests` 通过，18 个测试 OK。
  - `python -m compileall src tests` 通过。

### 电脑端同步工具 GUI 依赖环境与最终验收

- 环境补齐：
  - 新增/确认独立 Python 虚拟环境：`E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv`。
  - 使用该环境验证 `PySide6 6.11.1`、`qrcode`、`Pillow 12.3.0` 可正常导入。
  - 注意：系统 Python 仍不一定能直接导入这些依赖；电脑端 GUI 验收和运行应优先使用该虚拟环境 Python，或后续打包为 exe。
- 依赖接入后回归：
  - 使用虚拟环境 Python 运行 `python -m unittest discover -s tests`，20 个测试 OK。
  - 使用虚拟环境 Python 对关键源码执行 `py_compile`，通过。
  - 单独验证二维码渲染链路，`_qr_pixmap(...)` 可生成非空 `220x220` `QPixmap`。
- 发现并修复 PySide6 6.11.1 兼容问题：
  - 问题：`main_window.py` 原先使用 `self.style().SP_ComputerIcon`，在当前 PySide6 中 `QCommonStyle` 没有该属性，构建 `MainWindow` 时会抛出 `AttributeError`。
  - 修复：导入 `QStyle`，并改为 `self.style().standardIcon(QStyle.StandardPixmap.SP_ComputerIcon)`。
  - 修复后再次运行单测和 `py_compile`，均通过。
- 最终 GUI smoke test：
  - 使用 `QT_QPA_PLATFORM=offscreen` 构建 `QApplication`、`UiController`、`MainWindow`。
  - 使用临时 AppData 配置 `selected_host=127.0.0.1` 和临时端口，窗口构建时 HTTP 服务可自动启动。
  - 验证输出包含：`window_title=MobilePosSync 电脑同步工具`、`service_running=True`、`qr_status=可用`、`qr_pixmap_null=False`。
- 真实 Windows 托盘人工确认：
  - 用户已按清单确认窗口正常打开，二维码正常显示，关闭窗口会最小化到托盘，托盘图标可重新打开窗口，托盘菜单和退出流程正常。
- 结论：
  - 电脑端同步工具阶段 A 已完成代码级验收、无界面 GUI smoke test 和真实 Windows 托盘人工确认。
  - 本轮从电脑端开发开始的修复链路已闭环：后端只读安全边界、HTTP/token API、manifest/hash 校验、发布锁、前端绑定地址与二维码状态、自启动命令、PySide6 兼容和依赖环境均已记录。
  - 后续可以进入手机端同步开发。

### 电脑端同步工具发布同步

- 本轮同步内容：
  - 新增 `pc-sync-tool` 到发布仓库，包含电脑端同步工具源码、PySide6 前端、后端 HTTP/token/manifest 逻辑、测试和运行脚本。
  - 新增 `docs/plans/pc_sync_http_tool_plan.md` 到发布仓库，作为本轮电脑端同步工具阶段 A 的设计和验收依据。
  - 同步 `docs/PROJECT_STATUS.md` 和 `docs/PROJECT_LOG.md`，记录电脑端同步工具阶段 A 已完成验收。
- 回归验证：
  - Android debug APK Gradle 构建成功，本轮源码判断为 up-to-date。
  - `CoreSmokeTest` 通过。
  - 使用 `E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe` 运行 `python -m unittest discover -s tests`，20 个测试 OK。
  - `python -m compileall src tests` 通过。
- 发布边界：
  - 发布仓库只同步源码、计划文档、进度文档和 APK。
  - 不提交真实经营数据库、商品导出表、`python_envs` 虚拟环境、`__pycache__` 或 `.pyc` 缓存文件。

## 2026-07-10

### 手机端电脑同步接入验收

- 新增手机端同步模块：
  - `ComputerSyncConfig`：保存电脑端 host、port、token、lastSeen/lastSynced hash 和时间。
  - `ComputerSyncStore`：使用 `SharedPreferences` 持久化同步配置。
  - `ComputerSyncClient`：通过 `HttpURLConnection` 调用电脑端 `/health`、`/manifest.json`、`/latest.db`，请求均带 token。
  - `ComputerSyncManifest`：解析电脑端 manifest，包括 `ok`、`error`、`fileName`、`sizeBytes`、`sha256`、`createdAt`、`downloadPath`。
  - `ComputerSyncService`：解析 `mobilepos-sync://setup?...` 二维码、测试连接、检查 manifest、下载数据库、计算 SHA-256、标记已同步。
- UI 接入：
  - 导入页新增“电脑同步”卡片，显示配置状态、电脑地址、上次检查和上次同步。
  - 支持扫码连接电脑工具，扫码内容为电脑端二维码生成的 `mobilepos-sync://setup?host=...&port=...&token=...`。
  - 支持测试连接、检查新版本、立即同步。
  - 同步前弹出 manifest 摘要确认；本地存在手动修改或自建商品时，继续弹出二次覆盖确认。
- 业务接入：
  - `AppServices.syncProductsFromComputer(...)` 下载电脑端 latest `.db` 后，复用现有鸣盛 `.db` 导入和商品库保存逻辑。
  - 下载文件会在导入前计算 SHA-256 并与 manifest 比对，不一致时删除临时文件并拒绝导入。
  - 导入成功后更新商品库、导入快照、metadata，并记录 lastSynced hash。
- 权限和扫码：
  - `AndroidManifest.xml` 已加入 `INTERNET` 权限。
  - 既有扫码器已扩展/确认支持 `QR_CODE`。
- 首轮验收发现：
  - 问题 1：用户确认的 manifest 和实际导入的 manifest 可能不是同一个。原因是确认弹窗展示 manifest 后，执行同步时 `AppServices.syncProductsFromComputer(context)` 会重新 `checkManifest()`。
  - 问题 2：“检查新版本”按钮也会进入导入确认。原因是 `ImportScreen.handleManifest(manifest, syncWhenNew)` 没有使用 `syncWhenNew` 参数。
- 首轮验收结果：
  - `CoreSmokeTest` 通过。
  - 完整 debug APK Gradle 构建成功。
  - APK：`E:\AndroidEmergencyPos\app\build\outputs\apk\debug\app-debug.apk`，大小 `951268 bytes`。

### 手机端电脑同步流程修复验收

- 修复“确认的 manifest 与实际导入 manifest 不一致”：
  - `ImportScreen.confirmSync(manifest)`、`confirmSyncWithLocalChanges(manifest)`、`syncNow(manifest)` 保持传递同一个 `ComputerSyncManifest`。
  - `AppServices` 新增 `syncProductsFromComputer(Context context, ComputerSyncManifest confirmedManifest)`。
  - 下载、导入和 `markSynced` 均使用用户已确认的 `confirmedManifest`。
  - 保留无参 `syncProductsFromComputer(context)` 作为便捷入口，但导入页确认流程不再使用无参入口。
- 修复“检查新版本会触发导入确认”：
  - `handleManifest(manifest, false)` 现在只显示“已是最新版本”或“发现新版本”。
  - `handleManifest(manifest, true)` 才进入 `confirmSync(manifest)`。
- 保持原有保护：
  - 导入前确认仍保留。
  - 本地手动修改/自建商品二次确认仍保留。
  - SHA-256 校验仍由 `ComputerSyncService.downloadLatestDatabase(...)` 执行。
- 回归验证：
  - `CoreSmokeTest` 通过。
  - 已同步源码到 `E:\AndroidEmergencyPos` ASCII 构建副本。
  - 完整 debug APK Gradle 构建成功：`:app:assembleDebug BUILD SUCCESSFUL`。
  - 最新 APK：`E:\AndroidEmergencyPos\app\build\outputs\apk\debug\app-debug.apk`。
  - 发布/本地 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`。
  - APK 大小：`951496 bytes`。
  - 构建时间：`2026-07-10 13:51:40`。
- 结论：
  - 手机端电脑同步开发和本轮修复已通过代码级验收与完整 APK 构建。
  - 下一步需要做电脑端 `pc-sync-tool` + 手机 App 的真实端到端联调：扫码配置、连接测试、检查新版本、立即同步、hash 校验、导入覆盖确认、本地修改二次确认和导入后商品搜索/收银验证。

### 手机端电脑同步发布同步

- 本轮同步内容：
  - 同步 Android 手机端电脑同步接入源码到 GitHub 发布仓库。
  - 同步最新 debug APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 `951496 bytes`。
  - 同步 `docs/PROJECT_STATUS.md` 和 `docs/PROJECT_LOG.md` 的 2026-07-10 验收记录。
- 回归验证：
  - 已同步源码到 `E:\AndroidEmergencyPos` 构建副本。
  - 完整 debug APK Gradle 构建成功。
  - `CoreSmokeTest` 通过。
  - 电脑端 `pc-sync-tool` 20 个单测通过，`python -m compileall src tests` 通过。
- 发布边界：
  - 不提交真实经营数据库、商品导出表、`python_envs` 虚拟环境、Gradle build 目录、Python 缓存或 `.pyc` 文件。
