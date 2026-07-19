# 手机应急收银 App 项目进度

更新日期：2026-07-19

## 当前目标

为阿根廷华人超市制作一套 Android 10+ 原生应急收银 App。它在停电、电脑收银主机不可用、或临时需要手机查价时使用。App 离线运行，不直接修改电脑收银系统数据库；当前通过导入鸣盛 / ESpsa 商品数据库更新商品资料，后续计划接入已确认的 `MS2011` 实时主库只读导出，实现电脑到手机的单向同步。

## 当前 MS2011 实施状态

- S10/L3 进行中并按用户要求暂停；尚未完成阶段验收，也未进入 S11。
- MB-07、CB-01、CB-02、CB-03 和主机侧离线 G4 已通过：手机端具备前台 single-flight v2 检查、pending-only 下载、购物车定价快照固定、active/pending 订单边界切换和 durable 故障恢复测试。
- 稳定簇独立复核结论为逐任务 PASS，High/Medium 0；保留 1 个 Low：coordinator host smoke 未等待非零周期真实到期。
- MF-05 在暂停前产生两项部分修改：`MainActivity.java` 与新增 `MainActivityLifecycleContractTest.java`；尚未由主协调器编译、运行测试或验收，不能标记完成。
- MF-02 尚未写入：方案没有定义“长期未更新/长期失败明显警告”的快照年龄与连续失败阈值，必须先取得产品决定；MF-03 依赖 MF-02。
- 当前源码变化后尚未执行 S10 阶段全回归、跨端离线验收或 APK 构建；现有 build-output APK 仍是 S09 证据，不能代表 S10。
- G0B 继续锁定，目标身份仍为 `WRITE_CAPABILITY_PRESENT`；禁止真实 MS2011 自动读取、手机直连 SQL Server、数据库写入、真实生产发布。

## 本地主要目录

- 工作根目录：`E:\手机收银软件开发`
- Android 源码：`E:\手机收银软件开发\android-emergency-pos`
- 英文构建副本：`E:\AndroidEmergencyPos`
- 便携构建环境：`E:\AndroidBuildEnv`
- 最新鸣盛商品数据库样本：`E:\手机收银软件开发\AGT_MAIN_20260705.db`
- 鸣盛软件副本：`E:\手机收银软件开发\EPSA\ESpsa (1)`
- MS2011 实时只读导出：`E:\手机收银软件开发\MS2011_PRODUCT_EXPORT_20260713\MS2011_PRODUCT_EXPORT_20260713`
- MS2011 数据分析报告：`E:\手机收银软件开发\MS2011_商品分类单位促销数据分析.md`
- 鸣盛软件静态分析报告：`E:\手机收银软件开发\鸣盛收银软件_EPSA_静态分析报告.md`
- 最新阶段构建 APK（未发布）：`E:\手机收银软件开发\android-emergency-pos\app\build\outputs\apk\debug\app-debug.apk`
- `dist`/发布副本 APK：仍为旧制品，不代表当前源码
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
- 商品模型已区分 `LOCAL`、`LEGACY_IMPORT` 和 `MS2011_SYNC`；旧 JSON 缺少来源时保持 `LEGACY_IMPORT`，无法判定时生成原子写迁移报告，不静默猜测为本地商品。
- 手机启动可从已验证的 immutable v2 active/lastGood 快照只读重建同步商品；候选 catalog 完整校验后才一次替换，停用同步商品普通搜索和旧结账路径均不可售。
- 同步商品使用严格 `ms2011:<GID>` 身份、正常扫码优先于同条码 LOCAL，LOCAL 冲突保留可见；同步商品不写入本地 `products.json`，也不灌入旧简单促销字段。
- 条码手动输入添加商品。
- 手机相机扫码，基于 ZXing，支持常见零售条码格式。
- 未找到商品时弹窗提示，可取消返回。
- 可手动输入价格，按 `almacen` 分类加入购物车。
- 商品名称关键词搜索。
- 搜索已改为返回所有匹配商品，不再限制 10 条。
- 搜索支持大小写无关、重音符号无关、多关键词乱序、忽略 `de/del/el/la/los/las` 等常见西语连接词。
- 搜索结果弹窗可滚动，并显示匹配数量。
- 购物车支持改数量、删行、手动改价。
- 同一正式商品通过扫码、条码输入或搜索重复加入时，会按稳定 `product.id` 合并为一行并累加数量；合并保留原行 ID、手动单价和单行折扣，数量促销按新数量重新计算；手动 `almacen` 商品始终保持独立行。
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
- 已新增电脑端 `pc-sync-tool` PySide6 前端：主窗口、托盘菜单、来源选择、备份间隔、端口/IP/token 设置、手机连接信息、状态面板、日志列表、关闭窗口最小化到托盘。
- 已按手动 Token 连接方案更新电脑端前端：主流程不再显示二维码，改为显示电脑 IP、端口、Token，并提供复制全部连接信息、复制 IP、复制 Token。

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

- 2026-07-11 电脑端连接修复前端已接入：主窗口分别显示手机连接 IP、实际监听地址、HTTP 服务状态和本机健康检查结果；服务未运行、端口占用、本机健康检查失败和无效地址均有明确提示。IP 校验结果统一由后端诊断提供，前端不再自行判断；回环、监听、链路本地、组播、广播/保留及非私有地址会禁用连接信息/IP 复制。地址下拉框仅推荐私有局域网 IPv4，并优先排除常见虚拟、隧道和 VPN 适配器；窗口使用滚动容器保证缩小时仍可访问完整连接信息。使用指定 PySide6 虚拟环境运行 40 个测试通过，`compileall` 通过。

- 2026-07-11 电脑端连接修复后，`pc-sync-tool` HTTP 服务默认监听 `0.0.0.0`，而手机连接信息和 `/health.host` 继续使用所选局域网 IPv4；新增局域网地址筛选、结构化网络诊断和不含 Token 的 HTTP 请求事件日志。`python -m unittest discover -s tests -v` 30 个测试通过，`py_compile` 检查通过。
- 2026-07-11 已补齐电脑端 IPv4 校验：拒绝回环、未指定、链路本地、组播、全局广播和保留地址；无效地址不能生成可复制连接信息，`/health` 不会返回 `0.0.0.0`。使用 PySide6 虚拟环境运行完整测试集，37 个测试通过。
- 2026-07-11 已新增电脑端 `time_display.py`，将 UTC/带偏移 ISO 时间统一格式化为阿根廷时间 `yyyy-MM-dd HH:mm:ss ART`；manifest、事件日志和备份文件继续保存 UTC。时间模块测试覆盖 8 个边界场景。
- 2026-07-11 电脑端前端已接入阿根廷时间展示：最近备份、最近请求、controller 事件文本和主窗口事件日志统一显示 `yyyy-MM-dd HH:mm:ss ART`；新增前端回归覆盖，指定 PySide6 虚拟环境下完整电脑端测试 50 个通过，`compileall` 通过。

- `CoreSmokeTest` 通过。
- 已对照 `docs/PROJECT_LOG.md` 同步到 2026-07-06 的“修复收银/交易明细 tab 无法返回”记录。
- 2026-07-06 前端接入后，`CoreSmokeTest` 再次通过。
- 2026-07-06 前端接入后，使用 JDK `javac` 对新增 app UI 入口和相关 core/app 依赖做了 Java 编译检查，通过。
- 2026-07-06 商品编辑前端接入后，`CoreSmokeTest` 再次通过。
- 2026-07-06 商品编辑前端接入后，完整 debug APK Gradle 构建成功。
- 2026-07-06 修复收银/交易明细 tab 返回问题后，`CoreSmokeTest` 再次通过。
- 2026-07-06 修复收银/交易明细 tab 返回问题后，完整 debug APK Gradle 构建成功。
- 2026-07-07 精简收银页商品行 UI 后，`CoreSmokeTest` 通过，完整 debug APK Gradle 构建成功。
- 2026-07-08 历史 APK 记录：当时 `EmergencyPOS-debug.apk` 大小为 `931969 bytes`；当前最新 APK 以“近期已完成的连接修复”章节中的 2026-07-11 Hash 记录为准。
- 真实商品数据库中确认 `huevo`、`huevo blanco`、`maple` 等关键词存在匹配数据。
- 搜索回归测试覆盖：
  - `huevo` 返回全部匹配测试商品。
  - `maple huevo` 可乱序匹配。
  - `maple de huevo` 可忽略连接词匹配 `Huevo Blanco Maple`。
- 2026-07-09 电脑端同步工具前端接入后，后端 12 个单测继续通过，`python -m compileall src tests` 通过；使用工作区临时 AppData 验证 `--print-setup-url` 输出正常。
- 2026-07-09 电脑端同步工具前端修复后，单测扩展到 18 个并全部通过，`python -m compileall src tests` 通过；覆盖所选局域网 IP 传给 HTTP 服务、自启动命令区分源码/打包运行。
- 2026-07-10 电脑端手动连接信息前端接入后，`pc-sync-tool` 23 个单测通过，`python -m compileall src tests` 通过；offscreen 构建主窗口成功并验证连接信息字段显示正常。

## 近期已完成的连接修复

- 2026-07-11 手机端局域网连接修复完成：最终合并 manifest 已包含 `android:usesCleartextTraffic="true"`，允许当前局域网 HTTP 架构；同步 Client 已将明文 HTTP 被阻止、超时、连接拒绝、未知地址、HTTP 403、HTTP 错误和无效响应转换为结构化错误类型。
- 手机端同步配置层已拒绝 `127.0.0.1`、`localhost`、`0.0.0.0`、无效 IPv4 和缺失的端口/Token；健康检查会校验 `ok`、`app`、版本、主机和端口字段。
- 手机端前端已接入中西双语错误 presenter，按错误类型显示同一 Wi-Fi、防火墙、HTTP 服务、Token、IP 和 APK 版本等可执行提示；连接成功会显示电脑 IP、端口和电脑工具版本。
- 手机端导入页面已增加页面脱离监听、任务 generation、运行任务集合和 `dispose()`；页面离开或销毁时会中断连接线程并忽略过期回调，避免离开页面后继续弹窗或刷新旧控件。
- 手机端回归验证：`CoreSmokeTest`、`CartMergeSmokeTest`、`ArgentinaLedgerDateSmokeTest`、`ArgentinaTimeSmokeTest`、`ComputerSyncClientSmokeTest`、`ComputerSyncServiceSmokeTest`、`ComputerSyncErrorPresenterSmokeTest` 全部通过；完整 Debug APK Gradle 构建成功。
- 手机端阿根廷时间已统一：同步检查、同步完成、manifest、最近导入和导入快照在 UI 中显示 `yyyy-MM-dd HH:mm:ss ART`；日账、今日销售和销售单号使用 `America/Argentina/Buenos_Aires`，原始 `Instant`、manifest 和同步存储继续保留 UTC。
- 购物车同商品合并已通过最终边界修复：`sameProduct()` 会同时排除已有行和新加入商品中的手动价格商品，即使正式商品与手动 `almacen` 商品 ID 冲突，无论加入顺序都保持两行。
- 最新项目 APK 与构建输出 SHA-256 一致，大小均为 `1032530 bytes`，时间均为 `2026-07-11 10:17:56`，SHA-256 为 `48D488084C4160B999090647EA3130619040CB151A899E543495E604AF52E7C2`。
- 连接修复后的代码级验收通过：电脑端 50 个测试和 Python 编译检查通过，Android 构建及同步相关烟测通过；尚未完成目标收银电脑上的真实手机局域网联调。

## 2026-07-12 至 2026-07-13 鸣盛实时主库只读分析完成

- 已对 `EPSA\ESpsa (1)` 副本完成只读静态分析：764 个文件均可只读打开，但副本不含鸣盛核心程序的完整原始工程；核心 Windows 程序主要为受保护的 32 位原生程序，部分旧辅助程序为可反编译的 .NET 程序，但反编译结果不等于原始源码。
- 已确认运行中商品主库不在拷贝的 EPSA 目录内，而位于收银电脑 `D:\Espsa\SQL2000\Data\MS2011.MDF`，对应数据库名为 `MS2011`；运行时 MDF/LDF 被 SQL Server 占用，不采用直接复制方式。
- 已在 SQL 查询分析器中使用只读查询确认商品、分类、单位和促销表结构；开发过程中未执行 `INSERT`、`UPDATE`、`DELETE`、建表、恢复、分离或附加数据库等写操作。
- 旧版 `D:\Espsa\SQL2000\Tools\bcp.exe` 首次导出出现 `ODBCBCP/驱动程序版本不匹配`；解决方案是在 `D:\MS2011_PRODUCT_EXPORT_20260713` 外置目录准备 BCP 运行文件，并让其使用 Windows 匹配的系统 ODBC/BCP 驱动。未在 Espsa 目录内创建导出文件，也未替换鸣盛自带组件。
- 已通过 `bcp out -S SERVER -T -w` 只读导出 13 张表：`MS_GOODLIST`、`MS_GOODTYPELIST`、`MS_UNITLIST` 及 10 张促销相关表；全部 TSV 均为 UTF-16，列数与已确认表结构一致。
- 实时 `MS_GOODLIST` 有 11,168 条商品，较 EPSA 快照的 11,141 条多 27 条；最新 `GUpdateTime` 为 `2026-07-12 21:14:16`。`GID`、条码均完整且唯一，分类应按 `GClass = RTypeCode` 关联，单位应按 `GUnit = UNumCode` 关联。
- `GKCCount` 全部不大于零，现阶段不能作为可售库存判断；`GStopFlag=4` 的 38 条商品必须保留停用状态。
- 已还原复杂促销结构用于只读证据和候选建模；任何类型在取得完整黑盒证据并形成 `VERIFIED` 规则前都不得进入手机价格计算，不能猜测优先级、叠加、日期/时段、余数或舍入。
- 分析复现脚本位于 `ESpsa_analysis/analyze_ms2011_export.py`，只读取导出的 TSV 和 EPSA 快照，不连接、不修改鸣盛实时数据库。

## 尚未完成

- S10 尚未开始：手机前台同步协调、订单边界切换、生命周期触发、同步状态与只读/冲突 UI 仍待实施。
- 各促销类型仍需严格按 PV → PN/PE → PI 取得黑盒证据、规范规则和跨端集成；未验证字段不得直接启用自动促销。
- 真实生产同步仍被 G0B 锁定：当前目标身份为 `WRITE_CAPABILITY_PRESENT`，正式应用不得连接真实 MS2011、自动读取或生产发布。
- 2026-07-11 电脑端连接修复还需要在真实手机与收银电脑同一局域网下验收：确认 `0.0.0.0:8765` 的入站访问未被 Windows 防火墙或 AP 隔离阻断；程序不会自动创建或修改防火墙规则。
- 电脑端连接修复后的 EXE/ZIP 已按当前源码重新打包：`MobilePosSync.exe` 为 `2311392 bytes`，`MobilePosSync-windows.zip` 和 `MobilePosSync-windows-20260711.zip` 均为 `48624632 bytes`；仍需在目标收银电脑完成真实手机局域网联调。
- 购物车同商品合并仍需真机人工验收：分别通过扫码、条码输入和搜索连续加入同一正式商品，确认只显示一行并累加为 `x2/x3`；同时确认手动 `almacen` 商品保持独立行。

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
- 对鸣盛 `MS2011` 主库只允许执行 `SELECT` 和必要的只读系统元数据查询；不写回数据库，不直接复制运行中的 MDF/LDF，不在 Espsa 目录创建同步产物。
- 手机端只执行已经取得黑盒证据并标记 `VERIFIED` 的规范促销规则；原始候选、未知类型和未验证简单/复杂促销都不得静默计算。
- SQL 查询和 BCP 导出属于正常数据库访问，可能被 SQL Server 日志、Trace、审计软件或远程运维工具记录；项目不尝试隐藏或规避这些记录。
- UI 不直接读取鸣盛原始表，导入逻辑集中在 importer / Android adapter。
- 价格和优惠计算集中在 `core.pricing`。
- 销售单应保存交易发生时的商品、价格、数量、折扣、支付方式快照。
- 商品库继续保存为解析后的 JSON，不写回鸣盛原始 `.db`，也不保存原始 `.db` 副本。
- 商品编辑只维护当前手机本地商品库；重新导入 `.db` 可以覆盖本地手动修改和自建商品。
- 当前购物车中的商品行不随商品编辑、删除、导入或回滚自动变化。

## 建议下一步

1. 下一恢复点固定为只读核对 MF-05 的 `MainActivity.java` 和新增生命周期合同测试，随后由主协调器实际编译、测试并决定是否验收；不得直接跳到 MF-02。
2. MF-05 通过后先取得 MF-02 stale-age 与连续失败警告阈值的产品决定，再严格执行 MF-02 → MF-03；S10 阶段验收和文档同步完成后按用户要求停止，不进入 S11。
3. 只有外部管理员提供并复核 `READ_ONLY_PROVEN` 身份且用户批准后，才可进行真实 MS2011/LAN/设备验收；当前 G0B 保持锁定。
4. 继续保留真实 Android SQLite/文件系统/进程杀死恢复、APK 安装、软键盘、弹窗生命周期和页面遍历的人工验收清单。
5. 非本方案范围的销售持久化、按日期导出 UI、`.xlsx` 导入和正式 release 签名继续作为独立后续工作。

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

### 2026-07-10 电脑端手动连接信息后端接入

- 已按 `修改方案/manual_token_sync_connection_plan.md` 完成电脑端后端增量。
- 新增 `connection_info.py`，统一输出手动连接需要的 IP、端口和 Token。
- `UiController` 已提供连接信息方法，供前端显示和复制功能复用。
- `app.py` 已新增 `--print-connection-info`；旧 `--print-setup-url` 后续已删除。
- HTTP 协议、Token 鉴权、manifest/latest 下载、备份安全边界均未改变。
- 验证通过：`python -m unittest discover -s tests` 通过，23 个测试 OK；`python -m compileall src tests` 通过。

### 2026-07-10 电脑端旧二维码后端入口删除

- 已删除 `pc-sync-tool/src/qr_code.py`。
- 已删除 `app.py` 中的 `--print-setup-url` 旧入口。
- `README.md` 已更新为 `--print-connection-info` 手动连接信息命令。
- 旧 setup URL 测试已改为 `connection_summary` 测试。
- 验证通过：`python -m unittest discover -s tests` 通过，23 个测试 OK；`python -m compileall src tests` 通过。

### 2026-07-10 电脑端手动连接信息前端接入

- 已按 `修改方案/manual_token_sync_connection_plan.md` 完成电脑端前端主流程调整。
- `pc-sync-tool` 主窗口已删除二维码卡片，改为“手机连接信息”卡片。
- 连接信息卡片显示电脑 IP、HTTP 端口、Token、HTTP 可用状态，并提示手机和电脑必须在同一局域网。
- 已新增复制按钮：复制全部连接信息、复制 IP、复制 Token；托盘菜单同步改为“复制连接信息”。
- Token 重新生成提示已改为“手机端需要重新输入新的 Token”。
- `requirements.txt` 已移除 `qrcode[pil]`，`scripts/build_exe.ps1` 已移除 `qrcode.image.pil` hidden import。
- 旧 `qr_code.py` 和 `--print-setup-url` 已删除，不再作为电脑端 UI 主流程。
- HTTP 协议、Token 鉴权、manifest/latest 下载、备份安全边界均未改变。
- 验证通过：`python -m unittest discover -s tests` 通过，23 个测试 OK；`python -m compileall src tests` 通过；offscreen 构建 `MainWindow` 成功，连接信息字段显示正常。

### 2026-07-11 电脑端连接修复版本重新打包

- 已依据当前 `pc-sync-tool` 源码重新执行 PyInstaller 打包，包含局域网地址校验、连接诊断、连接信息卡片和滚动窗口等最新开发。
- 新 EXE：`pc-sync-tool/dist/MobilePosSync/MobilePosSync.exe`，大小 `2288007 bytes`。
- 新 ZIP：`pc-sync-tool/dist/MobilePosSync-windows-20260711.zip`，大小 `48572239 bytes`。
- 验证结果：电脑端 `python -m unittest discover -s tests -v` 通过，40 个测试 OK；`python -m compileall -q src tests` 通过；PyInstaller 打包成功。
- 发布边界：本轮只发布电脑端同步工具源码和打包产物，不包含鸣盛原始数据库、商品导出数据、虚拟环境、缓存或构建中间文件。
- 真实手机与收银电脑的同局域网端到端联调仍待在目标设备上完成；当前打包产物可用于该联调。

### 2026-07-11 阿根廷时间与购物车合并版本发布验收

- 手机端已完成阿根廷业务时区统一、正式商品按商品 ID 合并数量、手动 `almacen` 商品保持独立行，以及手动商品 ID 冲突边界修复。
- 电脑端已完成事件日志和主窗口时间统一显示为 `yyyy-MM-dd HH:mm:ss ART`，同时保留原始 UTC/带时区数据存储格式。
- 电脑端新增 `tzdata>=2025.1` 依赖，确保 Windows 打包环境能够加载 `America/Argentina/Buenos_Aires`。
- 电脑端验证：50 个测试通过，`compileall` 通过，时区加载检查通过，PyInstaller 打包成功。
- 新电脑端 EXE：`pc-sync-tool/dist/MobilePosSync/MobilePosSync.exe`，大小 `2314688 bytes`。
- 新电脑端 ZIP：`pc-sync-tool/dist/MobilePosSync-windows-20260711-argentina-time-cart-merge.zip`，大小 `48973489 bytes`。
- Android 最新 APK：`android-emergency-pos/dist/EmergencyPOS-debug.apk`，大小 `1032530 bytes`；手机端回归烟测与 Gradle 构建已通过。
- 剩余人工验收：在真实手机上验证正式商品连续加入时显示为一行并累计数量，手动 `almacen` 商品仍保持独立行；在真实收银电脑上验证电脑端 ZIP 的启动与局域网连接。

### 2026-07-14 文档与修改方案进度同步

- 新增 `docs/IMPLEMENTATION_STATUS.md`，集中记录当前已完成模块、各修改方案状态、剩余人工验收和安全边界。
- 新增 `修改方案/README.md`，区分已完成验收的方案与仍处于规划/证据收集阶段的 MS2011 商品促销实时同步方案。
- 已明确：MS2011 两份方案目前只完成设计、任务拆分和验收标准，尚未开始正式实现；没有把未取得黑盒证据的促销规则标记为已支持。
- README 已同步补充 Android 最新能力、电脑端 LAN 手动连接、阿根廷时间、购物车合并、打包方式和当前人工验收范围。

### 2026-07-16 MS2011 S01 证据门禁进度

- 项目级 Codex 协作配置已落地：`.codex/config.toml` 固定 `max_threads=4`、`max_depth=1`，并配置 MS2011 安全合同审查、PC 只读实施、Android v2 领域实施和发布验证四个专用代理。该配置只用于协调，不属于业务实现。
- MS2011 实施计划已加固为 S01 至 S17 小阶段和电脑后端、电脑前端、手机后端、手机前端四端覆盖；S01 的严格证据顺序为 `EV-01 → EV-03 → EV-02 → EV-04 → EV-05`。
- EV-01 已通过：形成 `修改方案/实施证据/workspace_baseline.md`；确认工作区根目录、`pc-sync-tool` 和 `android-emergency-pos` 不是有效 Git 仓库，`mobile_pos_publish` 是发布仓库；后续不得清理或覆盖 Git 无法识别的既有内容。
- EV-03 已通过：开发和目标电脑均验证 64 位 frozen Probe、`SQL Server` ODBC 驱动、Windows 集成认证、连接/查询 timeout 参数接受情况和两条固定只读查询；目标结果为 `MS2011`、商品数 `11180`、退出码 0。管理员 Windows 身份只证明兼容，不代表 EV-05 的 SQL 只读权限。
- EV-03 产物 `MS2011ReadOnlyProbe.exe` 为 `2,142,676 bytes`，SHA-256 `C21656A0DF00D940F2B418A63F7B6FB061B415CC6B4934E1370FA64707DE1AE6`；PC 完整回归 64/64、`compileall` 通过。
- EV-03 timeout 验收口径经用户批准：生产 MS2011 只证明真实驱动接受 timeout 且固定查询在阈值内完成；到期分类、清理和资源释放由 fake ODBC 测试覆盖；禁止通过不可达服务器、`WAITFOR`、制造锁或反复降低 timeout 在生产库人为制造超时。
- EV-02 已完整通过：新增固定查询的 SQL Server 2000 schema/stats 只读诊断器，精确限定 13 张表；数据库强制为 `MS2011`，stats 必须经过 `--schema-reviewed` 门禁。
- EV-02 自动验证：定向测试 24/24、PC 完整回归 88/88、`compileall` 通过；独立安全复审和产物验收均为 PASS。
- EV-02 交付包 `MS2011SchemaProbe-20260716.zip` 为 `9,480,513 bytes`，SHA-256 `8186D35638D1AE57ABF634C41164BB7925EB3734E60806A2D82132CD26B6E455`；内嵌 EXE SHA-256 `40475D45CA6250A465D432536BCB4353066F815068B198CD01483E24CFBBA9D8`，60 个条目，禁止文件命中数 0。
- 目标电脑 schema 结果为 `CAJA1\HOME / MS2011`、`requiresMappingReview=false`、13 张表和 13 个候选键列全部存在、退出码 0。
- 目标电脑 stats 结果为 `requiresCompositeKeyEvidence=false`；13 张表全部满足 `rowCount = nonNullKeyCount = distinctKeyCount`，13 个候选键均确认为当前目标数据库中的稳定单列键，退出码 0。`MS_GOODLIST` 当时为 11,195 行；该数字与 EV-03 的 11,180 来自不同取证时点，不得合并为静态快照。
- 当前只解锁 EV-04（促销候选和黑盒验证清单）。S01、G0A、G0B、EV-05 仍未通过，正式 MobilePosSync 真实 MS2011 连接、自动实时读取和生产发布继续禁用；业务代码尚未开始实施。

### 2026-07-17 MS2011 EV-04 促销候选盘点

- EV-04 已完成并通过：固定 QueryId 冻结探针在目标收银电脑的 `MS2011` 上执行一次正式盘点，`status=ok`、`ExitCode=0`、`doubleReadMatched=true`，采集时间 `2026-07-17T14:48:40-03:00`。
- 首次目标机运行因三个已确认旧式 `varchar` 商品 ID 列与严格整数合同不兼容而安全失败，返回 `INVALID_RESPONSE`/退出码 5，没有输出部分候选。修复只允许这三个固定查询位置接受规范 ASCII 正整数文本并立即归一化为整数，其余键仍保持严格类型合同。
- 修复版定向与完整回归共 121/121 通过，独立安全复审 PASS。发布 ZIP SHA-256 为 `230A94E8ED4A56C3FF10C3176CC297CEDB0D8FA104CD00D50D745327747194D1`，EXE SHA-256 为 `4DBF863D900634CF80746046AD7876DE28D803FFC79B116D901F53A97203C846`。
- 当前盘点共 41 个候选：当前启用 28、未来配置 0、历史失效 0、无法判断 13；来源哈希 `CA8150B69C6BEDEE38BED4675034B6A9AB2400C21DD42062F8C1215406C259EA`。当前候选类型为简单促销、数量百分比、数量固定总价和混合凑数固定总价。
- 13 个无法判断项完整保留：11 个简单字段不完整、1 个停用商品、1 个固定总价活动存在映射/明细不一致。盘点结果不包含名称、条码、价格、公式、连接秘密或绝对路径。
- 三份 Drive 证据保持原始 JSON/TXT：合同 `1iHW10mbpeHoBQbXU31S1M1xmER00hlB0`、盘点 `1emDQqqHrJxPKk3zTHM4nzZHY3bNHpYTE`、退出码 `1EVk1X3EUaR_AyT07v6puAKmlOpwbKKT3`；盘点文件目标机 SHA-256 为 `D2BCEEB1B5E030975D6594C83BE4C7B7C9FFE0AF91930831E51CC46106406894`。
- EV-01、EV-02、EV-03、EV-04 已满足 G0A，故 G0A 为 PASS；允许按依赖进入离线 fixture/fake ODBC 工作。S01 下一项为 EV-05。G0B 仍锁定，任何真实自动同步和生产发布继续禁用；促销公式、优先级、叠加、余数、时段和舍入仍须逐类黑盒验证。

### 2026-07-17 MS2011 EV-05 开发机就绪

- 新增固定查询权限诊断器 `diagnose_ms2011_permissions_readonly.py`、28 个定向测试、冻结构建脚本和 `修改方案/实施证据/ms2011_readonly_identity.md`。
- 诊断器只用 Windows 集成认证和 ODBC read-only access mode 执行固定 SELECT；分别双读 `MS2011` 与 `master` 权限元数据，不尝试任何写入、DDL、权限修改或“写失败”测试。
- 固定结论为 `READ_ONLY_PROVEN`、`WRITE_CAPABILITY_PRESENT`、`UNKNOWN`。BACKUP、未知权限位、无法判断的高权限角色、可执行用户过程或 master 扩展过程均不会被误判为安全。
- 独立安全复审发现并推动修复 BACKUP 位、master 扩展过程、ODBC accepted 状态、测试自引用、master 空证据与 `xp_cmdshell` 哨兵等问题；最终复审 PASS，无剩余阻断。
- 最终 EV-05 定向测试 28/28、PC 完整回归 149/149、`compileall` 均通过。
- 冻结 ZIP `MS2011PermissionProbe-20260717.zip` 为 9,483,327 bytes，SHA-256 `49ED06FA9A8650522D800B1BE295FBCF0E8B7D5D57D0A069833A225DD286D25C`；内嵌 EXE 为 2,149,684 bytes，SHA-256 `144CFF9A636DD67A2FA67DB379C13EC372152BB2ABE44BE4593415D93900BE99`。ZIP 60 个文件，禁止文件 0，重新解压后零连接合同退出码 0；Drive 文件 `188hHyLhm8RLZrremdynDaS_EToD9dhmA` 已确认保持原始 ZIP。
- 目标收银电脑 EV-05 已执行并取得明确结论，详见下一节；开发机就绪状态不再是当前阻断点。

### 2026-07-17 MS2011 EV-05 目标权限结论

- 目标机冻结 EXE SHA-256 与开发值一致；零连接合同和权限诊断均 `status=ok`、`ExitCode=0`，权限元数据双读一致。
- 实际 Windows 集成登录为 `SERVER\Administrador`，数据库用户为 `dbo`；目标数据库为 `MS2011`。
- 最终结论为 `WRITE_CAPABILITY_PRESENT`。目标身份同时属于 `sysadmin`、`securityadmin`、`serveradmin`、`setupadmin`、`processadmin`、`dbcreator`、`diskadmin`、`bulkadmin` 和 `db_owner`，并检测到 DDL、对象写、所需表写等能力。
- `odbcReadOnlyAccessModeAccepted=true` 仅说明驱动接受只读提示，不能撤销或覆盖服务器端写权限。
- 来源哈希为 `2E0908652939AD7D6E16E1A12B2C79AC221D73D1D70E21398A471284FC3FE485`。原始证据 ZIP 为 `https://drive.google.com/file/d/15Y4YIQjlG5L_eQYF7PAYvu-DWWPfBZz-/view`，1,955 bytes，SHA-256 `9F73112D3410CEAEB83546C4EF89A28AABDDDFED5D380E8598786FC6D29CB6BA`，内含四个原始 JSON/TXT 文件。
- 本次只执行固定 SELECT 权限查询，没有写数据库或修改权限。EV-05 取证任务完成，但未达到 PASS；G0B、真实自动读取、IV-02 和生产发布继续锁定。
- S01 证据收集阶段结束，允许进入不连接真实 MS2011 的 S02 离线合同工作。任何最小权限身份必须由外部管理员另行配置并人工复核，工具不得自动改权。

### 2026-07-17 MS2011 S02 离线合同完成

- CT-01 新增 Python `DecimalValue` 与 Java `DecimalValue`，分别使用 `decimal.Decimal` 和 `BigDecimal`；金额/数量以规范 TEXT 表示，禁止负数、指数、NaN、Infinity 和静默舍入，冻结 `1.25 × 2099.99 = 2624.9875`。
- CT-02/CT-03 冻结 `sourceProductKey`、snapshot/candidate/mapping/tier/schedule/group ID、下载路径、软硬资源上限和 schemaVersion 2 manifest；manifest 不包含 Token、SQL 服务器、驱动、Windows 用户或商品明细。
- CT-04 冻结 12 张 SQLite v2 表、外键/索引和跨端 `NormalizedPromotionRule` DTO；SQLite 十进制列使用 TEXT，`promotion_raw_rows` 不能被 Android 定价代码使用，fixture 不包含可执行促销公式。
- CT-05 新增只接受冻结 `QueryId` 的 `ReadOnlyMs2011Session` 和只能由 `AppPaths` 生成的 `ToolOwnedPath`；路径写入/替换/删除测试拒绝普通 Path、越界 ID、目录和受保护 active/pending/last-good/lock 状态。
- 两端共六份 fixture 逐字一致。PC S02 定向测试 14/14；使用项目 PySide6 环境的完整回归 163/163、`compileall` 通过。
- Android core 全量 Java 17 编译通过；`CoreSmokeTest`、购物车合并、阿根廷账本日期三组既有 smoke 与 Decimal、v2 manifest/ID、NormalizedPromotionRule 三组新合同测试均通过。
- 本阶段只使用内存 SQLite、临时 AppData 和 fake query runner，没有连接真实 MS2011、没有生成生产快照、没有启用促销公式。S02 为 PASS，只解锁 S03；G0B、真实自动读取、IV-02 和生产发布继续锁定。

### 2026-07-17 MS2011 S03 电脑端安全骨架完成

- PB-00 扩展配置但保持旧配置默认 `legacy_sqlite`；数据库名编译固定为 `MS2011`，SQL 用户名/密码字段、非法数据源和越界资源参数全部拒绝。v2 路径只由 `AppPaths` 在 LocalAppData 下生成，外部源路径使用不同的 `SourceReadPath` 类型。
- PB-01 新增 EV-02 十三表/候选键常量、不可变 QueryId→SELECT 目录和唯一 pyodbc 适配器。外部输入不能控制数据库、表、列、排序或 SQL；ODBC read-only access mode 不可用/被拒绝时明确失败，游标和连接在所有路径关闭。
- PB-02 新增独立单线程协调器：默认 15 秒检测、10 秒安静窗口；同进程最多一个同步任务，人工请求不无限排队也不绕过安全门。sysprocesses 探测无权限时标记降级并要求短超时/双读；连续超时、双读不一致或异常会熔断。
- 熔断期间自动完整读取保持停止，只做低成本探测；冷却到期后仍要求人工重试。取消只允许等待中或发布前任务，进入发布门后拒绝取消。
- S03 定向测试 23/23；PC 项目 PySide6 环境完整回归 179/179，`compileall` 通过。静态扫描确认只有 `ms2011_connection.py` 导入 pyodbc，固定查询目录没有写数据库语句。
- 本阶段没有连接真实 SQL、没有生成快照、没有修改电脑 UI、HTTP 或 v1 备份流程。S03 为 PASS，只解锁 S04 的 fixture/fake ODBC 确定性读取、变化探测和候选规范化；G0B 继续锁定。

### 2026-07-17 MS2011 S04 离线确定性读取与候选规范化完成

- PB-03 新增商品、分类、单位和 EV-04 促销证据的固定 QueryId。完整读取按 EV-02 稳定键排序，Decimal/datetime 保持源类型；两次读取分别计算规范化 SHA-256，任一查询失败、主键重复/乱序、关键关系缺失或哈希不同均整轮失败。
- 每张表的运行证据只记录 QueryId、读取轮次、行数和耗时，不记录商品、条码、价格或促销业务明细。
- PB-04 快速变化摘要只读取商品 COUNT/MAX(GUpdateTime)/MAX(GID) 和促销小表摘要，并明确 `provesComplete=false`。默认每 15 分钟仍要求完整指纹；是否允许全读必须服从繁忙、timeout 和熔断门禁。性能历史记录实际 p50、p95 和最大耗时。
- PB-05 商品身份固定为 `ms2011:<GID>`，条码改变不换身份；空单位保持 null，stop flag 原值保留且非零不可售；Decimal 使用规范文本。简单价格/门槛只保留为 evidence-only，商品规范化器不生成 VERIFIED 促销。
- 条码空/重复、分类/单位缺失、重复商品键和非法 Decimal 使用不可变 severity matrix；是否拒绝整份结果由 matrix 决定。
- PB-05P 提取简单、数量百分比、数量固定总价和混合凑数固定总价四类候选，但只输出 `UNVERIFIED` 或明确过期的 `INACTIVE`。每个原始行、映射、日期、星期、时段和组原值都有稳定 source key；重复商品映射不会折叠；规则集合固定为空。
- S04 定向测试 25/25；PC 项目 PySide6 环境完整回归 204/204，`compileall` 通过。没有连接真实 SQL、没有生成 SQLite/manifest/快照，也没有修改 UI、HTTP 或 v1。
- S04 为 PASS，只解锁 S05 的 fake ODBC SQLite v2、不可变发布、HTTP 和电脑 UI；G0B、真实自动读取和生产发布继续锁定。

### 2026-07-17 MS2011 S05 电脑端 v2 离线发布链路完成

- PB-06 至 PB-09、PF-01 和 PF-02 已在 fixture/fake ODBC 边界内完成：SQLite v2、自检、不可变发布、固定版本 HTTP v2、fake ODBC 全链路和非阻塞电脑 UI 已接通；旧 v1 端点保持兼容。
- PC 完整回归 225/225、`compileall` 通过；没有连接真实 SQL，复杂促销规则数保持为 0。
- S05 为 PASS，只解锁 S06；G0B、正式应用真实 SQL 连接、自动读取和生产发布继续锁定。

### 2026-07-17 MS2011 S06 手机端精确金额迁移完成

- Money/Discount、核心定价、找零、销售、日报、商品 JSON、CSV、鸣盛导入及金额 UI 已迁移为规范 BigDecimal/原币十进制；旧 long 金额桥和业务 double 路径已删除。
- 11 组 Java 合同/烟测和完整 Gradle debug 构建通过。阶段 APK 为 1,033,434 bytes，SHA-256 `2D45E3CA7D17104F382E2A19E9BD80CA65A63C5CB62AD936C56F92CA540A625E`，未覆盖正式发布副本。
- S06 为 PASS，只解锁 S07；G0B 不变。

### 2026-07-18 MS2011 S07 MB-02D 完成

- MB-02A 至 MB-02D 已完成：Quantity 使用受 CT-01 约束的 BigDecimal，覆盖购物车精确合并、定价、SaleLine/checkout 快照、canonical CSV 和结账数量 UI；非整数数量继续排除自动促销。
- Checkout 支持商品行 CT-01 小数数量编辑；商品搜索选中后以默认 1 打开数量对话框，扫码、条码和手工价格商品显式使用 `Quantity.one()`。非法输入不调用购物车 callback、不关闭对话框；合法输入恰好提交一次。
- 已删除 9 个 Quantity/int 临时交易数量兼容 API。生产 transaction bridge 扫描和 UI narrowing 扫描均为 `0 matches`。`Money.times(int quantity)` 无生产调用且不是 Quantity 委托桥，按用户批准范围保持不变。
- 主协调器最终独立执行 core `13/13`、app `6/6`；Gradle core/app Java 编译 `BUILD SUCCESSFUL in 8s`。核心、UI、范围后置复核均 PASS，无 High/Medium finding。
- S07 尚未 PASS：MF-01 显示一致性扫描和 Quantity 后完整 Gradle APK 构建仍未完成，真实 Android 软键盘/对话框交互也未验证。下一恢复点为 MF-01，不得跳到 S08。G0B、真实 MS2011 自动读取和生产发布继续锁定。

### 2026-07-18 MS2011 S07 MF-01 完成与阶段结论

- MF-01 已统一手机端金额/数量展示与十进制输入词法：`MoneyText`/`QuantityText` 均 null fail-fast 并输出 canonical text；金额、百分比与数量输入共用点/逗号归一化；Checkout 初始零金额和 Sales 交易数量均显式经过统一 formatter。
- 新增的 UI 架构测试最终采用 Android 可编译 wrapper，在系统临时目录编译/执行 JDK AST/TypeMirror runner。它按真实类型覆盖 Money/Quantity getter、参数、局部变量、嵌套 receiver、字符串转换 sink、静态通配导入和方法引用；compiler、类型归因、runner 或临时目录清理失败均失败关闭。最终扫描 24 个 UI Java、24 个对抗变体，临时残留 0。
- 主协调器最终独立执行 core `13/13`、app `7/7`；静态扫描的 primitive parse、formatter 外 canonical、raw sale quantity append 和硬编码数字货币均为 0。parser、UI 架构、独立对抗性和范围/hash 复核均 PASS，无 High/Medium/Low finding。
- 完整 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 12s`，34 actionable tasks（5 executed、29 up-to-date）。实施目录 APK 为 `android-emergency-pos/app/build/outputs/apk/debug/app-debug.apk`，1,010,742 bytes，2026-07-18 10:28:59 ART，SHA-256 `C4A1018D3033C6A4BA8ED410DAB98D1CCC3F3FEF499B5644AC45EE3C039E26AC`；独立 artifact validator 确认 ZIP 含 manifest、dex、resources 和 res 条目。没有复制到 `dist` 或 `mobile_pos_publish`，不构成发布验收。
- 既有百分比折扣在某些 Money/percentage 组合下可能产生超过四位小数的算术闭包风险；该行为早于 MF-01，不能靠猜测输入精度或舍入安全修复，保留为后续明确产品合同下的风险。真实 Android 软键盘、对话框生命周期、安装和人工页面遍历仍未验证。
- **S07 PASS**，只解锁按依赖执行 S08/MB-03；按用户指令当前停在 S08 之前。G0B、真实 MS2011 自动读取、IV-02 和生产发布继续锁定。

### 2026-07-18 S08 前置 CT-02R/CT-03R 跨端合同纠偏完成

- 用户新授权解除 S07 后人工停止点；S05/S07 前置均满足。S08/MB-03 启动只读同步随后发现两项冻结合同差异，因此先暂停 MB-03 并经用户批准执行限定纠偏。
- snapshotId UTC 基本时间现统一严格按真实日历解析。PC 原有严格校验保持不变；Android `V2Contract` 改用 `uuuuMMdd'T'HHmmss'Z'` 与 `ResolverStyle.STRICT`，两端均拒绝 2 月 30 日、非闰年 2 月 29 日、24 时、60 分和 60 秒。
- `categoryCount`、`unitCount` 统一冻结为 `0..2147483647` JSON integer；`minimumAppVersion` 统一冻结为 `1..2147483647`。PC 明确拒绝超界；Android 构造器先以 long 接收、校验后再转换为 int，不截断、不夹紧、不经浮点。
- 三份共享 fixture 逐字一致：`v2_id_cases.json` SHA-256 `D0CD76A68832FDB30B5A29419C1C57B34ABA27E3DAD0D94FC3E91A71F1409197`；`v2_manifest.json` `C9ED56BDB3BC7AE3A618EF523C5DF14C1B1F3A33644EE5E1D2999E31CA61182C`；`v2_manifest_invalid_cases.json` `A196B11C28E87FD93A05796F191891565B1F7F9A2A3D02BFF10F7B6D3BD0A17C`。
- 主协调器使用项目 Python 3.14.5 / PySide6 6.11.1 / pyodbc 5.3.0 执行完整 PC 回归 `226/226 PASS`，`compileall` 通过。系统 Python 首轮因缺少 PySide6/pyodbc 产生两个收集错误，切换到项目环境后完全通过，不是代码断言失败。
- Android Gradle `:core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac` 为 `BUILD SUCCESSFUL in 9s`，21 actionable tasks（4 executed、17 up-to-date）；主代理随后逐一执行 core main-based `13/13 PASS`、app main-based `7/7 PASS`，AST 扫描仍为 24 个 UI Java 与 24 个 synthetic 变体。
- 独立只读验证 PASS，无 High/Medium finding。`ComputerSyncClient.java` 和 `ComputerSyncService.java` SHA-256 仍分别为 `2778BC0E375FFC393E4B8D6F30B03ADD061DE00288FB31FBF0DCFAC1A598CF8B`、`33B16B5C44EB5625FFD7055EF39EF0B50BDF2CCEA79204079815499AFE7D5EEA`，证明 MB-03 尚未开始。
- CT-02R/CT-03R PASS，只解除 MB-03 的合同阻塞；当前下一任务为 S08/MB-03，MB-04 仍未解锁。G0B 保持锁定，目标身份仍为 `WRITE_CAPABILITY_PRESENT`；未连接真实 MS2011、LAN 或设备，未复制/发布 APK，陈旧 `dist`/发布副本不构成发布验收。

### 2026-07-18 S08 MB-03 v2 manifest 安全校验与流式暂存完成

- 新增 `ComputerSyncManifestV2` 并扩展 Android client/service：严格 15 字段与 JSON 类型，唯一委托 CT-02/CT-03；manifest 原始 UTF-8 上限 256 KiB，HTTP 声明长度必须为正且与实际 body 完全一致。
- v2 manifest/快照仅接受 HTTP 200，使用 Bearer，不含 query Token，禁用重定向和缓存。下载严格核对 Content-Length、X-File-Sha256、X-Snapshot-Id、实际字节与实际 SHA-256。
- 下载前按 CT-02 保守预留 incoming + active + pending + rollback；生产 API 不接受外部 File/目录，只从 Android `Context` 派生 app cache 固定 tmp 并内部创建随机 `snapshot-v2-*.part`。流与删除 helper 私有，失败、截断、超长、损坏、线程中断、显式取消及 service 二次 hash 取消均清理半成品。
- 测试覆盖严格字段/类型、无效 snapshot/path/size/count/version/hash、int32 越界、manifest 长度不一致、HTTP 201/206/302、错误 headers、截断、同长度损坏、运行中取消与 hash 取消。v1 `ComputerSyncManifest` SHA-256 保持 `5A71E62D873AEB5C6E9BDB8E7BB34A1765E9E6969B0D7B5F24211478E5676F3F`。
- 主协调器最终验证：Gradle core/app 编译 `BUILD SUCCESSFUL in 9s`，21 tasks（3 executed、18 up-to-date）；core `13/13 PASS`；app `8/8 PASS`，AST 扫描 24 个 UI Java/24 个 synthetic 变体；`assembleDebug` `BUILD SUCCESSFUL in 9s`，34 tasks（3 executed、31 up-to-date）。
- 最终文件 SHA-256：`ComputerSyncManifestV2.java` `AD38159CC7E826FE3B47EE3F414DA8A0B65ECC72F60F354867B5A20817BDABB9`；`ComputerSyncClient.java` `EC8FE89EA1990372B71BDB26FA3748B29BA8C984345E272CF47DF1A634D11F0B`；`ComputerSyncService.java` `94C7154BFFA45F9570391E30AE328A197B4C80965896A40AA93318F4A03650A0`；`ComputerSyncManifestV2SmokeTest.java` `6EF043FDF394A24815C69F08331D47463C40D9364C5D367E53AD3B6484EFACA1`。
- 实施 APK：`android-emergency-pos/app/build/outputs/apk/debug/app-debug.apk`，1,033,674 bytes，LastWriteTime `2026-07-18 13:16:18 -03:00`，SHA-256 `445F51ED9E75C9E99FB5172177DA247465A1BBDF11260965FB60163EF252044C`；ZIP 20 entries 且含 manifest/dex/resources/res。未复制到 `dist` 或 `mobile_pos_publish`，两处旧 APK 仍为 SHA-256 `48D488084C4160B999090647EA3130619040CB151A899E543495E604AF52E7C2`。
- 最终安全审查与独立验证均 PASS，High 0 / Medium 0。遗留 Low：真实 Android runtime `org.json` 解析、异常 cache/symlink 故障注入、真实设备页面销毁/安装/LAN 仍未验证。
- **MB-03 PASS**，只解锁 S08/MB-04；S08 尚未完成。G0B 继续锁定，目标身份仍为 `WRITE_CAPABILITY_PRESENT`；真实 MS2011 自动读取、手机直连 SQL、IV-02、数据库写入和生产发布继续禁止。

### 2026-07-18 S08 MB-04 不可变快照与崩溃恢复完成

- 新增 `V2SnapshotStore`、`V2SnapshotValidator`、`V2SnapshotReader`、`V2SnapshotStateStore` 和对应 smoke；对象与 CT-03 manifest 成对保存在 app-owned v2 目录，state 原子写并记录 active/pending/lastGood、摘要和连续失败计数。
- 启动恢复严格执行 active → lastGood → none；pending 先于最终文件移动持久化、从不自动启用。路径在解析前拒绝 symlink/遍历/非普通文件，pending 清理不能删除 active/lastGood；对抗测试实际覆盖 linked pending object/manifest 后目标字节不变。
- SQLite 只以 `OPEN_READONLY` 打开。验证器精确比较 CT-04 的 12 张表、有序列类型/nullability/default/PK、全部外键及 ON DELETE、显式索引名称/unique/列顺序、CHECK/UNIQUE，并核对 `schemaVersion`、`snapshotId`、lowercase SHA-256 `sourceHash`、integrity/foreign-key、对象 hash/size 和 manifest 六项计数；没有任意 SQL 或写能力。
- 最终五文件 SHA-256：Store `670274846DCE77206F92AF86FEFC2D96ED4ACF685431B1EC80B4E470AB5A1403`；Validator `70A7092B343AEC79AF900F2277A958CC7268046AD5FB69D3566D8C8013F3A7DB`；Reader `849B4532B351B4DF5D8815206CF532C825820072F60BC163039D110087432C6E`；StateStore `3F8A1427FFE95716278502C24CF7138C5FAA78E7CA70595C9486A118DCEA6FB8`；Smoke `D928F9E31F09C14AA68B7AFDEAEEAF7A11A9B1235DDDB90A57D91E83A54214BA`。
- 主协调器最终 Gradle `--rerun-tasks` 编译和 `assembleDebug`：`BUILD SUCCESSFUL in 12s`，38/38 tasks executed。core `13/13 PASS`、app `9/9 PASS`，合计 `22/22`；MB-04 `65 assertions`，AST 24/24，静态禁止能力 0。
- APK：`android-emergency-pos/app/build/outputs/apk/debug/app-debug.apk`，1,030,586 bytes，2026-07-18 15:16:24 -03:00，SHA-256 `44A7AF106FF65102593A1B9CB51825D0D25FA905E1079A24E646292FFBCA0D70`；ZIP 20 entries、14 dex、manifest/resources 完整，v2 签名通过。未复制到 `dist` 或 `mobile_pos_publish`。
- 独立安全审查和独立功能/制品验证均 PASS，High/Medium 0。Low 设备证据缺口：真实 Android SQLite/PRAGMA、symlink/原子移动/fsync、进程杀死恢复、APK 安装和页面遍历未执行。
- **MB-04 PASS，S08 PASS**。依赖上 S09/MB-05 已就绪，但用户要求完成后暂停，因此当前停在 MB-05 开始前。G0B 继续锁定，目标身份仍为 `WRITE_CAPABILITY_PRESENT`；真实 MS2011 自动读取、手机直连 SQL、IV-02、数据库写入和生产发布继续禁止。

### 2026-07-18 S09/L2 商品身份与 catalog 合并完成

- MB-05 已完成来源迁移：`Product` 区分 `LOCAL/LEGACY_IMPORT/MS2011_SYNC`，同步键严格为 `ms2011:<GID>`；旧 JSON 缺省来源保持 `LEGACY_IMPORT`，无法可靠判定的商品进入 `product_migration_report.json`，纯后端选择只接受 `LOCAL/LEGACY_IMPORT`。同步商品拒绝手机编辑/删除。
- `products.json`、metadata 和迁移报告均使用同目录随机临时文件、flush/fsync 和原子替换；失败路径清理临时文件且不破坏旧内容，不宣称跨文件事务原子性。
- MB-06 只从重新验证的 immutable active/lastGood v2 对读取商品，SQLite reader 使用 `OPEN_READONLY`。GID/source key、金额、stop、snapshotId、重复身份/条码先在内存候选中验证，成功后才替换 repository。
- 同步商品扫码优先，LOCAL 同条码商品保留在冲突列表；停用同步商品普通搜索隐藏、显式 lookup 返回 `STOPPED`，旧结账路径不可加入。同步 category/unit 空值保持源事实，`simple_price_decimal/simple_threshold_decimal` 不进入旧自动促销。
- 阶段复核发现并关闭同步商品本地残留风险：手机编辑持久化前只保留 `LOCAL/LEGACY_IMPORT`，`MS2011_SYNC` 不写入 `products.json`。独立验证还发现 immutable 测试异常类型误捕获，最终用专用断言关闭，未放宽生产异常合同。
- 最终主协调器 Gradle `--rerun-tasks` 编译和 `assembleDebug` 为 38/38 tasks executed、`BUILD SUCCESSFUL in 13s`；core 15/15、app 11/11，合计 26/26 PASS、0 fail、0 skip。Catalog 33、Origin 21、Migration 13、v2 reader 23、v2 store 65 assertions，AST 扫描 24 个 UI Java/24 个 synthetic 变体。
- 阶段 APK：`android-emergency-pos/app/build/outputs/apk/debug/app-debug.apk`，1,059,398 bytes，2026-07-18 21:54:54 -03:00，SHA-256 `15E299DC44F9FA7475E5FFE678533AD1F8255461F728E22176FDB06477178F36`；ZIP 20 entries、14 dex、manifest/resources 完整，APK v2 签名通过。未复制到 `dist` 或 `mobile_pos_publish`，旧副本不是发布证据。
- 独立验证对 MB-05、MB-06、S09 均为 PASS，High/Medium 0。Low 设备证据缺口：真实 Android SQLite/PRAGMA、fsync/原子移动、进程杀死恢复、安装和页面遍历仍未执行。
- **S09/L2 PASS**。当前按用户指令停止在 S10 开始前，不启动 MB-07/CB/MF。G0B 继续锁定，身份为 `WRITE_CAPABILITY_PRESENT`；真实 MS2011、自动读取、手机直连 SQL、数据库写入和生产发布继续禁止。

### 2026-07-19 MS2011 离线实现源码发布同步

- 当前活动阶段：`L3/S10`；实现阶段保持暂停，G0B 仍锁定，目标身份为 `WRITE_CAPABILITY_PRESENT`。
- 已核对并准备同步电脑端 MS2011 v2 合同、固定 QueryId 只读适配、变化探测、schema/商品/促销候选规范化、SQLite v2 发布、HTTP v2、权限诊断、同步协调器及 fixtures/tests。
- 已核对并准备同步 Android v2 快照、精确金额/数量、active/pending 生命周期、订单快照边界及 MF-05 未验收现场修改。
- 本轮实际电脑端验证：完整回归 `226/226` 通过；`python -m compileall -q src tests` 通过。
- 本轮不构建 APK/EXE/ZIP，不连接真实 MS2011/SQL，不生成生产快照，不做真实 LAN/真机验收；只发布源码、测试、方案、状态文档和 README。
