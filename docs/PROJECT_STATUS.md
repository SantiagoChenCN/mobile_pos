# 手机应急收银 App 项目进度

更新日期：2026-07-06

## 当前目标

为阿根廷华人超市制作一套 Android 10+ 原生应急收银 App。它在停电、电脑收银主机不可用、或临时需要手机查价时使用。App 离线运行，不直接修改电脑收银系统数据库，通过定期导入鸣盛 / ESpsa 商品数据库更新商品资料。

## 本地主要目录

- 工作根目录：`E:\手机收银软件开发`
- Android 源码：`E:\手机收银软件开发\android-emergency-pos`
- 英文构建副本：`E:\AndroidEmergencyPos`
- 便携构建环境：`E:\AndroidBuildEnv`
- 最新鸣盛商品数据库样本：`E:\手机收银软件开发\AGT_MAIN_20260705.db`
- 最新 APK：`E:\AndroidEmergencyPos\app\build\outputs\apk\debug\app-debug.apk`

## 远程与发布位置

- GitHub 仓库：https://github.com/SantiagoChenCN/mobile_pos
- GitHub 最新提交：`f115d33 Initial mobile POS app`
- Google Drive APK：https://drive.google.com/file/d/1ohzXrAsUOfK3cicSmrIKpilWcjryflE7/view?usp=drivesdk
- GitHub 仓库内 APK：`dist/EmergencyPOS-debug.apk`

## 已实现

- 原生 Android Java 项目结构，包含 `app` 和 `core` 两个模块。
- Android 10+ 目标环境。
- 中西双语切换。
- 状态栏安全边距处理，避免手机状态栏遮挡 App 顶部。
- 设置页通过 Android 文件选择器导入鸣盛 / ESpsa SQLite `.db` 商品库。
- 已识别并读取 `CJQ_GOODLIST` 商品表。
- 商品字段映射包含条码、名称、分类、单位、售价、优惠价、起购数量。
- 导入后的商品会保存到手机本地 `products.json`，App 重启后可继续使用商品库。
- 条码手动输入添加商品。
- 手机相机扫码，基于 ZXing，支持常见零售条码格式。
- 未找到商品时弹窗提示，可取消返回。
- 可手动输入价格，按 `almacen` 分类加入购物车。
- 商品名称关键词搜索。
- 搜索已改为返回所有匹配商品，不再限制 10 条。
- 搜索支持大小写无关、重音符号无关、多关键词乱序、忽略 `de/del/el/la/los/las` 等常见西语连接词。
- 搜索结果弹窗可滚动，并显示匹配数量。
- 购物车支持改数量、删行、手动改价。
- 单行商品支持百分比优惠和固定金额优惠，并可撤回手动改价/优惠。
- 整单支持百分比优惠和固定金额优惠，并可清除。
- 自动优惠支持当前数据库中可读的优惠价和起购数量规则。
- 支付方式包含现金、Mercado Pago、借记卡、信用卡、transferencia。
- 可保存销售单，并在当前 App 会话内查看交易明细。
- 可查看当天总账，按支付方式汇总。
- 可作废当前会话内的销售单。
- core 层已有 CSV 销售导出适配器。
- 已生成可直接安装的 debug APK。
- 已上传源码、文档和 APK 到 GitHub。

## 已验证

- `CoreSmokeTest` 通过。
- APK debug 构建成功。
- 真实商品数据库中确认 `huevo`、`huevo blanco`、`maple` 等关键词存在匹配数据。
- 搜索回归测试覆盖：
  - `huevo` 返回全部匹配测试商品。
  - `maple huevo` 可乱序匹配。
  - `maple de huevo` 可忽略连接词匹配 `Huevo Blanco Maple`。

## 尚未完成

- 销售记录持久化：当前销售单保存在内存里，App 被系统杀掉或重启后会丢失。需要实现本地销售存储。
- 按日期导出 Excel/CSV 的 Android UI：core 已有 CSV 导出适配器，但手机界面还没有完整导出入口。
- `.xlsx` 商品导入：设计文档中保留了 Excel 备用导入路径，但当前 App 只接入 `.db` 导入。
- 商品库版本记录和回退上一版：设计中有，当前只保存最新导入商品。
- 交易明细页目前是当前会话内明细，后续应接入持久化销售仓库。
- 正式 release APK 签名：当前产物是 debug APK。

## 重要技术边界

- 不上传、不提交真实经营数据库、商品导出表、ESpsa 分析目录。
- UI 不直接读取鸣盛原始表，导入逻辑集中在 importer / Android adapter。
- 价格和优惠计算集中在 `core.pricing`。
- 销售单应保存交易发生时的商品、价格、数量、折扣、支付方式快照。
- 后续优先补齐本地销售持久化，再做导出 UI。

## 建议下一步

1. 实现本地销售持久化，建议先用 SQLite 或 JSONL，保证每笔销售重启后仍可查看。
2. 把 `SalesScreen` 和 `DailySummaryScreen` 改为读取持久化销售仓库。
3. 接入按日期导出 CSV 的手机界面。
4. 再考虑 `.xlsx` 商品导入和商品库版本回退。
