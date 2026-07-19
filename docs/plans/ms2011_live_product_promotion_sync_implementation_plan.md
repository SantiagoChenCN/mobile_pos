# MS2011 实时商品与已应用复杂促销同步——细化实施计划

更新时间：2026-07-19  
设计边界：修改方案/ms2011_live_product_promotion_sync_plan.md  
主实施目录：pc-sync-tool、android-emergency-pos  
计划用途：供能力较弱的 Subagent 按 S01 至 S17 小阶段中的单一任务编号执行  
当前状态：S10/L3 进行中；MB-07、CB-01、CB-02、CB-03 与主机侧离线 G4 已 PASS；MF-05 有两文件部分修改但尚未编译/验收，MF-02 因 stale 阈值未冻结而待产品决定；用户要求暂停；真实 MS2011 启用仍由 G0B 锁定

文档优先级：产品边界和用户行为以 `ms2011_live_product_promotion_sync_plan.md` 为准；本文件冻结更具体的 schema、路径、门禁、任务依赖和验收合同。两者技术细节冲突时，以本文件 2026-07-14 修订内容为准，但 Agent 不得借此扩大产品范围。

### 0.1 固定技术栈

- 电脑端：Python；现有开发环境为 Python 3.14.5 64 位、PySide6 6.11.1。最终 Python/EXE 位数必须以 EV-03 的 SQL Server 2000 ODBC 兼容证据为准，不能仅按开发机位数决定。
- 电脑数据库访问：pyodbc + Windows 集成身份验证；只允许唯一适配模块接触 pyodbc。快照使用 Python 标准库 sqlite3，金额使用 decimal.Decimal。
- 电脑 UI/并发：现有 PySide6；Qt 主线程只展示，SQL、hash、SQLite、网络任务放 worker/单线程协调器。禁止引入第二套 GUI 或异步框架。
- 电脑发布：现有 HTTP 服务、Bearer Token、SHA-256、不可变 SQLite v2 对象、PyInstaller onedir；优先复用现有 manifest、http_server、event_log、network 和 AppData 路径模块。
- 手机端：Java 17、Android compile/target SDK 35、min SDK 29；金额和数量使用 BigDecimal，快照使用 Android SQLiteDatabase，JSON 使用现有轻量实现，不引入 Room、Retrofit、RxJava 或新的依赖注入框架。
- 手机并发：现有 AppServices + 单线程 ScheduledExecutorService；Activity/Screen 只发触发事件和展示状态。
- 时间：存储 UTC Instant，业务时区固定 America/Argentina/Buenos_Aires；鸣盛源 datetime 先保留本地原始语义，只有已验证合同允许转换。

## 1. 本计划的最终目标

在不修改、不停止、不维护、不妨碍鸣盛收银系统和 MS2011 数据库的前提下，实现：

1. 电脑端只读提取商品、分类、单位，以及鸣盛数据库中已经配置并可能参与实际收银定价的复杂促销。
2. 电脑端生成独立、可验证、可回滚的版本化 SQLite v2 快照。
3. 手机端下载并验证快照，只在订单边界切换商品和促销版本。
4. 金额和数量全链路使用精确十进制，2099.99 必须保持为 2099.99，不转换成 209999，也不使用二进制浮点参与结账。
5. 每种复杂促销必须先取得鸣盛黑盒证据，再单独启用手机自动计算。
6. 未验证、异常或未知促销不能静默按普通价结账；只阻止确实受影响的商品，允许收银员明确手动定价并留下审计信息。
7. 旧 AGT_MAIN、CSV 导入、手机本地商品、现有销售和日账功能保持可用。

“实时”指低干扰准实时，不指数据库事务提交后的即时推送。目标延迟是数据库空闲、电脑和手机网络正常、手机位于前台时，新数据通常在 60 秒内可供新购物车使用。该目标必须以实测分位数报告，不得写成绝对保证。

## 2. 已应用复杂促销的范围定义

本轮不把十张促销表的全部历史行无条件发送到手机。

候选促销必须满足：

1. 存在可识别的促销主记录。
2. 存在能够追溯到商品 GID 或条码的关联记录。
3. 日期、星期、时段或启用字段能够被保留和解释。
4. 根据已取得的结构证据，该记录可能被鸣盛收银程序参与定价。

候选促销进入以下状态之一：

- VERIFIED：黑盒证据充分，手机可以自动计算。
- UNVERIFIED：数据结构完整，但业务公式、优先级、余数或舍入尚未确认。
- INVALID：关联缺失、参数非法或结构矛盾。
- UNKNOWN_TYPE：出现当前合同无法表达的新类型。
- INACTIVE：有历史证据，但当前不会参与定价。

只有 VERIFIED 可以进入自动促销计算。UNVERIFIED、INVALID、UNKNOWN_TYPE 必须保留诊断证据，但不得猜测公式。INACTIVE 不阻止当前结账。

无法判断影响商品范围的异常促销不得粗暴阻止全部商品；电脑端应拒绝发布该促销的自动计算模型，并把问题升级为快照警告。若无法证明其不会影响广泛商品，则该轮促销快照不得晋升为可自动定价版本。

## 3. 不可突破的安全边界

所有任务均必须遵守：

- SQL 只能通过固定 QueryId 白名单执行 SELECT。
- 禁止任何 INSERT、UPDATE、DELETE、MERGE、EXEC、CREATE、ALTER、DROP、TRUNCATE、DBCC、BACKUP、RESTORE、ATTACH、DETACH、KILL。
- 禁止复制、移动、重命名或删除运行中的 MDF/LDF。
- 禁止停止、暂停或重启 SQL Server 与鸣盛程序。
- 禁止在 EPSA、Espsa 或鸣盛安装目录写入任何文件。
- 禁止程序自动创建或修改 Windows 防火墙规则。
- 禁止手机端直连 SQL Server 或向电脑发送写请求。
- 禁止通过修改生产促销、修改电脑时间、修改 SQL 时间或完成真实付款来制造黑盒测试条件。
- 黑盒验证优先使用已经存在的促销案例；需要造数时只能在用户明确批准的非生产副本或受控环境中进行。
- 数据库繁忙、读取超时、结构漂移、双读不一致时立即放弃本轮，保留旧快照。

READ UNCOMMITTED / NOLOCK 只能降低阻塞风险，不能提供事务级一致性。双读和安静窗口是低干扰一致性校验，不得在文档或 UI 中宣称为数据库原子快照。

### 3.1 受保护的鸣盛域

以下对象全部属于受保护的鸣盛域。电脑同步工具在任何程序控制路径中都不得写入、替换、移动、重命名、截断或删除：

- MS2011 及同一 SQL Server 实例中的数据库、表、记录、索引、权限、登录、角色和配置。
- MS2011.MDF、MS2011.LDF、其他 MDF/LDF、SQL 备份和数据库日志。
- EPSA、Espsa、SQL2000 和鸣盛安装目录中的程序、配置、授权、模板、报表、打印文件、脚本和日志。
- 鸣盛使用的注册表项、Windows 服务、计划任务、进程、共享目录和外部备份。
- 用户选择的 AGT_MAIN、导出 TSV、CSV 或其他源文件。源文件即使位于工具目录附近，也永远不是工具自有文件。

受保护域只允许固定只读 SELECT、只读打开和顺序读取。读取失败时只能报告并保留旧快照，不能尝试修复源数据。

### 3.2 工具自有可写域

工具唯一可写域是：

- %APPDATA%\MobilePosSync
- %LOCALAPPDATA%\MobilePosSync

工具允许在这两个根目录内创建、原子替换和删除自己生成的配置、事件日志、临时文件、不可变快照、manifest 和历史版本。

删除工具自有文件时仍必须满足：

1. 目标来自内部固定文件名或已验证 snapshotId，不接受任意用户路径。
2. 规范化和解析后的绝对路径仍位于工具自有根目录。
3. 目标不是符号链接、目录联接、重解析点或通过这些对象逃逸后的路径。
4. 不使用对任意路径的递归删除。
5. 历史清理只逐个删除 allowlist 中的普通文件。
6. 当前 active、pending、last good 或正在下载的对象不得删除。

### 3.3 零写入能力门禁

生产 MS2011 实时模式必须同时具备以下防线：

1. 使用由管理员在工具之外预先配置的只读 SQL 身份。
2. 工具不得创建登录、用户、角色，不得 GRANT、DENY、REVOKE 或改变任何数据库安全设置。
3. 上线前必须有权限证据证明该身份不是 sysadmin、db_owner、db_datawriter、db_ddladmin，也没有显式写权限。
4. 权限证据缺失、矛盾或无法解释时，实时模式必须 fail closed，不能用“应用层保证”替代。
5. ODBC 设置只读访问属性；旧驱动若忽略该属性，仍由服务器只读身份和 QueryId 门禁兜底。
6. 生产代码只能通过 ReadOnlyMs2011Session 调用固定 QueryId，不能取得原始 connection、cursor 或通用 execute。
7. pc-sync-tool/src 中只有数据库适配模块允许导入 pyodbc；架构测试必须阻止其他生产模块直接导入。
8. 文件写入和删除只能接收 ToolOwnedPath，不能接收普通来源路径后自行判断。

如果无法提供服务器级只读身份，则项目可以继续开发和离线测试，但不得在真实 MS2011 上启用实时模式。

本版本唯一支持的生产认证模型是 Windows 集成身份验证：启动 MobilePosSync 的 Windows 用户或其 Windows 组，必须已经由管理员在工具之外映射为 MS2011 只读身份。工具不保存 SQL 登录名或密码，不以其他账户模拟登录，也不创建或修改任何 SQL/Windows 身份。若当前收银 Windows 账户在 SQL Server 中属于 sysadmin、db_owner、db_datawriter、db_ddladmin 或具有显式写权限，则生产实时模式保持禁用；项目交付物只能提供权限诊断和部署说明，不能代替管理员修改权限。

部署证据必须写明：Windows 运行账户、SQL 解析后的登录身份、Windows 组映射、数据库角色、显式权限、诊断时间和诊断脚本 hash。任何一项无法确认时状态为 UNKNOWN。

## 4. 旧计划审查后必须修复的问题

本计划替代 2026-07-13 旧实施计划，原因如下：

1. 旧计划在 ODBC、位数和真实 syscolumns 证据取得前冻结 v2 合同，顺序错误。
2. 分析脚本中的部分分类字段是描述性别名，不能直接用于实时 SELECT。
3. double 和 SQLite REAL 会造成金额二进制误差，不适合结账。
4. latest.db 与 manifest.json 两次替换存在进程崩溃窗口。
5. 现有 publish_lock 仅为进程内锁，无法防止两个 EXE 同时发布。
6. 旧商品迁移规则可能把整批历史导入商品误转为 LOCAL。
7. Product 合并要求使用 GID，但旧计划没有冻结 sourceProductKey。
8. snapshotId 进入文件路径，却没有格式白名单、大小上限和磁盘配额。
9. 手机启动恢复、active/pending 崩溃恢复和旧快照清理顺序不完整。
10. 旧 SQLite 促销表无法证明能无损表达重复映射、多组组合和多时段。
11. 旧计划把当前数据量 11168 等写成固定验收值，数据更新后会产生假失败。
12. 手动价格绕过异常促销后没有销售审计字段。

## 5. 总体阶段与硬门禁

执行顺序：

    G0A 开发证据门禁
      → G1 精确十进制和跨端合同冻结
      → G2 电脑端只读商品快照
      → G3 手机端 v2 商品快照
      → G4 订单边界和崩溃恢复
      → G5 促销安全门
      → G6 每种促销垂直切片
      → G7 两端 UI
      → G0B 生产只读身份启用门禁
      → G8 真实电脑安全验收
      → G9 打包发布

任何门禁未通过，下游任务不得开始。允许并行的任务必须不修改同一所有权文件。

### 5.1 任务依赖速查

    EV-01 → EV-03
    EV-03 → EV-02、EV-05
    EV-02 → EV-04
    EV-02 + EV-03 → CT-01、CT-04、CT-05、PB-00、PB-01
    EV-04 → PB-05P、全部 PV-X
    EV-05 → 真实 MS2011 启用、IV-02、G9 生产连接验收
    CT-01..CT-05 → G2、G3
    PB-00 → PB-02、PB-07、PB-09、PF
    PB-01 + PB-02 → PB-03
    PB-03 + PB-04 → PB-05、PB-05P
    PB-05 + PB-05P → PB-06
    PB-06 → PB-07 → PB-08 → PB-09
    MB-01A → MB-01B → MB-01C → MB-01D → MB-01E
    MB-01E → MB-02A → MB-02B → MB-02C → MB-02D
    MB-01E → MB-05
    MB-03 + MB-04 + MB-05 → MB-06
    MB-06 + PB-09 → MB-07
    MB-02D + MB-06 → CB-01
    MB-07 + CB-01 → CB-02 → CB-03
    CB-03 → PM-01..PM-04
    PV-X → PN-X 和 PE-X
    全部阶段上线的 PN-X → PN-REGISTRY → PN-INTEGRATION
    PM-04 → PE-ORDER
    全部阶段上线的 PE-X + PE-ORDER → PE-REGISTRY
    PN-INTEGRATION + PE-REGISTRY → 全部阶段上线的 PI-X
    PB-09 → PF-01 → PF-02
    MB-01E + MB-02D → MF-01
    MF-01 + MB-07 + CB-03 → MF-02 → MF-03
    全部阶段上线的 PI-X + MF-03 → MF-04
    MB-07 → MF-05
    PF-02 + MF-04 + MF-05 + EV-05 → G8

“阶段上线”只指本次已经取得黑盒证据并被用户选择启用的促销类型，不要求未验证类型伪装完成；“项目最终完成”则必须满足 EV-04 已穷举当前启用促销类型，且清单内每一种类型均通过对应 PI-X。仍有当前启用类型为 UNVERIFIED、INVALID 或 UNKNOWN_TYPE 时，只能标记“部分完成”，不能使用“全部支持”或“项目完成”。

### 5.2 小工作量实施阶段

以下 S01 至 S17 保留为产品能力和宏观门禁顺序，G0A 至 G9 继续作为硬门禁。每个阶段可以包含多个已有任务编号，但一个实现 Agent 仍然只能领取其中一个任务编号；不得把整阶段一次性交给单个 Agent。上一阶段的产品、依赖和安全门禁未通过时，下一阶段不得开始。自 2026-07-18 起，同一阶段或下述交付批次内已由依赖表解锁的任务，可以在形成可恢复的任务证据后继续，不再要求为每个小任务重复全量构建、独立复核和全部全局文档同步。表格中的“无实施”表示该端禁止修改生产代码；括号中的验证工作不改变这一边界。

每阶段交付记录固定包含：阶段编号、四端实施状态、完成的任务编号、修改文件、测试命令和数量、人工证据、遗留风险、回退点、下一阶段是否可开始。

#### 2026-07-18 精简执行修订

本修订经用户明确批准，只调整剩余 S08 至 S17 的调度、复核、测试、构建、hash 和文档同步频率。所有任务编号、产品范围、依赖、文件所有权、促销黑盒证据、G0B、真实电脑/真机验收、发布门禁和第 20 节最终完成标准保持不变。

剩余工作按七个交付批次组织：

| 批次 | 覆盖阶段与任务 | 执行和验收节奏 |
|---|---|---|
| L1 | S08 / MB-04 | 一个写代理独占四个新快照类；稳定版本分别取得独立安全审查和独立功能/制品验证 PASS，再执行 Android 阶段回归和 APK 构建。 |
| L2 | S09 / MB-05、MB-06 | 严格 MB-05 → MB-06；共享一次 catalog、旧数据和本地商品阶段回归。无法可靠识别旧商品来源时仍必须人工选择。 |
| L3 | S10 / MB-07、CB-01～CB-03、MF-02、MF-03、MF-05 | MB-07 与不重叠文件的 CB-01 可并行；随后 CB-02 → CB-03。MF-05 可在 MB-07 后独立进行，MF-02 → MF-03 必须等待 CB-03；阶段末一次完成订单边界、生命周期、UI 和 APK 验收。 |
| L4 | S11 / PM-01～PM-04 | PM-03 在文件不重叠时可与候选模型/影响矩阵工作并行；PM-04 等其所需模型和矩阵稳定后实施；阶段末验证未知促销失败关闭和手动定价审计。 |
| L5 | S12～S15 / 全部 PV、PN、PE、registry、integration、PI、MF-04 | S11 通过后，各促销类型和 PV-ORDER 的只读黑盒证据可并行收集。每条类型流水线仍严格为 PV-X → PN-X 与 PE-X；未取得完整 PV 不得实现公式。共享 registry 只统一修改一次，之后集中执行跨端 PI 和 MF-04。 |
| L6 | S16 / IV-01～IV-04 | IV-01、IV-03 的离线部分和 IV-04 可复用同一稳定源码与证据运行；IV-02 只有在 EV-05=`READ_ONLY_PROVEN` 且用户批准真实外部操作后才能执行。四项均通过才可关闭 S16。 |
| L7 | S17 / G9、DOC-01 | PC 与 Android 各做一次最终完整回归和正式构建；集中核验 EXE、ZIP、APK、发布副本、内容、时间戳、hash 和最终文档。 |

精简执行规则：

1. 每次开始执行新任务或交付批次前，主协调器先向用户报告实际调用的子代理数量、任务、模型和推理强度；零调用也必须明确报告。
2. 普通任务默认一个写代理。只有合同存在会改变产品行为、跨端接受集合、文件能力或安全边界的实质歧义时，才增加一个前置只读审查代理。
3. 独立后置复核集中在稳定阶段/工作包快照：MB-04；S09 的 MB-05/MB-06；S10 的 MB-07、CB-01～CB-03；S11 的 PM-01～PM-04；每个准备上线类型的 PV/PN/PE、PV/PN/PE-ORDER、PN-INTEGRATION、PE-REGISTRY 和全部 PI；IV-02、IV-04；S17/G9。一次审查会话可覆盖同一稳定快照上的多个串行任务或互不重叠工作包，但必须逐任务/工作包给出 verdict，不能为同一证据重复启动等价审查。其他任务由主协调器检查实际文件、diff 和定向测试；发现高风险变化时升级独立复核。
   若具体任务合同或 `ACTIVE_ITERATION` 明确要求不同职责的多个独立 verdict，仍必须分别执行；MB-04 保留独立安全审查和独立功能/制品验证两个 PASS 条件。
4. 每个任务运行新增/变更测试、直接相关模块测试和必要编译；每个 S 阶段运行该端完整回归与编译；S10、S15、S16、S17 运行跨端或全链路验证。I/O、取消、损坏和崩溃恢复覆盖不得减少。
5. Android APK 只在阶段门禁要求时构建一次；PC EXE/ZIP 只在 S17/G9 正式构建。构建后的源码、fixture 或测试变化会使原制品证据失效。
6. 单任务只计算修改文件和明确冻结边界文件的 SHA-256；阶段门禁计算阶段源码/fixture 清单；S17 计算全部发布制品和发布副本 hash。
7. 同一阶段的任务完成后先在 `ACTIVE_ITERATION` 保留紧凑证据并标记 `TASK_PASS_PENDING_STAGE_SYNC`；依赖表明确解锁时可以继续同阶段下游任务。每个 S 阶段必须形成产品门禁结论；属于 L5 跨阶段批次的 S12～S14 在阶段门禁通过后可标记 `STAGE_PASS_PENDING_BATCH_SYNC` 并继续，但不得把未完成类型伪装通过。每个交付批次结束时一次性同步实施计划、`IMPLEMENTATION_STATUS`、`PROJECT_STATUS`、`PROJECT_LOG` 和方案索引，最后更新 `ACTIVE_ITERATION`。不得跨越未通过的阶段产品门禁、未关闭的交付批次或宏观门禁。
8. 精简模式只消除重复证据，不允许历史测试冒充本轮结果，不允许子代理口头结论替代主协调器核对，也不允许把“代码完成”写成阶段或项目完成。

#### S01：工作区、ODBC、真实结构与只读身份证据

任务：EV-01、EV-03、EV-02、EV-04、EV-05，严格按该顺序执行。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 新增只读诊断脚本、Probe EXE、schema/候选/权限证据和对应测试；不接入正式同步流程。 |
| 电脑端前端 | 无实施（仅记录现有 UI 基线）。 |
| 手机端后端 | 无实施（仅记录现有模型、同步和测试基线）。 |
| 手机端前端 | 无实施（仅记录现有页面基线）。 |

前置：无。  
完成：G0A 证据齐全；EV-05 形成 READ_ONLY_PROVEN、WRITE_CAPABILITY_PRESENT 或 UNKNOWN 明确结论。后两种结论不阻止离线阶段，但禁止真实实时模式。  
交付：五份 EV 证据、诊断脚本测试结果和工作区基线。

#### S02：精确十进制、manifest、SQLite 与能力隔离合同

任务：CT-01、CT-02、CT-03、CT-04、CT-05。任务可由不同 Agent 顺序执行，但 CT-03 必须等待 CT-01/CT-02。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 实现 Decimal、ID、manifest、SQLite v2、ReadOnlyMs2011Session 和 ToolOwnedPath 的纯合同/校验器与 fixture。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 实现同一份十进制、manifest、SQLite DTO/fixture 校验合同；不下载、不启用快照。 |
| 手机端前端 | 无实施。 |

前置：S01 的 EV-02/EV-03 完成。  
完成：两端 fixture 逐字一致；所有合同和架构测试通过；不连接真实 SQL。  
交付：冻结的 schemaVersion 2、共享 fixture、ID/路径/资源上限测试。

2026-07-17 完成状态：CT-01 至 CT-05 已按离线边界完成。电脑端新增 Decimal、ID/资源上限、v2 manifest、SQLite v2 schema、NormalizedPromotionRule、ReadOnlyMs2011Session 和 ToolOwnedPath 合同；手机 core 新增对应 BigDecimal、manifest 与规则 DTO。六份共享 fixture 逐字一致。PC S02 定向测试 14/14、完整回归 163/163、`compileall` 通过；Android core 全量 Java 17 编译、既有 3 组 smoke 和新增 3 组合同测试均通过。测试只使用内存 SQLite、临时 AppData 和 fake query runner，未连接真实 SQL、未生成生产快照、未启用任何促销公式。**S02 PASS**，只解锁 S03；G0B 状态不变。

#### S03：电脑端安全配置、QueryId 与调度骨架

任务：PB-00、PB-01、PB-02。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 增加数据源配置、AppData 路径、固定 QueryId 执行门、超时、安静窗口、熔断和 fake ODBC 测试。 |
| 电脑端前端 | 无实施；不得提前增加设置控件。 |
| 手机端后端 | 无实施。 |
| 手机端前端 | 无实施。 |

前置：S02 完成。  
完成：fake ODBC 下未知 QueryId、越界路径、超时、取消和熔断均有测试；旧配置仍以 legacy_sqlite 启动。  
交付：不会生成快照的安全连接和调度骨架。

2026-07-17 完成状态：PB-00 至 PB-02 已按 fake ODBC 边界完成。配置新增 `legacy_sqlite/ms2011_live` 数据源、检测周期、安静窗口、全量指纹周期、熔断和 v2 资源上限；旧配置缺字段时继续以 `legacy_sqlite` 启动，数据库固定为 `MS2011`，配置拒绝 SQL 用户名和密码。`AppPaths` 固定生成 LocalAppData v2 capability，源路径使用独立 `SourceReadPath`，配置不能把 v2 输出移出 AppData。新增 EV-02 十三表/候选键常量、不可变 QueryId→SELECT 目录和唯一 `ms2011_connection.py` ODBC 适配器；未知 QueryId、动态标识符、任意数据库和只读 access mode 不可用/被拒绝均失败关闭，所有资源在异常路径释放。单线程协调器实现安静窗口、短超时降级、强制双读上下文、有限人工请求、取消门、连续失败熔断、低成本探测和冷却后人工重试；sysprocesses 无权限只标记 `UNAVAILABLE`，不伪装空闲。S03 定向测试 23/23、PC 完整回归 179/179、`compileall` 通过；测试只使用临时目录、fake runner 和 fake ODBC，未连接真实 SQL、未生成快照、未修改 UI/HTTP/v1 流程。**S03 PASS**，只解锁 S04 的离线 deterministic reader/normalizer 工作；G0B 状态不变。

#### S04：电脑端确定性读取、变化探测和候选规范化

任务：PB-03、PB-04、PB-05、PB-05P。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 实现商品/分类/单位/促销原始读取、双读 hash、快速探测、全量指纹、商品规范化和促销候选提取。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 无实施；只允许读取共享 fixture 做兼容验证。 |
| 手机端前端 | 无实施。 |

前置：S03 完成，EV-04 已提供候选范围。  
完成：任一表失败整轮失败；重复键、缺失关系、简单促销候选和未知类型均进入固定状态或 issue；不生成 SQLite。  
交付：确定性 raw/normalized fixture、探测性能记录和 severity 结果。

2026-07-17 完成状态：PB-03、PB-04、PB-05 和 PB-05P 已按 fixture/fake ODBC 边界完成。固定 QueryId 目录新增商品、分类、单位、EV-04 促销证据和两类低成本变化摘要；完整读取严格按 EV-02 稳定键排序，保留源 Decimal/datetime，逐表记录 QueryId、行数和耗时，并进行两次独立规范化 SHA-256。任一表失败、主键重复/乱序、关键关系缺失或双读不一致均整轮失败。快速摘要明确 `provesComplete=false`；默认每 15 分钟要求一次受安全门约束的完整指纹，性能历史输出 p50/p95/max。商品规范化只以 `ms2011:<GID>` 为身份，保留空单位、原始 stop flag 和规范 Decimal；简单字段只标为 evidence-only。固定 severity matrix 决定 issue 及整份拒绝。候选提取只输出 `UNVERIFIED` 或证据明确的 `INACTIVE`，保留每个原始行稳定 source key、重复商品映射、日期/星期/时段/组原值；`normalized_rules` 固定为空。S04 定向测试 25/25、PC 完整回归 204/204、`compileall` 通过；未连接真实 SQL、未生成 SQLite/manifest/快照、未修改 UI/HTTP/v1。**S04 PASS**，只解锁 S05 的 fake ODBC SQLite/不可变发布/HTTP/UI 工作；G0B 状态不变。

#### S05：电脑端 SQLite、不可变发布、HTTP 与电脑 UI

任务：PB-06、PB-07、PB-08、PB-09、PF-01、PF-02。先完成 PB-06 至 PB-09，再串行完成 PF-01、PF-02。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 写入和自检 SQLite v2，增加跨进程锁、不可变对象、active manifest、版本固定下载端点和完整后端编排。 |
| 电脑端前端 | 增加数据源、只读能力、周期、立即同步、熔断、快照 ID 和计数展示；不实现后端规则。 |
| 手机端后端 | 无实施（仅可用测试客户端验证 HTTP 响应）。 |
| 手机端前端 | 无实施。 |

前置：S04 完成。  
完成：fake ODBC 全链路到 HTTP 下载通过；旧 v1 端点回归通过；两个电脑实例不能同时发布；UI 不阻塞 Qt 主线程。  
交付：可独立运行的电脑端商品 v2 发布能力，此时复杂促销仍没有可执行公式。

2026-07-17 完成状态：PB-06 至 PB-09、PF-01 和 PF-02 已按离线 fixture/fake ODBC 边界完成。SQLite v2 使用每轮唯一临时文件、单事务写入、关闭后只读重开自检，并复核 integrity、外键、必需表列和计数；发布采用跨进程锁、不可变对象/版本 manifest 和最后替换 active 指针，清理仅处理 ToolOwnedPath allowlist 且保护 active、pending、last-good 与下载引用。HTTP 保留全部 v1 端点，新增固定版本 v2 manifest/下载端点、Bearer 优先认证、限速/并发限制以及下载前大小/hash 复核。fake ODBC 集成测试已跑通双读、规范化、SQLite、不可变发布、manifest、本机 HTTP 下载与 hash 一致，复杂促销规则数固定为 0。电脑 UI 已显示旧 AGT_MAIN/MS2011 数据源、进程位数、只读能力、周期、立即同步、取消等待、阶段、耗时、失败/熔断、最近成功、快照 ID 和计数；只读测试和同步均使用 worker thread，且不存在绕过安全门按钮。PC 完整回归 225/225、`compileall` 通过。**S05 PASS**，只解锁 S06 的离线手机端精确金额迁移；目标身份仍为 `WRITE_CAPABILITY_PRESENT`，G0B、正式应用真实 SQL 连接、自动实时读取和生产发布继续锁定。

#### S06：手机端 Money 精确小数迁移

任务：MB-01A、MB-01B、MB-01C、MB-01D、MB-01E，严格串行。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无实施（仅运行 v2 十进制 fixture 回归）。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 迁移 Money/Discount、核心定价、找零、销售、JSON、CSV 和鸣盛导入；保留并最终移除整数兼容桥。 |
| 手机端前端 | 迁移金额输入和显示，统一 MoneyText/NumberTextParser；禁止 long/double 金额路径。 |

前置：S02 CT-01 完成；建议在 S05 后执行以便使用完整 v2 fixture。  
完成：MB-01E 架构测试通过；旧整数数据可读；2099.99 全链路仍为 2099.99。  
交付：不换单位、不用二进制浮点参与结账的手机金额模型。

2026-07-17 完成状态：MB-01A 至 MB-01E 已严格串行完成。Money/Discount 内部使用规范 BigDecimal，Money 只保留 String/BigDecimal 入口；旧 `amount()`、`legacyLongValueExact()`、`Money.of(long)` 和 basis-points 整数折扣入口均已删除。核心定价、找零、Sale/SaleLine、日报汇总、商品 JSON、CSV 导入导出和鸣盛导入保持原币精确十进制；新 JSON 写规范字符串，旧整数 JSON/CSV 通过字符串化兼容读取，不批量重写用户文件。鸣盛金额路径已移除 `setScale(0)`、`Math.round` 和 double 回退，非整数旧促销数量会明确失败而非舍入。手机端新增 MoneyText/NumberTextParser，点号与逗号输入均受 CT-01 位数/scale/长度约束，指定金额页面不再自行解析 long/double 或直接拼接旧 amount。金额架构扫描生产源码命中数 0；11 组 Java 合同/烟测全部通过，完整 Gradle debug 构建成功。构建 APK 为 1,033,434 bytes，SHA-256 `2D45E3CA7D17104F382E2A19E9BD80CA65A63C5CB62AD936C56F92CA540A625E`，仅作阶段验证，未覆盖正式发布副本。**S06 PASS**，解锁 S07 数量精确迁移；G0B 与真实 MS2011 自动读取仍锁定。

#### S07：手机端 Quantity 精确小数迁移与统一显示

任务：MB-02A、MB-02B、MB-02C、MB-02D、MF-01，严格串行。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无实施。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 增加 Quantity，迁移购物车、结账、销售和持久化；非整数数量排除全部自动促销。 |
| 手机端前端 | 增加小数数量输入并完成金额/数量显示一致性扫描。 |

前置：S06 完成。  
完成：旧整数数量兼容、手工小数数量、购物车合并和非整数促销排除测试通过；不再存在数量 double/int 兼容桥。  
交付：称重商品可手工输入小数数量，但不参与自动促销。

2026-07-18 完成状态：S07 已严格串行完成 MB-02A、MB-02B、MB-02C、MB-02D 和 MF-01。Quantity 已覆盖购物车、定价、销售快照、CSV 和结账数量 UI；商品行支持 CT-01 小数数量编辑，商品搜索选中后以默认 1 打开数量对话框，扫码/条码/手工商品显式使用 `Quantity.one()`。已删除 9 个 Quantity/int 临时兼容 API，生产 transaction quantity bridge 与 UI narrowing 扫描均为 0；`Money.times(int quantity)` 无生产调用、没有 Quantity 委托关系，按用户批准边界保持为非交易链路整数标量 helper。MF-01 统一 Money/Quantity formatter 与点/逗号十进制词法，修复 Checkout 零金额和 Sales 数量展示；JDK AST 架构测试扫描 24 个 UI Java 并执行 24 个对抗变体。主协调器最终独立执行 core 13/13、app 7/7，静态禁止模式 0，三路后置复核 PASS。完整 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 12s`；实施目录 APK 为 1,010,742 bytes，SHA-256 `C4A1018D3033C6A4BA8ED410DAB98D1CCC3F3FEF499B5644AC45EE3C039E26AC`，未复制到 `dist` 或发布副本。真实设备软键盘、对话框、安装与页面遍历仍未验证；这不改变本阶段离线代码/构建门禁结论。**S07 PASS**，只解锁后续按依赖执行 S08/MB-03；按用户指令当前停在 S08 之前，G0B、真实 MS2011 自动读取和生产发布继续锁定。

#### S08：手机端 v2 manifest、不可变快照和崩溃恢复

任务：MB-03、MB-04。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无实施（仅保持 S05 HTTP fixture 可用）。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 校验 manifest、固定 snapshotId 下载、流式临时文件、不可变 DB/manifest、state.json 和 active/lastGood 恢复。 |
| 手机端前端 | 无实施；本阶段不展示最终同步状态。 |

前置：S05、S07 完成。  
完成：损坏 hash、错误计数、路径穿越、超大文件、下载取消和杀进程恢复测试通过；尚不合并商品 catalog。  
交付：能够安全保存但尚不启用商品的手机 v2 快照仓库。

#### S09：手机端商品身份和 catalog 合并

任务：MB-05、MB-06。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无实施。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 增加 ProductOrigin/sourceProductKey，执行旧数据迁移，读取 v2 商品并构建候选 catalog；LOCAL 保持可编辑。 |
| 手机端前端 | 无实施；只读、停用和冲突界面统一留到 S10 的 MF-03。 |

前置：S08 完成，MB-01E 已完成。  
完成：重启可从 active DB 重建商品；同步商品不直接启用未验证简单促销；本地商品编辑无回归。  
交付：可验证、可搜索但尚未自动轮询、也尚未接入最终同步商品 UI 的新商品 catalog。

#### S10：手机前台同步、订单边界和状态 UI

任务：MB-07、CB-01、CB-02、CB-03、MF-02、MF-03 最终验收、MF-05。依赖顺序为 MB-07 + CB-01 → CB-02 → CB-03，再完成 MF。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无实施（仅作为 S05 服务端参与端到端测试）。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 增加前台协调器、周期/手动触发、active/pending 切换、购物车绑定快照和启动恢复。 |
| 手机端前端 | 显示最新/过期/离线/pending、同步商品只读和冲突；MainActivity 只发送生命周期触发。 |

前置：S09 完成。  
完成：空购物车启用、非空购物车延迟启用、完成/取消后切换、后台停止轮询和故障注入测试通过。  
交付：商品实时同步第一条可独立验收的完整纵向切片，促销仍只保留候选证据。

#### S11：手机促销安全门、整车引擎和手动定价审计

任务：PM-01、PM-02、PM-03、PM-04。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无实施（仅提供候选/issue fixture）。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 增加候选/规范规则模型、影响范围矩阵、整车促销引擎骨架和手动定价审计字段；不实现具体公式。 |
| 手机端前端 | 无实施；MF-04 必须等具体促销 PI 完成后再做。 |

前置：S10 完成。  
完成：UNVERIFIED/INVALID/UNKNOWN_TYPE 不会静默按普通价结账；手动定价能够解除对应阻止并写入 Sale/CSV。  
交付：没有具体处理器也能安全失败的促销基础设施。

#### S12：简单数量促销纵向切片

任务：PV-SIMPLE、PN-SIMPLE、PE-SIMPLE；PI-SIMPLE 留到 S15 统一执行。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 收集 GHuiPrice/GHuiPriceCount 黑盒证据并实现 SIMPLE_QUANTITY_PRICE v1 规范化器。 |
| 电脑端前端 | 无实施（只允许显示既有候选/计数）。 |
| 手机端后端 | 实现 SimpleQuantityPriceHandler 和独立 fixture 测试，不接入共享注册表。 |
| 手机端前端 | 无实施。 |

前置：S11 完成且存在安全可复现案例。  
完成：PV 证据完整；PN/PE 独立测试通过；未修改共享 registry。  
交付：等待统一注册的简单促销处理器和证据。

#### S13：三类复杂促销独立工作包

本阶段不是一个 Agent 的任务。拆成三个互不修改共享 registry 的小工作包，分别完成后单独验收：S13A 数量百分比（PV-PERCENT、PN-PERCENT、PE-PERCENT）、S13B 指定数量固定总价（PV-FIXED、PN-FIXED、PE-FIXED）、S13C 多商品组合固定总价（PV-MIX、PN-MIX、PE-MIX）。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 每个工作包分别收集证据并新增自己唯一的 normalizer 和测试；不得修改其他类型或共享 registry。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 每个工作包分别新增自己唯一的 Handler 和测试；不得修改选择策略或共享 registry。 |
| 手机端前端 | 无实施。 |

前置：S11 完成；每个工作包必须有自己的安全黑盒案例。  
完成：三个工作包分别报告完成/阻塞；阻塞的类型保持 UNVERIFIED，不阻止其他已完成工作包进入 S14/S15。  
交付：最多三组独立 normalizer/handler/fixture；不得在本阶段声称端到端已支持。

#### S14：时间、优先级和叠加规则切片

任务：PV-ORDER、PN-ORDER、PE-ORDER。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 冻结日期、星期、时段、优先级和叠加 ruleVersion；实现 schedule/priority 规范化。 |
| 电脑端前端 | 无实施。 |
| 手机端后端 | 实现 PromotionSelectionPolicy，固定 ART 时间并做确定性选择测试；不注册尚未通过的类型。 |
| 手机端前端 | 无实施。 |

前置：S11 已完成；PV-ORDER 的只读证据收集可与各类型 PV 并行。PV-ORDER 的最终裁决以及 PN-ORDER、PE-ORDER 必须等待 S12 和准备进入本轮上线的 S13 工作包完成，PE-ORDER 还必须等待 PM-04；没有证据的端点、优先级或叠加行为不得实现。  
完成：端点、星期映射、跨午夜、多个命中和非叠加/叠加行为均有证据；无法确认的类型不进入注册。  
交付：共享 registry 可以安全使用的选择合同。

#### S15：统一注册、写入快照、全部 PI 与促销 UI

任务：PN-REGISTRY、PN-INTEGRATION、PE-REGISTRY、准备上线类型对应的 PI-SIMPLE、PI-PERCENT、PI-FIXED、PI-MIX、PI-ORDER、MF-04，严格按此顺序；未完成 PV/PN/PE 的类型不执行对应 PI。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 一次性注册已通过类型，把 NormalizedPromotionRule 写入 v2 快照并执行跨端 PI fixture。 |
| 电脑端前端 | 无实施；复用既有状态/计数 UI。 |
| 手机端后端 | 一次性注册已通过 Handler，执行整车选择、分摊和鸣盛结果对比。 |
| 手机端前端 | 实现具体促销问题、手动定价原因和销售审计展示。 |

前置：准备进入本轮上线的类型均已完成各自 PV/PN/PE，且 S14 对这些类型需要的时间、优先级和叠加合同已经完成；只包含 PV/PN/PE 均通过的类型。  
完成：每个准备上线类型 PI 通过；evidenceHash 一致；未知 type/ruleVersion 仍安全阻止；当前未完成类型明确记录为部分完成。  
交付：可在手机新购物车中执行的已验证促销纵向切片。

#### S16：离线、真实电脑、安全和性能验收

任务：IV-01、IV-02、IV-03、IV-04。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无生产实施；执行离线全链路、真实只读分级验收、性能采样和安全回归。失败必须退回所属任务。 |
| 电脑端前端 | 无实施（仅验收连接、状态、熔断和无卡顿表现）。 |
| 手机端后端 | 无生产实施（仅执行下载、恢复、catalog、购物车和促销真机验收）。 |
| 手机端前端 | 无实施（仅验收同步状态、只读提示、促销问题和手动定价流程）。 |

前置：S15 完成；EV-05 必须为 READ_ONLY_PROVEN 才能执行 IV-02。  
完成：全部 IV 门槛通过；任一真实电脑卡顿、阻塞、异常写入或性能超标均停止发布。  
交付：可审计的性能、安全、真实电脑和真机证据。

#### S17：完整回归、打包、文档和发布核验

任务：G9、DOC-01。

| 端 | 本阶段实施内容 |
|---|---|
| 电脑端后端 | 无业务实施；运行完整测试、compileall、PyInstaller，核验 pyodbc、EXE/ZIP 内容、时间戳和 hash。 |
| 电脑端前端 | 无实施（仅运行打包 GUI 冒烟测试）。 |
| 手机端后端 | 无业务实施；运行 core/app smoke tests 和 APK 构建。 |
| 手机端前端 | 无实施（仅真机冒烟验证最终 APK）。 |

前置：S16 完成且无未处理的发布阻塞。  
完成：构建输出与发布副本 hash 一致；PROJECT_STATUS、PROJECT_LOG、修改方案 README 已同步；未完成促销按“部分完成”记录。  
交付：最终 EXE、ZIP、APK、hash、时间戳、内容清单和项目文档。

阶段状态只能是“未开始、进行中、完成、部分完成、阻塞”。S13 的单个工作包可以独立标记阻塞；S15 只能注册完成工作包。S01 至 S15 任何生产逻辑失败必须回退到对应阶段修复，禁止在 S16/S17 直接修改业务逻辑以绕过验收。

## 6. Subagent 通用执行规则

每个 Subagent 一次只领取一个任务编号。

开始前必须：

1. 阅读本计划第 1 至 6 节。
2. 阅读该任务的前置条件和依赖产物。
3. 检查允许修改文件是否已有未合并修改。
4. 先写失败测试或 characterization test，再修改生产代码。

执行中必须：

- 只修改“允许修改”列出的文件。
- 发现需要越界时停止并报告，不顺手扩大范围。
- 不使用真实 MS2011 做单元测试。
- 时间、网络、ODBC、文件系统均使用可注入接口或 fake。
- 单元测试不得真实 sleep 5 秒或 10 秒。
- 不根据字段名称猜促销语义。

交付报告固定格式：

    阶段编号：
    任务编号：
    负责端：电脑后端 / 电脑前端 / 手机后端 / 手机前端
    完成状态：完成 / 部分完成 / 阻塞
    修改文件：
    新增或变更接口：
    未触碰边界：
    测试命令与结果：
    人工证据：
    遗留风险：
    下游是否可开始：是 / 否

## 7. G0A/G0B：证据、开发与生产启用门禁

### EV-01：冻结工作区基线

负责层：只读审计  
允许修改：仅新增 修改方案/实施证据/workspace_baseline.md  
禁止修改：任何业务源码

步骤：

1. 记录两端关键目录、现有测试命令和当前产物位置。
2. 记录 pc-sync-tool 与 Android 的当前测试数量。
3. 记录 Money、Discount、Cart、Product、ProductLocalStore、ComputerSyncClient、config.py、http_server.py 的当前接口。
4. 记录当前工作区非本任务修改，避免后续覆盖用户内容。

完成标准：形成可复查基线；不声称当前根目录是有效 Git 仓库。

### EV-02：确认实时 SQL 精确表结构

负责层：电脑后端证据  
允许修改：

- pc-sync-tool/scripts/diagnose_ms2011_schema_readonly.py
- pc-sync-tool/tests/test_ms2011_schema_diagnostic.py
- 修改方案/实施证据/ms2011_schema_evidence.md

脚本只允许通过固定 SELECT 读取：

- DB_NAME、@@SERVERNAME。
- dbo.sysobjects、dbo.syscolumns、dbo.systypes 中指定十三张候选表的元数据。
- 指定表的 COUNT、MIN/MAX 主键等低成本统计。

要求：

- 输出真实列名、类型、长度、精度、小数位、nullable。
- 确认每张表的稳定排序键；不预设所有表只有单列主键。
- 把分析脚本中的“描述性别名”替换为真实列名证据。
- 不输出业务明细和凭据。

停止条件：实际列名或类型与导出分析不一致时，不修补猜测，先更新证据和映射提案。

### EV-03：ODBC、Python 和打包位数可行性

负责层：电脑后端证据  
允许修改：

- pc-sync-tool/requirements.txt
- pc-sync-tool/scripts/diagnose_ms2011_readonly.py
- pc-sync-tool/scripts/build_ms2011_probe_exe.ps1
- pc-sync-tool/tests/test_sql_driver_probe.py
- 修改方案/实施证据/odbc_compatibility.md

必须验证：

1. Python、PyInstaller、目标 EXE 是 32 位还是 64 位。
2. 可见 ODBC 驱动的位数和名称。
3. Windows 集成身份验证能连接 MS2011。
4. 目标电脑上的真实驱动接受连接 timeout 与查询 timeout 配置，且两条固定诊断查询在配置阈值内完成；timeout 参数传递、连接/查询阶段分类和资源释放由 fake ODBC 自动化测试覆盖。生产 `MS2011` 不得通过不可达服务器、`WAITFOR`、制造锁、反复降低 timeout 或重复 Probe 人为触发超时。实际到期中止只能在隔离环境另行验证，不作为 EV-03 的生产目标电脑阻断条件。
5. 独立诊断 Probe EXE 能执行两条固定只读诊断查询；Probe 不复用正式应用入口，不提供通用 SQL 输入。
6. 不复制鸣盛自带 odbcbcp.dll 或旧 BCP 依赖。

诊断查询仅限数据库名和商品行数。失败必须区分无驱动、位数不匹配、登录失败、权限不足、协议失败和超时。正式 MobilePosSync EXE 的实时连接验证必须等 PB-09 完成后在 G9 执行，EV-03 不得要求尚未实现的正式应用具备 SQL 能力。

### EV-04：促销候选和黑盒验证清单

负责层：促销证据  
允许修改：

- 修改方案/促销验证/promotion_candidate_inventory.md
- 修改方案/促销验证/black_box_safety_protocol.md
- pc-sync-tool/scripts/inventory_ms2011_promotions_readonly.py
- pc-sync-tool/tests/test_promotion_inventory_readonly.py

输出：

- 当前候选促销类型、源主键和关联商品。
- 当前生效、未来配置、历史失效和异常记录的区分依据。
- 每类尚缺的公式、优先级、余数、时段和舍入证据。
- 禁止在生产环境执行的测试动作。

候选清单只能来自既有导出 TSV 或固定 QueryId 的只读盘点脚本。脚本读取范围、稳定排序键和输出脱敏规则必须在测试中固定；不得让 Agent 根据活动名称手工猜测当前启用类型。清单必须同时记录“当前启用”“未来配置”“历史失效”“无法判断”四类及其计数，使最终验收能够证明当前启用类型没有遗漏。

2026-07-17 状态：EV-04 **PASS**。目标机冻结探针在 `MS2011` 上完成一次正式盘点，`status=ok`、`ExitCode=0`、`doubleReadMatched=true`；采集时间为 `2026-07-17T14:48:40-03:00`，来源哈希为 `CA8150B69C6BEDEE38BED4675034B6A9AB2400C21DD42062F8C1215406C259EA`。共得到 41 个候选，其中当前启用 28、未来配置 0、历史失效 0、无法判断 13；候选数组长度、四类计数和 QueryId 行数已核对。当前范围包含 `PRODUCT_SIMPLE`、`QUANTITY_PERCENT`、`QUANTITY_FIXED_TOTAL`、`MIX_MATCH_FIXED_TOTAL` 四种候选类型；13 个无法判断项及异常代码全部保留。原始证据：

- 合同 JSON：`https://drive.google.com/file/d/1iHW10mbpeHoBQbXU31S1M1xmER00hlB0/view`
- 盘点 JSON：`https://drive.google.com/file/d/1emDQqqHrJxPKk3zTHM4nzZHY3bNHpYTE/view`
- 退出码 TXT：`https://drive.google.com/file/d/1EVk1X3EUaR_AyT07v6puAKmlOpwbKKT3/view`

该证据只完成候选范围盘点，不验证公式语义。EV-01、EV-02、EV-03 和 EV-04 现已满足 G0A 条件，因此 **G0A PASS**，允许按阶段依赖继续 fixture、fake ODBC、既有导出数据和离线任务；S01 的下一项为 EV-05。G0B 仍锁定，正式 MobilePosSync 真实 MS2011 连接、自动实时读取、IV-02 和生产发布继续禁用。

### EV-05：生产只读身份和权限证据

负责层：部署安全证据  
允许修改：

- pc-sync-tool/scripts/diagnose_ms2011_permissions_readonly.py
- pc-sync-tool/tests/test_ms2011_permission_diagnostic.py
- 修改方案/实施证据/ms2011_readonly_identity.md

要求：

1. 记录实际连接身份、认证方式和运行账户，不记录秘密。
2. 只通过固定 SELECT 查询 SQL Server 2000 支持的角色成员关系和权限元数据。
3. 明确检查 sysadmin、db_owner、db_datawriter、db_ddladmin 和显式写权限。
4. 不通过尝试 INSERT、UPDATE、DELETE 或建表来“测试拒绝”。
5. 权限模型无法完整证明时输出 UNKNOWN，不得输出 SAFE。
6. 工具不得自动修正权限；只读身份必须由管理员在工具之外预先配置。
7. 记录 ODBC read-only access mode 是否被驱动接受，但不得把驱动提示当成服务器权限证明。

输出状态固定为 READ_ONLY_PROVEN、WRITE_CAPABILITY_PRESENT、UNKNOWN。只有 READ_ONLY_PROVEN 可以通过生产门禁。

2026-07-17 最终状态：EV-05 固定查询诊断器、28 个定向测试、149 个 PC 完整回归、`compileall`、独立安全复审、冻结打包和目标电脑取证均已完成。冻结 ZIP 为 `pc-sync-tool/dist/MS2011PermissionProbe-20260717.zip`，SHA-256 `49ED06FA9A8650522D800B1BE295FBCF0E8B7D5D57D0A069833A225DD286D25C`；内嵌 EXE SHA-256 `144CFF9A636DD67A2FA67DB379C13EC372152BB2ABE44BE4593415D93900BE99`。Drive 传输文件为 `https://drive.google.com/file/d/188hHyLhm8RLZrremdynDaS_EToD9dhmA/view`。目标身份 `SERVER\Administrador / dbo` 的诊断结果为 `WRITE_CAPABILITY_PRESENT`、`doubleReadMatched=true`、`ExitCode=0`，来源哈希 `2E0908652939AD7D6E16E1A12B2C79AC221D73D1D70E21398A471284FC3FE485`。原始证据 ZIP 为 `https://drive.google.com/file/d/15Y4YIQjlG5L_eQYF7PAYvu-DWWPfBZz-/view`，SHA-256 `9F73112D3410CEAEB83546C4EF89A28AABDDDFED5D380E8598786FC6D29CB6BA`。S01 证据收集完成，可继续离线 S02；EV-05 未达到 PASS，G0B 继续锁定。完整合同与证据见 `修改方案/实施证据/ms2011_readonly_identity.md`。

G0A 开发门禁通过条件：EV-01、EV-02、EV-03 成功，EV-04 能列出候选范围。G0A 通过后允许使用 fixture、fake ODBC 和既有导出数据继续 CT、PB、MB、CB、PM 及离线集成任务。

G0B 生产启用门禁通过条件：EV-05 为 READ_ONLY_PROVEN，且 Windows 集成身份部署说明已由人工复核。G0B 未通过时，所有离线开发和旧模式可继续，但真实 MS2011 连接、自动实时读取、IV-02 和生产发布必须保持禁用。

## 8. G1：跨端合同冻结

### CT-01：精确十进制合同

前置：EV-02 完成  
允许修改：

- pc-sync-tool/src/decimal_value.py
- pc-sync-tool/tests/test_decimal_value.py
- android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/model/DecimalValue.java
- 对应测试
- 两端 tests/fixtures/v2_decimal_cases.json

合同：

- Python 使用 decimal.Decimal。
- Java 使用 java.math.BigDecimal。
- SQLite 使用规范化十进制 TEXT，不使用 REAL 保存业务金额或数量。
- JSON 和 manifest 中十进制值使用字符串。
- 规范形式禁止指数；零为 0；去除无意义尾零；负数、NaN、Infinity 拒绝。
- 原始文本和规范文本分开保存；hash 只使用规范文本。
- 数值比较使用 compareTo，不使用 equals 比较 scale。
- MONEY 初始上限为整数 15 位、小数 4 位；QUANTITY 初始上限为整数 11 位、小数 4 位；规范文本最大 32 字符。该边界覆盖 SQL Server money 和已知 numeric(15,4)，只有 EV-02 取得更宽真实字段证据并升级合同 fixture 后才能放宽。
- 超出业务上限的源值保留原始文本并产生 INVALID issue，不得截断、四舍五入或转成 Infinity；手机输入在进入领域模型前直接拒绝。

必须覆盖：

- 2099.99。
- 1.25 × 2099.99。
- 0、整数、小数、尾零、超长精度。
- 非法指数、空值、负数、NaN、Infinity。

最终金额显示位数和舍入时点由黑盒证据决定；未取得证据的促销不得自行 setScale。

### CT-02：身份、路径和资源上限合同

允许修改：两端合同 fixture 和纯校验器，不改网络行为。

固定规则：

- sourceProductKey 格式为 ms2011:<GID>。
- snapshotId 格式为 ms2011-<UTC基本时间>-<12位小写十六进制>。
- snapshotId 中的 UTC 基本时间必须按真实 UTC 日历严格解析；无效月份、日期、时、分或秒必须拒绝，禁止 SMART/宽松解析自动归一化非法日期。
- snapshotId 只能通过正则白名单，不能直接接受路径分隔符、点点或 URL 编码变体。
- candidateId 格式为 pc-<24位小写十六进制>，内容为 SHA-256(sourceType + "\n" + canonicalSourceKey) 的前 24 位；mappingId、tierId、scheduleId、groupId 使用各自固定前缀和同样的规范输入算法。算法和测试向量必须由共享 fixture 冻结。
- 手机本地文件名由校验后的 ID 生成，不使用 manifest 任意路径。
- downloadPath 必须精确等于 /v2/snapshots/<snapshotId>.db，其中路径中的 snapshotId 必须与 manifest 字段逐字一致；不能使用固定 latest 路径，也不能只检查 /v2/ 前缀。
- minimumAppVersion 固定使用 Android versionCode 正整数，不使用自由文本或语义版本比较。
- 初始软上限：manifest 256 KiB、快照 256 MiB、商品 250000、促销候选 50000、validation issue 10000；编译时硬上限分别为 1 MiB、1 GiB、1000000、250000、50000。配置只能降低软上限，不能超过硬上限。
- 下载前检查可用磁盘空间；至少保留 active、pending 和一个回滚版本所需空间。

### CT-03：v2 manifest 合同

前置：CT-01、CT-02  
新增共享 fixture：v2_manifest.json、v2_manifest_invalid_cases.json

字段：

    ok
    schemaVersion
    snapshotId
    sourceType
    createdAtUtc
    sizeBytes
    sha256
    minimumAppVersion
    productCount
    categoryCount
    unitCount
    promotionCandidateCount
    verifiedPromotionCount
    validationIssueCount
    downloadPath

要求：

- schemaVersion 初始为 2。
- ok 必须为 JSON boolean true；sourceType 固定为 ms2011_live；createdAtUtc 为 ISO-8601 UTC Instant。
- SHA-256 为 64 位十六进制。
- sizeBytes 和所有计数为非负 JSON 整数，并受 CT-02 上限约束；所有计数与快照复查结果一致，不与历史固定数字比较。
- categoryCount、unitCount 必须为 0 至 2147483647 的 JSON 整数；minimumAppVersion 必须为 1 至 2147483647 的 Android versionCode JSON 整数。不得截断、夹紧、浮点转换或接受超界值。
- downloadPath 必须由 snapshotId 确定性生成。
- manifest 不包含 Token、SQL 服务器、驱动、Windows 用户和商品明细。

2026-07-18 限定修复记录：S08/MB-03 启动只读同步发现 Android `V2Contract` 的默认 SMART 日期解析可接受无效日历日期，同时 PC 任意精度 JSON integer 与 Android `int` DTO 对 categoryCount、unitCount、minimumAppVersion 的接受集合不一致。用户批准先执行 CT-02R/CT-03R：严格解析 snapshotId UTC 基本时间，并冻结上述三个字段的 32 位边界；两端共享 invalid fixture、纯校验器和合同测试必须逐项一致。修复完成前 MB-03 写入保持暂停；G0B 和真实 MS2011 边界不变。

2026-07-18 完成记录：CT-02R/CT-03R 已按 PC→Android 严格串行完成。PC `v2_manifest.py` 和 Android `V2Manifest` 统一拒绝三个字段越过冻结 int32 边界；Android `V2Contract` 使用 `uuuu` 与 `ResolverStyle.STRICT`，两端均拒绝 2 月 30 日、非闰年 2 月 29 日及 24 时/60 分/60 秒。三份共享 fixture 逐字一致：ID `D0CD76A68832FDB30B5A29419C1C57B34ABA27E3DAD0D94FC3E91A71F1409197`、manifest `C9ED56BDB3BC7AE3A618EF523C5DF14C1B1F3A33644EE5E1D2999E31CA61182C`、invalid manifest `A196B11C28E87FD93A05796F191891565B1F7F9A2A3D02BFF10F7B6D3BD0A17C`。主协调器使用项目 Python 环境执行 PC 完整回归 `226/226 PASS` 和 `compileall`；Gradle core/app 编译 `BUILD SUCCESSFUL in 9s`，随后 core `13/13`、app `7/7` main-based 测试通过。独立复核 PASS，无 High/Medium finding。MB-03 client/service 哈希保持 pre-write 值，说明 MB-03 尚未开始；本纠偏只解除其合同阻塞，不解锁 MB-04。G0B、`WRITE_CAPABILITY_PRESENT`、真实自动读取和生产发布状态不变。

### CT-04：SQLite v2 跨端最小合同

前置：EV-02、CT-01  
注意：本任务冻结商品快照、原始促销证据和通用规范化规则载体，但不提前冻结尚未验证的具体促销公式。具体 parameters JSON Schema 仍由各 PV-X/PN-X 的 ruleVersion 和共享 fixture 冻结。

必需表：

- sync_metadata
- products
- categories
- units
- promotion_candidates
- promotion_candidate_products
- promotion_raw_rows
- promotion_rules
- promotion_rule_tiers
- promotion_rule_schedules
- promotion_rule_groups
- validation_issues

关键字段：

    products:
      source_product_key TEXT PRIMARY KEY
      gid TEXT NOT NULL
      barcode TEXT NOT NULL
      secondary_barcode TEXT
      name TEXT NOT NULL
      category_code TEXT
      unit_code TEXT
      sale_price_raw TEXT NOT NULL
      sale_price_decimal TEXT NOT NULL
      simple_price_raw TEXT
      simple_price_decimal TEXT
      simple_threshold_raw TEXT
      simple_threshold_decimal TEXT
      stop_flag INTEGER NOT NULL
      source_update_time_local TEXT

    sync_metadata:
      key TEXT PRIMARY KEY
      value TEXT NOT NULL

    categories:
      category_code TEXT PRIMARY KEY
      category_name TEXT NOT NULL
      source_order INTEGER NOT NULL

    units:
      unit_code TEXT PRIMARY KEY
      unit_name TEXT NOT NULL
      source_order INTEGER NOT NULL

    promotion_candidates:
      candidate_id TEXT PRIMARY KEY
      source_type TEXT NOT NULL
      source_key TEXT NOT NULL
      verification_status TEXT NOT NULL
      begin_local TEXT
      end_local TEXT
      normalized_rule_version TEXT
      evidence_hash TEXT

    promotion_candidate_products:
      mapping_id TEXT PRIMARY KEY
      candidate_id TEXT NOT NULL
      source_product_key TEXT
      source_barcode TEXT
      group_code TEXT
      mapping_order INTEGER

    promotion_rules:
      rule_id TEXT PRIMARY KEY
      candidate_id TEXT NOT NULL UNIQUE
      rule_type TEXT NOT NULL
      rule_version TEXT NOT NULL
      evidence_hash TEXT NOT NULL
      parameters_json TEXT NOT NULL
      priority_order INTEGER
      stack_mode TEXT NOT NULL

    promotion_rule_tiers:
      tier_id TEXT PRIMARY KEY
      rule_id TEXT NOT NULL
      threshold_decimal TEXT NOT NULL
      value_kind TEXT NOT NULL
      value_decimal TEXT NOT NULL
      tier_order INTEGER NOT NULL

    promotion_rule_schedules:
      schedule_id TEXT PRIMARY KEY
      rule_id TEXT NOT NULL
      begin_date_local TEXT
      end_date_local TEXT
      weekday INTEGER
      begin_time_local TEXT
      end_time_local TEXT
      schedule_order INTEGER NOT NULL

    promotion_rule_groups:
      group_id TEXT PRIMARY KEY
      rule_id TEXT NOT NULL
      group_code TEXT NOT NULL
      required_count_decimal TEXT
      group_order INTEGER NOT NULL

    promotion_raw_rows:
      raw_row_id TEXT PRIMARY KEY
      candidate_id TEXT
      source_table TEXT NOT NULL
      source_key TEXT NOT NULL
      source_order INTEGER NOT NULL
      original_json TEXT NOT NULL
      canonical_json TEXT NOT NULL

    validation_issues:
      issue_id TEXT PRIMARY KEY
      severity TEXT NOT NULL
      issue_code TEXT NOT NULL
      candidate_id TEXT
      source_product_key TEXT
      message_code TEXT NOT NULL
      diagnostic_json TEXT NOT NULL

不得用 candidate_id + GID 作为唯一键，因为同一商品可能存在多映射或多组关系。promotion_rules 只保存已经 VERIFIED 的规范化规则；UNVERIFIED、INVALID、UNKNOWN_TYPE、INACTIVE 仍保留 candidate、mapping、raw row 和 issue，但不得生成可执行 rule。所有外键、唯一约束、ON DELETE 行为和索引必须在 fixture 中明确。

promotion_raw_rows 只保存白名单促销字段，不复制无关表列。original_json 保存源文本，canonical_json 只保存按 CT-01 规范化后的同一白名单字段。Android 生产代码不得解析 promotion_raw_rows 计算价格；它只能读取 promotion_rules、tiers、schedules、groups 和商品映射。

NormalizedPromotionRule 跨端 DTO 固定包含 ruleId、candidateId、ruleType、ruleVersion、evidenceHash、parameters、tiers、schedules、groups、priorityOrder、stackMode。每种 PN-X 只能构造这个 DTO；通用 SQLite writer 负责写入上述关系表。parameters_json 必须使用对应 type + ruleVersion 的共享 JSON Schema，禁止把鸣盛原始行直接塞入 parameters_json。

日期格式固定为 yyyy-MM-dd，本地时间固定为 HH:mm:ss，数据库 datetime 原始文本另存 raw 字段；createdAtUtc 使用 ISO-8601 UTC Instant。weekday 的数值含义必须等 PV-ORDER 证据冻结后写入 ruleVersion，未验证时不生成 promotion_rule_schedules。

完成标准：两端 fixture 逐字一致；SQLite integrity_check 和 foreign_key_check 可执行。

### CT-05：数据库与文件能力隔离合同

允许新增：

- pc-sync-tool/src/read_only_ms2011_session.py
- pc-sync-tool/src/tool_owned_path.py
- pc-sync-tool/tests/test_architecture_boundaries.py
- pc-sync-tool/tests/test_tool_owned_path.py

合同：

- ReadOnlyMs2011Session 只接受 QueryId 和参数，返回已复制的行数据，不暴露 connection 或 cursor。
- 任何 SQL 文本只存在于 query catalog；其他生产文件出现 SQL 执行调用时测试失败。
- pc-sync-tool/src 下除 ms2011_connection.py 外不得导入 pyodbc。
- ToolOwnedPath 只能由 AppPaths 为固定 AppData 子路径创建，普通字符串和任意 Path 不能直接构造。
- 写入、replace、unlink 和历史清理接口只接受 ToolOwnedPath。
- SourceReadPath 与 ToolOwnedPath 是不同类型，不能互相隐式转换。
- 删除接口拒绝目录、重解析点、越界路径、active/pending/last good 和未知文件名。

G1 通过条件：五份合同和架构测试全部通过。合同变更必须升级 schema 或 rule version，不能由下游 Agent 私改。

## 9. G2：电脑端只读商品快照

### PB-00：配置、路径和旧版本迁移

前置：EV-02、EV-03、CT-02  
允许修改：

- pc-sync-tool/src/config.py
- pc-sync-tool/src/paths.py
- pc-sync-tool/tests/test_config_and_event_log.py
- pc-sync-tool/tests/test_paths.py

配置新增：

- dataSource：legacy_sqlite 或 ms2011_live。
- liveDetectionIntervalSeconds：0 或 5..86400。
- sqlServer、sqlDriver。
- sqlQuietWindowSeconds。
- fullFingerprintIntervalSeconds。
- circuitFailureThreshold、circuitCooldownSeconds。
- v2RetentionCount、v2MaxSnapshotBytes。

规则：

- sqlDatabase 不接受任意配置，程序固定为 MS2011。
- 不保存 SQL 用户名或密码。
- 旧配置缺少新字段时继续以 legacy_sqlite 启动。
- 配置写入继续使用原子替换。
- 所有 v2 文件必须位于工具自己的 LocalAppData。
- 用户输入的源路径只能生成 SourceReadPath，不能生成 ToolOwnedPath。
- 配置文件即使被手工篡改，也不能把 v2 写入根目录改到 AppData 之外。

AppPaths 增加 objects、manifests、tmp、active manifest 和跨进程锁路径。测试覆盖旧配置迁移、上下限、非法枚举、密码字段拒绝和 AppData 边界。

### PB-01：只读 QueryId 执行门

允许新增：

- pc-sync-tool/src/ms2011_connection.py
- pc-sync-tool/src/ms2011_query_catalog.py
- pc-sync-tool/src/ms2011_schema.py
- 对应测试

接口不得暴露 execute(sql_text)、原始 connection 或 cursor。业务层只能调用：

    execute(QueryId, parameters)

QueryId 到 SQL 文本为内部固定映射。数据库名强制为 MS2011；服务器名允许配置但必须校验长度和字符。表名和列名只能来自 EV-02 证据生成的常量。

测试：

- 所有 QueryId 均为 SELECT。
- 未知 QueryId 拒绝。
- 外部输入不能进入表名、列名和排序表达式。
- 连接和 cursor 在所有异常路径关闭。
- ODBC access mode 请求只读；驱动不支持或拒绝时返回明确能力状态。
- 架构测试确认除唯一适配模块外没有生产代码直接导入 pyodbc。
- QueryId 目录不得包含 SELECT INTO、OPENROWSET、OPENDATASOURCE 或任何有副作用的扩展形式。

### PB-02：安全调度器、超时和熔断

允许新增：

- pc-sync-tool/src/live_sync_models.py
- pc-sync-tool/src/live_sync_coordinator.py
- pc-sync-tool/src/ms2011_activity_probe.py
- 对应测试

规则：

- 独立单线程协调器，同一进程最多一个同步任务。
- 自动检测默认 15 秒，范围 5 秒至 24 小时，0 为关闭。
- 安静窗口默认 10 秒。
- sysprocesses 探测是提示性能力；无权限时降级为短超时和双读，不得伪装成功。
- 连续超时、双读不一致或异常达到阈值后进入熔断，停止自动完整读取，只保留低成本探测和人工重试。
- 手动立即同步不绕过安全门，不无限排队。
- 取消只取消等待或尚未发布的任务。

状态必须包含 phase、reasonCode、耗时、连续失败数、熔断状态和下次允许时间。

### PB-03：确定性原始读取

前置：PB-01、PB-02  
允许新增：

- pc-sync-tool/src/ms2011_reader.py
- pc-sync-tool/src/ms2011_raw_models.py
- pc-sync-tool/tests/fixtures/ms2011_rows.py
- 对应测试

要求：

- 商品、分类、单位和候选促销表按 EV-02 确认的稳定复合排序键读取。
- Decimal 和 datetime 保持源语义，不提前变 double 或 UTC。
- 任一表失败则整轮失败，不产生部分快照。
- 双读分别完成规范化 hash；两次相同才继续。
- NOLOCK 读出的重复主键、缺失关系、异常行数必须被校验器捕获。
- 每张表记录行数、读取耗时和 QueryId，不记录业务明细。

### PB-04：变化探测和性能预算

允许新增：change_probe.py 及测试。

策略：

- 快速探测使用商品 COUNT、MAX(GUpdateTime)、MAX(GID) 和促销小表低成本摘要。
- 快速探测不能作为完整正确性证明。
- 周期性全量指纹捕获未更新 GUpdateTime 的变化，默认 15 分钟。
- 全量指纹本质上是读取任务，必须受熔断、超时和繁忙策略约束。
- 记录 p50、p95、最大耗时；不得用“通常很快”代替数据。

### PB-05：商品规范化与验证

允许新增：

- pc-sync-tool/src/snapshot_normalizer.py
- pc-sync-tool/src/snapshot_validator.py
- 对应测试

规则：

- 商品身份只使用 ms2011:<GID>。
- 条码变化不改变商品身份。
- 空单位保留 null；UI 再回退为 UN/件。
- stop_flag 原值保留，非零统一不可售，但不得丢失原值。
- Decimal 生成规范十进制文本。
- products 中的 simple_price/simple_threshold 只是源证据；是否生成 PRODUCT_SIMPLE 可执行规则由 PB-05P、PN-SIMPLE 和 evidenceHash 决定，商品规范化器不得仅凭字段非零把它标成 VERIFIED。
- 条码空值或重复、分类单位缺失、源主键重复必须有明确 severity。
- 是否拒绝整份快照由固定 severity matrix 决定，不由异常处理代码临时决定。

### PB-05P：促销候选原始证据提取

前置：EV-04、PB-03  
允许新增：

- pc-sync-tool/src/promotion_candidate_extractor.py
- pc-sync-tool/src/promotion_raw_models.py
- pc-sync-tool/tests/test_promotion_candidate_extractor.py
- pc-sync-tool/tests/fixtures/promotion_raw_rows.py

本任务不实现任何促销公式，只负责：

1. 按 EV-04 证据识别候选主记录。
2. 对 GHuiPrice + GHuiPriceCount 满足候选条件的商品生成 PRODUCT_SIMPLE candidate、商品映射和原始字段；在 PV-SIMPLE 通过前保持 UNVERIFIED。
3. 保留复杂促销的商品映射、组、顺序、日期、星期、时段和白名单参数。
4. 给每个原始行生成稳定 source key 和 mapping_id。
5. 把无法关联、重复键、未知表值和缺失明细写入 validation issues。
6. 未取得黑盒证据的候选统一为 UNVERIFIED。
7. 历史失效且能够明确判断的记录标为 INACTIVE。

禁止根据活动名称、字段名或常见零售经验生成 normalized rule。测试必须证明重复商品映射不会因错误唯一键被折叠。

### PB-06：SQLite v2 写入与自检

允许新增 sqlite_v2_writer.py 及测试。

要求：

- 每轮使用唯一临时文件，不共享 latest.tmp。
- 单事务写入；失败回滚并删除临时文件。
- 设置 user_version=2、foreign_keys=ON。
- 完成 integrity_check、foreign_key_check、必需表/列检查、计数复查。
- 关闭数据库后重新打开再验证。
- 写入完成前不得进入发布目录。

### PB-07：不可变快照和原子指针发布

允许新增：

- pc-sync-tool/src/v2_publisher.py
- pc-sync-tool/src/process_lock.py
- 修改 paths.py、manifest.py
- 对应测试

目录：

    snapshots-v2/
      objects/<snapshotId>.db
      manifests/<snapshotId>.json
      active-manifest.json
      tmp/<随机名>

发布顺序：

1. 临时数据库完整验证。
2. 计算 SHA-256。
3. 原子移动为不可变 objects/<snapshotId>.db。
4. 写入并验证版本 manifest。
5. 最后原子替换 active-manifest.json。
6. 清理只删除未被 active/pending/last good/下载引用的旧对象。

进程在任意一步崩溃时，旧 active manifest 必须仍能指向旧不可变文件。

必须增加 Windows 单实例或跨进程文件锁；现有 threading.RLock 只能继续承担进程内 HTTP/发布协调。发布、HTTP 打开文件和历史清理共享进程内 reader 引用计数；HTTP 已打开对象在请求完成前不得进入清理集合。Windows 删除失败只能记录并延后重试，不能强制关闭下载或递归清理。

所有写入和清理操作必须通过 ToolOwnedPath。历史清理不得接收 source path，不得递归删除目录，不得跟随重解析点；只删除已经从 active/pending/last good 引用集合中排除的内部 allowlist 普通文件。

### PB-08：HTTP v2

允许修改 http_server.py 和对应测试。

要求：

- 保留 v1 端点。
- 新增 /v2/manifest.json 和 /v2/snapshots/<snapshotId>.db。
- v2 优先支持 Authorization: Bearer <token>；为兼容可暂时接受 query token，但日志绝不记录查询字符串。
- manifest 的 downloadPath 永远固定到该 manifest 的 snapshotId；下载端点只读取该 ID 对应的不可变对象，不重新解析“当前 latest”。
- 非法 snapshotId、不是 active/pending/保留历史集合中的 ID 或对象不存在时返回 404；客户端不得把服务器路径转换为本地路径。
- 下载前复核大小和 hash。
- 设置 Content-Length、X-File-Sha256、X-Snapshot-Id、no-store。
- 限制并发下载和请求速率，避免目标电脑资源被异常客户端耗尽。
- 客户端中断后及时关闭文件和 socket。

### PB-09：后端编排与回归

允许修改 app.py、ui/controller.py 和测试。

完成标准：不启动 PySide6，使用 fake ODBC 完整跑通：

    两次读取
    → 规范化
    → SQLite
    → 不可变发布
    → manifest
    → 本机 HTTP 下载
    → hash 一致

G2 通过条件：旧 v1 全套测试继续通过；v2 商品快照不包含自动复杂促销公式。

## 10. G3：手机端精确金额、数量和 v2 商品快照

Android 路径约定：本节及 G4/G5/G6 中未写完整路径的 core 类位于 `android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core` 对应子包；app 同步类位于 `android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync`；AppServices 位于 `.../app/AppServices.java`；UI 类位于 `.../ui` 或 `.../ui/screens`。每个新增生产类必须在同模块测试目录有同包测试；Agent 不得自行新建第三个模块。

### MB-01A：Money/Discount 领域类型与兼容桥

前置：CT-01  
只允许修改 core/model/Money.java、Discount.java、DecimalValue.java 和对应 core 测试。

- Money 内部改为规范化 BigDecimal；Money.of(String)、Money.of(BigDecimal) 为新主入口，Money.of(long) 暂时保留。
- 暂时增加明确命名的 legacyLongValueExact() 兼容桥，只允许旧整数调用点使用；小数调用时必须抛出，不得舍入。
- 不提供 double 构造、double 返回值或隐式 Number 转换。
- Discount 的百分数保存为原始精确十进制百分数；源参数语义仍由 PV-X 决定。
- equals/hashCode 使用规范化数值，比较使用 compareTo。

完成标准：只修改领域类型和测试，项目允许暂时依靠兼容桥继续编译；不得在此任务迁移 UI、JSON 或 CSV。

### MB-01B：核心定价、找零与销售金额迁移

前置：MB-01A  
只允许修改 core/pricing、core/checkout、core/ledger 中金额调用点和对应测试。

- 所有加减乘、比较、折扣和找零使用 Money/BigDecimal API。
- 不调用 legacyLongValueExact()，不自行 setScale，不使用 double。
- Sale、SaleLine 和汇总模型保持原币单位，不转换成分。

### MB-01C：商品持久化、导入与导出兼容

前置：MB-01B  
只允许修改 ProductLocalStore、AndroidDbProductImporter、core/importer、core/exporter 和对应测试。

- 新 JSON 金额写规范十进制字符串；兼容读取旧整数 JSON，但不批量重写用户文件。
- CSV 明确写原币十进制文本；旧整数 CSV 仍可读取。
- 鸣盛导入不再 setScale(0)、Math.round 或经 double 回退。
- 发现旧数据单位无法确定时停止并生成迁移报告。

### MB-01D：金额 UI 输入和显示迁移

前置：MB-01C  
只允许新增 MoneyText、NumberTextParser，并修改 CashPaymentDialog、ProductFormScreen、CheckoutScreen、SalesScreen、DailySummaryScreen 及对应 UI 测试。

- 接受点和逗号输入，转换为规范 BigDecimal；显示保持原币单位。
- 页面不得 Long.parseLong、Double.parseDouble、直接拼接 amount() 或自行舍入。
- 输入必须执行 CT-01 位数、scale 和长度上限。

### MB-01E：移除金额兼容桥和架构回归

前置：MB-01D  
只允许修改 Money、剩余编译失败调用点和新增金额架构测试。

- 删除 legacyLongValueExact() 以及旧 amount() long 语义。
- 架构测试扫描生产 Java，禁止金额路径使用 Math.round、Double.parseDouble 和 long 金额持久化。
- 完整 core/app 编译和旧数据兼容测试通过后才允许开始数量迁移。

### MB-02A：Quantity 领域类型与兼容桥

前置：MB-01E  
只允许新增 core/model/Quantity.java 和对应测试，并对 CartLine 增加最小兼容入口。

- Quantity 内部使用规范 BigDecimal，执行 CT-01 的 QUANTITY 边界。
- 提供 one()、of(String)、of(BigDecimal)、add 和 isInteger。
- 旧整数构造暂时映射到 Quantity，不提供 double 入口。

### MB-02B：购物车和结账数量迁移

前置：MB-02A  
只允许修改 Cart、CartLine、CheckoutService、定价接口和对应 core 测试。

- 条码/搜索默认加入 Quantity.one()。
- 商品合并使用精确加法。
- 非整数判断使用 stripTrailingZeros().scale() > 0，不转换 double。
- 非整数数量排除全部自动促销，但仍允许正常价或带审计的手动价。

### MB-02C：销售、CSV 与数量持久化迁移

前置：MB-02B  
只允许修改 SaleLine、销售导出、商品/购物车相关持久化和对应测试。

- 新数据以规范十进制字符串保存，兼容读取旧整数数量。
- 不静默截断历史小数数量；非法值形成明确错误。

### MB-02D：数量 UI 与兼容入口清理

前置：MB-02C  
只允许新增 QuantityText，并修改 CheckoutScreen 中数量输入、商品搜索加入数量和对应 UI 测试。

- 手动数量允许 CT-01 范围内小数；默认数量仍为 1。
- 移除 int 数量兼容桥和所有数量 double 转换。
- 完整 core/app 回归通过后，数量迁移才算完成。

2026-07-18 完成记录：用户明确批准为满足本任务退出条件补充修改 `Quantity`、`Cart`、`CartLine`、`CheckoutService`、`SaleLine` 及受删除 API 影响的测试，同时冻结 `Money`、`DefaultPriceCalculator`、`NumberTextParser` 和 `ProductSearchResultDialog`。实现新增 `QuantityText`，支持点/逗号、CT-01 `11+4`、最长 32 字符、null fail-fast 和可执行 `applyIfValid` 提交合同；非法输入 callback 0 次并留在对话框，合法输入 callback 恰好 1 次。商品搜索与行数量编辑均使用该合同，默认数量为 `Quantity.one()`。9 个临时 transaction quantity int API 全部删除；生产桥扫描 0、UI narrowing 扫描 0。最终 core 13/13、app 6/6、Gradle Java 编译通过，三路后置复核 PASS，无 High/Medium finding。未执行设备交互或 `assembleDebug`，未连接真实 MS2011、未发布。MB-02D 完成，只解锁 MF-01；S07/G0B 状态不变。

### MB-03：v2 manifest 客户端安全校验

允许新增 ComputerSyncManifestV2 和修改 client/service。

要求：

- 严格使用 CT-02、CT-03 校验器。
- snapshotId、downloadPath、size、count、version、hash 任一非法立即拒绝。
- 下载前检查磁盘空间和最大尺寸。
- 下载流式写临时文件，不把整个 DB 放内存。
- 临时文件使用随机名并在失败、取消、页面销毁时清理。

2026-07-18 完成记录：MB-03 已完成 Android v2 manifest 与流式暂存安全入口。新增 `ComputerSyncManifestV2`，严格要求 15 字段、JSON 类型、CT-02/CT-03、256 KiB 原始 UTF-8 上限及 manifest 声明/实际长度一致；v2 HTTP 仅接受 200，使用 Bearer、禁止重定向和缓存，并严格核对 Content-Length、X-File-Sha256、X-Snapshot-Id、实际字节和流式 SHA-256。生产入口只接收 Android `Context`，在 app cache 固定子目录内部生成随机 `snapshot-v2-*.part`，不接受调用者提供的 File/目录；失败、线程中断、显式取消、二次 hash 取消均失败关闭并清理。测试覆盖缺失/额外字段、错误类型、非法 ID/路径/大小/计数/版本/hash、声明/实际 manifest 长度、截断、超长、同长度损坏、错误 headers、运行中取消和 service hash 取消。主协调器最终 Gradle core/app 编译为 `BUILD SUCCESSFUL in 9s`（21 tasks，3 executed、18 up-to-date），core main-based `13/13 PASS`、app `8/8 PASS`，AST 扫描 24 个 UI Java/24 个 synthetic 变体；最终 `assembleDebug` 为 `BUILD SUCCESSFUL in 9s`（34 tasks，3 executed、31 up-to-date）。APK 1,033,674 bytes，SHA-256 `445F51ED9E75C9E99FB5172177DA247465A1BBDF11260965FB60163EF252044C`，未复制或发布。最终安全审查与独立验证均 PASS，High 0 / Medium 0；保留 Android runtime `org.json` 与异常 cache/symlink instrumentation 两项 Low 设备证据缺口。MB-03 完成，只解锁 S08/MB-04；G0B、真实 MS2011 自动读取、IV-02 和生产发布继续锁定。

### MB-04：手机不可变快照仓库与崩溃恢复

允许新增：

- V2SnapshotStore
- V2SnapshotValidator
- V2SnapshotReader
- V2SnapshotStateStore
- 对应测试

目录：

    files/computer-sync-v2/objects/<snapshotId>.db
    files/computer-sync-v2/manifests/<snapshotId>.json
    files/computer-sync-v2/state.json
    files/computer-sync-v2/tmp/

每个数据库对象必须与通过 CT-03 校验的不可变 manifest 一起持久化。state.json 使用原子写，包含 activeSnapshotId、pendingSnapshotId、lastGoodSnapshotId、各 ID 对应的 sha256/sizeBytes/schemaVersion/versionCode/count 摘要以及连续失败信息。state 中的摘要必须与不可变 manifest 逐项一致；SharedPreferences 不保存完整商品或促销。

启动恢复顺序：

1. 读取 state。
2. 校验 active 对象和对应 manifest 均存在，重新计算对象 hash，并验证 state、manifest、SQLite metadata 三方一致。
3. active 无效时尝试 lastGood。
4. pending 不完整时删除，不自动启用。
5. 全部无效时保留本地商品并明确显示无有效电脑快照。

2026-07-18 完成记录：MB-04 已新增 `V2SnapshotStore`、`V2SnapshotValidator`、`V2SnapshotReader`、`V2SnapshotStateStore` 及 65 项 host smoke。不可变对象与 CT-03 manifest 成对持久化；`state.json` 使用同目录随机临时文件、flush/fsync 和原子移动，pending 在最终 pair move 前先持久化；恢复严格按 active → lastGood → none，pending 从不自动启用且清理拒绝 symlink、非普通文件和 active/lastGood 目标。SQLite 只以 `OPEN_READONLY` 打开，并按 CT-04 精确校验 12 张表有序列属性、完整外键、显式索引列顺序、UNIQUE/CHECK、`schemaVersion`/`snapshotId`/`sourceHash`、integrity/foreign-key、对象 hash/size 和六项计数。最终主协调器 Gradle `--rerun-tasks` 编译与 `assembleDebug` 为 38/38 tasks executed、`BUILD SUCCESSFUL in 12s`；core `13/13`、app `9/9`，合计 `22/22 PASS`，AST 24/24。五文件 SHA-256 为 Store `670274846DCE77206F92AF86FEFC2D96ED4ACF685431B1EC80B4E470AB5A1403`、Validator `70A7092B343AEC79AF900F2277A958CC7268046AD5FB69D3566D8C8013F3A7DB`、Reader `849B4532B351B4DF5D8815206CF532C825820072F60BC163039D110087432C6E`、StateStore `3F8A1427FFE95716278502C24CF7138C5FAA78E7CA70595C9486A118DCEA6FB8`、Smoke `D928F9E31F09C14AA68B7AFDEAEEAF7A11A9B1235DDDB90A57D91E83A54214BA`。APK 1,030,586 bytes，SHA-256 `44A7AF106FF65102593A1B9CB51825D0D25FA905E1079A24E646292FFBCA0D70`，未复制或发布。独立安全审查与独立功能/制品验证均 PASS，High/Medium 为 0；真实 Android SQLite/PRAGMA、symlink、原子移动、进程杀死恢复、安装和页面遍历仍为 Low 设备证据缺口。**MB-04 PASS，S08 PASS**；依赖上可解锁 S09/MB-05，但按用户最新指令停在开始前，不执行下一阶段。

### MB-05：商品来源、稳定身份和旧数据迁移

前置：MB-01E  
允许修改 Product、ProductLocalStore、ProductEditingService、catalog 和测试。

Product 增加：

- ProductOrigin：LOCAL、LEGACY_IMPORT、MS2011_SYNC。
- sourceProductKey。
- sourceSnapshotId。
- stopped。

迁移规则：

1. 已有 Product.id 与实时 GID 能被可靠证明对应时，转换为 MS2011_SYNC。
2. 条码匹配只能作为迁移证据，不能替代稳定身份。
3. 未匹配的 LEGACY_IMPORT 保持 LEGACY_IMPORT，不得自动变为 LOCAL。
4. 只有明确由手机创建的商品才是 LOCAL。
5. 当前元数据无法区分手工修改和自建商品时，生成迁移报告并要求人工选择，不静默猜测。
6. 同步商品与 LOCAL 条码冲突时，同步商品用于正常扫描；本地冲突商品进入可见冲突列表，不得静默丢失。

ProductLocalStore 的 products.json 和 metadata 必须改为临时文件加原子替换。

### MB-06：v2 商品读取与 catalog 合并

前置：MB-04、MB-05  
要求：

- SQLite reader 只负责映射，不直接修改全局 repository。
- 先在内存建立候选 catalog 并校验，再一次性替换。
- 停用同步商品保留但普通搜索隐藏；按条码查找返回“已停用”状态。
- category/unit 空值只在展示层回退。
- MS2011_SYNC 商品不得把 products.simple_price_decimal/simple_threshold_decimal 直接灌入旧 Product 自动促销字段；只有 promotion_rules 中 VERIFIED 的 SIMPLE_QUANTITY_PRICE 规则可以自动生效。LEGACY_IMPORT/LOCAL 的既有简单促销行为继续兼容。
- 重启后必须从 active v2 快照重建同步商品，不能依赖上次合并后的偶然内存状态。

2026-07-18 完成记录：MB-05 新增 `ProductOrigin/sourceProductKey/sourceSnapshotId/stopped`、旧数据迁移报告和纯后端人工选择合同；旧九参数商品保持 `LEGACY_IMPORT`，只有明确手机创建为 `LOCAL`，同步身份只能来自严格 `ms2011:<GID>`，同步商品拒绝手机编辑/删除。`products.json`、metadata 和 `product_migration_report.json` 均使用同目录随机临时文件、flush/fsync 和原子替换，失败清理且不损坏旧文件。MB-06 新增 verified immutable v2 商品只读映射、内存候选 catalog、显式 `FOUND/STOPPED/NOT_FOUND` 条码状态和 LOCAL 冲突列表；启动从 active/lastGood 重建，重复身份/同步条码/非法 snapshot 失败关闭，停用同步商品在普通搜索与旧结账路径均不可售，`simple_price_decimal/simple_threshold_decimal` 不注入旧自动促销。阶段复核另关闭一项 Medium：本地编辑持久化前只保留 `LOCAL/LEGACY_IMPORT`，`MS2011_SYNC` 不写入 `products.json`。最终 Gradle `--rerun-tasks` 编译与 `assembleDebug` 为 38/38 tasks executed、`BUILD SUCCESSFUL in 13s`；core `15/15`、app `11/11`，合计 `26/26 PASS`，Catalog 33、Origin 21、Migration 13、v2 reader 23、v2 store 65 assertions，AST 24/24。debug APK 为 1,059,398 bytes，SHA-256 `15E299DC44F9FA7475E5FFE678533AD1F8255461F728E22176FDB06477178F36`，ZIP 20 entries/14 dex，v2 签名通过，未复制或发布。独立验证对 MB-05、MB-06 和 S09 均为 PASS，High/Medium 0；真实 Android SQLite/文件系统/进程杀死、安装、页面遍历、LAN/SQL/生产身份仍未验证。**S09/L2 PASS**，只解锁按依赖核验 S10；当前按用户指令停在 S10 开始前。

### MB-07：手机前台同步协调器

前置：MB-03、MB-04、MB-06、PB-09  
允许新增或修改：

- android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncCoordinator.java
- ComputerSyncTrigger.java
- ComputerSyncState.java
- ComputerSyncStateListener.java
- ComputerSyncConfig.java
- ComputerSyncStore.java
- AppServices.java
- 对应 app 测试

规则：

- 使用单线程 ScheduledExecutorService。
- 默认 30 秒，0 为关闭，范围 5..86400。
- AtomicBoolean 或单线程队列防止并发检查和下载。
- 触发源固定为 APP_RESUME、ENTER_CHECKOUT、NEW_CART、INTERVAL、MANUAL。
- stopForeground 取消周期任务，但不破坏 active 快照。
- 单次失败递增计数；长期失败显示过期状态，不清除 last good。
- coordinator 不持有 Activity 或 Screen 的强引用。
- 所有 listener 在页面销毁时可注销，过期 generation 回调被忽略。

G3 通过条件：只同步商品时两端可完整工作；旧 v1、CSV、本地商品和现有结账回归通过。

## 11. G4：订单边界和快照生命周期

### CB-01：购物车绑定快照

允许修改 Cart、CheckoutService 和新增 PricingSnapshotRef。

Cart 创建时固定：

- pricingSnapshotId。
- 商品对象快照。
- 促销规则版本。

非空 Cart 不得因 repository 或 active snapshot 切换而变价。

### CB-02：active/pending 原子切换

允许新增 ActiveSnapshotManager 并修改 AppServices。

规则：

- 空购物车：验证完成后启用新快照。
- 非空购物车：仅 stage pending。
- 完成或取消订单后先启用 pending，再创建新 Cart。
- 旧对象在当前 Cart 不再引用前不能清理。
- 同一进程只允许一个切换操作。

### CB-03：生命周期、取消和恢复

覆盖：

- 下载中杀进程。
- 下载完成、写 state 前杀进程。
- pending 存在时杀进程。
- active 文件损坏。
- 当前购物车为空与非空两种恢复。

G4 通过条件：故障注入测试证明不会出现半启用快照和订单中途变价。

2026-07-19 阶段进度记录：MB-07 完成单线程/single-flight 前台协调、固定触发、pending-only 下载和失败保留；CB-01 固定购物车 `PricingSnapshotRef` 与完整商品 lookup；CB-02 以“验证候选 → 可回滚 catalog 应用 → 新 Cart → durable active”顺序完成 active/pending 切换；CB-03 以真实 durable store/reader 入口覆盖下载中断、state 提交前中断、pending 重启、active 损坏和空/非空购物车恢复。主协调器实际复跑 CB-02 编译 21/21 与直接测试 11/11，CB-03 编译 20/20 与 5/5、188 assertions；稳定簇独立复核逐项给出 MB-07、CB-01、CB-02、CB-03、G4 PASS，High/Medium 0、Low 1。MF-05 中止前已修改 `MainActivity.java` 并新增 `MainActivityLifecycleContractTest.java`，但尚未编译/验收，不得标记完成。MF-02 的“长期未更新/长期失败明显警告”缺少 snapshot age 和连续失败阈值，禁止自行猜测；MF-03 仍等待 MF-02。用户要求在此暂停，恢复时从 MF-05 两文件只读核对和验收开始；S10 完成后停止，不进入 S11。G0B 与 `WRITE_CAPABILITY_PRESENT` 不变。

## 12. G5：促销领域安全门

### PM-01：促销候选模型

允许新增 core/promotion 下模型，不实现具体公式。

模型必须包含：

- candidateId、type、verificationStatus。
- source keys、关联商品、组和顺序。
- schedule 原始值和已验证解释版本。
- ruleVersion、snapshotId。
- 原始十进制参数。
- NormalizedPromotionRule、tier、schedule、group 和 evidenceHash。

手机端 V2SnapshotReader 只允许从 promotion_rules、promotion_rule_tiers、promotion_rule_schedules、promotion_rule_groups 和 promotion_candidate_products 构造可执行规则。promotion_raw_rows 仅供诊断展示与审计，任何 Android 定价代码引用原始表名或原始字段名时架构测试必须失败。

### PM-02：影响范围与 severity matrix

固定规则：

- VERIFIED 且当前 schedule 命中：允许处理器计算。
- UNVERIFIED/INVALID/UNKNOWN_TYPE 且能明确关联当前商品：产生 blocking issue。
- 当前 schedule 明确不生效：不阻止。
- schedule 本身无法解释且商品有关联：阻止受影响商品。
- 无法定位任何商品的孤立异常：记录 PC 快照问题；是否拒绝整轮由 severity matrix 决定。

### PM-03：手动定价审计

允许修改 SaleLine、Sale、CSV 导出和结账模型。

手动绕过必须保存：

- snapshotId。
- candidateId 和 issue code。
- 原自动价格或未知状态。
- 手动单价。
- 操作者选择的固定原因代码。
- ART 时间和 UTC Instant。

不得只在 UI 显示一次后丢失。

### PM-04：整车促销引擎骨架

接口：

    evaluate(cart, promotionSnapshot, now)

返回：

- checkoutAllowed。
- 每行基础小计、促销分摊、最终小计。
- 应用促销 ID。
- blocking/warning issues。

组合促销必须在整车层计算，不能塞回单行 DefaultPriceCalculator。

G5 通过条件：未知促销不会静默结账；手动覆盖能够解除对应行阻止并留下销售证据。

## 13. G6：每种已应用促销的垂直切片

每种促销严格执行四个任务，不能跳步：

    PV-X 黑盒证据
      → PN-X 电脑规范化
      → PE-X 手机处理器
      → PI-X 端到端验收

### 13.1 简单数量促销

- PV-SIMPLE：确认 GHuiPrice、GHuiPriceCount、门槛、超过门槛、非整数数量和舍入。
- PN-SIMPLE：只把证据支持的字段规范化为 SIMPLE_QUANTITY_PRICE v1 的 NormalizedPromotionRule。
- PE-SIMPLE：实现 SimpleQuantityPriceHandler。
- PI-SIMPLE：用同一 fixture 比较 PC 规范化、Android 计算和鸣盛结果。

### 13.2 数量百分比促销

- PV-PERCENT：确认 CXTSALEKOU 的真实含义、阶梯选择、超过门槛、重复应用、优先级和舍入。
- PN-PERCENT：实现 QUANTITY_PERCENT v1。
- PE-PERCENT：实现 QuantityPercentHandler。
- PI-PERCENT：边界和冲突端到端。

### 13.3 指定数量固定总价

- PV-FIXED：确认门槛、多个阶梯、超过门槛、重复分组和余数。
- PN-FIXED：实现 QUANTITY_FIXED_TOTAL v1。
- PE-FIXED：实现 QuantityFixedTotalHandler。
- PI-FIXED：验证行分摊和订单总额。

### 13.4 多商品组合固定总价

- PV-MIX：确认任意组合、同商品多件、组选择、重复组合、余数和分摊。
- PN-MIX：实现 MIX_MATCH_FIXED_TOTAL v1，保留 mapping_id、group 和 order。
- PE-MIX：实现 MixMatchFixedTotalHandler。
- PI-MIX：验证组合选择稳定且与鸣盛一致。

### 13.5 时间、优先级和叠加

- PV-ORDER：确认日期端点、星期映射、时段端点、简单/复杂优先级、多个复杂促销是否叠加。
- PN-ORDER：冻结 schedule/priority ruleVersion。
- PE-ORDER：实现 PromotionSelectionPolicy。
- PI-ORDER：固定 now 和 America/Argentina/Buenos_Aires 测试。

每个 PV 产物必须记录：

- 源促销主键和类型。
- ART 测试时间。
- GID、条码和数量。
- 鸣盛显示的单价、优惠、行小计和总额。
- 是否会员或特殊价格类型。
- 截图或人工双人复核记录。
- 结论和仍不明确项。

没有完整 PV fixture，PN、PE、PI 不得开始。某一类型未通过不阻止已通过类型上线，但受该类型影响商品必须继续走安全门。

### 13.6 垂直切片文件所有权

PV-X 只允许修改：

- 修改方案/促销验证/<type>_results.md
- android-emergency-pos/core/src/test/resources/promotions/<type>.json
- pc-sync-tool/tests/fixtures/promotions/<type>.json

PN-X 只允许新增：

- pc-sync-tool/src/promotion_normalizers/<type>.py
- pc-sync-tool/tests/test_promotion_normalizer_<type>.py

并允许新增该类型的共享 JSON Schema 与规范化 fixture。PN-X 必须返回 CT-04 冻结的 NormalizedPromotionRule；不得修改 SQLite 表结构或自行写数据库。通用 sqlite_v2_writer 通过跨类型合同测试证明可以写入所有已注册规则、阶梯、时段和分组。

PN-X 不得修改共享注册表。所有准备上线的 PN-X 完成后，由独立任务 PN-REGISTRY 一次性修改：

- pc-sync-tool/src/promotion_normalizers/registry.py
- pc-sync-tool/tests/test_promotion_normalizer_registry.py

PE-X 只允许新增：

- android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/promotion/handlers/<Type>Handler.java
- 对应独立测试

PE-X 不得修改共享选择器。所有准备上线的 PE-X 完成后，由独立任务 PE-REGISTRY 一次性修改：

- PromotionHandlerRegistry.java
- PromotionSelectionPolicy.java
- 对应测试

PI-X 只允许修改集成 fixture 和集成测试，不修生产逻辑。发现差异时按 PN-X 或 PE-X 归属退回。

共享 fixture 必须包含 schemaVersion、ruleVersion 和 evidenceHash。evidenceHash 是黑盒结果文档的 SHA-256，用于证明处理器依据哪一版证据开发。

#### PN-REGISTRY：电脑端规则注册

前置：准备上线的 PN-X 全部通过  
只允许修改 promotion_normalizers/registry.py 和注册表测试。

注册表必须按固定 type + ruleVersion 查找，不允许“找不到就使用相似处理器”。未注册规则保持 UNVERIFIED。

#### PN-INTEGRATION：规范化规则进入 v2 快照

前置：PN-REGISTRY、准备上线的 PN-X 全部通过  
只允许修改 snapshot_normalizer.py、sqlite_v2_writer.py、live_sync_coordinator.py 和对应集成测试。

- 候选先经过 registry；只有返回完整 NormalizedPromotionRule 且 evidenceHash 与 PV fixture 一致时才能写 promotion_rules 及其子表。
- writer 使用 CT-04 已冻结的通用写入接口，不为具体促销类型拼接专用 SQL。
- 任一规范化规则失败时，该 candidate 降为 INVALID/UNVERIFIED 并形成 issue；不得生成半条 rule。
- 发布前复查 verifiedPromotionCount 等于 promotion_rules 行数，tier/schedule/group 外键完整。
- fake ODBC 全链路测试必须证明已注册规则能够从原始行进入 SQLite，再被 Android fixture reader 读取。

#### PE-REGISTRY：手机端处理器注册

前置：准备上线的 PE-X 和 PV-ORDER 全部通过  
只允许修改 PromotionHandlerRegistry、PromotionSelectionPolicy 和对应测试。

处理器选择必须确定性排序；同一输入重复运行产生相同选择和分摊。未知 type/ruleVersion 不得回退到默认公式。

## 14. G7：两端 UI

### PF-01：电脑数据源与只读连接

前置：PB-09  
只允许修改：

- pc-sync-tool/src/ui/main_window.py
- pc-sync-tool/tests/test_main_window.py

显示旧 AGT_MAIN 与 MS2011 实时模式、驱动位数、只读连接测试、当前安全能力。测试连接必须在 worker thread，不能阻塞 Qt 主线程。

### PF-02：电脑调度、熔断和状态

前置：PF-01  
只允许修改 main_window.py、connection_presentation.py 和对应 UI 测试；不得修改 controller 或协调器。

显示周期、立即同步、取消等待、阶段、耗时、连续失败、熔断、最近成功、快照 ID 和各计数。不得提供“强制忽略安全检查”。

### MF-01：手机金额与数量显示一致性验收

前置：MB-01E、MB-02D  
只允许修改 MoneyText、QuantityText、NumberTextParser、缺失的展示调用点和对应 UI 测试；核心输入迁移已由 MB-01D、MB-02D 完成，不得在本任务修改领域类型或持久化。

统一复查 Decimal parser/formatter；接受点和逗号输入，内部为规范 BigDecimal。测试扫描所有页面，证明不存在自行 Long.parseLong、Double.parseDouble 或直接拼接业务金额/数量的遗漏。

2026-07-18 完成记录：`MoneyText` 与 `QuantityText` 均 fail-fast 并只输出领域 canonical text；`QuantityText`、金额和百分比输入共用 `NumberTextParser.normalizeUnsignedDecimal`，继续接受点/逗号并拒绝混合分隔符、指数、符号和超出 CT-01 的值。`CheckoutScreen` 初始零金额改用 `MoneyText.currency(Money.ZERO)`，`SalesScreen` 交易数量改用 `QuantityText.format`。新增 `DecimalUiArchitectureTest`：Android unit-test wrapper 只在系统临时目录编译并执行内嵌 JDK AST/TypeMirror runner，按真实类型识别 Money/Quantity getter、参数、局部变量、嵌套 receiver、字符串转换 sink、静态通配导入和方法引用；compiler/attribution/清理失败均失败关闭。最终扫描 24 个 UI Java、24 个 synthetic 对抗变体，临时残留 0；core 13/13、app 7/7、静态禁止模式 0，parser/UI 架构/对抗性/范围复核均 PASS。完整 debug APK 构建成功，路径 `android-emergency-pos/app/build/outputs/apk/debug/app-debug.apk`，1,010,742 bytes，SHA-256 `C4A1018D3033C6A4BA8ED410DAB98D1CCC3F3FEF499B5644AC45EE3C039E26AC`；未复制、安装或发布。既有百分比折扣算术在某些 Money/percentage 组合下可能产生超过四位小数的闭包风险不是 MF-01 引入，也不能靠猜测输入精度或舍入修复，保留为后续明确产品合同下的风险；真实设备交互仍未验证。MF-01 完成，S07 PASS；按用户指令不开始 S08。

### MF-02：同步状态和数据过期

前置：MF-01、MB-07、CB-03  
允许修改 ImportScreen 和同步状态 presenter；不得修改 coordinator。

显示 active/pending、最后检查、最后同步、离线、连续失败、快照年龄。长期未更新为明显警告，单次失败不阻塞旧快照收银。

### MF-03：同步商品只读和冲突列表

前置：MF-02  
允许修改 ProductEditScreen、ProductFormScreen、ProductSearchResultDialog、CheckoutScreen 和对应测试。

MS2011_SYNC 显示“由电脑端管理”；后端和 UI 双重拒绝编辑。LOCAL 继续可编辑。条码冲突和停用商品必须有独立提示。

### MF-04：促销问题和手动定价

前置：MF-03、全部阶段上线的 PI-X  
允许修改 CheckoutScreen、SalesScreen 和对应 presenter/UI 测试；不得修改 PromotionEngine。

显示具体商品、问题代码和促销状态。只允许通过带原因代码的手动定价继续，不提供“忽略并按普通价结账”。

### MF-05：Activity 生命周期触发

前置：MB-07  
只允许修改 MainActivity.java 和对应测试。

规则：

- onResume 通知 coordinator.startForeground。
- onPause 通知 coordinator.stopForeground。
- 进入收银页触发 ENTER_CHECKOUT。
- 新建购物车触发 NEW_CART。
- Activity 不创建 executor、不下载、不解析 SQLite。

UI Agent 不得连接 SQL、解析 SQLite、计算 hash、切换快照或实现促销公式。

## 15. G8：集成、安全与性能验收

### IV-01：离线 fixture 全链路

不连接真实 SQL：

    raw fixture
    → reader
    → normalizer
    → SQLite
    → immutable publish
    → HTTP
    → Android validate/read
    → catalog
    → cart snapshot
    → promotion engine

计数以 fixture 和 manifest 一致为准，不硬编码历史生产数量。

### IV-02：真实电脑分级验收

顺序：

1. 只运行 EV-03 诊断查询。
2. 确认无阻塞后，运行一次商品手动快照。
3. 同时在鸣盛执行正常查询、扫码、开单和改价。
4. 观察超时、熔断、安静窗口和双读。
5. 再启用候选促销原始读取，但不启用手机公式。
6. 最后逐种启用已经通过 PI-X 的处理器。
7. 自动检测观察至少一个完整营业周期：优先为实际开门到关门的完整营业日；若营业日超过 8 小时，最低连续观察 8 小时，但必须覆盖扫码、开单、改价、取消订单、完成订单和鸣盛商品修改后的同步。

停止条件：

- 鸣盛出现可感知卡顿。
- SQL 等待或阻塞异常增加。
- 读取导致业务异常。
- 数据库、配置、服务状态发生非预期变化。

### IV-03：性能预算

必须记录：

- 快速探测 p50/p95/max。
- 完整双读 p50/p95/max。
- SQLite 生成和发布耗时。
- manifest 检查与手机下载耗时。
- Android 校验、读取、catalog 替换耗时。
- 内存峰值、CPU 峰值、快照大小和磁盘保留量。

初始通过预算如下；只有 EV/IV 实测证据、原因分析和用户批准三者齐全时才能调整，Agent 不得为了通过测试自行放宽：

- 快速探测：p95 不超过 2 秒，单次最大不超过 5 秒，查询超时上限 5 秒。
- 单次完整读取：任一 SQL 查询超时上限 10 秒；双读、规范化、SQLite 自检和发布合计 p95 不超过 30 秒，单次最大不超过 45 秒。
- 手机已经取得 manifest 后，下载、hash、SQLite 校验和候选 catalog 构建 p95 不超过 10 秒。
- 常规 GUpdateTime/促销摘要能够检测的变更，端到端 p95 不超过 60 秒。
- 未更新 GUpdateTime、只能由周期性全量指纹发现的变更，默认最坏检测延迟明确为 15 分钟加一次同步周期；UI 和验收报告不得把这类变化宣称为 60 秒内同步。
- 任意可感知鸣盛卡顿、SQL 阻塞异常增加或同步查询超过超时，直接失败并触发放弃/熔断，不能以总体平均值掩盖。

60 秒目标的测量起点为源修改已提交且数据库进入可读取状态，终点为手机新购物车可使用新 active 快照。非空旧购物车延迟切换不计为失败，但必须显示 pending。性能报告必须分别列出快速探测路径和全量指纹兜底路径，不能混为同一个“实时”指标。

### IV-04：安全回归

证明：

- 实际 SQL QueryId 全部为 SELECT。
- 生产连接身份的门禁状态为 READ_ONLY_PROVEN；WRITE_CAPABILITY_PRESENT 和 UNKNOWN 均拒绝实时模式。
- 工具没有创建登录、用户、角色或修改权限的代码路径。
- 除唯一数据库适配模块外，生产代码没有直接导入 pyodbc。
- 没有 MDF/LDF 文件操作。
- 没有鸣盛目录写入。
- 没有防火墙修改。
- Token 未进入日志。
- 路径穿越、超大 manifest、超大文件、错误 hash、错误计数被拒绝。
- 两个 PC 实例不能同时发布。
- 进程崩溃后仍能使用 last good snapshot。
- 配置篡改、路径穿越、符号链接、目录联接和重解析点不能把写入或删除引到 AppData 之外。
- 历史清理只删除工具自有普通文件，永远不删除来源文件。

## 16. G9：完整回归、打包和发布

电脑端：

    指定 PySide6 虚拟环境运行完整 unittest
    python -m compileall -q src tests
    执行 build_exe.ps1
    在目标电脑验证打包 EXE 的 pyodbc、位数和只读连接

Android：

    运行全部 core/app smoke tests
    执行 build-debug-apk.ps1
    真机验证商品、订单边界和已启用促销

只有所有目标门禁通过后才更新版本号。最终必须记录：

- EXE、ZIP、APK 路径。
- 文件大小和时间戳。
- SHA-256。
- ZIP 内关键文件和 pyodbc 依赖。
- 发布副本与构建输出 hash 一致。

### DOC-01：项目状态、日志和方案索引同步

前置：G8 结论已经形成，G9 构建结果和 hash 已取得。  
只允许修改：

- docs/PROJECT_STATUS.md
- docs/PROJECT_LOG.md
- 修改方案/README.md

PROJECT_STATUS 只写当前真实能力、未完成门禁和发布物；PROJECT_LOG 按时间追加任务、测试、真实电脑证据、阻塞与 hash；修改方案 README 更新本计划状态。仍有当前启用促销类型未通过 PI-X 时必须写“部分完成”，不得因商品实时同步可用而写“项目全部完成”。DOC-01 完成后再执行最终发布复制。

## 17. 文件所有权和并行规则

独占文件：

- pc-sync-tool/src/config.py：PB 配置任务独占。
- pc-sync-tool/src/ui/controller.py：PB-09 独占，PF 只调用。
- pc-sync-tool/src/ui/main_window.py：PF 串行。
- Money.java、Discount.java：MB-01A 后由 MB-01E 清理，A 至 E 严格串行。
- Cart.java、CartLine.java、CheckoutService.java：MB-02A 至 MB-02D 严格串行，完成后由 CB-01 接管。
- Product.java、ProductLocalStore.java：先由 MB-01C/MB-01E 完成金额迁移，再由 MB-05 接管；不得并行。
- AppServices.java：MB-06、MB-07、CB-02 严格按依赖表串行。
- promotion engine 核心选择器：PM-04、PE-ORDER 串行。
- main_window.py：PF-01 后执行 PF-02。
- CheckoutScreen、ProductFormScreen、SalesScreen：所有 MB 金额/数量任务结束后，MF-01 至 MF-04 严格串行。

允许并行示例：

- EV-03 ODBC 证据与 Android MB-01A 的测试设计可以并行，但 MB-01A 实现必须等 CT-01。
- 不同 PV 黑盒证据可并行收集。
- 不同促销处理器只有在共享选择策略未被同时修改时才能并行。

## 18. 每个任务的最小测试门槛

每个任务至少需要：

1. 新功能单元测试。
2. 直接相关模块和回归风险测试。
3. 必要的编译或 compileall。
4. 一个失败路径。
5. 一个取消、超时或损坏场景，若任务涉及 I/O。
6. 不触碰安全边界的证据。

每个 S 阶段或交付批次关闭前必须再运行该端全部已有测试和完整编译；S10、S15、S16、S17 按精简执行修订运行跨端/全链路验证。“测试通过”必须报告命令和通过数量。不得只写“已验证”，也不得用源码变化前的阶段结果代替当前结果。

### 18.1 标准验证命令

电脑端普通任务默认运行对应测试模块和 compileall；阶段门禁至少运行：

    & 'E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe' -m unittest discover -s tests -v
    & 'E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe' -m compileall -q src tests

工作目录必须为 E:\手机收银软件开发\pc-sync-tool。

电脑端打包任务再运行：

    powershell -ExecutionPolicy Bypass -File 'E:\手机收银软件开发\pc-sync-tool\scripts\build_exe.ps1'

Android 普通任务至少运行对应 core/app 定向测试和编译检查。阶段门禁运行全部 core/app smoke tests；实施计划明确要求 APK 证据的阶段以及最终打包任务再运行：

    powershell -ExecutionPolicy Bypass -File 'E:\手机收银软件开发\android-emergency-pos\scripts\build-debug-apk.ps1'

若因中文路径导致 Gradle 构建异常，允许使用既有 E:\AndroidEmergencyPos 构建镜像，但必须先同步本任务明确修改的文件，并在报告中比较源码和产物 hash；不得把镜像当作新的开发主目录。

真实 SQL、真实手机和真实收银电脑验证永远不是单元测试的默认步骤，只能在对应 EV/IV 任务中按安全顺序执行。

## 19. 通用 Subagent 提示词

    先阅读：
    1. 修改方案/ms2011_live_product_promotion_sync_plan.md
    2. 修改方案/ms2011_live_product_promotion_sync_implementation_plan.md

    你本次位于阶段 <STAGE-ID>，只执行任务编号 <TASK-ID>，负责端为 <LAYER>。
    先核对该阶段四端表；标为“无实施”的端禁止修改生产代码。
    严格遵守该任务的前置条件、允许修改文件和停止条件。
    先写测试，再做最小实现。
    不连接真实 MS2011 执行写操作，不复制 MDF/LDF，不修改鸣盛目录、
    SQL 服务、防火墙或其他层代码。
    不猜测促销公式、优先级、余数或舍入。

    完成后按第 6 节固定格式报告。
    如果证据、合同或前置任务缺失，停止并报告，不自行补假设。

## 20. 最终完成标准

只有同时满足以下条件才能宣布完成：

- 真实 SQL 结构、驱动位数和打包连接均有证据。
- 生产 SQL 身份已由工具外部预先配置并取得 READ_ONLY_PROVEN 证据；工具本身不改变任何权限。
- 所有 SQL 只能通过固定 SELECT QueryId 执行。
- 同步对鸣盛无可感知卡顿；异常时自动放弃或熔断。
- 金额和数量全链路为精确十进制。
- 商品、分类、单位和已应用复杂促销候选进入版本化 v2 快照。
- 只有通过黑盒证据的促销类型自动计算。
- EV-04 已证明当前启用促销类型清单完整，且清单内每种类型均通过对应 PI-X；否则整体状态只能是“部分完成”。
- 未验证和异常促销不被静默忽略。
- 非空购物车不会因同步中途变价。
- 快照发布和手机启用具备崩溃恢复。
- 所有写入和删除均被限制在工具自有 AppData；鸣盛受保护域没有任何写入、替换、移动、重命名或删除能力。
- 旧导入、本地商品、销售和日账无回归。
- 真实电脑、真机、自动化、性能和安全验收全部通过。
- 最新 EXE、ZIP、APK 已重新构建并完成 hash、时间戳和内容核验。
- docs/PROJECT_STATUS.md、docs/PROJECT_LOG.md 和修改方案/README.md 已按 DOC-01 同步。

任何一项缺失，只能标记“部分完成”或“阻塞”，不得写“全部支持”。
