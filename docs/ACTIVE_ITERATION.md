# Active Iteration

最后更新：2026-07-19（America/Argentina/Buenos_Aires）  
项目：MS2011 实时商品与已应用促销同步  
当前批次：L3/S10 实施  
当前阶段：S10 前台同步与订单快照边界  
当前任务：`MF-05 Activity 生命周期触发（部分修改未验收）；MF-02 stale 阈值待确认`  
状态：`USER_PAUSED`  
主协调器：当前主代理

## Current conclusion

- 用户已明确授权继续 S10；S09/L2 的人工停止点已解除。
- 本轮只读同步已完成：实际读取 `AGENTS.md`、本文件、实施计划 L3/S10 与 MB-07、CB-01～CB-03、MF-02、MF-03、MF-05 完整章节，以及产品方案中的同步、订单边界、状态、UI 与安全章节。
- **MB-07 PASS**：单线程/单飞前台协调器、固定五类触发、30 秒默认/0 关闭/5..86400、pending-only、失败保留 last-good、原始 manifest bytes 防御性保留、可注销 listener 和 AppServices 装配均已完成；未接 Activity/UI/Cart。
- **CB-01 PASS**：Cart 固定 `PricingSnapshotRef`、商品对象和规则版本；repository 替换后旧购物车不变；合法 LOCAL/同步条码冲突保持同步优先，停用同步商品继续阻断本地回退；空 active-v2 可保留显式 snapshotId；手工 almacén 不查询 catalog。
- 主协调器已核查实际文件、hash、冻结边界、编译、定向测试和静态扫描。按精简计划，两个任务标记 `TASK_PASS_PENDING_STAGE_SYNC`，S10/L3 全局文档在阶段门禁统一同步。
- **CB-02 PASS**：active/pending 切换采用“验证候选 → 可回滚 catalog 应用 → 新 Cart → 持久化 active”的提交顺序；失败保持原 active/catalog/cart；空购物车可切换，非空购物车只保留 pending，订单完成/取消后创建绑定新快照的新 Cart；无 pending 且身份一致时保持原 Cart 与零 I/O；生产代码未调用 `activateForTest`。
- **CB-03 PASS**：仅新增 durable 生命周期故障注入测试；覆盖中断下载、下载完成但状态未提交、pending 重启、active 损坏、空/非空购物车与完成/取消订单边界；没有修改生产文件或使用 `activateForTest`。
- **稳定簇独立复核 PASS**：MB-07、CB-01、CB-02、CB-03 和主机侧离线 G4 均 PASS；High/Medium 0。唯一 Low 为 coordinator smoke 未等待非零周期真实到期，不阻塞阶段继续。
- MF-05 已由 MB-07 解锁；MF-02 已满足代码前置，但“长期未更新/长期失败”的警告阈值在产品方案和实施计划中均未冻结，且已批准合同禁止自行发明阈值，因此 MF-02 在写前等待最小产品决定；MF-03 必须等待 MF-02。
- 用户于 2026-07-19 明确要求暂停并更新文档；正在运行的 MF-05 写代理已中止。中止前已修改 `MainActivity.java` 并新增 `MainActivityLifecycleContractTest.java`，但代理未返回最终报告，主协调器未执行编译、测试或验收，因此 MF-05 只能标记为“部分修改、未验证”，不得视为 PASS。
- G0B 继续锁定；目标身份仍为 `WRITE_CAPABILITY_PRESENT`。禁止真实 MS2011/SQL、手机直连 SQL Server、自动实时读取、数据库写入、真实 LAN/设备操作和生产发布。
- 本次用户明确要求把当前更新上传 GitHub；本次仅同步源码、测试、方案和状态文档到发布副本，不构建或发布 S17/G9 制品，不解除实现阶段暂停或 G0B。

## Approved contract decisions

1. **MB-07 原始 manifest 字节边界**：批准最小范围扩展，仅允许 `ComputerSyncManifestV2.fromUtf8Json` 保存原始 UTF-8 bytes 的防御性副本并以防御性副本暴露给同包 coordinator；不得改变协议、查询、下载或 active/pending 语义。
2. **CB-01 购物车快照身份与查询语义**：批准购物车创建时捕获完整商品 lookup；active-v2 使用其 snapshotId；无 active-v2 使用 `local-library`；S11 前促销规则版本使用 `none`；手工 almacén 是购物车内临时商品且不查询 catalog。
3. **MB-07 stale 边界**：本任务只持久化 consecutiveFailures、lastCheck、lastSuccess、snapshotId，不自行发明长期失败/过期阈值；可见 stale 判定留给已有明确阈值的后续 UI 合同。
4. **空 active-v2 快照**：v2 合同允许 0 个商品，禁止通过商品列表推断 active snapshotId。CB-01 提供显式 `startCart(PricingSnapshotRef)`；CB-02 后续由 `ActiveSnapshotManager` 传入真实 active snapshotId 和完整 lookup。无参 `startCart()` 仅保留旧调用的 `local-library`/`none` 兼容语义，不代表 active-v2。

## Current file boundary

- MF-05 只允许修改 `android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java` 和对应测试；只发送 `startForeground/stopForeground`，不得修改 coordinator、AppServices、其他 UI 或后端。
- 当前已存在的 MF-05 部分修改正好位于上述两文件；暂停期间不得继续修改。恢复时先只读核对实际内容和 hashes，再决定直接验收或在同一任务内修正。
- MF-02 后续只允许修改 `ImportScreen`、新增/修改同步状态 presenter 与对应直接测试；不得修改 coordinator。阈值未获决定前禁止写入 MF-02。
- 禁止修改 PC、SQL、Gradle/依赖、`dist`、`mobile_pos_publish` 和全局状态文档（主协调器除外）；禁止真实 Android/LAN/MS2011/发布。

## Last validation

- CB-02 主 Gradle：`--no-daemon --rerun-tasks :core:compileJava :core:compileTestJava :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac`，`BUILD SUCCESSFUL in 11s`，21/21 executed。
- CB-02 主定向测试：core 4/4、app 7/7，合计 11/11 PASS；Catalog 39、Cart snapshot 25、ActiveSnapshotManager 39、v2 store 71、v2 reader 23、coordinator 27 assertions。
- 静态扫描：`ActiveSnapshotManager` 禁止模式 0；生产代码 `activateForTest(` 引用 0；store 保持先验证后写状态；`mobile_pos_publish` 未触碰。
- CB-02 关键 SHA-256：AppServices `7A71B8A8E214AF49FEB336D0F3060C945AA4056E8C51F845E95F2F7B6CAB4487`；ActiveSnapshotManager `0C82456B2FE354C6A94574BEDC7E218E904A8015933B778AE387D31CE7CAA463`；V2SnapshotStore `30C8170D1E4DEEDCA6E39EAD68EC215DA3BE1E5816EC824090D53B83428F6491`；V2ProductSnapshotReader `37D22D26F131EC43A21E3DE4D186F9269C4E03D4CABBC57AD3277DE9BB820567`；ProductCatalogService `AFB153A61BBF85F30263D60EFBDF976DC71B32687DD41BCD6DBB3B0E9E03B818`。
- MB-07 Coordinator、CB-01 Cart/CheckoutService/PricingSnapshotRef 及其余冻结文件 hashes 与前一通过点一致。
- CB-03 主 Gradle：`:app:compileDebugUnitTestJavaWithJavac --rerun-tasks`，`BUILD SUCCESSFUL in 11s`，20/20 executed。主定向测试 5/5 PASS、188 assertions：CB-03 28、store 71、product reader 23、manager 39、coordinator 27。
- CB-03 新测试 SHA-256：`3D90BF66F54BAF92CC2A4E09434601225A636B08A319160B7562271F6AB5536B`。`activateForTest` 命中 0；SQL/网络/外部系统命中 0（唯一宽泛 `DELETE` 文本是临时测试目录 `File.delete()` 清理）。
- 独立复核 verdict：MB-07 PASS、CB-01 PASS、CB-02 PASS、CB-03 PASS、G4 PASS；High 0、Medium 0、Low 1。明确保留真实 Android kill/restart、SQLite/文件系统、Activity 生命周期、真机 UI、真实 LAN/MS2011 未验证。
- MF-05 暂停现场：`MainActivity.java` SHA-256 `020843C5D7F1769F52107947E5FB0D6A7701D5A0049AA3F1C2F538CF8CD56AC6`；新增 `MainActivityLifecycleContractTest.java` SHA-256 `6F31DBEBD69A55B5654772E435D62E39633A88ABCBC090ED96D3A6D10712989F`。现场可见 `onResume` 先 `super.onResume()` 后 `startForeground()`，`onPause` 先 `stopForeground()` 后 `super.onPause()`；本轮暂停后测试/编译为 `0/0`，未验收。

## Next exact action

1. 等待用户明确恢复授权；恢复后先只读核对 MF-05 两个部分修改文件及其 hashes，不得跳过到 MF-02。
2. 主协调器编译并运行 MF-05 直接测试、相关回归和静态扫描；只有实际通过后才标记 MF-05 PASS。
3. 再向用户确认 MF-02 的长期未更新/长期失败警告阈值；决定后实施 MF-02，再串行 MF-03。
4. 完成 MF 后执行 S10 全量 Android 测试、跨端离线验证、APK 构建和全局文档同步；S10 结束后停止，禁止进入 S11。

## Acceptance and rollback

- MF-05 验收：Activity 只在 `onResume`/`onPause` 发送前台开始/停止，不持有或复制同步状态，不改变当前 Cart，不引入额外 Activity/UI 所有权。
- MF-02 阻塞项：需要明确“明显过期警告”的 snapshot age 阈值，以及连续失败达到多少次进入明显警告；单次失败必须仍为非阻塞状态。
- 当前稳定回退点仍为 MB-07、CB-01、CB-02 的通过 hashes，加 CB-03 唯一新增测试 hash `3D90BF66F54BAF92CC2A4E09434601225A636B08A319160B7562271F6AB5536B`。MF-05 两个部分修改文件属于未验收工作，不得自动删除或回滚；如需回退，只能在确认其精确 pre-write 证据后人工处理。禁止 Git reset/checkout、清理或格式化未知修改。
