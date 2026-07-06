# Android Emergency POS 商品编辑与导入回滚开发计划

## 1. 目标

本计划用于给当前工作目录中的 `android-emergency-pos` Android 项目增加轻量级商品编辑能力。远程仓库名可以仍然是 `mobile_pos`，但本地开发、构建和代码修改以 `android-emergency-pos` 为准；`mobile_pos_publish` 只作为发布副本/同步副本参考，不作为首要修改目录。

项目定位仍然是**安卓离线应急收银 App**，不是正式、臃肿的管理系统。

当前项目框架约束：

- 继续使用原生 Android Java，保持 `app` 和 `core` 两个模块。
- 业务模型、价格计算、仓库接口优先放在 `core`。
- Android 文件、Uri、Activity、Toast、Dialog、SharedPreferences 等平台相关逻辑放在 `app`。
- UI 继续沿用现有 `MainActivity` + `ui.screens` 的程序化 View 写法，不引入 Compose、Room、SQLite ORM 或大型框架。
- 商品库继续以解析后的 JSON 保存在 App 内部存储，不写回鸣盛原始 `.db`。

本次修改重点：

- 增加商品编辑入口。
- 已入库商品可修改常用字段。
- 未入库商品可新建。
- 商品可删除，删除前二次确认。
- 导入页面独立出来。
- 保存最近 5 次导入快照，支持回滚。
- 设置页面轻量化。
- 收银流程保持现状，交易明细放入收银内部，日账独立到一级首页。

## 2. 已确认需求

### 2.1 一级首页

App 每次打开先进入一级首页。

入口顺序：

```text
商品编辑
收银
日账
设置
导入
```

结构：

```text
Home
├── 商品编辑 / Product Editing
├── 收银 / Checkout
│   ├── 收银
│   └── 交易明细
├── 日账 / Daily Summary
├── 设置 / Settings
└── 导入 / Import
```

二级页面要求：

- 顶部左侧有“返回首页”。
- Android 系统返回键也返回一级首页。
- 如果商品编辑表单存在未保存修改，返回时弹窗提示：继续编辑 / 放弃修改。

### 2.2 商品可编辑字段

只编辑当前项目已经识别和使用的字段：

| 字段 | 来源 / 含义 | 规则 |
|---|---|---|
| `barcode` | `GBarcode`，也支持自定义数字短码，如 `001` | 必填，1-13 位数字，不允许重复 |
| `name` | `GNameX` / `GYiNameJian` | 必填 |
| `category` | `RTypeName` / `GType` | 下拉选择，UI 允许空白；保存到当前 `Product` 时空白按 `Product.MANUAL_ALMACEN_CATEGORY` 归一为 `almacen` |
| `unitName` | `UName` / `GUNIT` | 下拉选择，允许空白 |
| `salePrice` | `GSalePrice` | 必填，整数，必须大于 0 |
| `promotionPrice` | `GHuiPrice` | 可空 |
| `promotionMinQuantity` | `GHuiPriceCount` | 可空 |

不增加其他复杂字段。

鸣盛 `.db` 适配规则：

- 当前导入器读取鸣盛/ESpsa SQLite 数据库中的 `CJQ_GOODLIST` 商品表。
- 字段映射必须和现有 `MingshengProductMapper` 保持一致：`GNID` 优先作为商品内部 ID，缺失时用 `GID`；条码用 `GBarcode`；名称优先 `GNameX`，缺失时用 `GYiNameJian`；分类优先 `RTypeName`，缺失时用 `GType`；单位优先 `UName`，缺失时用 `GUNIT`；售价用 `GSalePrice`；促销价用 `GHuiPrice`；促销起购数量用 `GHuiPriceCount`。
- 金额字段继续按当前项目规则保存为整数金额 `Money.of(long)`，导入时小数四舍五入到整数；商品编辑页也只允许整数价格，避免和现有 `Money` 模型冲突。
- 只有 `promotionPrice > 0` 且 `promotionMinQuantity > 0` 时才视为有效自动促销；否则促销字段按空处理。
- `Product` 当前不允许空 `id`、空 `name` 或空 `salePrice`。如果鸣盛行缺少这些核心字段，导入器应跳过该行或按现有导入错误策略处理，不能生成无效商品对象。

### 2.3 商品编辑入口逻辑

进入商品编辑后，先看到搜索/扫码页面。

支持：

- 按条码/短码查找或新建。
- 扫码查找或新建。
- 关键词搜索。

逻辑：

```text
条码/短码查找：
找到商品 → 直接进入编辑页
没找到 → 进入新建商品页，并自动填入该条码/短码

扫码：
找到商品 → 直接进入编辑页
没找到 → 进入新建商品页，并自动填入扫码结果

关键词搜索：
无结果 → 提示无结果
一个结果 → 直接进入编辑页
多个结果 → 显示列表，用户选择后进入编辑页
```

关键词搜索结果显示：

```text
商品名
条码
售价
分类
单位
```

排序：沿用当前 `InMemoryProductRepository` 的匹配度排序。

### 2.4 新建商品规则

新建商品时：

- 商品内部 ID 自动生成，例如 `local-<epochMillis>`。
- 条码/短码只允许数字。
- 条码/短码长度 1 到 13 位。
- 售价只支持整数，不支持小数。
- 售价必须大于 0。
- 商品名必填。
- 分类和单位从下拉框选择，UI 允许空白；分类保存到当前 `Product` 时如果为空，按现有模型归一为 `almacen`，单位可继续保存为空字符串。
- 促销价和促销数量都为空时表示无促销。
- 如果填写促销价，就必须填写促销数量。
- 如果填写促销数量，就必须填写促销价。
- 促销价必须小于正常售价。
- 促销数量必须是整数且大于 0；如果后续确认只做“买多件优惠”，可在 UI 文案上提示建议大于 1，但不要和鸣盛 `GHuiPriceCount=1` 的数据冲突。
- 如果条码已存在，弹窗选择：编辑已有商品 / 取消新建。
- 如果商品名完全重复但条码不同，弹窗提醒，但允许继续保存。
- 保存成功后返回商品编辑搜索页。

### 2.5 修改已有商品规则

修改已有商品时：

- 直接覆盖当前手机本地商品库里的商品。
- 保存后立刻影响后续收银搜索和加入商品。
- 当前购物车已经加入的商品不自动更新。
- 如果修改条码，保存前必须确认。
- 如果新条码已经被其他商品占用，禁止保存。
- 条码、售价、促销发生变化时，保存前弹窗确认。
- 只修改名称、分类、单位时，直接保存。
- 保存成功后弹窗显示变化列表，点 OK 后返回商品编辑搜索页。

当前购物车兼容性说明：

- 当前 `CartLine` 保存的是加入购物车当时的 `Product` 对象，结账生成 `SaleLine` 时也会把商品名、条码、分类、价格等写成销售快照。
- 因此商品编辑、删除、导入、回滚只影响之后从 repository 搜索和加入购物车的商品；已经在当前购物车里的行继续按加入时的商品对象和价格计算。
- 实现商品编辑时不要为了“同步最新商品信息”去遍历修改当前 `Cart`，否则会破坏应急收银场景下的价格快照原则。

变化提示示例：

```text
售价：$1500 → $1800
促销：$1200 x3 → $1100 x3
名称：Coca cola → Coca Cola 2.25L
分类：Bebidas → Almacén
单位：un → pack
```

### 2.6 删除商品规则

商品编辑中支持删除商品。

删除要求：

- 删除前二次确认。
- 第一次确认弹窗显示商品名、条码、售价，提示删除后不能通过搜索或条码加入收银。
- 第二次确认使用更短的危险操作确认文案，例如“确认删除这个本地商品？”，用户再次确认后才执行删除。
- 删除后该商品不能通过搜索或条码加入收银。
- 删除只影响当前手机本地商品库。
- 如果以后重新导入的 `.db` 里仍有该商品，则商品恢复。
- 删除成功后返回商品编辑搜索页。
- 当前购物车里已经加入的该商品仍然保留，继续按当时价格结账。

### 2.7 分类和单位选项

分类和单位下拉选项来源：

- 优先从最近一次导入快照中提取。
- 不从当前已编辑商品库实时提取，避免删除或修改商品导致选项变化。
- 如果某个当前商品的分类/单位不在选项里，编辑时临时加入当前值。
- 如果没有导入快照，使用默认兜底列表。
- 下拉框允许空白选项。

默认分类：

```text
Almacén
Bebidas
Limpieza
Perfumería
Lácteos
Fiambres
Panadería
Verdulería
Carnicería
Congelados
Mascotas
Bazar
Otros
```

默认单位：

```text
un
kg
g
L
ml
pack
caja
bolsa
docena
```

### 2.8 导入和回滚规则

导入页面独立，不再放在设置里。

导入页面显示：

```text
当前商品数
最近导入时间
最近导入文件名
当前商品库是否已手动修改
最近 5 次导入版本
```

每个导入版本显示：

```text
导入时间
原文件名
商品数量
促销商品数量
```

规则：

- App 只保存解析后的商品快照，不保存原始 `.db` 文件副本。
- 最近导入版本只记录真正导入 `.db` 后生成的快照。
- 手动修改/新建/删除商品不会加入回滚列表。
- 最多保留最近 5 次导入快照。
- 超过 5 个时自动删除最旧的。
- 导入新 `.db` 前弹窗提醒：继续导入会清空当前本地修改和自建商品。
- 导入成功后替换当前商品库，清除“已手动修改”状态。
- 导入失败时保留当前商品库，不做任何替换。
- 回滚前弹窗提醒：回滚会替换当前商品库，并清空当前本地修改和自建商品。
- 回滚成功后替换当前商品库，清除“已手动修改”状态。
- 导入或回滚都不清空当前购物车。

### 2.9 设置页面

设置页面只保留轻量内容：

- 当前商品数。
- App 版本。
- 离线模式。
- 语言切换。

语言切换需要保存，下次打开 App 使用上次选择的语言。

## 3. 非目标功能

本阶段明确不做：

- 不接 controlador fiscal。
- 不做 AFIP / Factura A / Factura B。
- 不做库存管理。
- 不做多设备同步。
- 不做 PIN / 员工权限。
- 不把商品库迁移到 SQLite / Room。
- 不写回原始明盛 `.db`。
- 不做复杂商品版本历史。
- 不重构销售持久化。

## 4. 工程原则

### 4.1 模块化开发

所有新增功能必须按模块拆分，不允许把大量逻辑堆进一个 Activity 或一个 Screen。

重点规则：

- UI 只负责显示、输入、弹窗、跳转。
- Service 负责业务流程和校验。
- Store 负责文件读写。
- Repository 负责内存中的商品查询和索引。
- Formatter 负责文案格式化。
- Helper 负责单一工具逻辑。

### 4.2 函数不要臃肿

建议限制：

| 类型 | 建议长度 |
|---|---:|
| UI render 方法 | 80 行以内 |
| 校验方法 | 40 行以内 |
| 文件读写方法 | 50 行以内 |
| 业务操作方法 | 60 行以内 |

如果函数过大，必须拆成小函数。

错误示例：

```java
void saveProduct() {
    // 读取 UI
    // 校验
    // 检查重复
    // 显示确认弹窗
    // 更新 repository
    // 保存文件
    // 显示结果
    // 跳转页面
}
```

推荐拆分：

```java
private ProductDraft readDraftFromForm();
private ProductValidationResult validateDraft(ProductDraft draft);
private boolean requiresCriticalChangeConfirmation(Product original, ProductDraft draft);
private List<ProductChange> diffProduct(Product original, Product updated);
private void persistProductUpdate(Product updated);
private void showChangeSummaryAndReturn(List<ProductChange> changes);
```

### 4.3 避免隐藏副作用

禁止业务服务直接操作 Android UI。

- 不在 Service 中弹 AlertDialog。
- 不在 Store 中 Toast。
- 不在 Repository 中写文件。
- 不在 Activity 中写复杂校验。

## 5. 数据存储设计

当前项目已有 `ProductLocalStore`，用 `products.json` 保存商品。本阶段继续使用 JSON，但需要重构为商品库状态存储。

### 5.1 文件结构

使用 App 内部存储：

```text
/files/products.json
/files/product_library_meta.json
/files/import_snapshots/
    snapshot-YYYYMMDD-HHMMSS.json
```

### 5.2 当前商品库

`products.json` 保存当前有效商品库。

会修改该文件的操作：

- 新建商品。
- 修改商品。
- 删除商品。
- 导入 `.db` 成功。
- 回滚到历史导入快照。

### 5.3 商品库元数据

新增：

```java
ProductLibraryMetadata
```

建议字段：

```java
public final class ProductLibraryMetadata {
    private final String lastImportFileName;
    private final String lastImportTimeIso;
    private final int lastImportProductCount;
    private final int lastImportPromotionCount;
    private final boolean manuallyModified;
    private final List<ImportSnapshotInfo> recentImports;
}
```

### 5.4 导入快照信息

新增：

```java
ImportSnapshotInfo
```

建议字段：

```java
public final class ImportSnapshotInfo {
    private final String snapshotId;
    private final String fileName;
    private final String importedAtIso;
    private final int productCount;
    private final int promotionCount;
}
```

### 5.5 商品库状态

新增：

```java
ProductLibraryState
```

建议字段：

```java
public final class ProductLibraryState {
    private final List<Product> products;
    private final ProductLibraryMetadata metadata;
}
```

## 6. ProductLocalStore 重构方案

### 6.1 Public API

```java
public final class ProductLocalStore {
    public ProductLibraryState loadState(Context context);

    public void saveCurrentProducts(
            Context context,
            List<Product> products,
            boolean manuallyModified
    ) throws ProductStoreException;

    public ProductLibraryState saveImportResult(
            Context context,
            ProductImportResult result,
            String originalFileName
    ) throws ProductStoreException;

    public List<ImportSnapshotInfo> listImportSnapshots(Context context);

    public ProductLibraryState restoreSnapshot(
            Context context,
            String snapshotId
    ) throws ProductStoreException;

    public ProductLibraryMetadata loadMetadata(Context context);
}
```

兼容当前实现时要注意：

- 现有 `ProductLocalStore.load()` 读取失败会返回空列表。本次重构后，`loadState()` 不应把 JSON 损坏、字段错误、读取异常静默当作“没有商品”；应返回带错误的空状态或抛出 `ProductStoreException`，由 `AppServices` 决定提示用户，避免误把损坏文件覆盖成空商品库。
- 如果 `products.json` 不存在，才表示首次启动/未导入，可以返回空商品库和默认 metadata。
- 写入 `products.json`、metadata、snapshot 时继续使用 UTF-8 JSON；不要保存原始 `.db` 文件副本。

### 6.2 Private helper 拆分

```java
private File currentProductsFile(Context context);
private File metadataFile(Context context);
private File snapshotDirectory(Context context);
private File snapshotFile(Context context, String snapshotId);

private List<Product> readProducts(File file);
private void writeProducts(File file, List<Product> products);

private ProductLibraryMetadata readMetadata(File file);
private void writeMetadata(File file, ProductLibraryMetadata metadata);

private ImportSnapshotInfo createSnapshotInfo(
        String fileName,
        ProductImportResult result
);

private void writeSnapshot(
        Context context,
        ImportSnapshotInfo info,
        List<Product> products
);

private List<ImportSnapshotInfo> pruneSnapshotsToLatestFive(
        Context context,
        List<ImportSnapshotInfo> snapshots
);
```

### 6.3 保存导入结果的安全顺序

导入成功并完成本地文件保存后，再替换当前内存商品库。当前 `AppServices.importMingshengDatabase()` 是先 `catalog.applyImport(result)` 再保存 `products.json`，本次重构必须调整为先持久化成功、再 `productRepository.replaceAll(...)`，避免“内存已换库但文件保存失败”的半成功状态。

顺序：

1. 解析 `.db` 成功，得到 `ProductImportResult`。
2. 写入新 snapshot 文件。
3. 写入当前 `products.json`。
4. 写入 `product_library_meta.json`。
5. 删除超过 5 个的旧 snapshot。
6. 以上文件操作全部成功后，更新内存 repository。

如果解析失败，不能触碰当前商品库。  
如果解析成功但保存 snapshot、`products.json` 或 metadata 失败，也不能更新内存 repository；错误提示应说明“已读取 `.db`，但保存到手机本地失败，当前商品库未替换”。

## 7. Repository 改造方案

### 7.1 ProductRepository 增加方法

```java
List<Product> all();

Optional<Product> findById(String productId);

void upsert(Product product);

void deleteById(String productId);

boolean barcodeExists(String barcode, String excludeProductId);

boolean exactNameExists(String name, String excludeProductId);
```

当前 `ProductRepository.searchByName(String query, int limit)` 虽然名字叫 `searchByName`，实际在 `InMemoryProductRepository` 中已经匹配名称、条码、分类、单位，并带有匹配度排序。为了减少无关改名，可以先保留现有方法名；如果新增 `searchByKeyword`，也应作为薄包装或后续统一重命名，不要在同一阶段大范围改调用点。

### 7.2 InMemoryProductRepository 增加 ID 索引

当前已有：

```java
Map<String, Product> byBarcode;
List<Product> products;
```

增加：

```java
private final Map<String, Product> byId = new HashMap<String, Product>();
```

拆分索引函数：

```java
private void clearIndexes();
private void addToIndexes(Product product);
private void removeFromIndexes(Product product);
private int indexOfProductId(String productId);
```

### 7.3 upsert 行为

`upsert(Product product)`：

- 如果 ID 存在，替换列表中的原商品。
- 移除旧条码索引。
- 添加新条码索引。
- 更新 ID 索引。
- 如果 ID 不存在，追加商品并建立索引。

### 7.4 delete 行为

`deleteById(String productId)`：

- 找到商品。
- 从 `products` 删除。
- 从 `byId` 删除。
- 从 `byBarcode` 删除。

## 8. 新增业务服务

### 8.1 ProductEditingService

新增：

```java
ProductEditingService
```

职责：

- 条码查找。
- 关键词搜索。
- 新建商品。
- 修改商品。
- 删除商品。
- 校验商品草稿。
- 检测重复条码。
- 检测同名商品。
- 提供分类和单位选项。

建议 API：

```java
Optional<Product> findByBarcode(String barcode);

List<Product> searchByKeyword(String query);

ProductCreateResult createProduct(ProductDraft draft);

ProductUpdateResult updateProduct(String productId, ProductDraft draft);

ProductDeleteResult deleteProduct(String productId);

ProductValidationResult validateForCreate(ProductDraft draft);

ProductValidationResult validateForUpdate(String productId, ProductDraft draft);

boolean hasSameNameProduct(String name, String excludeProductId);

List<String> categoryOptions(Product currentProductOrNull);

List<String> unitOptions(Product currentProductOrNull);
```

内部函数拆分：

```java
private Product buildNewProduct(ProductDraft draft);
private Product buildUpdatedProduct(Product original, ProductDraft draft);
private String generateLocalProductId();
private void persistCurrentRepositoryProducts();
private List<ProductChange> diff(Product before, Product after);
```

### 8.2 ProductLibraryService

新增：

```java
ProductLibraryService
```

职责：

- 当前商品库元数据。
- 最近导入快照列表。
- 回滚快照。
- 手动修改后保存当前商品库。

建议 API：

```java
ProductLibraryMetadata metadata();

List<ImportSnapshotInfo> recentImports();

ProductLibraryState restoreSnapshot(String snapshotId);

void markManualProductLibraryChange(List<Product> currentProducts);
```

### 8.3 ProductOptionProvider

新增：

```java
ProductOptionProvider
```

职责：

- 从最近导入快照提取分类/单位。
- 没有快照时提供默认兜底列表。
- 当前商品的特殊分类/单位不在列表时临时加入。
- 添加空白选项。

建议 API：

```java
List<String> categoryOptions(Product currentProductOrNull);
List<String> unitOptions(Product currentProductOrNull);
```

内部拆分：

```java
private List<String> extractCategoriesFromLatestSnapshot();
private List<String> extractUnitsFromLatestSnapshot();
private List<String> fallbackCategories();
private List<String> fallbackUnits();
private List<String> withBlankOption(List<String> options);
private List<String> withCurrentValue(List<String> options, String currentValue);
private List<String> normalizeAndSortOptions(List<String> options);
```

### 8.4 ProductChangeFormatter

新增：

```java
ProductChangeFormatter
```

职责：

- 比较修改前后商品字段。
- 生成变化列表。
- 按当前语言格式化弹窗文案。

建议 API：

```java
List<ProductChange> diff(Product before, Product after);
String formatForDialog(List<ProductChange> changes, AppLanguage language);
```

### 8.5 UserPreferencesStore

新增：

```java
UserPreferencesStore
```

职责：保存语言设置。

建议 API：

```java
AppLanguage loadLanguage(Context context);
void saveLanguage(Context context, AppLanguage language);
```

### 8.6 AndroidFileNameResolver

新增：

```java
AndroidFileNameResolver
```

职责：从用户选择的 Uri 中提取显示文件名。

建议 API：

```java
String displayName(Context context, Uri uri);
```

Fallback：

```java
uri.getLastPathSegment()
```

不要把文件名解析逻辑写进 `MainActivity`。优先使用 `ContentResolver.query(uri, null, ...)` 读取 `OpenableColumns.DISPLAY_NAME`；只有查询失败或显示名为空时，才 fallback 到 `uri.getLastPathSegment()`。

## 9. 新增数据类

建议新增：

```text
ProductLibraryState
ProductLibraryMetadata
ImportSnapshotInfo
ProductStoreException
ProductDraft
ProductValidationResult
ProductCreateResult
ProductUpdateResult
ProductDeleteResult
ProductChange
```

### 9.1 ProductDraft

建议保存 UI 原始输入文本，方便校验错误提示：

```java
public final class ProductDraft {
    private final String barcode;
    private final String name;
    private final String category;
    private final String unitName;
    private final String salePriceText;
    private final String promotionPriceText;
    private final String promotionMinQuantityText;
}
```

### 9.2 ProductValidationResult

建议：

```java
public final class ProductValidationResult {
    private final boolean valid;
    private final List<String> errorsZh;
    private final List<String> errorsEs;
    private final ParsedProductDraft parsedDraft;
}
```

`ParsedProductDraft` 可保存已经解析后的 long/int 数值，避免重复 parse。

### 9.3 ProductChange

建议：

```java
public final class ProductChange {
    private final String fieldLabelZh;
    private final String fieldLabelEs;
    private final String oldValue;
    private final String newValue;
}
```

## 10. UI 修改方案

### 10.1 Screen enum

修改：

```java
public enum Screen {
    HOME,
    PRODUCT_EDIT,
    CHECKOUT,
    DAILY,
    SETTINGS,
    IMPORT
}
```

收银内部 tab 单独建 enum：

```java
public enum CheckoutTab {
    CHECKOUT,
    SALES_DETAIL
}
```

不要把收银内部 tab 混进一级 Screen。

### 10.2 MainActivity 职责

`MainActivity` 只负责一级导航和系统回退。

建议方法：

```java
private void renderShell();
private void renderHome();
private void renderCurrentScreen();
private View headerWithHomeBack(String title);
private void navigateHome();
private void navigateTo(Screen screen);
private boolean currentScreenHasUnsavedChanges();
private void confirmLeaveIfNeeded(Runnable afterConfirm);
```

不要把商品编辑校验、导入回滚、文件读写写在 `MainActivity` 里。

### 10.3 HomeScreen

新增：

```java
HomeScreen
```

职责：

- 显示 5 个入口按钮。
- 调用导航 callback。

建议接口：

```java
public interface HomeNavigation {
    void openProductEditing();
    void openCheckout();
    void openDailySummary();
    void openSettings();
    void openImport();
}
```

### 10.4 ProductEditSearchScreen

新增：

```java
ProductEditSearchScreen
```

职责：

- 条码/短码输入。
- 按条码查找/新建。
- 扫码。
- 关键词输入。
- 关键词搜索。
- 搜索结果列表。

建议拆分：

```java
public View render();
private View barcodePanel();
private View keywordPanel();
private void handleBarcodeLookup();
private void handleScanResult(String barcode);
private void handleKeywordSearch();
private void openEdit(Product product);
private void openCreate(String barcode);
private void showMultipleResults(List<Product> products);
private void showNoKeywordResults();
```

### 10.5 ProductFormScreen

新增：

```java
ProductFormScreen
```

模式：

```java
public enum ProductFormMode {
    CREATE,
    EDIT
}
```

字段顺序：

```text
条码
商品名
售价
分类
单位
促销价
促销数量
```

建议拆分：

```java
public View render();
private View formContent();
private View actionButtons();
private ProductDraft readDraft();
private void handleSave();
private void handleCreate(ProductDraft draft);
private void handleUpdate(ProductDraft draft);
private void maybeConfirmCriticalChanges(ProductDraft draft, Runnable afterConfirm);
private void maybeConfirmDuplicateName(ProductDraft draft, Runnable afterConfirm);
private void showValidationErrors(ProductValidationResult result);
private void showChangeSummary(List<ProductChange> changes);
private void confirmDeleteFirst();
private void confirmDeleteSecond();
private boolean hasUnsavedChanges();
private void confirmDiscardChanges(Runnable afterDiscard);
```

如果 `handleSave()` 变长，继续拆：

```java
private void handleCreateSave(ProductDraft draft);
private void handleUpdateSave(ProductDraft draft);
```

### 10.6 CheckoutSectionScreen

新增或重构：

```java
CheckoutSectionScreen
```

职责：

- 顶部切换按钮：收银 / 交易明细。
- 收银 tab 显示现有 `CheckoutScreen`。
- 交易明细 tab 显示现有 `SalesScreen`。

建议拆分：

```java
public View render();
private View tabSwitcher();
private View activeTabContent();
private void switchToCheckout();
private void switchToSalesDetail();
```

### 10.7 ImportScreen

新增：

```java
ImportScreen
```

职责：

- 显示当前商品库状态。
- 导入新 `.db`。
- 显示最近 5 次导入快照。
- 回滚快照。
- 弹窗确认导入/回滚。

建议拆分：

```java
public View render();
private View currentLibraryPanel();
private View importButtonPanel();
private View snapshotListPanel();
private View snapshotRow(ImportSnapshotInfo snapshot);
private void confirmImportNewDb();
private void confirmRestoreSnapshot(ImportSnapshotInfo snapshot);
private void restoreSnapshot(String snapshotId);
```

### 10.8 SettingsScreen

设置页面移除导入功能，只保留：

- 商品数量。
- App version。
- 离线模式。
- 语言切换。

## 11. AppServices 集成方案

### 11.1 新增字段

```java
private final ProductEditingService productEditing;
private final ProductLibraryService productLibrary;
private final UserPreferencesStore preferencesStore;
```

### 11.2 新增 accessor

```java
public ProductEditingService productEditing() {
    return productEditing;
}

public ProductLibraryService productLibrary() {
    return productLibrary;
}

public UserPreferencesStore preferencesStore() {
    return preferencesStore;
}
```

### 11.3 启动加载逻辑

当前逻辑中如果本地商品为空，会加载 demo 商品。正式应急使用中不建议自动加载 demo。

修改为：

```java
ProductLibraryState state = productLocalStore.loadState(context);
productRepository.replaceAll(state.products());
```

如果没有商品，商品库为空，但允许进入商品编辑并使用默认分类/单位新建商品。

当前代码位置是 `AppServices.create(Context context)`：`productRepository.replaceAll(storedProducts.isEmpty() ? demoProducts() : storedProducts)`。本次实现时应改为直接加载 `ProductLibraryState` 的 products；`createDemoServices()` 可以保留给测试或演示，不影响正式 App 启动。

### 11.4 导入逻辑

导入应该由 `AppServices` 协调，但不要把所有细节写进去。

建议：

```java
public ProductImportResult importMingshengDatabase(Context context, Uri uri)
        throws ProductImportException {
    String fileName = fileNameResolver.displayName(context, uri);
    ProductImportResult result = new AndroidDbProductImporter().importFromUri(context, uri);
    ProductLibraryState state = productLocalStore.saveImportResult(context, result, fileName);
    productRepository.replaceAll(state.products());
    lastImportMessage = "products=" + result.productCount() + ", promotions=" + result.promotionCount();
    return result;
}
```

如果该函数变长，继续拆：

```java
private ProductImportResult parseImport(Context context, Uri uri);
private ProductLibraryState persistImport(Context context, ProductImportResult result, String fileName);
private void applyProductLibraryState(ProductLibraryState state);
```

## 12. 关键流程实现细节

### 12.1 新建商品流程

```text
ProductEditSearchScreen
→ openCreate(barcode)
→ ProductFormScreen(CREATE)
→ readDraft()
→ validateForCreate()
→ 检查条码重复
→ 检查同名商品
→ createProduct()
→ repository.upsert()
→ productLocalStore.saveCurrentProducts(..., manuallyModified=true)
→ show success dialog
→ return ProductEditSearchScreen
```

### 12.2 修改商品流程

```text
ProductFormScreen(EDIT)
→ readDraft()
→ validateForUpdate()
→ 检查条码重复
→ 生成 ProductChange diff
→ 如果条码/售价/促销变化，显示确认弹窗
→ updateProduct()
→ repository.upsert()
→ productLocalStore.saveCurrentProducts(..., manuallyModified=true)
→ show change summary dialog
→ return ProductEditSearchScreen
```

### 12.3 删除商品流程

```text
ProductFormScreen(EDIT)
→ tap delete
→ confirmDeleteFirst() 显示商品名、条码、售价
→ confirmDeleteSecond() 二次确认危险操作
→ repository.deleteById()
→ productLocalStore.saveCurrentProducts(..., manuallyModified=true)
→ return ProductEditSearchScreen
```

### 12.4 导入流程

```text
ImportScreen
→ confirmImportNewDb()
→ file picker
→ AndroidDbProductImporter.importFromUri()
→ ProductLocalStore.saveImportResult()
→ repository.replaceAll(importedProducts)
→ manuallyModified=false
→ prune snapshots to latest five
→ refresh ImportScreen
```

失败处理：

```text
如果 importFromUri() 抛异常：
当前 products.json 不变
metadata 不变
repository 不变
显示错误提示
```

### 12.5 回滚流程

```text
ImportScreen
→ confirmRestoreSnapshot(snapshot)
→ ProductLocalStore.restoreSnapshot(snapshotId)
→ repository.replaceAll(snapshotProducts)
→ manuallyModified=false
→ refresh ImportScreen
```

购物车不清空。

## 13. 测试清单

### 13.1 新建商品

- 新建条码 `001` 的商品。
- 收银输入 `001` 能加入商品。
- 关键词能搜索到该商品。
- 重启 App 后商品仍存在。

### 13.2 修改商品

- 修改售价，确认弹窗显示旧价和新价。
- 保存后新加入购物车使用新价格。
- 修改前已在购物车里的商品保持旧价格。
- 修改条码时有确认。
- 新条码重复时禁止保存。

### 13.3 删除商品

- 删除前显示商品名、条码、售价。
- 删除后无法搜索到该商品。
- 删除后无法通过条码加入。
- 已在购物车里的该商品仍可结账。

### 13.4 导入

- 导入有效 `.db` 后商品库替换。
- 导入页面显示文件名、时间、商品数、促销商品数。
- 导入后“已手动修改”清除。
- 导入无效文件时当前商品库不变。

### 13.5 回滚

- 连续导入超过 5 次，只保留最近 5 次。
- 回滚某个快照后商品库替换。
- 回滚后“已手动修改”清除。
- 回滚不清空购物车。

### 13.6 分类/单位

- 有导入快照时，分类/单位来自最近导入快照。
- 没有导入快照时，使用默认分类/单位。
- 当前商品分类/单位不在列表时，临时加入当前值。
- 空白选项可保存。

### 13.7 语言

- 切换语言后退出 App。
- 重新打开仍然保持上次语言。

## 14. 推荐开发顺序

### Step 1：新增数据类

```text
ProductLibraryState
ProductLibraryMetadata
ImportSnapshotInfo
ProductStoreException
ProductDraft
ProductValidationResult
ProductCreateResult
ProductUpdateResult
ProductDeleteResult
ProductChange
```

### Step 2：重构 ProductLocalStore

增加：

```text
当前商品库保存
metadata 保存
导入快照保存
最近 5 次快照列表
回滚快照
导入失败不替换
```

### Step 3：扩展 Repository

修改：

```text
ProductRepository
InMemoryProductRepository
ProductCatalogService
```

增加：

```text
all
findById
upsert
deleteById
barcodeExists
exactNameExists
```

### Step 4：新增业务服务

```text
ProductEditingService
ProductLibraryService
ProductOptionProvider
ProductChangeFormatter
UserPreferencesStore
AndroidFileNameResolver
```

### Step 5：重构导航

```text
Screen enum
MainActivity
HomeScreen
CheckoutSectionScreen
```

### Step 6：新增商品编辑 UI

```text
ProductEditSearchScreen
ProductFormScreen
```

并实现：

```text
条码查找/新建
扫码查找/新建
关键词搜索
编辑
新建
删除
未保存提醒
变化提示
```

### Step 7：新增导入 UI

```text
ImportScreen
```

实现：

```text
导入新 db
当前商品库状态
最近 5 次导入快照
回滚
导入/回滚确认弹窗
```

### Step 8：简化设置页

移除导入按钮，只保留：

```text
当前商品数
App 版本
离线模式
语言切换
```

### Step 9：联调测试

按测试清单逐项验证。

## 15. 最终结论

本阶段的正确方向是：

```text
不扩展成正式管理系统。
不重构收银主流程。
只增加轻量商品编辑、导入快照和首页结构。
```

关键原则：

```text
已经在购物车里的商品保持原状态。
商品编辑只影响之后搜索和加入购物车的商品。
导入和回滚只替换当前商品库，不清空购物车。
```

所有实现都必须保持小函数、小模块、职责清晰。函数过大时立即拆分，避免把 UI、校验、存储、业务逻辑写在同一个方法里。
