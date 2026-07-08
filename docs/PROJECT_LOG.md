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
