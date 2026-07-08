# 手机应急收银 App 项目进度

更新日期：2026-07-07

## 当前目标

为阿根廷华人超市制作一套 Android 10+ 原生应急收银 App。它在停电、电脑收银主机不可用、或临时需要手机查价时使用。App 离线运行，不直接修改电脑收银系统数据库，通过定期导入鸣盛 / ESpsa 商品数据库更新商品资料。

## 本地主要目录

- 工作根目录：`E:\手机收银软件开发`
- Android 源码：`E:\手机收银软件开发\android-emergency-pos`
- 英文构建副本：`E:\AndroidEmergencyPos`
- 便携构建环境：`E:\AndroidBuildEnv`
- 最新鸣盛商品数据库样本：`E:\手机收银软件开发\AGT_MAIN_20260705.db`
- 最新 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`

## 远程与发布位置

- GitHub 仓库：https://github.com/SantiagoChenCN/mobile_pos
- GitHub 状态：同步到 `main`，具体最新提交以仓库为准
- Google Drive APK：https://drive.google.com/file/d/1ohzXrAsUOfK3cicSmrIKpilWcjryflE7/view?usp=drivesdk
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
- 最新本地 APK：`E:\手机收银软件开发\android-emergency-pos\dist\EmergencyPOS-debug.apk`，大小 876821 bytes，构建时间 2026-07-07 18:11:54。
- 真实商品数据库中确认 `huevo`、`huevo blanco`、`maple` 等关键词存在匹配数据。
- 搜索回归测试覆盖：
  - `huevo` 返回全部匹配测试商品。
  - `maple huevo` 可乱序匹配。
  - `maple de huevo` 可忽略连接词匹配 `Huevo Blanco Maple`。

## 尚未完成

- 最新前端接入后还需要在真机或模拟器上做端到端手工验收：商品新建、修改、删除、导入、回滚、扫码进入商品编辑、收银加入商品、当前购物车快照不随商品编辑变化。
- 收银和商品编辑中的关键词搜索在弹出结果前有短暂停顿，需优化搜索索引、结果数量限制或异步搜索体验。
- Google Drive 中的 APK 尚未更新为本次 tab 修复后的新版本。
- 销售记录持久化：当前销售单保存在内存里，App 被系统杀掉或重启后会丢失。需要实现本地销售存储。
- 按日期导出 Excel/CSV 的 Android UI：core 已有 CSV 导出适配器，但手机界面还没有完整导出入口。
- `.xlsx` 商品导入：设计文档中保留了 Excel 备用导入路径，但当前 App 只接入 `.db` 导入。
- 交易明细页目前是当前会话内明细，后续应接入持久化销售仓库。
- 正式 release APK 签名：当前产物是 debug APK。

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
