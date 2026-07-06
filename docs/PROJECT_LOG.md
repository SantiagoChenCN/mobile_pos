# 项目日志

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
