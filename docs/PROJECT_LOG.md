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
