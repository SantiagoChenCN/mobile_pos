# 手机应急收银 App 项目进度

更新日期：2026-07-09

## 当前目标

为阿根廷华人超市制作一套 Android 10+ 原生应急收银 App。它在停电、电脑收银主机不可用、或临时需要手机查价时使用。App 离线运行，不直接修改电脑收银系统数据库，通过定期导入鸣盛 / ESpsa 商品数据库更新商品资料。

## 本地主要目录

- 工作根目录：`E:\手机收银软件开发`
- Android 源码：`E:\手机收银软件开发\android-emergency-pos`
- 英文构建副本：`E:\AndroidEmergencyPos`
- 便携构建环境：`E:\AndroidBuildEnv`
- 最新鸣盛商品数据库样本：`E:\手机收银软件开发\AGT_MAIN_20260705.db`
- 最新 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`
- 电脑端同步工具：`E:\手机收银软件开发\pc-sync-tool`

## 远程与发布位置

- GitHub 仓库：https://github.com/SantiagoChenCN/mobile_pos
- GitHub 状态：同步到 `main`，具体最新提交以仓库为准
- Google Drive APK：https://drive.google.com/file/d/1zyOpiRhhO8fvbKNIe4hevnzrfDg25WBy/view?usp=drivesdk
- GitHub 仓库内 APK：`dist/EmergencyPOS-debug.apk`

## 已实现

- 原生 Android Java 项目结构，包含 `app` 和 `core` 两个模块。
- Android 10+ 目标环境。
- 中西双语切换。
- 语言选择已持久化到 `SharedPreferences`，下次打开 App 会沿用上次语言。
- 状态栏安全边距处理，避免手机状态栏遮挡 App 顶部。
- App 已重构为一级首页入口：商品编辑、收银、日账、设置、导入。
- 二级页面顶部提供“返回首页”，Android 系统返回键在二级页面返回首页。
- 设置页已轻量化，只保留商品数、App 版本、离线模式和语言切换。
- 导入页已从设置页独立出来，作为首页一级入口。
- 已识别并读取 `CJQ_GOODLIST` 商品表。
- 商品字段映射包含条码、名称、分类、单位、售价、优惠价、起购数量。
- 导入后的商品会保存到手机本地 `products.json`，App 重启后可继续使用商品库。
- 商品库已新增 `product_library_meta.json` 和 `import_snapshots/` 快照存储。
- 最近 5 次 `.db` 导入会保存解析后的 JSON 快照，不保存原始 `.db` 副本。
- 导入页可显示当前商品数、最近导入时间、最近导入文件、本地是否手动修改、最近导入版本列表。
- 导入新 `.db` 前会确认会覆盖本地修改；导入成功后替换当前商品库并清除“已手动修改”状态。
- 商品库支持从最近导入快照回滚，回滚前会确认会覆盖当前本地修改。
- 条码手动输入添加商品。
- 手机相机扫码，基于 ZXing，支持常见零售条码格式。
- 未找到商品时弹窗提示，可取消返回。
- 可手动输入价格，按 `almacen` 分类加入购物车。
- 商品名称关键词搜索。
- 搜索已改为返回所有匹配商品，不再限制 10 条。
- 搜索支持大小写无关、重音符号无关、多关键词乱序、忽略 `de/del/el/la/los/las` 等常见西语连接词。
- 搜索结果弹窗可滚动，并显示匹配数量。
- 购物车支持改数量、删行、手动改价。
- 收银页购物车商品行已改为紧凑布局，每行只显示商品名、条码、数量、单价、小计和右侧“操作 / Mas”按钮；改数量、改价、删除、折扣、减价、撤回改动等操作已移入二级操作弹窗。
- 单行商品支持百分比优惠和固定金额优惠，并可撤回手动改价/优惠。
- 整单支持百分比优惠和固定金额优惠，并可清除。
- 自动优惠支持当前数据库中可读的优惠价和起购数量规则。
- 支付方式包含现金、Mercado Pago、借记卡、信用卡、transferencia。
- 可保存销售单，并在当前 App 会话内查看交易明细。
- 收银页已改为内部 tab：收银 / 交易明细；交易明细不再作为一级底部导航入口。
- 收银页内部 tab 已修复为可在“收银”和“交易明细”之间双向切换。
- 可查看当天总账，按支付方式汇总。
- 可作废当前会话内的销售单。
- 商品编辑入口已实现，包含条码/短码查找、扫码查找、关键词搜索。
- 条码/短码或扫码找到商品时进入编辑页，找不到时进入新建商品页并自动填入条码。
- 关键词搜索无结果时提示；一个结果直接进入编辑；多个结果显示可滚动列表供选择。
- 商品表单支持新建、修改、删除已入库商品。
- 商品表单字段包含条码、商品名、售价、分类、单位、促销价、促销数量。
- 商品编辑复用后端校验：条码 1-13 位数字、售价整数且大于 0、促销价和促销数量成对填写、促销价小于售价、条码不可重复。
- 新建商品遇到同名商品会提示但允许继续保存；遇到已有条码可转去编辑已有商品或取消新建。
- 修改条码、售价、促销信息时保存前会确认；保存后显示变化列表。
- 删除商品需要两次确认，删除只影响后续搜索和加入购物车，不影响当前购物车快照。
- 商品编辑表单存在未保存修改时，返回首页或返回搜索页会提示继续编辑或放弃修改。
- core 层已有 CSV 销售导出适配器。
- 已生成可直接安装的 debug APK。
- 已上传源码、文档和 APK 到 GitHub。
- 已新增电脑端 `pc-sync-tool` 后端骨架，完成配置、AppData 路径、源 `.db` 定位、`CJQ_GOODLIST` 只读校验、SHA-256、manifest、原子备份、历史保留、HTTP API、事件日志、开机启动注册表封装和测试。
- 已新增电脑端 `pc-sync-tool` PySide6 前端：主窗口、托盘菜单、来源选择、备份间隔、端口/IP/token 设置、二维码展示、状态面板、日志列表、关闭窗口最小化到托盘。

## 已整理的开发方案

- `E:\手机收银软件开发\修改方案\product_editing_plan.md` 已更新为适配当前 `android-emergency-pos` 项目框架的商品编辑与导入回滚开发计划。
- 方案已明确本地开发主目录是 `android-emergency-pos`；`mobile_pos_publish` 只作为发布副本/同步副本参考，不作为首要修改目录。
- 方案继续沿用当前原生 Android Java、`app/core` 分层、程序化 View、JSON 商品库存储，不引入 Room、Compose 或大型管理系统框架。
- 方案已对齐鸣盛 / ESpsa `.db` 格式：当前导入器读取 `CJQ_GOODLIST`，字段映射以 `GNID/GID`、`GBarcode`、`GNameX/GYiNameJian`、`RTypeName/GType`、`UName/GUNIT`、`GSalePrice`、`GHuiPrice`、`GHuiPriceCount` 为准。
- 方案已明确金额继续保存为整数 `Money.of(long)`；导入小数价格按当前 mapper 四舍五入，商品编辑页也只允许整数价格。
- 方案已修正商品分类规则：UI 可显示空白选项，但保存到当前 `Product` 时空分类按现有模型归一为 `almacen`。
- 方案已明确商品编辑、删除、导入、回滚只影响后续搜索和加入购物车；当前购物车中已有商品保持加入时的商品和价格快照。
- 方案已明确导入安全顺序：先解析 `.db`，再写入 snapshot、`products.json`、metadata，全部保存成功后才替换内存 repository。
- 方案已统一删除商品为两次确认，并补充最近 5 次导入快照只保存解析后的 JSON，不保存原始 `.db` 副本。

## 已验证

- `CoreSmokeTest` 通过。
- 已对照 `docs/PROJECT_LOG.md` 同步到 2026-07-06 的“修复收银/交易明细 tab 无法返回”记录。
- 2026-07-06 前端接入后，`CoreSmokeTest` 再次通过。
- 2026-07-06 前端接入后，使用 JDK `javac` 对新增 app UI 入口和相关 core/app 依赖做了 Java 编译检查，通过。
- 2026-07-06 商品编辑前端接入后，`CoreSmokeTest` 再次通过。
- 2026-07-06 商品编辑前端接入后，完整 debug APK Gradle 构建成功。
- 2026-07-06 修复收银/交易明细 tab 返回问题后，`CoreSmokeTest` 再次通过。
- 2026-07-06 修复收银/交易明细 tab 返回问题后，完整 debug APK Gradle 构建成功。
- 2026-07-07 精简收银页商品行 UI 后，`CoreSmokeTest` 通过，完整 debug APK Gradle 构建成功。
- 最新本地 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 931969 bytes，构建时间 2026-07-08 02:00:36。
- 真实商品数据库中确认 `huevo`、`huevo blanco`、`maple` 等关键词存在匹配数据。
- 搜索回归测试覆盖：
  - `huevo` 返回全部匹配测试商品。
  - `maple huevo` 可乱序匹配。
  - `maple de huevo` 可忽略连接词匹配 `Huevo Blanco Maple`。
- 2026-07-09 电脑端同步工具前端接入后，后端 12 个单测继续通过，`python -m compileall src tests` 通过；使用工作区临时 AppData 验证 `--print-setup-url` 输出正常。
- 2026-07-09 电脑端同步工具前端修复后，单测扩展到 18 个并全部通过，`python -m compileall src tests` 通过；覆盖所选局域网 IP 传给 HTTP 服务、自启动命令区分源码/打包运行。

## 尚未完成

- 最新前端接入后还需要在真机或模拟器上做端到端手工验收：商品新建、修改、删除、导入、回滚、扫码进入商品编辑、收银加入商品、当前购物车快照不随商品编辑变化。
- 收银和商品编辑中的关键词搜索在弹出结果前有短暂停顿，需优化搜索索引、结果数量限制或异步搜索体验。
- 销售记录持久化：当前销售单保存在内存里，App 被系统杀掉或重启后会丢失。需要实现本地销售存储。
- 按日期导出 Excel/CSV 的 Android UI：core 已有 CSV 导出适配器，但手机界面还没有完整导出入口。
- `.xlsx` 商品导入：设计文档中保留了 Excel 备用导入路径，但当前 App 只接入 `.db` 导入。
- 交易明细页目前是当前会话内明细，后续应接入持久化销售仓库。
- 正式 release APK 签名：当前产物是 debug APK。
- 电脑端同步工具 GUI 依赖环境、主窗口构建、HTTP 自动启动、二维码渲染和真实 Windows 托盘人工确认均已通过。

## 重要技术边界

- 不上传、不提交真实经营数据库、商品导出表、ESpsa 分析目录。
- UI 不直接读取鸣盛原始表，导入逻辑集中在 importer / Android adapter。
- 价格和优惠计算集中在 `core.pricing`。
- 销售单应保存交易发生时的商品、价格、数量、折扣、支付方式快照。
- 商品库继续保存为解析后的 JSON，不写回鸣盛原始 `.db`，也不保存原始 `.db` 副本。
- 商品编辑只维护当前手机本地商品库；重新导入 `.db` 可以覆盖本地手动修改和自建商品。
- 当前购物车中的商品行不随商品编辑、删除、导入或回滚自动变化。

## 建议下一步

1. 优先优化收银和商品编辑的关键词搜索体验，减少点击搜索到弹出结果之间的停顿。
2. 在真机或模拟器上按 `product_editing_plan.md` 测试清单验收商品编辑、导入、回滚、收银明细 tab 返回和收银快照兼容性。
3. GitHub 发布副本已随本次修复同步；后续继续以 `android-emergency-pos` 为主开发目录，`mobile_pos_publish` 只作为发布副本。
4. 将新版 APK 上传到 Google Drive，需要保留旧版本时使用新文件名上传。
5. 继续补齐本地销售持久化、交易明细持久化、日账读取持久化销售仓库、按日期导出 CSV UI。
6. 后续再评估 `.xlsx` 商品导入和正式 release APK 签名。
## 2026-07-07 搜索无卡顿优化验收完成

- 已按 `修改方案/search_optimization_plan.md` 完成开发验收。
- 后端搜索已建立预处理索引：新增 `ProductSearchEntry`，`InMemoryProductRepository` 维护 `searchEntries` / `searchEntryById`，在 `replaceAll()`、`upsert()`、`deleteById()` 时同步更新索引。
- 搜索匹配继续支持大小写无关、重音符号无关、多关键词乱序、忽略常见西语连接词，且结果不再做固定 10 条上限。
- 前端搜索已移出 UI 线程：新增 `SearchTaskRunner`，收银页和商品编辑页通过后台任务执行搜索，并使用 latest request 防护避免旧结果覆盖新结果。
- 搜索结果 UI 已改为复用组件：新增 `ProductSearchResultAdapter` 和 `ProductSearchResultDialog`，使用 `ListView` 展示全部匹配结果，避免一次性创建大量按钮。
- 收银页搜索结果点击后仍加入购物车；商品编辑页搜索结果点击后进入编辑页；未改变购物车、价格计算、促销计算或销售记录逻辑。
- 验收已通过：`CoreSmokeTest` 通过；完整 debug APK Gradle 构建成功。
- 最新验收 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 `889497 bytes`，构建时间 `2026-07-07 19:52:44`。
## 2026-07-08 UI/收银/字体适配验收完成

- 已按 `修改方案/ui_checkout_search_font_improvement_plan.md` 完成并验收本轮 UI、收银找零、搜索交互改进。
- 已按 `修改方案/text_scale_ui_controls_fix_plan.md` 完成并验收字体大小适配遗漏修复。
- 已实现离开页面/页面重绘时取消 pending 搜索回调，避免旧页面搜索结果在新页面弹出。
- 已实现回车/IME 搜索动作：收银页输入关键词回车可搜索；商品编辑页关键词回车可搜索；商品编辑页条码输入框回车仍执行条码查找/新建。
- 已实现现金结账找零弹窗：现金支付时输入客户付款金额，金额不足不保存销售，金额足够显示找零并确认保存；非现金支付流程保持原逻辑。
- 已实现字体大小设置：设置页支持小、标准、大、特大，并持久化到偏好设置；主要文本、按钮、输入框、支付方式下拉框、商品表单下拉框统一跟随字体档位缩放。
- 已补齐统一 UI helper：`Views.editText(...)`、`Views.spinnerAdapter(...)`、`KeyboardActions`、`CashPaymentDialog`、`TextScale`。
- 已新增 `CashChangeCalculator` / `CashChangeResult` 并在 `CoreSmokeTest` 覆盖现金找零边界。
- 验收已通过：页面类中不再散落未缩放的 `new EditText(...)` 或默认 `ArrayAdapter`；`CoreSmokeTest` 通过；完整 debug APK Gradle 构建成功。
- 最新验收 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 `904393 bytes`，构建时间 `2026-07-08 01:18:20`。
## 2026-07-08 卡片式 UI 与多格式导入验收完成

- 已按 `修改方案/ui_cards_and_multi_format_import_plan.md` 完成本轮开发验收。
- 一级界面已从简单按钮陈列改为更适合收银工具的卡片式工作台：主页、导入页、设置页、收银入口页使用统一卡片样式，文本层级和主操作更清晰。
- 导入页已改为导入格式卡片，可选择鸣盛 `.db` 数据库或通用 `.csv` 商品表。
- 导入流程已支持按格式打开系统文件选择器，并保留鸣盛 `.db` 原有导入能力。
- 已新增通用导入架构：`ImportFormat`、`ProductImportAdapter`、`ImportFormatRegistry`，导入格式选择与解析逻辑不写入 UI 页面。
- 已新增 `CsvProductImportAdapter`，第一版支持通用 CSV 商品表，字段别名覆盖条码、名称、售价、分类、单位；缺少必填字段、空文件、无有效商品会给出错误或 warning。
- `AppServices` 已提供统一导入入口，导入成功后继续复用现有商品库覆盖、快照、metadata 和本地修改状态重置逻辑。
- 回归保持：收银、商品编辑、搜索、现金找零、字体大小设置不改变业务逻辑。
- 验收已通过：`CoreSmokeTest` 通过；完整 debug APK Gradle 构建成功。
- 最新验收 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 `931969 bytes`，构建时间 `2026-07-08 02:00:36`。
## 2026-07-09 电脑端同步工具后端完成

- 已按 `修改方案/pc_sync_http_tool_plan.md` 完成阶段 A 的电脑端后端开发，目录为 `E:\手机收银软件开发\pc-sync-tool`。
- 已实现纯 Python 标准库后端模块：配置读写、AppData 路径、token、源文件/文件夹定位、SQLite 只读表校验、稳定性检查、原子备份、manifest、HTTP API、事件日志、注册表开机启动封装和同步地址生成。
- HTTP API 覆盖 `/health`、`/manifest.json`、`/latest.db`，均要求 `token`；错误 token 返回 403。
- 后端只读打开鸣盛源 `.db`，只写入工具自己的 AppData 目录，不修改鸣盛原目录文件。
- 验收已通过：`python -m unittest discover -s tests` 通过，`python -m compileall src tests` 通过。

### 2026-07-09 后端一致性修复

- HTTP 服务默认绑定地址已改为使用 `selectedHost`。
- 备份复制后已增加源文件二次稳定校验，复制期间源文件变化不会发布新版本。
- `/latest.db` 下载前已强制校验 manifest 与实际文件的 size/hash 一致性。
- manifest 缺失、无 hash、size 不匹配或 hash 不匹配时，HTTP 不再发送数据库文件。
- 回归测试已通过：`python -m unittest discover -s tests` 通过，16 个测试 OK。

### 2026-07-09 发布/下载竞态修复

- 已新增共享发布锁，覆盖备份发布 `latest.db` / `manifest.json` 和 HTTP `/latest.db` 的“校验+发送”临界区。
- 已消除 HTTP 校验通过后备份线程替换 `latest.db`，导致旧 hash header 发送新 DB 文件的竞态窗口。
- 回归测试已通过：`python -m unittest discover -s tests` 通过，20 个测试 OK；`python -m compileall src tests` 通过。
## 2026-07-09 电脑端同步工具前端接入

- 已按 `修改方案/pc_sync_http_tool_plan.md` 完成阶段 A 的电脑端前端代码接入。
- 新增 `pc-sync-tool/src/ui/`，包含 Qt 主窗口、控制器和局域网 IP 候选辅助。
- `python src\app.py` 默认启动桌面工具；`--backup-once`、`--serve`、`--print-setup-url` 后端命令继续保留。
- 前端只调用后端模块：保存配置、启动/停止 HTTP、立即备份、检测来源、读取 manifest、读取事件日志、生成同步地址；不直接复制源文件、不直接写 manifest。
- 已修复所选局域网 IP 没有显式传给后端服务的问题；UI 会显示实际服务绑定地址和二维码状态。
- 已修复开机自启动命令：源码运行写入 `python.exe src\app.py --gui`，打包运行写入 exe 本身。
- 依赖已补充 `PySide6` 和 `qrcode[pil]`。
- 验证已通过：18 个单测通过，`python -m compileall src tests` 通过；使用工作区临时 AppData 验证 `--print-setup-url` 输出正常。
- GUI 依赖与最终 smoke test 已完成：使用 `E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe` 验证 `PySide6 6.11.1`、`qrcode`、`Pillow 12.3.0` 可导入；`20` 个单测通过；关键源码 `py_compile` 通过；offscreen 构建 `MainWindow` 成功，HTTP 服务启动成功，二维码 `QPixmap` 生成成功。
- 已修复 PySide6 6.11.1 图标 API 兼容问题：`main_window.py` 改为使用 `QStyle.StandardPixmap.SP_ComputerIcon`，避免 `QCommonStyle` 无 `SP_ComputerIcon` 属性导致主窗口启动崩溃。

### 2026-07-09 电脑端同步工具最终验收结论

- 电脑端后端、前端、GUI 依赖环境与关键兼容修复已完成验收。
- 当前电脑端同步工具核心能力已闭环：只读定位鸣盛 `.db`、复制到工具自有 AppData、生成 manifest/hash、保留最近 5 个历史备份、通过 token HTTP API 提供 `/health`、`/manifest.json`、`/latest.db`、二维码连接地址、托盘/窗口 UI、自动备份间隔配置和开机启动命令封装。
- 本轮修复闭环包括：HTTP 绑定 `selectedHost`、源 DB 复制后二次稳定校验、manifest/latest hash/size 下载前校验、共享发布锁消除下载/发布竞态、源码/打包自启动命令区分、服务绑定/二维码状态 UI 显示、PySide6 图标兼容修复。
- 验证结果：`python -m unittest discover -s tests` 通过，`20` 个测试 OK；关键源码 `py_compile` 通过；offscreen GUI smoke test 输出 `service_running=True`、`qr_status=可用`、`qr_pixmap_null=False`。
- 说明：当前 smoke test 已验证主窗口构建、HTTP 自动启动和二维码渲染；用户已人工确认真实 Windows 窗口、二维码、关闭最小化到托盘、托盘菜单和退出流程均正常。
- 手机端接收/扫码/下载/校验/导入流程已在 2026-07-10 完成开发与修复验收，下一步进入电脑端 + 手机端真实联调。

## 2026-07-10 手机端电脑同步接入与修复验收完成

- 已新增手机端 `app/sync` 模块：`ComputerSyncConfig`、`ComputerSyncStore`、`ComputerSyncClient`、`ComputerSyncManifest`、`ComputerSyncService`、`ComputerSyncException`。
- 手机端已支持扫描电脑端二维码 `mobilepos-sync://setup?host=...&port=...&token=...`，保存 host、port、token 到本机 `SharedPreferences`。
- 导入页新增“电脑同步”卡片：显示配置状态、电脑地址、上次检查、上次同步；支持扫码连接、测试连接、检查新版本、立即同步。
- 手机端网络同步路径已接入电脑端契约：`/health` 测试连接、`/manifest.json` 检查备份 manifest、`/latest.db` 下载数据库副本，所有请求带 token。
- 下载后会计算本地文件 SHA-256，并与 manifest 的 `sha256` 比对；hash 不一致会删除临时文件并拒绝导入。
- 同步导入复用既有鸣盛 `.db` 导入链路和 `ProductLibraryService` 保存逻辑，导入成功后更新本地商品库、快照和 lastSynced hash。
- UI 已保留导入前确认；如果手机本地存在手动修改或自建商品，会进行二次确认后才覆盖。
- 验收发现并修复两项流程问题：
  - “检查新版本”按钮原本也会进入导入确认；现已改为只显示“已是最新版本”或“发现新版本”，只有“立即同步”才进入导入确认。
  - 用户确认的 manifest 原本可能在实际导入前被重新请求替换；现已改为将同一个 `ComputerSyncManifest` 从确认弹窗传递到 `syncNow(manifest)` 和 `AppServices.syncProductsFromComputer(context, confirmedManifest)`，下载、导入和 `markSynced` 均使用已确认 manifest。
- 权限与扫码：`AndroidManifest.xml` 已包含 `INTERNET` 和 `CAMERA`；扫码器已支持 `QR_CODE`。
- 验证结果：`CoreSmokeTest` 通过；完整 debug APK Gradle 构建成功。
- 最新验收 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 `951496 bytes`，构建时间 `2026-07-10 13:51:40`。
- 后续待做：使用真实手机和已确认通过的电脑端 `pc-sync-tool` 做端到端联调，覆盖扫码配置、连接测试、检查新版本、立即同步、hash 校验、导入覆盖确认、本地修改二次确认和同步后商品搜索/收银可用性。
