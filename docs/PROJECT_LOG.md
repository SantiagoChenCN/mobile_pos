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

### 电脑端手动连接信息后端接入

- 依据：`修改方案/manual_token_sync_connection_plan.md`。
- 本轮只做电脑端后端增量，不改 HTTP 协议、不改备份/hash/manifest 逻辑、不删除二维码 UI。
- 新增 `pc-sync-tool/src/connection_info.py`，集中生成手动连接所需的电脑 IP、端口和 Token。
- `UiController` 新增 `connection_host()`、`connection_port()`、`connection_token()`、`connection_summary()`，供后续前端连接信息卡片和复制按钮调用。
- `app.py` 新增 `--print-connection-info`，输出：
  - `电脑IP：...`
  - `端口：...`
  - `Token：...`
- `--print-setup-url` 初版曾保留兼容；后续已按手动连接方案删除。
- 验证：`python -m unittest discover -s tests` 通过，23 个测试 OK；`python -m compileall src tests` 通过。

### 电脑端旧二维码后端入口删除

- 删除旧二维码模块：`pc-sync-tool/src/qr_code.py`。
- 删除 `app.py` 中的旧导入、`--print-setup-url` 参数和对应输出分支。
- `README.md` 已改为使用 `python src\app.py --print-connection-info`，不再描述扫码/二维码连接。
- `test_config_and_event_log.py` 已从 setup URL 测试改为 `connection_summary` 测试。
- 检查确认 `pc-sync-tool/src`、`tests`、`README.md`、`requirements.txt`、`scripts` 中无 `qr_code`、`setup_url`、`--print-setup-url` 或二维码/扫码连接引用。
- 验证：`python -m unittest discover -s tests` 通过，23 个测试 OK；`python -m compileall src tests` 通过。

### 电脑端手动连接信息前端接入

- 依据：`修改方案/manual_token_sync_connection_plan.md`。
- 本轮只做电脑端前端和打包依赖调整，不改 HTTP 协议、不改备份/hash/manifest 逻辑。
- 主窗口改动：
  - 删除二维码卡片和二维码渲染函数。
  - 新增“手机连接信息”卡片，显示电脑 IP、端口、Token 和 HTTP 连接状态。
  - 新增复制全部连接信息、复制 IP、复制 Token 按钮。
  - 状态区“二维码”改为“手机连接”。
  - 底部按钮和托盘菜单改为“复制连接信息”。
  - Token 重新生成提示改为手机端需要重新输入新 Token。
- 依赖/打包改动：
  - `requirements.txt` 移除 `qrcode[pil]`。
  - `scripts/build_exe.ps1` 移除 `--hidden-import qrcode.image.pil`。
  - `qr_code.py` 和 `--print-setup-url` 已删除，不再调用。
- 验证：
  - 使用 `E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe` 运行 `python -m unittest discover -s tests`，23 个测试 OK。
  - `python -m compileall src tests` 通过。
  - offscreen 构建 `MainWindow` 成功，连接信息字段输出 `127.0.0.1`、测试端口、`TOKEN123`，状态为 `可用`。

### 2026-07-11 电脑端局域网连接修复（后端）

- 依据：`修改方案/computer_phone_sync_connection_fix_plan.md`；本轮仅修改 `pc-sync-tool` 后端与测试，不改 PySide6 布局、Android 或鸣盛原始数据库。
- HTTP 监听地址与手机展示地址已分离：`SyncHttpService`、GUI controller 和 `--serve` 统一监听 `0.0.0.0`；`selected_host` 仅表示手机应输入的局域网 IPv4，`/health.host` 返回该展示地址。
- 新增 `network.py`：候选地址会排除 `127.0.0.1`、`0.0.0.0` 和 `169.254.x.x`，优先常见私有 IPv4；首次生成配置时存在可用局域网 IPv4 则作为默认展示地址。
- 新增 `network_diagnostics.py`：返回服务状态、本机 `/health` 校验、监听地址、展示地址、端口、告警代码和消息；诊断不读取或修改鸣盛数据库，也不记录 Token。
- HTTP 事件日志新增健康检查成功和无效 Token 请求记录，均不写入查询字符串或 Token 值。
- 连接摘要不会把 `0.0.0.0`、`127.0.0.1` 或 `169.254.x.x` 作为可复制给手机的 IP。
- 防火墙策略保持只读提示边界：本轮没有新增、修改或删除 Windows 防火墙规则。
- 验证：`python -m unittest discover -s tests -v` 通过，30 个测试 OK；`src` 与 `tests` 的 Python 编译检查通过。
- 待人工验收：在真实手机与收银电脑同一局域网下验证端口入站访问；若本机健康检查成功而手机仍无法访问，应检查 Windows 防火墙、同一 Wi-Fi 与 AP/客户端隔离。

### 2026-07-11 电脑端 IPv4 连接地址校验补充

- `network.py` 的 `is_phone_connectable_host()` 作为唯一的手机连接地址校验入口，拒绝：`127.0.0.1`、`0.0.0.0`、`169.254.x.x`、组播地址、`255.255.255.255` 和保留 IPv4。
- 正常局域网 IPv4（覆盖 `10.x.x.x`、`172.16.x.x`、`192.168.x.x`）保持可用；候选地址发现也复用同一规则。
- `ConnectionInfo` 对无效地址不生成可复制的结构化连接信息，摘要不显示无效 IP；UI 继续通过既有诊断结果控制复制按钮，无需重复实现地址判断。
- HTTP 服务继续监听 `0.0.0.0`；`/health` 对有效配置返回所选局域网 IP，对遗留无效配置绝不回传 `0.0.0.0`。
- 验证：使用 `E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe` 执行 `python -m unittest discover -s tests -v`，37 个测试 OK；Python 编译检查通过。

### 2026-07-11 电脑端阿根廷时间格式化后端模块

- 依据：`修改方案/argentina_time_and_cart_merge_plan.md` 第 6 节；本轮只实现电脑端后端部分，不修改电脑端 UI 接入、Android 或购物车逻辑。
- 新增 `pc-sync-tool/src/time_display.py`，提供 `parse_iso_datetime()` 和 `format_argentina_time()`。
- 支持 `Z`、`+00:00`、`-03:00`、带时区 `datetime` 和无时区旧 `datetime`；无时区值按 UTC 兼容旧数据，不读取电脑系统时区。
- 优先使用 `America/Argentina/Buenos_Aires`，Windows/PyInstaller 缺少 IANA 时区数据时回退到固定 `UTC-03:00 / ART`，不引入第三方依赖。
- 空值返回 `-`，非法历史文本原样返回，避免展示层崩溃。
- 未修改 `manifest.utc_now_iso()`、manifest `createdAt/version`、事件日志写入时间和备份历史文件名，它们继续保存 UTC 绝对时间。
- 新增 `tests/test_time_display.py`，覆盖日期跨界、空值、非法文本、Z/偏移 ISO、带时区和无时区 `datetime`。

### 2026-07-11 电脑端局域网连接修复（前端）

- 依据：`修改方案/computer_phone_sync_connection_fix_plan.md`；本轮只修改 `pc-sync-tool` 的 PySide6 展示层及对应测试，不改 HTTP 协议、配置读写、防火墙规则或鸣盛数据库。
- 新增 `ui/connection_presentation.py`：集中把结构化网络诊断结果转换为连接状态、地址警告、复制权限和人工排查提示，避免将判断逻辑堆入主窗口。
- “手机连接信息”卡片现在同时显示手机应输入的局域网 IP 与实际监听地址（例如 `0.0.0.0:8765`），两者用途明确分离。
- 当服务未运行、本机健康检查失败或地址为 `127.0.0.1`、`0.0.0.0`、`169.254.x.x` 时，界面显示可执行的提示；无效地址或未运行服务时禁用“复制连接信息”和“复制 IP”，Token 仍不会写入事件日志或错误详情。
- 本机健康检查通过后，界面提示手机仍连接失败时检查 Windows 防火墙、同一 Wi-Fi 和路由器客户端隔离；程序没有新增或修改防火墙规则。
- 主窗口主体改为可滚动容器，并设定最小窗口尺寸，缩小时连接信息仍可访问。
- 为避免事件日志被本机 `/health` 检查刷满，诊断仅在启动、停止或保存连接设置后刷新，3 秒状态轮询只渲染缓存的诊断结果。
- 验证：使用 `E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe` 运行 `python -m unittest discover -s tests -v`，35 个测试全部通过；offscreen 主窗口测试验证了监听地址显示、无效地址禁用复制和滚动容器；`python -m compileall -q src tests` 通过。

### 2026-07-11 电脑端 IP 校验与状态展示修正

- 根据连接修复反馈，IP 校验改为由 `network.py` 的结构化 `PhoneHostValidation` 统一提供；`NetworkDiagnosticResult` 无论 HTTP 服务是否运行都会携带该结果，前端不再直接导入或调用 IP 校验函数。
- 校验会拒绝回环、`0.0.0.0`、链路本地、组播、广播/保留、非私有 IPv4 和非 IPv4 输入，并提供可执行的中文提示；无效地址时禁用“复制 IP”和“复制全部连接信息”。
- Windows 下候选地址优先读取实体 Ethernet/Wi-Fi 适配器，并按描述排除常见虚拟、隧道、VPN、Docker 和 WSL 适配器；地址下拉框只保留经相同校验通过的私有局域网 IPv4，因此不会推荐回环、组播、广播或公网地址。
- 连接卡片将“HTTP 服务”“实际监听”“供手机连接的地址”“本机健康检查”分开显示；防火墙、同一 Wi-Fi 和 AP 隔离仍为只读人工排查提示。
- 验证：指定 PySide6 虚拟环境运行 `python -m unittest discover -s tests -v`，40 个测试全部通过；`python -m compileall -q src tests` 通过；当前 Windows 环境地址发现结果为 `192.168.0.197`。

### 2026-07-11 手机端局域网连接修复

- 修复背景：手机端手动填写电脑 IP、端口和 Token 后，测试连接显示“电脑同步失败，无法读取电脑同步工具响应”。静态检查确认 Android 目标版本为 35，原清单没有允许局域网明文 HTTP；电脑端原先还可能只绑定 `127.0.0.1`。
- Android 清单修复：`AndroidManifest.xml` 增加 `android:usesCleartextTraffic="true"`；构建后检查最终合并 manifest，确认该配置确实进入 APK。
- 手机端同步后端修复：新增 `ComputerSyncFailureReason` 和结构化异常；`ComputerSyncClient` 区分明文 HTTP 阻止、连接超时、连接拒绝、未知地址、HTTP 403 Token 错误、其他 HTTP 错误和无效 JSON/健康响应；不把完整 URL 或 Token 写入错误信息。
- 手机端配置修复：`ComputerSyncService` 拒绝 `127.0.0.1`、`localhost`、`0.0.0.0`、无效 IPv4、无效端口和空 Token；`/health` 必须确认返回的是 `MobilePosSync`，并校验版本、主机和端口字段。
- 手机端前端修复：新增 `ComputerSyncErrorPresenter` 和 `ComputerSyncErrorPresentation`，按错误类型输出中文/西班牙语的具体排查建议；连接成功显示电脑 IP、端口和同步工具版本。
- 离开页面修复：`ImportScreen` 对页面脱离进行监听，使用任务 generation、运行任务集合和 `dispose()` 中断/忽略旧连接任务，防止离开页面后旧回调弹窗或更新已销毁页面。
- Android 验证：`CoreSmokeTest`、`ComputerSyncClientSmokeTest`、`ComputerSyncServiceSmokeTest`、`ComputerSyncErrorPresenterSmokeTest` 全部通过；完整 Debug APK Gradle 构建成功；项目 APK 与构建输出 SHA-256 一致，最新 APK 为 `E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 `979155 bytes`，时间 `2026-07-11 01:05:42`。

### 2026-07-11 连接修复综合验收

- 电脑端后端：HTTP 服务监听地址与手机展示地址已分离，监听 `0.0.0.0`；局域网地址过滤已覆盖回环、未指定、链路本地、组播、广播/保留和非私有地址；`/health` 不返回 `0.0.0.0`；HTTP 事件日志不记录 Token。
- 电脑端前端：连接信息卡片分别显示手机连接 IP、实际监听地址、HTTP 服务状态和本机健康检查；无效地址或服务未运行时禁用复制按钮，并显示防火墙、同一 Wi-Fi 和 AP 隔离提示。
- 电脑端验证：使用项目 PySide6 虚拟环境运行 `python -m unittest discover -s tests -v`，40 个测试全部通过；`python -m compileall -q src tests` 通过。
- 手机端验证：Android 构建成功，最终 manifest 包含 `usesCleartextTraffic=true`，同步 Client、Service 和错误 presenter 烟测全部通过。
- 发布状态：新版 APK 已同步到项目 `dist`；电脑端 EXE 和 ZIP 仍为本轮修复前的旧产物，尚未重新打包，不能用于最终联调。
- 安全边界：本轮没有读取、修改、移动、覆盖或锁定鸣盛软件及其原始数据库文件；程序仍只处理工具自己的配置、备份和日志目录。
- 下一步：重新打包电脑端 EXE/ZIP，在手机和目标收银电脑同一局域网下完成 `/health`、测试连接、检查 manifest、下载数据库、SHA-256 校验和导入流程的真实端到端联调。

### 2026-07-11 电脑端连接修复版本重新打包与发布准备

- 针对上一条记录中电脑端 EXE/ZIP 仍为旧产物的问题，使用当前源码重新执行 PyInstaller 打包。
- 新 EXE：`pc-sync-tool/dist/MobilePosSync/MobilePosSync.exe`，大小 `2288007 bytes`。
- 新 ZIP：`pc-sync-tool/dist/MobilePosSync-windows-20260711.zip`，大小 `48572239 bytes`。
- 回归验证：电脑端 `python -m unittest discover -s tests -v` 通过，40 个测试 OK；`python -m compileall -q src tests` 通过；PyInstaller 构建成功。
- 本轮不读取、不修改鸣盛原始数据库；打包内容只包含电脑端同步工具及其运行依赖。真实手机与收银电脑的局域网端到端联调仍需在目标设备上执行。

### 2026-07-11 电脑端阿根廷时间展示前端接入

- 依据：`修改方案/argentina_time_and_cart_merge_plan.md` 第 7 节；本轮严格限定在 `pc-sync-tool` 电脑端前端展示，不修改 Android、购物车、manifest 协议或 UTC 存储。
- `UiController.latest_backup_text()` 对 manifest 的 `createdAt` 使用后端已有 `time_display.format_argentina_time()`。
- `UiController.latest_request_text()` 和 `_format_event()` 对事件日志时间使用统一格式化器。
- `MainWindow._refresh_log()` 对事件日志列表时间使用统一格式化器；最近备份、最近请求和日志列表均显示 `yyyy-MM-dd HH:mm:ss ART`。
- 原始 manifest、事件日志和备份文件名仍由原有逻辑保存 UTC/带时区 ISO 时间，电脑系统时区不会影响界面显示。
- 新增 controller 与 offscreen 主窗口回归测试，覆盖 UTC 跨日期转换后的用户可见时间。
- 验证：使用 `E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe` 运行 `python -m unittest discover -s tests -v`，50 个测试全部通过；`python -m compileall -q src tests` 通过。

### 2026-07-11 电脑端前端时间展示版本打包

- 使用项目指定虚拟环境执行 PyInstaller，重新生成 `pc-sync-tool/dist/MobilePosSync/MobilePosSync.exe`，大小 `2311392 bytes`。
- 重新生成 `pc-sync-tool/dist/MobilePosSync-windows.zip` 和 `pc-sync-tool/dist/MobilePosSync-windows-20260711.zip`，大小均为 `48624632 bytes`。
- 两个 ZIP 均核对包含当前 EXE，归档条目数均为 210；未读取、修改或打包鸣盛原始数据库文件。

### 2026-07-11 手机端阿根廷业务时间与购物车同商品合并

- 依据：`修改方案/argentina_time_and_cart_merge_plan.md`。
- 新增 `app/time/ArgentinaTime.java`，统一使用 `America/Argentina/Buenos_Aires`，显示格式为 `yyyy-MM-dd HH:mm:ss ART`；空值显示 `-`，非法历史时间安全回退，不受手机系统时区影响。
- 同步/导入页面的最近导入、上次检查、上次同步、manifest 时间和最近导入快照均接入 `ArgentinaTime`。
- `AppServices` 装配的 `CheckoutService` 和 `LedgerService` 统一使用阿根廷业务时区；交易列表和日账的“今天”使用 `ArgentinaTime.today()`，销售单号时间也使用传入的阿根廷业务时区。
- UTC 边界保持不变：销售 `Instant`、同步检查/完成时间、manifest、导入快照存储和 HTTP 协议继续保存 UTC/带时区 ISO 时间，仅在 UI 展示时转换。
- `core/checkout/Cart.addProduct()` 改为正式商品按稳定 `product.id` 查找已有行并累加数量；通过 `existing.withQuantity()` 保留原行 ID、商品快照、手动单价和单行折扣，价格计算器会按合并后的数量重新计算数量促销。
- 扫码、条码输入和搜索选择均复用 `Cart.addProduct()`，不在 UI 重复实现合并规则。
- 手动 `almacen` 商品不参与合并，即使价格相同也保持独立行。

### 2026-07-11 手动商品 ID 冲突边界修复与最终验收

- 首轮验收发现：购物车合并只检查新加入商品是否为手动商品；极端情况下，正式商品 ID 与已有手动商品 ID 相同时可能错误合并。
- `Cart.sameProduct()` 已改为同时检查左右商品：任意一侧 `isManualPriceProduct()` 为 true 时都返回 false，只允许两个正式商品按相同 `product.id` 合并。
- `CartMergeSmokeTest` 新增两个方向的冲突测试：先手动后正式、先正式后手动；相同 ID 均保持两行。
- 回归测试通过：`CoreSmokeTest`、`CartMergeSmokeTest`、`ArgentinaLedgerDateSmokeTest`、`ArgentinaTimeSmokeTest`、`ComputerSyncClientSmokeTest`、`ComputerSyncServiceSmokeTest`、`ComputerSyncErrorPresenterSmokeTest` 全部通过。
- 完整 Android Debug APK Gradle 构建成功。
- 构建 APK 与项目 `android-emergency-pos/dist/EmergencyPOS-debug.apk` 大小均为 `1032530 bytes`，时间均为 `2026-07-11 10:17:56`。
- 两个 APK 的 SHA-256 完全一致：`48D488084C4160B999090647EA3130619040CB151A899E543495E604AF52E7C2`。
- 电脑端阿根廷时间版本已重新打包：EXE/ZIP 时间晚于最新电脑端源码；电脑端 50 个测试和 `compileall` 已通过。
- 安全边界保持不变：未读取、修改、移动、覆盖或锁定鸣盛软件及其原始数据库文件。
- 剩余人工验收：在真机上分别通过扫码、条码输入和搜索连续加入同一正式商品，确认购物车只显示一行并依次变为 `x2/x3`；同时检查手动 `almacen` 商品仍为独立行。

### 2026-07-11 阿根廷时间与购物车合并版本发布准备

- 同步最新手机端和电脑端开发后，重新运行电脑端完整回归：50 个测试通过，`python -m compileall -q src tests` 通过。
- 发现电脑端打包环境缺少 `tzdata`，导致 Windows Python 无法加载 `America/Argentina/Buenos_Aires`；已将 `tzdata>=2025.1` 写入 `pc-sync-tool/requirements.txt` 并安装到 E 盘虚拟环境。
- 时区加载检查通过：`ZoneInfo('America/Argentina/Buenos_Aires')` 可正常加载；PyInstaller 日志已识别并打包 `tzdata` 时区数据，不再出现隐藏导入缺失警告。
- 重新打包电脑端：EXE `2314688 bytes`；ZIP `MobilePosSync-windows-20260711-argentina-time-cart-merge.zip`，`48973489 bytes`。
- 电脑端 ZIP 未包含鸣盛原始数据库、商品导出文件、Python 虚拟环境或构建缓存；本轮仅发布同步工具及其运行依赖。
- 手机端最新 APK 已由前一轮 Android Gradle 验收生成，大小 `1032530 bytes`；本轮将与电脑端新 ZIP 一并同步发布。

### 2026-07-14 文档、README 与修改方案状态同步

- 对照当前源码、测试、构建产物和 `修改方案` 目录重新核对项目状态。
- 新增 `docs/IMPLEMENTATION_STATUS.md` 作为总进度索引，覆盖 Android、电脑端同步工具、已完成方案、MS2011 方案状态和剩余验收。
- 新增 `修改方案/README.md` 作为修改方案索引：已完成方案共 9 份；MS2011 商品/促销实时同步的计划文档共 2 份，目前为规划和证据收集状态，未开始正式实现。
- 更新主发布仓库 README、Android README 和电脑端 README，补充当前功能、手动 IP/端口/Token 连接、LAN 安全边界、`tzdata` 依赖、打包方式和人工联调要求。
- 本轮没有修改鸣盛软件、原始数据库、SQL Server、MDF/LDF 或外部经营数据，也没有把未验证的促销规则实现或宣称为已支持。

## 2026-07-12

### 鸣盛 EPSA 软件副本只读静态分析

- 分析对象：`E:\手机收银软件开发\EPSA\ESpsa (1)`；全程仅做静态读取，没有运行 EXE、DLL、APK、BAT、REG 或 SQL 脚本，也没有修改副本文件。
- 共清点 764 个文件，全部能够以只读方式打开；未发现文件 ACL 拒绝、隐藏文件或系统文件。
- 副本不含鸣盛核心业务程序的完整原始工程。`ESPSA_Pro.exe`、`Pventa_Pro.exe`、`TBT.exe`、`WebShopService.exe` 等为受保护的 32 位原生程序，只能读取 PE 元数据、依赖、资源和有限字符串；`IDATA.exe`、`MSBASIC.exe`、`MSUPDATE.exe` 为 .NET 程序，可恢复部分近似伪源码，但不等于原始源码。
- `AGT_MAIN.db`、`AGT_REPORT.db`、`AGT_PRINT.db` 为可完整只读查询的 SQLite 数据库；其中 EPSA 副本的商品快照含 11,141 条商品。
- 拷贝的 `SQL2000\Data` 目录不含实时 `MS2011.MDF/LDF`，仅有加密的每日备份 ZIP；因此确认此前使用的 `.db` 是商品快照，不是实时修改的 SQL Server 主库。
- 发现配置文件含数据库凭据、支付 Token 等敏感信息，报告未展示具体值；同时识别到关闭防火墙、删除数据库和清空营业记录等高风险维护脚本，明确禁止在日常收银电脑执行。
- 产出：`E:\手机收银软件开发\鸣盛收银软件_EPSA_静态分析报告.md`。

## 2026-07-13

### 实时 MS2011 主库定位与只读结构确认

- 在收银电脑确认 SQL Server 2000 实时数据文件位于 `D:\Espsa\SQL2000\Data\MS2011.MDF`，日志文件为对应的 `MS2011.LDF`；数据库名为 `MS2011`。
- 运行中的 MDF/LDF 由 SQL Server 占用，不能把直接复制数据库文件作为实时同步方案；改为通过 SQL Server 正常连接执行只读查询和导出。
- 在 SQL 查询分析器连接 `SERVER`，选择 `MS2011`，仅执行 `SELECT` 和系统表元数据查询，确认主要表：商品 `MS_GOODLIST`、分类 `MS_GOODTYPELIST`、单位 `MS_UNITLIST`，以及促销相关表。
- `MS_GOODLIST` 已确认包含 59 个字段，包括 `GID`、`GBarcode`、`GNameX`、`GSalePrice`、`GHuiPrice`、`GHuiPriceCount`、`GClass`、`GUnit`、`GStopFlag`、`GUpdateTime` 等。
- 查询显示实时主库当时有 11,168 条商品、无空条码，更新时间范围为 `2026-06-25 00:00:00` 至 `2026-07-12 21:14:16`。
- 操作安全边界：未执行 `INSERT`、`UPDATE`、`DELETE`、建表、恢复、分离、附加或服务停止；正常 SQL 连接和查询仍可能被 SQL Server 日志、Trace、审计或远程运维工具记录，不做规避。

### BCP 驱动冲突与外置只读导出解决

- 初次使用 `D:\Espsa\SQL2000\Tools\bcp.exe` 导出时失败，错误为 `[Microsoft][ODBC SQL Server Driver]ODBCBCP/驱动程序版本不匹配`。
- 为避免修改鸣盛安装目录，在 `D:\MS2011_PRODUCT_EXPORT_20260713` 外置目录准备 BCP 运行文件；只复制 `bcp.exe` 和对应资源文件，不复制旧 `odbcbcp.dll`，并临时让 PATH 优先使用 Windows `SysWOW64/System32` 中匹配的系统驱动。
- 使用 Windows 身份验证和 Unicode 文本导出：`bcp ... out ... -S SERVER -T -w`。该命令只读取表数据，导出文件全部写入外置目录，没有在 Espsa 内创建文件或替换原组件。
- 成功导出 13 张表：`MS_GOODLIST`、`MS_GOODTYPELIST`、`MS_UNITLIST`、`MS_CUXIAO_GOOD`、`MS_SALE_CXDAN1`、`MS_SALE_CXDETAIL1`、`MS_SALE_CXMASTERDING`、`MS_SALE_CXMASTERFOUR`、`MS_SALE_CXTABLE1`、`MS_SALE_CXTABLEDING`、`MS_SALE_CXTABLEFOUR`、`MS_SALE_WEEKDETAIL1`、`MS_SALE_WEEKDING`。
- 导出随后复制到工作区：`E:\手机收银软件开发\MS2011_PRODUCT_EXPORT_20260713\MS2011_PRODUCT_EXPORT_20260713`。

### 商品、分类、单位与促销数据分析

- 13 个 TSV 均可按 BCP Unicode（UTF-16）读取，每行列数均与表结构一致，主键无空值、无重复，未发现完全重复行。
- 实时 `MS_GOODLIST` 有 11,168 条商品，比 EPSA `AGT_MAIN (1).db/CJQ_GOODLIST` 的 11,141 条多 27 条，确认本次导出来自更新后的实时主库而非每日备份或旧快照。
- 商品关键字段质量：`GID`、`GBarcode`、`GNameX` 全部非空，`GID` 和条码全部唯一，`GSalePrice` 全部大于零；`GStopFlag=0` 有 11,130 条，`GStopFlag=4` 有 38 条。
- 分类真实关联为 `MS_GOODLIST.GClass = MS_GOODTYPELIST.RTypeCode`，20 个分类代码均能关联；单位真实关联为 `MS_GOODLIST.GUnit = MS_UNITLIST.UNumCode`，所有非空单位代码均有效，但 2,406 条商品未设置单位。
- `GKCCount` 的 11,168 条记录全部不大于零，其中 3,729 条为负、7,439 条为零；现阶段禁止用 `GKCCount > 0` 作为商品可售条件。
- 已识别 10 个当前日期有效的复杂促销活动和 24 条商品映射，并还原三种规则：数量百分比折扣、指定数量固定总价、同活动商品混合凑数量固定总价。
- 原始数据异常记录：`ARCOR CHOCO 2*4000` 的 GID `6631` 有复杂活动映射但缺少定价明细；GID `3240`、`6631` 同时存在简单和复杂促销字段；11 个商品 `GHuiPrice>0` 但 `GHuiPriceCount=0`；`BLOCK CHOCO MANI 110G` 的复杂活动绑定与名称明显不符；停用商品 GID `11033` 仍保留简单促销字段。
- 实操范围确认后，商品、分类、单位和简单促销可以进入下一步只读同步迭代；手机端只使用 `GHuiPrice/GHuiPriceCount`，复杂促销表不参与同步和结算。
- 建议同步方式：商品及其简单促销字段按 `(GUpdateTime, GID)` 增量；分类和单位全量读取后计算快照哈希；新快照全部验证成功后原子替换。
- 分析报告：`E:\手机收银软件开发\MS2011_商品分类单位促销数据分析.md`。
- 复现脚本：`E:\手机收银软件开发\ESpsa_analysis\analyze_ms2011_export.py`；脚本只读取导出 TSV 和 EPSA 快照，不连接、不修改实时数据库。

### 促销实操范围确认

- 实际收银业务只使用 `MS_GOODLIST` 中的简单促销字段 `GHuiPrice + GHuiPriceCount`，不使用复杂促销表。
- 因复杂促销不进入手机端数据集和价格计算，即使同一商品同时留有简单字段和复杂活动映射，也不会产生促销叠加、优先级或冲突问题。
- 简单促销只在 `GHuiPrice > 0` 且 `GHuiPriceCount > 0` 时生效；门槛数量为空或不大于零时保留原始字段但不触发优惠。
- `GStopFlag` 的停用状态优先于简单促销，停用商品不能因保留优惠字段而恢复为可售。
- 原先列出的复杂促销缺明细、错误绑定等现象继续保留为数据分析证据，但不再作为当前手机收银迭代的上线阻塞项。

## 2026-07-14 项目协作配置与 MS2011 实施计划加固

- 按 `$project-swarm` 流程完成只读项目同步；确认根目录 `.git` 为空且不可作为仓库，`mobile_pos_publish` 是发布仓库，`pc-sync-tool`、`android-emergency-pos` 和发布副本必须保持边界清晰。
- 经用户批准新增项目级 `.codex/config.toml` 与四个代理 profile：`ms2011-safety-contract-reviewer`、`pc-ms2011-readonly-implementer`、`android-v2-domain-implementer`、`pos-release-validator`；并发上限 4、深度 1。新会话加载该配置。
- 对 `修改方案/ms2011_live_product_promotion_sync_implementation_plan.md` 完成第二轮合同加固：拆为 S01 至 S17 小阶段；每阶段明确电脑后端、电脑前端、手机后端、手机前端的实施或“无实施”；固定证据、测试、人工验收、遗留风险、回退点和下一阶段许可格式。
- 冻结鸣盛/EPSA 保护域：MS2011、MDF/LDF、鸣盛安装目录、源导出等默认硬只读；正式代码只能通过固定 QueryId 白名单执行 SELECT；工具写入只允许进入自身 AppData allowlist。
- 固定 S01 顺序为 `EV-01 → EV-03 → EV-02 → EV-04 → EV-05`。任一门禁未通过不得提前实施下游业务代码。

## 2026-07-14 EV-01 工作区冻结基线

- 新增 `修改方案/实施证据/workspace_baseline.md`，记录两端关键目录、静态测试数量、现有产物位置和 hash、Android/PC 关键接口、发布副本差异及无法由 Git 识别的既有内容。
- 确认工作区根目录、`pc-sync-tool`、`android-emergency-pos` 均不是有效 Git 仓库；EV-01 不声称重新执行测试或构建，也不清理任何预先存在文件。
- 记录状态文档与较新 MS2011 计划之间的漂移：执行以 `修改方案/` 下较新计划为准，旧文档中的“复杂促销不接入”和单一增量游标不能替代新合同。
- EV-01 结论：PASS；只解锁 EV-03。S01、G0A、G0B 仍未完成。

## 2026-07-14 至 2026-07-16 EV-03 ODBC、位数和目标兼容证据

- 新增 `pc-sync-tool/scripts/diagnose_ms2011_readonly.py`、`scripts/build_ms2011_probe_exe.ps1`、`tests/test_sql_driver_probe.py`，并在项目虚拟环境安装 `pyodbc 5.3.0`。
- Probe 只提供零连接 `--list-drivers` 和固定 `DATABASE_NAME`、`PRODUCT_COUNT` 两条 SELECT；拒绝任意 SQL、用户名、密码、连接字符串注入和不可见驱动；使用 Windows 集成认证、ODBC read-only access mode、autocommit 及独立连接/查询 timeout。
- TDD 首次按预期因脚本不存在失败；安全审查又发现查询映射可变及第一条查询失败清理覆盖不足。补测试并改为 `MappingProxyType` 后，PC 完整回归 64/64、`compileall` 通过；技术安全复审 PASS。
- 构建 `pc-sync-tool/dist/MS2011ReadOnlyProbe/MS2011ReadOnlyProbe.exe`：2,142,676 bytes，PE x64，onedir 60 文件，SHA-256 `C21656A0DF00D940F2B418A63F7B6FB061B415CC6B4934E1370FA64707DE1AE6`；禁止文件扫描无 BCP、MDF/LDF、业务导出或凭据。
- 2026-07-16 在目标收银电脑核验相同 EXE hash；64 位 frozen Probe 只枚举到 `SQL Server` 驱动，并以 Windows 集成身份成功执行固定查询：数据库 `MS2011`、商品数 `11180`、退出码 0。
- 目标证据保存为 Drive `drivers.json`、`probe-result.json`、`target-evidence.txt`，并由独立代理核对 PASS。管理员 Windows 身份仅证明兼容，不证明服务器端最小权限或只读权限。
- 安全审查拒绝在生产库人为触发 timeout。用户批准方案 A：真实驱动只需接受 timeout 且固定查询在阈值内完成；timeout 分类、清理和资源释放由 fake ODBC 自动化覆盖；禁止不可达服务器、`WAITFOR`、制造锁、重复 Probe 或反复降低 timeout。
- 验收口径已同步到实施计划和 `修改方案/实施证据/odbc_compatibility.md`。EV-03 最终 PASS，只解锁 EV-02；EV-04、EV-05、S01、G0A、G0B 状态不变。

## 2026-07-16 EV-02 精确 schema 与稳定键证据

- 用户选择方案 A：交付完整独立 frozen onedir EXE，目标电脑不安装 Python、pyodbc、ODBC 驱动或旧 BCP 组件；目标验证严格分为 schema 审查和经批准的一次 stats。
- 新增 `pc-sync-tool/scripts/diagnose_ms2011_schema_readonly.py`、`tests/test_ms2011_schema_diagnostic.py` 和 `修改方案/实施证据/ms2011_schema_evidence.md`。
- 合同写死 13 张候选表及候选键；数据库强制为 `MS2011`；真实模式先执行 `SELECT DB_NAME(), @@SERVERNAME`；schema 使用 SQL Server 2000 兼容系统表；stats 只执行 13 条固定 `COUNT/MIN/MAX` 聚合并使用 `WITH (NOLOCK)`，且必须显式提供 `--schema-reviewed`。
- TDD 先后记录脚本缺失和 Decimal JSON 序列化失败；首轮安全审查发现 timeout setter 连接泄漏、stats 缺 NOLOCK、缺数据库身份校验、合同测试自引用和 stats 可绕过人工审查五项阻断。全部先补红测试再修复。
- 最终 EV-02 定向测试 24/24、PC 完整回归 88/88、`compileall` 通过；独立安全复审 PASS，独立产物验收 PASS。
- 构建 `MS2011SchemaProbe.exe`：2,147,725 bytes，SHA-256 `40475D45CA6250A465D432536BCB4353066F815068B198CD01483E24CFBBA9D8`。传输包 `MS2011SchemaProbe-20260716.zip`：9,480,513 bytes，SHA-256 `8186D35638D1AE57ABF634C41164BB7925EB3734E60806A2D82132CD26B6E455`；ZIP 60 条目，内嵌 EXE hash 一致，禁止文件 0。
- 目标电脑先验证 EXE hash 和零连接合同，再运行一次 schema：实际身份 `CAJA1\HOME / MS2011`，`status=ok`、`requiresMappingReview=false`、13 张表和全部候选键列存在、耗时 341.27 ms、退出码 0。
- 主代理审查 schema 后才批准一次 `--stats --schema-reviewed`。stats 原始 JSON 为 3,910 bytes，`status=ok`、`requiresCompositeKeyEvidence=false`、总耗时 123.154 ms、退出码 0。
- 13 张表均满足 `rowCount = nonNullKeyCount = distinctKeyCount`，全部为 `VERIFIED_SINGLE_KEY`。`MS_GOODLIST.GID` 为 11,195/11,195/11,195，MIN 1、MAX 11248；其余 12 张表同样无 NULL 或重复候选键。
- Drive 原始 stats：`https://drive.google.com/file/d/1EbIU3KA7wjzRVeWVyQRagL8XI_53HcVX/view`；退出码：`https://drive.google.com/file/d/1h4IN41qJt1k0KVCQvj4avGcAoeKs9q9r`。主代理与独立验证代理均 PASS。
- EV-02 完整通过，只解锁 EV-04。S01、G0A、G0B、EV-05、正式 MobilePosSync 真实 MS2011 连接和生产启用仍未通过；业务代码尚未开始实施。

## 2026-07-16 至 2026-07-17 EV-04 促销候选盘点与目标机复测

- 在 EV-02 schema 通过后，按固定 QueryId 合同实现 `MS2011PromotionInventoryProbe`，只读取候选商品、促销映射、数量百分比、数量固定总价和混合凑数固定总价所需的固定列；每个盘点查询双读并比较规范化来源哈希，不提供任意 SQL 输入。
- 首次目标机盘点在 `QUANTITY_PERCENT_DETAILS` 返回形状检查处安全失败：`status=error`、`category=INVALID_RESPONSE`、退出码 5，未输出部分候选。结合已采纳 schema 确认 `CXGGOODID`、`CGOODID`、`C4GOODID` 是旧式 `varchar` 商品 ID 列。
- 修复采用最小兼容面：只对 `QUANTITY_PERCENT_DETAILS[1]`、`QUANTITY_FIXED_DETAILS[1]`、`MIX_MATCH_PRODUCTS[1]` 接受匹配 `^[1-9][0-9]*$` 的 ASCII 文本并立即转为整数；其他源键、外键和 GID 位置继续要求整数类型。对应负例、前导零、空白、符号、Unicode 数字和非数字均拒绝。
- 修复后测试 121/121 通过，`compileall` 通过，独立安全审查 PASS。交付 ZIP `MS2011PromotionInventoryProbe-20260717.zip` 的 SHA-256 为 `230A94E8ED4A56C3FF10C3176CC297CEDB0D8FA104CD00D50D745327747194D1`；EXE 为 2,155,338 bytes，SHA-256 `4DBF863D900634CF80746046AD7876DE28D803FFC79B116D901F53A97203C846`。
- 目标机先整目录备份旧探针、解压新 ZIP、核对 EXE hash 和零连接合同，再执行一次正式盘点。结果：`status=ok`、`mode=inventory`、数据库 `MS2011`、`capturedAtArt=2026-07-17T14:48:40-03:00`、`doubleReadMatched=true`、`ExitCode=0`。
- 盘点来源哈希为 `CA8150B69C6BEDEE38BED4675034B6A9AB2400C21DD42062F8C1215406C259EA`；候选数组长度 41，分类为当前启用 28、未来配置 0、历史失效 0、无法判断 13，重算结果一致。
- QueryId 行数：简单候选 32、促销映射 23、百分比主表/明细/全局规则/时段 6/10/6/42、固定总价主表/明细/时段 2/9/14、混合凑数主表/商品 1/3。
- 当前 28 个候选覆盖四类：20 个简单促销、6 个数量百分比、1 个数量固定总价、1 个混合凑数固定总价。另有 12 个简单候选和 1 个固定总价候选无法判断；异常代码与关联商品 ID 均保留。
- 与旧导出分析相比，复杂候选 `MS_SALE_CXMASTERDING:8 / GID 3646` 已不在当前盘点中，GID 3646 当前作为简单促销候选出现；这说明历史导出不能替代实时清单，后续必须以本次来源哈希和采集时间为证据边界。
- 原始 Drive 证据保持原始文件类型：合同 JSON `https://drive.google.com/file/d/1iHW10mbpeHoBQbXU31S1M1xmER00hlB0/view`、盘点 JSON `https://drive.google.com/file/d/1emDQqqHrJxPKk3zTHM4nzZHY3bNHpYTE/view`、退出码 TXT `https://drive.google.com/file/d/1EVk1X3EUaR_AyT07v6puAKmlOpwbKKT3/view`。盘点文件目标机 SHA-256 为 `D2BCEEB1B5E030975D6594C83BE4C7B7C9FFE0AF91930831E51CC46106406894`。
- EV-04 结论为 PASS；EV-01、EV-02、EV-03、EV-04 已满足 G0A，因此 G0A PASS。该结论只解锁按依赖执行的离线 fixture/fake ODBC/既有导出任务，不证明公式语义。S01 下一项为 EV-05；G0B、真实自动读取、IV-02 和生产发布继续锁定。

## 2026-07-17 EV-05 生产只读权限诊断器开发与冻结

- 按 S01 固定顺序进入 EV-05。先查阅 Microsoft 官方 SQL Server 权限文档，选用 SQL Server 2000 兼容的 `PERMISSIONS()`、`IS_MEMBER`、`IS_SRVROLEMEMBER` 和 `dbo.sysobjects`，没有使用新版 `fn_my_permissions` 或 `HAS_PERMS_BY_NAME`。
- TDD 首次运行因 `diagnose_ms2011_permissions_readonly.py` 不存在而失败；随后实现 7 个固定 QueryId，只执行 SELECT，不接受任意 SQL，并通过 Windows 集成认证、ODBC read-only access mode、自动提交及独立 timeout 连接。
- 权限快照双读 MS2011 角色、语句位图、13 张所需表和所有可见用户对象，再通过第二个只读连接双读 master 扩展过程；两部分共同进入规范化 SHA-256。
- `WRITE_CAPABILITY_PRESENT` 覆盖明确的服务器/数据库写角色、DDL、grantable 权限及对象写权限；`UNKNOWN` 覆盖 NULL 角色、BACKUP/未知位、其他高权限角色、可执行用户过程和 master 扩展过程。只有完全闭合且所需 13 表具备完整 SELECT 时才允许 `READ_ONLY_PROVEN`。
- 首轮独立复审阻断了三个缺口：显式 BACKUP 位漏判、master/system 扩展过程未覆盖、未明确记录 ODBC 属性已被驱动接受。修复后又发现 master 空结果可能被误当成无权限；最终加入 `xp_cmdshell` 固定哨兵，要求非空且哨兵恰好一条，并补 master 第二次读取变化测试。
- 测试合同改为独立硬编码 7 个 QueryId、13 张表、17 个角色和 Microsoft 官方权限位值，避免实现与测试同步漏项。最终 EV-05 定向测试 28/28，PC 完整回归 149/149，`compileall` 通过；独立最终复审 PASS。
- 新增构建脚本 `scripts/build_ms2011_permission_probe_exe.ps1`。冻结 EXE 为 2,149,684 bytes，SHA-256 `144CFF9A636DD67A2FA67DB379C13EC372152BB2ABE44BE4593415D93900BE99`；ZIP 为 9,483,327 bytes，SHA-256 `49ED06FA9A8650522D800B1BE295FBCF0E8B7D5D57D0A069833A225DD286D25C`。
- ZIP 共 60 个文件、禁止文件命中 0；重新解压后的 EXE hash 一致，`--describe-contract` 返回 frozen 64 位 Python 3.14.5、`status=ok`、退出码 0。
- 经用户明确批准，冻结 ZIP 已上传到私有 Drive：`https://drive.google.com/file/d/188hHyLhm8RLZrremdynDaS_EToD9dhmA/view`；回读元数据为 `application/zip`、9,483,327 bytes，未转换为 Google Docs。
- 当前只完成 EV-05 开发机部分。目标机尚未执行，所以不能标记 EV-05/S01/G0B 通过；下一步只允许在空闲窗口核对哈希并运行一次权限取证。结果若非 `READ_ONLY_PROVEN`，必须停止且不得自动调整 SQL 权限。

## 2026-07-17 EV-05 目标权限取证完成

- 目标电脑解压路径比原指令多一层顶级目录；改用实际路径 `D:\MS2011PermissionProbe\MS2011PermissionProbe\MS2011PermissionProbe.exe` 后，EXE SHA-256 核对为冻结值 `144CFF9A636DD67A2FA67DB379C13EC372152BB2ABE44BE4593415D93900BE99`。
- 零连接 `--describe-contract` 成功，合同明确 `neverTestsWrites=true`；随后仅运行一次 `--permissions`。合同与权限诊断退出码均为 0。
- 权限结果为 `status=ok`、`database=MS2011`、`doubleReadMatched=true`、登录 `SERVER\Administrador`、数据库用户 `dbo`、`permissionAssessment=WRITE_CAPABILITY_PRESENT`。
- 目标身份具有 `sysadmin`、`securityadmin`、`serveradmin`、`setupadmin`、`processadmin`、`dbcreator`、`diskadmin`、`bulkadmin`、`db_owner` 及 DDL/对象写/所需表写等能力。ODBC 驱动虽然接受 read-only access mode，但不能覆盖这些服务器端权限。
- 原始结果来源哈希为 `2E0908652939AD7D6E16E1A12B2C79AC221D73D1D70E21398A471284FC3FE485`。
- 用户将 EV-05 证据目录压缩并上传至私有 Drive：`https://drive.google.com/file/d/15Y4YIQjlG5L_eQYF7PAYvu-DWWPfBZz-/view`。主代理回读确认 MIME 为 `application/zip`、大小 1,955 bytes、SHA-256 `9F73112D3410CEAEB83546C4EF89A28AABDDDFED5D380E8598786FC6D29CB6BA`，内含合同/权限 JSON 和各自退出码 TXT 共四个原始文件。
- 本次探针只执行固定 SELECT，没有数据库写入或权限变更。EV-05 取证任务完成但未达到 PASS；G0B 明确保持锁定，不重复运行、不自动调整 SQL 权限。
- S01 的五项证据已形成明确结论，证据收集阶段结束。下一步可按计划进入 S02 离线合同工作；真实 MS2011 自动读取、IV-02 和生产发布仍禁止。

## 2026-07-17 S02 精确十进制、manifest、SQLite 与能力隔离合同

- CT-01 新增 `pc-sync-tool/src/decimal_value.py`、Java `DecimalValue.java` 和两端同名 fixture。规范化保留 raw/canonical 双文本，使用无指数非负十进制，金额上限 15+4 位、数量上限 11+4 位，超限直接拒绝且不截断。
- CT-02 新增 `v2_contract.py` 与 Java `V2Contract`：冻结 `ms2011:<GID>`、UTC snapshotId、五类派生 ID 的 SHA-256 前 24 位算法、精确版本下载路径、软硬上限和 active/pending/rollback 磁盘预留。
- CT-03 新增独立 `v2_manifest.py` 与 Java `V2Manifest`，不修改既有 v1 `manifest.py`。字段集合、UTC Instant、lowercase SHA-256、versionCode、计数上限和 snapshotId/downloadPath 一致性全部失败关闭。
- CT-04 新增共享 `sqlite_v2_schema.sql`、`normalized_promotion_rule.py` 与 Java DTO。内存 SQLite `integrity_check=ok`、`foreign_key_check=[]`；12 张必需表、外键、ON DELETE 和索引已冻结，业务十进制不用 REAL。
- 通用规则 fixture 使用 `FIXTURE_ONLY / UNVERIFIED`、空 parameters/tiers/schedules/groups，不表达真实促销公式；weekday 在 PV-ORDER 证据前保持未冻结。
- CT-05 新增 `read_only_ms2011_session.py`、`tool_owned_path.py` 和架构测试。会话拒绝字符串 QueryId，不暴露 connection/cursor，并复制返回行；工具路径由 `AppPaths` 固定 factory 生成，普通 Path 不能进入 v2 写/替换/删除接口。
- 新增合同没有接入当前 UI、HTTP 或现有 v1 发布流程；`AppPaths.ensure()` 也不会提前创建 v2 目录。
- 验证：S02 Python 定向测试 14/14；PC 项目 PySide6 环境完整回归 163/163、`compileall` 通过。Android core 全量 Java 17 编译通过，既有 3 组 smoke 与新增 3 组合同测试均通过。
- 测试数据源仅为共享 fixture、内存 SQLite、临时 AppData 和 fake runner；没有真实 SQL 连接或生产快照写入。S02 PASS，只解锁 S03 fake ODBC 安全骨架；G0B 不变。

## 2026-07-17 S03 安全配置、固定 QueryId 与调度熔断骨架

- PB-00 在不改变 v1 默认行为的前提下扩展 `SyncConfig`：旧 JSON 缺少新字段时保持 `legacy_sqlite`；新增 live detection、安静窗口、全量指纹、熔断、v2 保留数和大小上限。`sqlDatabase` 固定为 `MS2011` 且不持久化；用户名/密码字段直接拒绝。
- `paths.py` 新增与 `ToolOwnedPath` 类型隔离的 `SourceReadPath`。objects、manifests、tmp、active、pending、last-good 和 lock capability 均由 `AppPaths` 固定生成并限制在 LocalAppData，篡改配置不能改变 v2 根目录。
- PB-01 新增 `ms2011_schema.py`、`ms2011_query_catalog.py` 和唯一 ODBC 适配器 `ms2011_connection.py`。十三表/候选键来自 EV-02；QueryId 目录为不可变固定 SELECT，不接受 SQL 文本或动态标识符，数据库强制 `MS2011`。
- 适配器仅使用 Windows 集成身份，并在连接前请求 ODBC read-only access mode；属性缺失或驱动拒绝会返回明确 reason code，不会移除只读请求后重试。成功、timeout 和其他异常路径都会关闭 cursor/connection。
- PB-02 新增活动提示探针和单线程协调器。繁忙状态进入不可绕过的安静窗口；探针无权限返回 `UNAVAILABLE` 并降级为更短查询 timeout + 强制双读上下文，不会伪装成空闲。
- 连续 timeout、双读不一致或异常达到阈值后打开熔断；自动路径只保留低成本探测，冷却后也必须人工重试。单进程最多一个任务、人工请求不堆积，取消门只允许等待或发布前取消。
- S03 定向测试 23/23；项目 PySide6 环境完整回归 179/179，`compileall` 通过。静态扫描只发现 `ms2011_connection.py` 导入 pyodbc，固定 QueryId 无写语句。
- 所有新测试只使用临时目录、fake runner 和 fake ODBC；未连接目标数据库、未生成生产快照、未改变 UI/HTTP/v1。S03 PASS，只解锁 S04 离线工作；G0B 不变。

## 2026-07-17 S04 确定性双读、变化探测、商品规范化与促销候选证据

- PB-03 将商品、分类、单位、简单候选、促销映射及三类复杂促销主/明细/时段查询加入内部不可变 QueryId 目录；所有完整读取查询使用 EV-02 已确认稳定键 ORDER BY，不接受外部 SQL、表名、列名或排序。
- `DeterministicMs2011Reader` 连续执行两轮固定查询，按 Decimal/datetime 类型标签生成规范化 SHA-256。任一查询异常、行数越界、主键非法/重复/乱序、关键主从或商品关系缺失均立即失败；两轮来源哈希不一致返回 `DOUBLE_READ_MISMATCH`。
- 每表 metric 只含 QueryId、pass index、row count、elapsed ms，不包含业务行。
- PB-04 新增商品与促销低成本摘要、最多 256 个样本的 p50/p95/max 实测历史和默认 900 秒全量指纹策略。快速摘要显式标记非权威；摘要变化或周期到期仅提出全读要求，熔断/繁忙门禁未授权时返回 blocked。
- PB-05 新增商品 DTO 和固定 severity matrix。身份只用 GID，条码变化不改变 key；空单位不填默认值；stop flag 原值与 sellable 分离；Money/Quantity 使用 DecimalValue 规范文本；simple 字段固定 `simple_evidence_only=true`。
- PB-05P 只提取候选和原始证据，不构造 NormalizedPromotionRule。四类候选均保留 `UNVERIFIED`，只有结束时间与同一时区证据明确早于采集时才为 `INACTIVE`；时区信息不匹配继续 UNVERIFIED。
- 每个候选、映射和原始行使用稳定派生 ID/source key；相同商品的不同 XID 映射完整保留。未知表、缺主、缺商品、缺明细和重复源键进入固定 validation issue。
- S04 定向测试 25/25，包含完整 fixture 管线 `double read → normalize products → extract evidence`；PC 完整回归 204/204，`compileall` 通过。静态检查仍只有唯一 ODBC 适配器导入 pyodbc，固定查询无写语句。
- 阶段未连接目标 SQL、未写 SQLite、未发布 manifest/快照、未改 UI/HTTP/v1。S04 PASS，只解锁 S05 fake ODBC 工作；G0B 不变。

## 2026-07-17 S05 SQLite v2、不可变发布、HTTP v2 与电脑 UI

- PB-06 至 PB-09 完成 SQLite v2 单事务写入/关闭后自检、跨进程锁、不可变对象与 manifest、active 最后替换、受保护历史清理、固定 snapshotId HTTP v2 及 fake ODBC 端到端下载/hash 验证。
- PF-01/PF-02 完成数据源、安全能力、周期、阶段、失败/熔断、快照和计数展示；测试与同步使用 worker thread，不提供绕过安全门入口。
- PC 完整回归 225/225、`compileall` 通过；旧 v1 回归包含在内。未连接真实 SQL，S05 PASS，只解锁 S06。

## 2026-07-17 S06 Android Money/Discount 精确十进制迁移

- MB-01A 至 MB-01E 严格串行完成：金额模型、定价/结账/销售/日报、JSON/CSV/鸣盛导入和 UI 输入显示统一为规范 BigDecimal 原币十进制；旧 long 金额兼容桥和业务 double 路径清理完成。
- 11 组 Java 合同/烟测通过，完整 Gradle debug 构建成功。阶段 APK 1,033,434 bytes，SHA-256 `2D45E3CA7D17104F382E2A19E9BD80CA65A63C5CB62AD936C56F92CA540A625E`；未覆盖正式发布副本。
- S06 PASS，只解锁 S07；没有连接 SQL，G0B 不变。

## 2026-07-18 S07 Quantity 迁移暂停记录

- MB-02A 完成 Quantity(BigDecimal) 合同和 CartLine 小数承载；旧 int 构造/读取桥暂时保留。
- MB-02B 完成 Cart 精确数量合并和 BigDecimal 定价；非整数数量排除自动促销。`0.1 + 0.2 = 0.3` 与 `1.5 × 2.5 = 3.75` 已验证，6 组定向合同/烟测记录为通过。
- 当前暂停在 MB-02C 前。SaleLine/CSV/数量持久化、MB-02D 数量 UI 与兼容桥删除、MF-01 和完整 Gradle 构建均未完成。
- S07 不得标记 PASS，恢复入口固定为 MB-02C。新增 `AGENTS.md` 与 `docs/ACTIVE_ITERATION.md` 作为压缩上下文后的读取规则和唯一动作检查点。

## 2026-07-18 S07 MB-02C 销售、CSV 与数量持久化迁移

- `android-v2-domain-implementer` 独占完成 MB-02C：`SaleLine` 内部数量改为 Quantity，旧 int 构造精确委托；`CheckoutService` 创建销售快照时传递 `quantityValue()`；销售 CSV 显式写 `canonicalText()`。新增 `SaleQuantityPersistenceContractTest`。
- 合同覆盖 checkout `1.2500 -> SaleLine 1.25 -> CSV 1.25`、旧整数 2、历史 `2.5000 -> 2.5`、`2147483648` 无 int narrowing、零/负/scale/整数位/null 失败，以及 null/损坏输出流转 `SalesExportException`。现场不存在购物车或销售磁盘 reader，没有虚构新存储层；`ProductLocalStore.promotionMinQuantity` 保持整数促销门槛语义。
- 主协调器使用 JDK 17.0.19 编译全部 core main/test Java，并逐一执行 13 个测试主类，结果 `13/13 PASS`；Gradle `:core:compileJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac` 为 `BUILD SUCCESSFUL in 10s`；随后执行 5 个 app smoke，结果 `5/5 PASS`。
- MB-02C 四文件 narrowing 扫描 `legacyIntValueExact|intValue|longValue|Integer.toString(line.quantity())|double|Math.round|setScale` 为 `0 matches`。安全/合同复核与验证复核均 PASS，无 High/Medium finding。
- 修改文件及最终 SHA-256：`SaleLine.java` `9F9C6A478C0868B343A9494928173DE9012DF89F892199B3A17BF0BCFDCD272A`；`CheckoutService.java` `31D102E2749AA7D580DFB7BA81FFBDF8E177AD04BBAFB13D12E822EA6695400F`；`CsvSalesExportAdapter.java` `9B33098667E3F58B7F00E040D967C2DA4DA95D700C3B8D3785F6F5E590F02219`；`SaleQuantityPersistenceContractTest.java` `FD66DF133F108F2D82CD8304FDD168DA6B7192C2A3AD9F2AB96A8577199D8C8D`。
- 未运行完整 `assembleDebug`，未复制或发布 APK，未连接真实 SQL/设备/收银电脑，未修改 PC、HTTP、QueryId、依赖或 `mobile_pos_publish`。S07 仍未 PASS，G0B 继续锁定；MB-02D 已解锁但尚未开始，下一步先核对其精确文件所有权和全部 int 数量入口。

## 2026-07-18 项目代理并发与任务拆分规则调整

- 用户将项目目标并发槽位调整为 10；`.codex/config.toml` 的 `agents.max_threads` 已从 4 更新为 10，`agents.max_depth` 保持 1，未设置项目默认模型。
- `AGENTS.md` 已明确：累计子代理数量不设上限，主协调器根据任务复杂度、依赖和文件所有权决定每轮实际代理数；繁杂任务优先拆成独立、边界明确、可单独验收的小任务，同时继续禁止紧耦合写入并行和门禁跳跃。
- 使用 Python 3.14 `tomllib` 重新读取项目相对路径 `.codex/config.toml`，校验结果为 `TOML_VALID max_threads=10 max_depth=1`。
- Codex 项目配置在会话启动时加载；当前会话仍受已加载的 4 槽位限制。实际启用 10 槽位需要重新打开本项目的 Codex 会话。

## 2026-07-18 S07 MB-02D 数量 UI 与兼容入口清理

- 用户选择 A，批准为满足同一任务的全部 int 桥退出条件，将 MB-02D 最小范围补充为 `Quantity`、`Cart`、`CartLine`、`CheckoutService`、`SaleLine`、`CheckoutScreen`、新增 `QuantityText` 及删除 API 后必须迁移的测试；`Money`、`DefaultPriceCalculator`、`NumberTextParser`、`ProductSearchResultDialog` 保持不变。
- 唯一写代理删除 9 个 Quantity/int 临时交易数量 API。Checkout 商品行改为 CT-01 小数数量编辑，商品搜索选中后以 `Quantity.one()` 打开数量对话框，扫码、条码和手工价格商品继续显式默认 1；显示使用 canonical `QuantityText`。
- `QuantityText` 支持点/逗号、11 位整数、4 位小数和最长 32 字符；null format fail-fast。后置 UI 复核发现源码字符串断言不能证明 callback 次数的 Medium finding 后，在同一批准范围内新增纯 Java `applyIfValid` 合同：非法输入 callback 0 次并留窗，合法输入 callback 恰好 1 次并传递精确 Quantity。修复后 UI 复核 PASS。
- 主协调器最终独立把 core main/test 共 70 个 Java 源文件编译到新临时目录并运行全部主测试：`13/13 PASS`。Gradle `:core:compileJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac` 为 `BUILD SUCCESSFUL in 8s`，20 actionable tasks，1 executed、19 up-to-date；随后 app main-based 测试 `6/6 PASS`。
- 最终扫描：9 个 transaction quantity bridge `0 matches`；Checkout/QuantityText narrowing 与旧整数 UI 路径 `0 matches`。`Money.times(int quantity)` 生产调用为 0、没有 Quantity 委托关系，按用户 A 冻结边界记录为非交易链路整数标量 helper，不扩展修改范围。
- 核心、UI 和范围/hash 三路独立复核最终均 PASS，无 High/Medium finding。第二轮修复恰好只改变 `QuantityText.java`、`CheckoutScreen.java`、`QuantityTextContractTest.java` 三个批准文件；禁止文件 hash 保持不变。
- 最终生产文件 SHA-256：`Quantity.java` `7B0235AF59F700F1696521AD27B3E07B95EBD624CFC7E8E868BBDE67D7780B1A`；`Cart.java` `540B98494C15238499FD8A8B53CBDA3B0F4FF819FBAF96FFBA871678567254EA`；`CartLine.java` `42FB5DEC5975585ABF3767F88B648D626649D405563D4A2D1281B0F4A8FD16D7`；`CheckoutService.java` `AFA9A5110E633CFCB142E1842770EE25A89814939912B8EE0218ED03E6CC1C38`；`SaleLine.java` `E8C0469111209759744F08365D1829102F987BCD1FDCD5DE22A15EC806C465AE`；`QuantityText.java` `B5F28BB2168B48E6373B707556179F49BA26540C4DFFAAA74920E41B54728F83`；`CheckoutScreen.java` `AF5BC4970977071575D1A856A82000D1F26629F270FACAB9540179888C06977E`。
- 最终测试文件 SHA-256：`CoreSmokeTest.java` `C57E1926C26A12D7DFFC18E18DD10AE1BF6F60745B5A3B9B05BFC8638C8DA551`；`CartMergeSmokeTest.java` `88F19694203D62215AA2AAA56714510A0A29D61CFBCEE824DA93733C3779BAA7`；`QuantityContractTest.java` `AD0F82B5386C73EDDA2EB292511299132D96292BE3E58CE079C83CC8F0810321`；`ExactMoneyPricingContractTest.java` `F518B0154EA42561F8F6B7468D384CEC6D2E2B7AA1D2C4D2AB06D6BC4F03425F`；`SaleQuantityPersistenceContractTest.java` `D853FEDF38BF388A6BA1AA3CCDEEA451CBB81DFBBE50F78D1548665403B83005`；`ExactMoneyImportExportContractTest.java` `A5265563A24AA97F17E47B92DC8FDB6B231850725AF82207C78634D3CFAA9966`；`QuantityTextContractTest.java` `4E93EA3656E57291C7863C01AB92F95F9DCDD22B4C9F22E67D0A12DB115265D8`。
- 未执行 `assembleDebug`、未复制或发布 APK、未连接真实 MS2011/设备/收银电脑、未修改 PC/SQL/HTTP/QueryId/依赖或 `mobile_pos_publish`。真实 Android 软键盘、焦点和对话框生命周期仍未设备验证。MB-02D 完成，只解锁 MF-01；S07 与 G0B 仍未通过。

## 2026-07-18 S07 MF-01 显示一致性验收与阶段完成

- 启动调查代理分别扫描金额展示、数量展示和 parser/test 范围；现场 104 个 Android 生产 Java、24 个 UI Java 中确认两个真实遗漏：`CheckoutScreen` 初始 `"$0"` 绕过 `MoneyText`，`SalesScreen` 直接拼接 `SaleLine.quantity()`；同时确认 `QuantityText` 与 `NumberTextParser` 重复维护十进制词法、`MoneyText.format(null)` 静默伪装为 0。
- 唯一实现代理在冻结范围内完成最小生产修改：`MoneyText` null fail-fast，`QuantityText` 共用 `NumberTextParser.normalizeUnsignedDecimal`，Checkout 零金额使用 `MoneyText.currency(Money.ZERO)`，Sales 数量使用 `QuantityText.format`；新增 `DecimalUiArchitectureTest`。`QuantityTextContractTest` 保持不变。
- 第一轮 UI 后置复核发现架构测试只锁定已知源码字符串，无法通用防止 getter 变体、局部变量、`String.format`、静态通配导入、方法引用和 formatter 内嵌 raw concat。两轮 regex 增强仍被对抗性复核发现漏报，因此停止堆叠 getter 名单。
- JDK AST 可行性调查确认桌面 JDK 17 支持 `javax.tools`/`com.sun.source`，但主协调器实际运行 Gradle 证明 Android unit-test 编译 bootclasspath 不提供这些模块；直接导入候选以 package-not-found 安全失败，没有被采纳。实现代理随后长时间把共享测试文件停在不可编译的 AST/旧 regex 混合状态，主协调器中断该代理并接管恢复。
- 最终架构测试使用 Android 可编译 wrapper：运行 main-based 测试时在带固定随机前缀的系统临时目录写入、编译和执行内嵌 JDK AST/TypeMirror runner，不改 Gradle 或依赖。runner 按真实类型识别 Money/Quantity getter、参数、局部变量、嵌套 receiver、`append`/`String.valueOf`/`String.format`/`String.formatted`/`Objects.toString`/`MessageFormat.format`/`toString`/字符串拼接、primitive parser、Money/Quantity factory、BigDecimal 构造、静态导入和 method/constructor reference；compiler/attribution/runner/清理失败均失败关闭。
- runner 首次实际执行准确失败于不存在的 `ElementKind.isConstructor()`，退出码 1，且临时残留 0；主协调器单行改为显式 `ElementKind.CONSTRUCTOR` 后通过。最终对抗复核又发现 `MessageFormat.format("{0}", money)` 漏口，增加 resolved sink 与 synthetic 后关闭。最终扫描 24 个 UI Java、24 个 synthetic 变体，`NEW_TEMP_DIRS=0`。
- 主协调器最终独立回归：core main-based `13/13 PASS`，app main-based `7/7 PASS`。静态扫描 UI 的 `Long/Double/Float.parse*`、formatter 外 `canonicalText()`、raw sale quantity append、硬编码数字货币均为 0；Money formatter 22 处、Quantity formatter 4 处逐行复查。parser、UI 架构、独立对抗性和范围/hash 三路最终复核均 PASS，无 High/Medium/Low finding。
- 完整构建命令：`E:\AndroidBuildEnv\gradle\gradle-8.11\bin\gradle.bat --no-daemon :app:assembleDebug`；结果 `BUILD SUCCESSFUL in 12s`，34 actionable tasks，5 executed、29 up-to-date。APK：`E:\手机收银软件开发\android-emergency-pos\app\build\outputs\apk\debug\app-debug.apk`，1,010,742 bytes，LastWriteTime `2026-07-18 10:28:59 -03:00`，SHA-256 `C4A1018D3033C6A4BA8ED410DAB98D1CCC3F3FEF499B5644AC45EE3C039E26AC`。主协调器和独立 artifact validator 均确认 ZIP 可读且包含 `AndroidManifest.xml`、dex、`resources.arsc` 和 `res/...`。
- 最终 MF-01 文件 SHA-256：`MoneyText.java` `2733144BA592ADBC2E45949C703CF7EA2D8CD9C7CF54433E86BFF69C19281160`；`QuantityText.java` `03F8983B15D543172C9FC3F65DD0995EEE7772C56D6778477BE6B85E4360F354`；`NumberTextParser.java` `FB316D0F9CAB3F236017D4E89020BBFA1BDD017B9701F46CB2E8D76CDF69DA36`；`CheckoutScreen.java` `BA73FF84BF94EE738677C4A7FB6323ED0E27EC325F87700CBBA935ACAB9C976A`；`SalesScreen.java` `20D037218414ADF3586B484B74DF1AB25145A06B49531401913D65A107725CE2`；`MoneyTextParserContractTest.java` `E541AD6BF6404DB83029D66A0ADDC41ACC074D58EE16B621F01AEBA456186C83`；`QuantityTextContractTest.java` `4E93EA3656E57291C7863C01AB92F95F9DCDD22B4C9F22E67D0A12DB115265D8`；`DecimalUiArchitectureTest.java` `AC1326AF29C9662754DF25E321139B4B7034A68A587377CE8D89F503DDE79020`。
- 没有连接真实 MS2011、设备或收银电脑，没有修改 PC/SQL/HTTP/QueryId/依赖，没有复制 APK 到 `dist` 或 `mobile_pos_publish`。`dist` 旧 APK 仍为 1,032,530 bytes、2026-07-11 10:17:56、SHA-256 `48D488084C4160B999090647EA3130619040CB151A899E543495E604AF52E7C2`；本 APK 只是实施阶段构建，不是发布制品。
- 遗留风险：真实 Android 软键盘、对话框生命周期、安装和人工页面遍历未执行；既有百分比折扣在某些 Money/percentage 组合下可能产生超过四位小数，该问题早于 MF-01，不能在本任务猜测输入精度或舍入。**S07 PASS**，只解锁 S08/MB-03；按用户指令停在 S08 之前，G0B、真实自动读取、IV-02 和生产发布继续锁定。回退点为 ACTIVE/本日志中的 MF-01 pre-write hashes；由于实施目录无有效 Git，不得使用 `git reset` 推断回退。

## 2026-07-18 S08 前置 CT-02R/CT-03R 跨端合同纠偏

- 用户选择先修复只读同步发现的 CT-02/CT-03 跨端差异，再进入 MB-03。冻结合同为：snapshotId 中 `yyyyMMdd'T'HHmmss'Z'` 必须按 UTC 真实日历严格解析；`categoryCount`、`unitCount` 为 `0..2147483647` 的 JSON integer；`minimumAppVersion` 为 `1..2147483647` 的 JSON integer。
- PC 修改 `src/v2_manifest.py`、`tests/test_v2_contracts.py` 和两份负向 fixture；Android 修改 `V2Contract.java`、`V2Manifest.java`、`V2ContractTest.java` 及对应两份 fixture。PC `v2_contract.py` 保持不变，SHA-256 `587BF816E2C91C0CF9538035C1EADEEA7CD0645ED9B7437035EE16AF57F803FF`。
- TDD 证据：PC 定向测试修复前 5 tests / 3 failures，修复后 5/5 PASS；Android 修复前严格日期断言失败并出现预期 long-to-int 编译失败，修复后 core main-based 13/13 PASS。
- PC 完整回归：系统 Python 首轮发现 218 tests，216 通过、2 个模块因缺少 `PySide6`/`pyodbc` 在收集阶段失败，无代码断言失败；随后使用 `python_envs\pyside6_qrcode\.venv\Scripts\python.exe`（Python 3.14.5、PySide6 6.11.1、pyodbc 5.3.0）在 `pc-sync-tool` 执行 `python -B -m unittest discover -s tests -p 'test_*.py'`，结果 226/226 PASS；`python -m compileall -q src tests` 通过。
- Android 编译命令：设置 `JAVA_HOME=E:\AndroidBuildEnv\jdk\jdk-17.0.19+10`、`GRADLE_USER_HOME=E:\AndroidBuildEnv\.gradle-cache` 后执行 `E:\AndroidBuildEnv\gradle\gradle-8.11\bin\gradle.bat --no-daemon :core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac`；结果 `BUILD SUCCESSFUL in 9s`，21 actionable tasks，4 executed、17 up-to-date。随后逐一运行 core main-based 13/13 PASS、app main-based 7/7 PASS；`DecimalUiArchitectureTest` 扫描 24 个 UI Java 和 24 个 synthetic 变体。
- 独立安全/合同复核 PASS，无 High/Medium finding。两端三份共享 fixture 逐字节一致：`v2_id_cases.json` 1,166 bytes、SHA-256 `D0CD76A68832FDB30B5A29419C1C57B34ABA27E3DAD0D94FC3E91A71F1409197`；`v2_manifest.json` 535 bytes、`C9ED56BDB3BC7AE3A618EF523C5DF14C1B1F3A33644EE5E1D2999E31CA61182C`；`v2_manifest_invalid_cases.json` 1,414 bytes、`A196B11C28E87FD93A05796F191891565B1F7F9A2A3D02BFF10F7B6D3BD0A17C`。
- 最终实现/测试 SHA-256：PC `v2_manifest.py` `A2194C14BDE1947E8F3F6D7C580B887704FBE422A2BB992D10DDD532331C6DF8`、`test_v2_contracts.py` `743F0D6D95F1BEDF74A23EC8E232E10580D2AC6D53A23BAB21CEFFAA0B7D29B7`；Android `V2Contract.java` `502ECFF276E756E2304BE0D8528CFBDFCF990EA1258119A4E1D223F8C6BC068D`、`V2Manifest.java` `AA499CA9D96DD023837EA83B9D37454CD687D74B2A3DC002E59BCD78C9DC643F`、`V2ContractTest.java` `5608ECE284EC68F384292E5658E7B731D2130D0B87B716E7A9520DAE8DF41E50`。
- MB-03 未触碰：`ComputerSyncClient.java` 保持 `2778BC0E375FFC393E4B8D6F30B03ADD061DE00288FB31FBF0DCFAC1A598CF8B`，`ComputerSyncService.java` 保持 `33B16B5C44EB5625FFD7055EF39EF0B50BDF2CCEA79204079815499AFE7D5EEA`。未连接真实 MS2011/设备/收银电脑，未修改 SQL、HTTP 服务端、QueryId、依赖、构建配置、`dist` 或 `mobile_pos_publish`，未复制或发布 APK。
- 安全结论：G0B 继续锁定，目标身份仍为 `WRITE_CAPABILITY_PRESENT`；真实自动读取、IV-02、手机直连 SQL Server、数据库写入和生产发布继续禁止。CT-02R/CT-03R 完成且文档同步后，只解锁 S08/MB-03；回退必须按上述精确文件和 pre-write SHA-256 人工核对，不得使用 `git reset`。

## 2026-07-18 S08 MB-03 v2 manifest 安全校验与流式暂存

- 文件范围严格为 4 项：新增 `ComputerSyncManifestV2.java`、修改 `ComputerSyncClient.java`、修改 `ComputerSyncService.java`、新增 `ComputerSyncManifestV2SmokeTest.java`。未修改 core 合同/fixture、v1 manifest、PC、SQL、HTTP 服务端、UI、AppServices、配置、依赖、Gradle、`dist` 或发布仓库。
- 初始 TDD 红灯：实现前 `:app:compileDebugUnitTestJavaWithJavac` 最终仅剩 5 个 `ComputerSyncManifestV2` 缺失类型错误；第一次安全审查发现删除异常会跳过 disconnect/detach、显式取消未覆盖二次 hash、JSON/manifest length/文件能力/损坏矩阵缺口。FIX-01 新增测试后又以 3 个缺失 `fromFieldValues`/`readManifestResponse` API 编译错误形成红灯。
- 最终合同：15 字段/类型与 CT-02/CT-03 严格校验；manifest 1..256 KiB 且声明/实际字节一致；v2 仅 HTTP 200、Bearer、禁止 redirect/cache；快照 Content-Length/header snapshot/header hash/实际长度/实际 SHA-256 全相等；8 KiB 流式读写，不把 DB 整体载入内存。
- 文件安全：生产下载入口只接收 `Context/config/manifest/cancellation`，从 `context.getCacheDir()/computer-sync-v2/tmp` 内部创建随机 `snapshot-v2-*.part`；不接受调用者 File/目录。stream 与 delete helper 均 private；disconnect/detach 先于失败清理；service 二次 hash 每块和返回前检查线程中断与显式取消。
- 对抗测试覆盖 missing/extra、ok string、float/bool integer、非法 snapshot/path/size/count/version/hash/int32、manifest 声明/实际长度、HTTP 201/206/302、错误 headers、截断、超长、同长度损坏、预先/运行中取消、service hash 中取消和失败清理。
- 主协调器最终命令：设置 `JAVA_HOME=E:\AndroidBuildEnv\jdk\jdk-17.0.19+10`、`GRADLE_USER_HOME=E:\AndroidBuildEnv\.gradle-cache` 后执行 `E:\AndroidBuildEnv\gradle\gradle-8.11\bin\gradle.bat --no-daemon :core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac`；结果 `BUILD SUCCESSFUL in 9s`，21 actionable tasks，3 executed、18 up-to-date。
- 主协调器逐一运行全部 main-based 测试：core `13/13 PASS`；app `8/8 PASS`，其中 `DecimalUiArchitectureTest` 扫描 24 个 UI Java 和 24 个 synthetic 变体。第一次 app 命令因错误 Android jar 路径在测试启动前报 `NoClassDefFoundError: org/json/JSONException`；改用 `E:\AndroidBuildEnv\android-sdk\platforms\android-35\android.jar` 后全量通过，该环境错误不计为代码断言失败。
- 完整构建：`E:\AndroidBuildEnv\gradle\gradle-8.11\bin\gradle.bat --no-daemon :app:assembleDebug`；最终结果 `BUILD SUCCESSFUL in 9s`，34 actionable tasks，3 executed、31 up-to-date。
- 最终文件 SHA-256：`ComputerSyncManifestV2.java` `AD38159CC7E826FE3B47EE3F414DA8A0B65ECC72F60F354867B5A20817BDABB9`；`ComputerSyncClient.java` `EC8FE89EA1990372B71BDB26FA3748B29BA8C984345E272CF47DF1A634D11F0B`；`ComputerSyncService.java` `94C7154BFFA45F9570391E30AE328A197B4C80965896A40AA93318F4A03650A0`；`ComputerSyncManifestV2SmokeTest.java` `6EF043FDF394A24815C69F08331D47463C40D9364C5D367E53AD3B6484EFACA1`。
- 最终 APK：`E:\手机收银软件开发\android-emergency-pos\app\build\outputs\apk\debug\app-debug.apk`，1,033,674 bytes，LastWriteTime `2026-07-18 13:16:18 -03:00`，SHA-256 `445F51ED9E75C9E99FB5172177DA247465A1BBDF11260965FB60163EF252044C`。ZIP 20 entries，含 AndroidManifest.xml、14 个匹配 dex 条目、resources.arsc 和 3 个 res 条目。未复制/发布；`dist` 与发布副本旧 APK 保持 1,032,530 bytes、SHA-256 `48D488084C4160B999090647EA3130619040CB151A899E543495E604AF52E7C2`。
- 两次最终稳定快照独立复核：`mb03_validate` 自行运行 core 13/13、app 8/8 并验证 APK；`mb03_safety_audit` 最终 High 0、Medium 0、Low 2。Low 为真实 Android runtime `org.json` 和异常 cache/symlink instrumentation 未执行，不阻断离线 MB-03。
- 安全扫描 `java.sql|jdbc|SQL Server|pyodbc|SQL writes` 为 0 matches。未连接真实 MS2011、LAN、设备或外部服务。G0B/`WRITE_CAPABILITY_PRESENT` 不变，真实自动读取、数据库写入、IV-02 和生产发布继续禁止。
- **MB-03 PASS**，只解锁 MB-04；S08 尚未完成。回退按 pre-write Client `2778BC0E...98CF8B`、Service `33B16B5C...7D5EEA` 与两项新增文件人工执行，不得使用 `git reset`；不得触碰 CT-02R/CT-03R core/PC/fixture 修复。

## 2026-07-18 S08 MB-04 不可变快照仓库与崩溃恢复

- 文件范围严格为五项新增文件：`V2SnapshotStore.java`、`V2SnapshotValidator.java`、`V2SnapshotReader.java`、`V2SnapshotStateStore.java`、`V2SnapshotStoreSmokeTest.java`。未修改 MB-03 frozen files、core/fixture、PC、SQL/QueryId/HTTP、UI/catalog、Gradle/依赖、`dist` 或发布仓库。
- FINAL-01 主协调器拒绝 4 个 Medium：pending state 晚于 pair move、pending 与 active/lastGood 删除保护不足、CT-04 必需列不完整、state duplicate/resource/fault matrix 不足。FIX-01 关闭后首次完整回归通过，但独立安全审查又发现 2 个 Medium：canonicalize 后丢失叶 symlink 身份；SQLite 未证明完整 CT-04 constraints/metadata。FIX-02 关闭原问题后，主协调器再拒绝索引列顺序未验证以及 fake `schemaOk=false` 负向测试捷径。FIX-03 最终使用可注入精确 SchemaFacts 与真实变异测试关闭全部问题。
- 最终合同：对象/manifest 不可变配对；state 同目录随机 tmp + flush/fsync + atomic move；pending-before-move，active → lastGood → none，pending 永不自动启用；遍历、祖先/叶 symlink、非普通文件失败关闭，清理只处理安全 tool-owned child 且保护 active/lastGood。
- SQLite 生产路径唯一 `OPEN_READONLY`。固定只读 PRAGMA/SELECT 采集 12 表有序列 facts、所有表 FK、显式 index_info、CHECK/UNIQUE、metadata 和 counts；精确比较 CT-04 fixture。`sourceHash` 要求与 PC writer 一致的 lowercase SHA-256 格式，不与 DB 文件 SHA 猜测等同。写 SQL、DDL、`execSQL`、`OPEN_READWRITE`、网络、SharedPreferences/SQL Server 静态命中均为 0。
- 主协调器最终构建命令：设置 `JAVA_HOME=E:\AndroidBuildEnv\jdk\jdk-17.0.19+10`、`GRADLE_USER_HOME=E:\AndroidBuildEnv\.gradle-cache` 后执行 `E:\AndroidBuildEnv\gradle\gradle-8.11\bin\gradle.bat --no-daemon --rerun-tasks :core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac :app:assembleDebug`。结果 `BUILD SUCCESSFUL in 12s`，38/38 actionable tasks executed。
- 主协调器使用 JDK 17 分别遍历运行全部 `public static void main` 测试：core `13/13 PASS`、app `9/9 PASS`，合计 `22/22`；`V2SnapshotStoreSmokeTest` 报告 `65 assertions PASS`，`DecimalUiArchitectureTest` 扫描 24 个 UI Java 和 24 个 synthetic variants。
- 最终 APK 核验：`E:\手机收银软件开发\android-emergency-pos\app\build\outputs\apk\debug\app-debug.apk`，1,030,586 bytes，LastWriteTime `2026-07-18 15:16:24 -03:00`，SHA-256 `44A7AF106FF65102593A1B9CB51825D0D25FA905E1079A24E646292FFBCA0D70`。`jar tf` 为 20 entries、14 dex，并含 `AndroidManifest.xml`/`resources.arsc`；`apksigner verify --verbose` 显示 v2 scheme verified、1 signer。未复制、安装或发布。
- 最终文件 SHA-256：Store `670274846DCE77206F92AF86FEFC2D96ED4ACF685431B1EC80B4E470AB5A1403`；Validator `70A7092B343AEC79AF900F2277A958CC7268046AD5FB69D3566D8C8013F3A7DB`；Reader `849B4532B351B4DF5D8815206CF532C825820072F60BC163039D110087432C6E`；StateStore `3F8A1427FFE95716278502C24CF7138C5FAA78E7CA70595C9486A118DCEA6FB8`；Smoke `D928F9E31F09C14AA68B7AFDEAEEAF7A11A9B1235DDDB90A57D91E83A54214BA`。五个冻结生产文件 hashes 均保持 ACTIVE 基线。
- 最终独立功能/制品验证实际重跑 core `13/13`、app `9/9` 和 65 assertions，PASS、High/Medium/Low 0。最终独立安全审查只读检查 plans/source/fixture/writer/hash/scan，执行测试/构建 `0/0`，确认四个既有 Medium 全部关闭，PASS、High/Medium 0；Low 为真实 Android SQLite/PRAGMA、symlink/atomic move/fsync、process-kill、安装和页面遍历证据缺失。
- **MB-04 PASS，S08 PASS**。用户明确要求完成后不进入下一阶段；恢复点固定为 S09/MB-05 `READY_USER_STOP`。G0B 继续锁定，身份为 `WRITE_CAPABILITY_PRESENT`，真实 MS2011 自动读取、IV-02、手机直连 SQL、数据库写入和生产发布继续禁止。
- 回退点：四个生产类与 smoke 在 MB-04 前均不存在；如需回退，只能在确认归属后人工处理这五个新增文件，并复核 frozen hashes。实施目录无可靠 Git，不得使用 `git reset`/`git checkout --` 推断回退；不得触碰 build cache、`dist`、发布仓库或用户修改。

## 2026-07-18 S09/L2 MB-05、MB-06 与阶段门禁

- 用户批准 MB-05 最小迁移报告合同和安全修正，并授权继续到下一阶段边界。MB-05 新增来源模型、迁移报告/选择服务与原子本地存储；旧九参数商品保持 `LEGACY_IMPORT`，明确手机创建为 `LOCAL`，`MS2011_SYNC` 只能来自严格 GID 身份，无法判定时保留旧来源并要求人工选择。
- MB-06 只从 `V2SnapshotStore` 重新验证的 immutable active/lastGood 对取得 DB，以 `SQLiteDatabase.OPEN_READONLY` 固定 SELECT 映射商品；候选先校验 source key/GID、snapshotId、金额、stop、重复身份/条码，再一次替换 catalog。同步商品不读取 `simple_price_decimal/simple_threshold_decimal` 进入旧促销字段。
- 合并行为：同步条码优先，LOCAL 同条码商品保留为冲突；停用同步商品普通搜索隐藏、显式 lookup 为 `STOPPED`，旧 `CheckoutService` 路径不可加入且不回退 LOCAL；仅 exact legacy id=GID 证据替换旧导入，条码不得赋予同步身份。
- 主协调器阶段复核发现 1 个 Medium：本地编辑持久化原会把整个内存 catalog（含 `MS2011_SYNC`）写入 `products.json`，可能在 active/lastGood 全失效时残留同步商品。SAFETY-FIX-01 在 catalog 生成不可变本地持久化候选，`AppServices` 两个构造路径都只将 `LOCAL/LEGACY_IMPORT` 交给持久化端口；`ProductLocalStore`、`ProductEditingService` 和迁移合同保持冻结。独立验证发现测试把 `UnsupportedOperationException` 当作 `IllegalArgumentException` 的误捕获后，使用专用断言修正，未放宽生产异常集合。
- 最终构建命令：`E:\AndroidBuildEnv\gradle\gradle-8.11\bin\gradle.bat --no-daemon --rerun-tasks :core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac :app:assembleDebug`；结果 `BUILD SUCCESSFUL in 13s`，38/38 actionable tasks executed。沙箱内一次尝试因 `native-platform.dll` 无法加载在 2.3 秒内失败，改用已批准的外部 Gradle 环境后成功；一次工具等待被用户中断但未启动 Java/Gradle、未生成新 APK，现场核验后重跑关闭。
- 主协调器最终逐一运行全部 main-based 测试：core 15/15、app 11/11，合计 26/26 PASS、0 failed、0 skipped。关键输出：Catalog 33 assertions、ProductOrigin 21、ProductLocalStore migration 13、v2 product reader 23、v2 snapshot store 65，Decimal UI 架构扫描 24 个 UI Java/24 个 synthetic 变体。
- 最终 APK：`E:\手机收银软件开发\android-emergency-pos\app\build\outputs\apk\debug\app-debug.apk`，1,059,398 bytes，LastWriteTime `2026-07-18T21:54:54.2563875-03:00`，SHA-256 `15E299DC44F9FA7475E5FFE678533AD1F8255461F728E22176FDB06477178F36`。ZIP 20 entries、14 dex、manifest/resources；`apksigner` 为 v2 verified、1 个 Android Debug signer。未复制到 `dist` 或 `mobile_pos_publish`。
- 最终 MB-05 核心 hashes：ProductOrigin `BD99A1CD3ADFB746BF919A09AB9B9798F9133232C4CFA1679B737757D46C58B1`；ProductEditingService `473B329B04FFBEA5CCA2A74226A60656C28F292C98FDA5562EBCAF09C018F54C`；ProductLocalStore `B1CB2C05DC3DEEFEA4CC2519C4B5ECF7F0D9F99C159EB6E90CE78D06752CAB18`；MigrationIssue `ACEB0AD95E0189D8EB593183A60EFB57B873E10CF3F0A3BE828234C57B47AF65`；Report `7D3C7DC28D6FF9CC59A121ABD153935A3E3A0C17C0DAF0B2D6977A8613AEF45D`；Selection `62881F66BB7F2854B26B7F90C6B238FAA1121CF18752D9CABEE763E34B6864DE`；MigrationService `A6C77CD9254C3B3294EE1B78AD2DF5B5D209E194E74418220E15D9B275F7C83E`。
- 最终共享/MB-06 hashes：Product `6C0ED01F25054133E2FBCE3F3CA58A193B221E733BB67CD8B4548E36B59308E0`；Repository `A5222CB179F275955B29114F4BA036A3A188BEE908A965FA1012D8784E5A50B2`；InMemoryRepository `C661A98055179EAC67B580E21C8268BD84402018A9B05BB0CC6B4078FAA8B99B`；CatalogService `39E2CDDF79D4BA9706C53D3E66ABE4899852435ED2097C8E497DAB741CAD6EF1`；BarcodeLookup `3B35C7D263C2729C962396D7BE9DE8CD1305E9C3781AD5623582CD1B07B4A560`；BarcodeConflict `0C7D1564516B6F6F77C4E8DB9FBF7DF4D06E8CBB76E714253ED92DEEBDD2547B`；Candidate `EE747E583BA0A921CDE6808E88FD82014C642B163740B65351C79EFBC320EC09`；V2SnapshotStore `3DA70CAEE1CEC2C27B77424225E44CD6A8A41BEFED31CDF39CE7598A7339889B`；V2ProductReader `151716788B5B2E36E6B27144EB2602CF54C0A258F15F1E1BE00F32277DC7EDD1`；AppServices `D26D2B36DDBE384BFDB76E827F607CA34F6BBA56BF9A229718F059309FB9B677`。
- 测试 hashes：Origin `6DB4991EF645ADF9C892E50A76EDDCB781417DCB01F92A18D08FB9CE14DCFE40`；Migration `6A27F211DED4613B3616D26CCFEF774996BC5DF1EFC71A1A51B0299A29E332E7`；Catalog `B8F6C1385948F017A399488EEF8461B7D7C22870428C31F99F0B24E18EC5CEAC`；v2 reader `28A7B2F9AAEF39F56E0E040FF0768C92449CCFBE3B0C829A4E9D1CF2598FEEDC`。冻结 Validator/StateStore/Reader、ComputerSync、导入器和 UI hashes 与 S08/ACTIVE 基线一致。
- 独立 `pos-release-validator` 实际重跑 core 15/15、app 11/11，核对源码/hash/APK/aapt2/apksigner，并分别给出 MB-05 PASS、MB-06 PASS、S09 offline gate PASS；High/Medium 0。Low 为真实 Android SQLite/PRAGMA、fsync/atomic move、process-kill、安装、页面遍历、LAN/SQL/生产身份未验证。
- **S09/L2 PASS**，只解锁 S10 启动前依赖核验。按用户指令当前停在 S10 开始前，不执行 MB-07/CB/MF。G0B 继续锁定，目标身份 `WRITE_CAPABILITY_PRESENT`；禁止真实 MS2011、手机直连 SQL、数据库写入、自动实时读取和生产发布。回退必须按 ACTIVE/本日志的 pre-write hashes 人工核对，不得使用 Git reset/checkout 推断。

## 2026-07-19 S10/L3 MB-07、CB-01～CB-03、G4 与用户暂停

- 用户解除 S09 后人工停止点后，S10 按精简 L3 依赖推进。MB-07 完成单线程/single-flight 前台协调、固定五类触发、30 秒默认/0 关闭/5..86400、pending-only 下载、失败保留 last-good、原始 manifest bytes 防御性保留和可注销 listener。CB-01 固定 Cart 的 `PricingSnapshotRef`、完整商品 lookup 与 `none` 规则版本，repository 替换后旧 Cart 不变，STOPPED 同步商品继续阻断 LOCAL 回退。
- CB-02 完成 active/pending 安全切换：候选重验、可回滚 catalog 应用、新 Cart 建立后才 durable 激活；无 pending 且 Cart 身份一致时零 I/O 返回原 Cart；失败保留原 active/catalog/cart。主协调器最终 Gradle `--rerun-tasks :core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac` 为 21/21 executed、`BUILD SUCCESSFUL in 11s`；core 4/4、app 7/7，合计 11/11 PASS。
- CB-03 仅新增 `CB03SnapshotLifecycleSmokeTest.java`，用真实 durable `persistDownloaded`/`activatePendingVerified` 与重开目录覆盖下载中断、下载完成但 state 未提交、pending 重启、active 损坏、空/非空 Cart 和完成/取消边界，不调用 `activateForTest`。主协调器 Gradle `:app:compileDebugUnitTestJavaWithJavac --rerun-tasks` 为 20/20 executed、`BUILD SUCCESSFUL in 11s`；5/5 PASS、188 assertions（CB-03 28、store 71、product reader 23、manager 39、coordinator 27）。
- 稳定簇独立只读复核逐项给出 MB-07、CB-01、CB-02、CB-03、主机侧离线 G4 PASS；High 0、Medium 0、Low 1。Low 是 coordinator smoke 未等待非零周期真实到期；生产实现静态满足周期/停止合同，不阻塞。真实 Android kill/restart、SQLite/文件系统、Activity 生命周期、真机 UI、真实 LAN/MS2011 均未验证。
- 关键 SHA-256：Coordinator `FECD8BCBA85C923B24AFE36AC4B825A183794B2B754D578714638F2538056350`；AppServices `7A71B8A8E214AF49FEB336D0F3060C945AA4056E8C51F845E95F2F7B6CAB4487`；Cart `DCF53BE5378F2DAB9616F7EB4865026F7D0BBAEA1FFD0467FCCF17492CFB1C8B`；PricingSnapshotRef `DC9B4519E8658D59F7FC8276303562E6707F72696228D72BDFFA15D39A1F4D53`；ActiveSnapshotManager `0C82456B2FE354C6A94574BEDC7E218E904A8015933B778AE387D31CE7CAA463`；V2SnapshotStore `30C8170D1E4DEEDCA6E39EAD68EC215DA3BE1E5816EC824090D53B83428F6491`；CB-03 test `3D90BF66F54BAF92CC2A4E09434601225A636B08A319160B7562271F6AB5536B`。
- MF-05 写代理在用户暂停前修改 `MainActivity.java` 并新增 `MainActivityLifecycleContractTest.java`，随后被主协调器中止。暂停现场 hashes：MainActivity `020843C5D7F1769F52107947E5FB0D6A7701D5A0049AA3F1C2F538CF8CD56AC6`；test `6F31DBEBD69A55B5654772E435D62E39633A88ABCBC090ED96D3A6D10712989F`。本次暂停后未运行 MF-05 编译/测试（0/0），两文件属于部分修改、未验收，不得写成 PASS。
- MF-02 在写前发现合同阻塞：产品方案和实施计划要求“长期未更新/长期失败明显警告”，但没有定义 snapshot age 或连续失败阈值；按已批准边界不得自行猜测。MF-03 继续等待 MF-02。
- 用户明确要求“先暂停在这里，更新一下文档”。S10 尚未完成，未执行阶段全回归、跨端离线验收或新 APK 构建，未复制/安装/发布任何制品，未触碰 `mobile_pos_publish`。恢复点固定为：先只读核对并验收 MF-05 两文件，再确认 MF-02 阈值，随后 MF-02 → MF-03；S10 完成后停止，不进入 S11。G0B/`WRITE_CAPABILITY_PRESENT` 不变。

### 2026-07-19 MS2011 离线源码 GitHub 发布批次

- 按 `docs/ACTIVE_ITERATION.md` 开始本次发布同步；不调用子代理，不恢复 S10 实现，不执行真实外部系统操作。
- 电脑端验证命令：`E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe -m unittest discover -s tests -v`，结果 `226/226` 通过；`python -m compileall -q src tests` 通过。
- 发布副本将包含 MS2011 v2 离线合同、固定 QueryId/只读会话、变化探测、schema/规范化/促销候选、SQLite v2、HTTP v2、权限诊断、同步协调器、fixtures 和测试，以及 Android v2 快照/订单边界代码与 MF-05 未验收现场文件。
- 状态保持：MB-07、CB-01、CB-02、CB-03、G4 PASS；MF-05 部分修改未验收；MF-02 等待 stale 阈值；MF-03 未解锁；S10 未完成。
- 安全边界：未连接真实 MS2011/SQL，未修改鸣盛域，未生成生产快照，未构建 Android/PC 正式制品，未进行真实 LAN/真机验收。
- 发布排除：业务数据库、商品导出数据、`ESpsa_analysis`、`MS2011_PRODUCT_EXPORT_20260713`、MS2011 探测 ZIP/EXE、虚拟环境、build/dist、缓存和 Python 字节码。
