# 一级界面卡片式 UI 与多格式导入开发方案

更新时间：2026-07-08  
适用项目：`android-emergency-pos`  
目标读者：前端 agent、后端 agent、验收 agent

## 1. 本轮目标

本轮继续迭代两个方向：

1. UI 美化：一级界面改为卡片式设计，不再是简单按钮陈列。
2. 多格式导入：导入页支持选择导入格式，第一版先支持鸣盛 `.db` 和通用 `.csv`。

本轮不做：

- Excel `.xlsx` 的完整导入。
- 所有收银软件私有格式的一次性全覆盖。
- Android 全设备强制扫描文件。
- 多设备同步、打印、税务票据。

Excel 和其他收银软件格式后续通过 adapter 继续扩展。

## 2. 架构边界

本项目是原生 Android Java 项目，不是 Web 项目。这里的“前后端分离”按以下方式执行：

- 后端/业务层：`core` 模块，以及 `app` 中和导入服务、文件解析、数据保存相关的 service/adapter。
- 前端/UI 层：`app/src/main/java/com/espsa/mobilepos/ui` 和 `ui/screens` 下的页面、卡片、弹窗、文件选择入口。

开发必须保持模块化和函数拆分：

- UI 卡片样式集中在 `Views`、`StyleGuide` 或 `ui/components`，不要每个页面复制一套 padding/颜色/字号。
- 导入格式识别和解析放在 adapter/registry 中，不要写进 `ImportScreen` 或 `MainActivity`。
- `MainActivity` 只负责文件选择结果分发，不负责解析数据库或 CSV。
- core 只处理格式、字段映射、导入结果，不依赖 Android。
- app 层只处理 Android `Uri`、文件选择器、ContentResolver。
- 每个新增类职责单一，每个方法只做一件事。

## 3. 重要要求

### 3.1 前端 agent 必须调用 frontend-design skill

前端 agent 在开始 UI 美化前，必须调用 `frontend-design` skill。

前端设计方向：

- 这是收银工具，不是营销 landing page。
- 一级页面要更像“操作工作台”：清晰、稳定、能快速扫视。
- 允许卡片化，但不要过度装饰。
- 不要使用单一紫色/深蓝/米色主题。
- 不要用大 hero、渐变大背景、装饰光斑。
- 所有控件仍然要适合手机竖屏。

### 3.2 第一版导入格式范围

第一版只实现：

- 鸣盛 SQLite 数据库：`.db`
- 通用 CSV 商品表：`.csv`

第二阶段再考虑：

- `.xlsx`
- 其他收银软件专用数据库格式
- 更复杂的字段映射配置

这样可以避免一次性范围过大，也能先建立可扩展导入架构。

## 4. 推荐开发顺序

建议按以下顺序执行：

1. 后端 agent：定义导入格式模型和统一 adapter 接口。
2. 后端 agent：把现有鸣盛 `.db` 导入包装进新架构，保证功能不回退。
3. 后端 agent：新增通用 CSV 导入 adapter 和 core 测试。
4. 前端 agent：修改 `ImportGateway` 和 `MainActivity`，支持按格式发起文件选择。
5. 前端 agent：导入页改为格式卡片选择。
6. 前端 agent：一级界面卡片式 UI 美化。
7. 验收 agent：跑 core 烟测、APK 构建、手工验收 DB/CSV 导入和一级页面 UI。

后端导入架构和前端 UI 卡片可以并行，但 `ImportGateway` 变更需要前后端对齐。

## 5. 后端开发任务

### 5.1 新增 ImportFormat

建议新增文件：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/ImportFormat.java
```

职责：

- 表达支持的导入格式。
- 提供格式 id、显示名、扩展名、说明。

推荐第一版枚举：

```java
public enum ImportFormat {
    MINGSHENG_DB,
    GENERIC_CSV
}
```

建议提供方法：

```java
public String id();
public String[] extensions();
public boolean acceptsFileName(String fileName);
```

注意：

- `ImportFormat` 不依赖 Android。
- 不要把 UI 文案硬塞进 core，显示文案可由 UI 层根据枚举处理。

### 5.2 新增统一导入 adapter 接口

建议新增文件：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/ProductImportAdapter.java
```

推荐接口：

```java
public interface ProductImportAdapter {
    ImportFormat format();
    ProductImportResult importProducts(InputStream inputStream, String sourceFileName) throws ProductImportException;
}
```

职责：

- 每种格式一个 adapter。
- adapter 输出统一的 `ProductImportResult`。
- adapter 内部负责字段映射、格式校验、错误信息。

### 5.3 新增 ImportFormatRegistry

建议新增文件：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/ImportFormatRegistry.java
```

职责：

- 保存已支持的 adapter。
- 按 `ImportFormat` 获取对应 adapter。
- 后续新增格式时只注册新 adapter。

推荐方法：

```java
public ProductImportAdapter adapterFor(ImportFormat format);
public List<ImportFormat> supportedFormats();
```

### 5.4 迁移鸣盛 DB 导入

现状：

- `AndroidDbProductImporter` 位于 app 层。
- 使用 Android `SQLiteDatabase` 和 `MingshengProductMapper`。
- `AppServices.importMingshengDatabase(...)` 固定调用该导入器。

第一版建议：

- 先保留 `AndroidDbProductImporter` 在 app 层，因为 Android `SQLiteDatabase` 依赖 Android。
- 新增 app 层 adapter 包装它，例如：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/importer/AndroidMingshengDbImportAdapter.java
```

职责：

- 实现 app 层统一导入接口，或由 `AppServices` 根据 `ImportFormat.MINGSHENG_DB` 调用。
- 保持现有鸣盛 `.db` 导入行为不变。

注意：

- 不要破坏现有 `MingshengProductMapper`。
- 不要改变鸣盛字段映射规则。
- 不要改变商品库快照、回滚、本地保存逻辑。

### 5.5 新增通用 CSV 导入

建议新增文件：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/CsvProductImportAdapter.java
```

职责：

- 从 CSV 读取商品。
- 映射到内部 `Product`。
- 输出 `ProductImportResult`。

第一版支持字段别名：

条码：

```text
barcode, code, codigo, sku, 条码, 商品条码
```

名称：

```text
name, product_name, nombre, descripcion, 商品名, 商品名称
```

售价：

```text
price, sale_price, precio, precio_venta, 售价, 价格
```

分类：

```text
category, categoria, rubro, 分类
```

单位：

```text
unit, unidad, uom, 单位
```

第一版规则：

- 必填：条码、名称、售价。
- 可选：分类、单位。
- 分类为空时默认 `almacen`。
- 单位为空时默认 `un`。
- 价格必须能解析为正整数。
- 重复条码时保留第一条，后续加入 warnings。
- 空行跳过。
- 缺少必填字段时加入 warnings；如果整份文件没有有效商品，则抛出 `ProductImportException`。

注意：

- CSV 解析尽量用小型函数拆分：
  - `readHeader`
  - `resolveColumns`
  - `parseRow`
  - `parseMoney`
  - `normalizeHeader`
- 不要用脆弱的大段字符串拼接逻辑。
- 如果没有可用 CSV 库，第一版至少要正确处理逗号、引号、换行内引号的常见情况；如果做不到完整 RFC，文档中要明确限制。

### 5.6 AppServices 统一导入入口

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/AppServices.java
```

建议新增方法：

```java
public ProductImportResult importProducts(Context context, Uri uri, ImportFormat format) throws ProductImportException;
```

职责：

- 根据 `format` 选择 adapter。
- 导入成功后复用现有：
  - `catalog.applyImport(result)`
  - `productLocalStore.saveImportedProducts(...)`
  - 快照记录
  - 本地修改状态重置

注意：

- 旧方法 `importMingshengDatabase(...)` 可以保留为兼容包装，但 UI 新流程应走 `importProducts(...)`。
- 不要让 `MainActivity` 直接调用具体 adapter。

## 6. 前端开发任务

### 6.1 ImportGateway 支持格式参数

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/ImportGateway.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
```

建议接口：

```java
void requestImportFile(ImportFormat format);
```

`MainActivity` 增加字段：

```java
private ImportFormat pendingImportFormat;
```

流程：

1. 用户在导入页选择格式。
2. `ImportScreen` 调用 `importGateway.requestImportFile(format)`。
3. `MainActivity` 保存 `pendingImportFormat`。
4. 根据格式打开系统文件选择器。
5. `onActivityResult` 收到 uri 后调用 `services.importProducts(this, uri, pendingImportFormat)`。

### 6.2 按格式过滤文件选择器

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
```

建议：

鸣盛 `.db`：

```java
intent.setType("*/*");
intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
    "application/octet-stream",
    "application/vnd.sqlite3",
    "application/x-sqlite3"
});
```

CSV：

```java
intent.setType("text/*");
intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
    "text/csv",
    "text/comma-separated-values",
    "text/plain"
});
```

注意：

- Android 系统文件选择器无法保证绝对只显示目标扩展名。
- 所以导入前仍要校验文件名/格式。
- 如果用户取消选择，提示“未选择文件”，不要当作错误导入。
- “没有匹配文件”通常由系统文件选择器空列表体现；app 不强扫全设备。

### 6.3 导入页改为格式选择卡片

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ImportScreen.java
```

设计要求：

- 前端 agent 必须先调用 `frontend-design` skill。
- 页面从“一个导入按钮”改为“导入格式卡片列表”。
- 每个卡片包含：
  - 格式名称
  - 支持扩展名
  - 适用说明
  - 导入按钮

第一版卡片：

1. 鸣盛数据库
   - `.db`
   - 说明：适合从鸣盛收银软件导出的商品库。
2. 通用 CSV 商品表
   - `.csv`
   - 说明：适合包含条码、名称、售价字段的商品表。

导入成功结果：

- 商品数
- 促销数
- 警告数
- 来源文件名

导入失败结果：

- 格式不支持
- 文件读取失败
- 缺少必要字段
- 没有有效商品

### 6.4 一级界面卡片式 UI

重点文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/HomeScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ImportScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/SettingsScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutSectionScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/Views.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/StyleGuide.java
```

建议新增：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/components/ActionCard.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/components/InfoCard.java
```

也可以先放在 `Views` 中，但如果方法太多，应拆到 `ui/components`。

卡片设计要求：

- 卡片圆角不超过 8dp。
- 每张卡片有明确主操作。
- 文本层级清楚：标题、说明、状态/数字、按钮。
- 手机竖屏下卡片单列排列。
- 不要卡片套卡片。
- 不要把页面做成营销 hero。
- 不要用装饰光斑、渐变大背景。
- 颜色保持收银工具风格：清晰、稳定、耐看。

首页建议卡片：

- 收银：扫码、搜索、现金找零。
- 商品编辑：维护条码、名称、售价、促销。
- 导入商品库：鸣盛 DB、CSV。
- 每日总账：查看今日销售、作废、付款汇总。
- 设置：语言、字体大小、离线模式。

导入页建议卡片：

- 当前商品库状态卡片。
- 导入格式卡片。
- 最近导入快照卡片。

设置页建议卡片：

- 基础信息卡片。
- 字体大小卡片。
- 语言卡片。

CheckoutSectionScreen 建议：

- 保留收银/交易明细两个 tab。
- tab 可以改为更清晰的 segmented card 样式。
- 不改变收银业务流程。

## 7. 多 agent 提示词

### 7.1 前端 agent 提示词

```text
你负责 android-emergency-pos 的前端/UI 层开发。

开始 UI 美化前必须调用 frontend-design skill。

任务：
1. 一级页面改为卡片式设计，不再简单按钮陈列。
2. 导入页改为格式选择卡片。
3. ImportGateway 支持传入 ImportFormat。
4. MainActivity 根据格式打开对应文件选择器。
5. 导入成功/失败提示要清晰。

必须保持模块化和函数拆分：
- 卡片样式集中在 Views/StyleGuide 或 ui/components。
- 不要在每个 Screen 里复制 padding、颜色、字号。
- MainActivity 只负责文件选择和结果分发，不解析文件。
- ImportScreen 只负责展示格式和触发导入，不解析文件。
- 不要改变收银、商品编辑、搜索、现金找零业务逻辑。

第一版导入格式只接入鸣盛 .db 和通用 .csv。
完成后运行 debug APK 构建。
```

### 7.2 后端 agent 提示词

```text
你负责 android-emergency-pos 的后端/导入业务开发。

任务：
1. 新增 ImportFormat。
2. 新增统一 ProductImportAdapter 接口。
3. 新增 ImportFormatRegistry 或等价的 adapter 分发机制。
4. 保持鸣盛 .db 导入能力不回退。
5. 新增通用 CSV 商品导入 adapter。
6. AppServices 提供 importProducts(Context, Uri, ImportFormat) 统一入口。
7. CoreSmokeTest 增加 CSV 导入测试。

必须保持模块化和函数拆分：
- 不要把 CSV 解析塞进 AppServices。
- 不要把导入格式判断写进 UI 页面。
- 鸣盛字段映射继续由 MingshengProductMapper 负责。
- CSV 字段解析拆成 header 解析、字段映射、行解析、金额解析等小函数。
- 不要修改 Sale、CheckoutService、商品编辑保存逻辑。

第一版只实现鸣盛 .db 和通用 .csv。
完成后运行 core/scripts/compile-and-test.ps1。
```

### 7.3 验收 agent 提示词

```text
你负责验收本轮迭代，不做实现。

请检查：
- 前端是否调用并遵循 frontend-design skill 的 UI 方向。
- 首页是否已改为卡片式一级导航。
- 导入页是否可以选择鸣盛 .db 和通用 .csv。
- 选择 .db 时是否走数据库文件选择流程。
- 选择 .csv 时是否走 CSV 文件选择流程。
- 鸣盛 .db 原有导入是否不回退。
- CSV 是否能导入标准字段商品。
- 错误 CSV、空文件、缺少字段是否有清晰错误。
- 设置页字体大小、收银、商品编辑、现金找零是否没有回归。
- core 烟测是否通过。
- debug APK 是否构建通过。

发现问题时按严重程度列出文件和行号。
```

## 8. 预计新增文件

后端/core：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/ImportFormat.java
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/ProductImportAdapter.java
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/ImportFormatRegistry.java
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/importer/CsvProductImportAdapter.java
```

前端/app：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/components/ActionCard.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/components/InfoCard.java
```

可选 app 导入协调类：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/importer/AndroidMingshengDbImportAdapter.java
```

## 9. 预计修改文件

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/ImportGateway.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/AppServices.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/Views.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/StyleGuide.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/HomeScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ImportScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/SettingsScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutSectionScreen.java
android-emergency-pos/core/src/test/java/com/espsa/mobilepos/core/CoreSmokeTest.java
```

## 10. 验收命令

运行目录：

```text
E:\手机收银软件开发\android-emergency-pos
```

后端烟测：

```powershell
powershell -ExecutionPolicy Bypass -File .\core\scripts\compile-and-test.ps1
```

APK 构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug-apk.ps1
```

## 11. 手工验收流程

### 11.1 UI 卡片

1. 打开首页。
2. 确认一级入口不是简单按钮陈列，而是卡片式导航。
3. 检查卡片标题、说明、主操作是否清楚。
4. 打开导入页、设置页、收银入口页，确认风格一致。
5. 切换字体大小到“特大”，确认卡片文字不明显重叠。

### 11.2 鸣盛 DB 导入

1. 打开导入页。
2. 选择“鸣盛数据库 .db”。
3. 系统文件选择器打开。
4. 选择鸣盛 `.db` 文件。
5. 确认导入成功，商品数正确。
6. 确认最近导入快照更新。

### 11.3 CSV 导入

1. 准备包含 `barcode,name,price` 的 CSV。
2. 打开导入页。
3. 选择“通用 CSV 商品表”。
4. 选择 CSV 文件。
5. 确认商品导入成功。
6. 搜索一个 CSV 中的商品，确认可查到。

### 11.4 错误处理

1. 选择 CSV 格式但导入缺少必填字段的 CSV。
2. 确认显示缺少字段或没有有效商品。
3. 选择空文件。
4. 确认显示清楚错误。
5. 在文件选择器点取消。
6. 确认不会崩溃，显示未选择文件或安静返回。

### 11.5 回归

1. 收银扫码/搜索/现金找零仍正常。
2. 商品编辑保存/删除仍正常。
3. 字体大小设置仍生效。
4. 每日总账仍显示正常。

## 12. 风险和约束

- Android 系统文件选择器不能保证完全按扩展名过滤，所以导入前必须二次校验格式。
- 不建议为了“自动寻找设备所有 db/csv 文件”申请全文件管理权限；第一版使用系统文件选择器更稳。
- CSV 字段命名差异很大，第一版只支持常见别名，后续可增加字段映射设置。
- 其他收银软件数据库表结构未知，不能承诺自动导入所有 `.db`。
- UI 卡片化不能牺牲收银效率，主操作必须清晰可点。

## 13. 完成定义

本轮完成必须满足：

- 首页、导入页、设置页等一级界面完成卡片式改造。
- 前端 UI 改造遵循 `frontend-design` skill。
- 导入页支持选择鸣盛 `.db` 和通用 `.csv`。
- 鸣盛 `.db` 原有导入能力不回退。
- 通用 CSV 可导入有效商品。
- 错误文件、空文件、取消选择都有合理处理。
- core 烟测通过。
- debug APK 构建通过。
- 代码保持模块化和函数拆分，没有把导入解析写进 UI 页面。
