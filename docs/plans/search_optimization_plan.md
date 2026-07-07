# Android Emergency POS 搜索无顿感优化开发计划

## 1. 目标

本计划用于优化 `android-emergency-pos` 中收银页和商品编辑页的商品关键词搜索体验。

当前问题：

- 在收银页使用关键词搜索商品时，点击搜索后会短暂停顿，随后才弹出商品选择列表。
- 商品编辑页关键词搜索也复用类似搜索逻辑，商品数量超过 1 万条时存在同类风险。
- 当前搜索在 UI 线程同步执行，并且每次搜索都会遍历所有商品、反复做文本 normalize、正则替换、token 判断、打分和排序。
- 当前结果弹窗会为每个匹配商品创建一个 `Button`，匹配结果多时，UI 创建成本也会导致卡顿。

目标：

- 搜索点击后主界面不冻结。
- 允许搜索结果不做硬数量上限。
- 即使匹配几千个商品，弹窗也应该快速出现并保持滑动流畅。
- 搜索逻辑继续离线运行，不引入远程服务。
- 保持当前商品匹配能力：大小写无关、重音符号无关、多关键词乱序、忽略常见西语连接词。

最终体验目标：

```text
用户输入关键词
→ 点击搜索
→ 立即看到搜索中状态，界面可响应
→ 搜索完成后弹出结果列表
→ 列表可显示全部匹配数量
→ 屏幕只渲染可见行，滑动时复用行视图
```

## 2. 当前相关代码

主开发目录：

```text
E:\手机收银软件开发\android-emergency-pos
```

主要相关文件：

```text
core/src/main/java/com/espsa/mobilepos/core/catalog/ProductRepository.java
core/src/main/java/com/espsa/mobilepos/core/catalog/InMemoryProductRepository.java
core/src/main/java/com/espsa/mobilepos/core/catalog/ProductCatalogService.java
core/src/main/java/com/espsa/mobilepos/core/editing/ProductEditingService.java
app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutScreen.java
app/src/main/java/com/espsa/mobilepos/ui/screens/ProductEditScreen.java
```

当前卡顿来源：

- `CheckoutScreen.showSearchDialog()` 中直接调用 `services.catalog().searchByName(trimmed)`。
- `ProductEditScreen.searchKeyword()` 中直接调用 `services.productEditing().searchByKeyword(query)`。
- `InMemoryProductRepository.searchByName()` 每次搜索都遍历 `products`。
- `scoreProduct()` 每次都会对商品名、条码、分类、单位做 `normalizeSearchText()`。
- `normalizeSearchText()` 内部使用 `Normalizer` 和正则替换，这些操作对 1 万多个商品重复执行会造成停顿。
- 搜索结果弹窗当前用循环创建大量 `Button`，匹配结果多时会阻塞 UI。

## 3. 非目标

本阶段不做：

- 不把商品库迁移到 Room / SQLite。
- 不引入服务器搜索。
- 不改变商品导入字段映射。
- 不降低现有关键词匹配能力。
- 不把搜索结果硬限制为固定数量。
- 不改动价格计算、购物车、销售记录逻辑。

## 4. 总体方案

采用三层优化：

```text
搜索索引层
→ 后台搜索执行层
→ 虚拟列表结果 UI 层
```

核心原则：

- 搜索结果数量可以不限制。
- UI 渲染必须限制为“当前可见行”，不能一次性创建所有结果按钮。
- 商品 normalize 和 token 拆分必须在商品库加载、导入、编辑、删除时预处理，不要在每次搜索时重复做。
- UI 线程只负责显示状态和渲染结果，不负责大规模搜索计算。

## 4.1 多 Agent 分工

为了方便多 agent 并行开发，本计划拆成两条主线：

```text
后端开发 Agent
→ core 搜索索引、搜索 API、repository 性能优化、单元测试

前端开发 Agent
→ app UI 搜索体验、后台线程、虚拟列表弹窗、收银/商品编辑页接入
```

协作边界：

- 后端 Agent 不改 `CheckoutScreen`、`ProductEditScreen` 的 UI 交互，除非是为了编译适配公开 API。
- 前端 Agent 不改搜索打分算法和 repository 内部索引结构，除非发现 API 无法满足 UI。
- 两边通过 `ProductRepository` / `ProductCatalogService` / `ProductEditingService` 交接。
- 如果需要新增共享数据结构，优先放在 `core`，不要放在 Android UI 包里。

建议开发顺序：

1. 后端 Agent 先完成 `ProductSearchEntry` 和 repository 预索引。
2. 后端 Agent 保持或补齐 `searchByName(query)` / `searchByName(query, limit)` API。
3. 前端 Agent 在后端 API 稳定后接入后台搜索和虚拟列表。
4. 两边最后一起跑 smoke test 和完整 APK 构建。

## 5. 搜索索引层

### 5.1 新增 SearchEntry

建议在 `core.catalog` 中新增：

```java
final class ProductSearchEntry {
    private final Product product;
    private final String normalizedName;
    private final String normalizedBarcode;
    private final String normalizedCategory;
    private final String normalizedUnitName;
    private final String searchable;
    private final String[] nameTokens;
}
```

职责：

- 保存商品对象。
- 保存预处理后的可搜索文本。
- 保存商品名 token，避免每次搜索时反复 split。

注意：

- 这个类可以先 package-private，不需要暴露给 UI。
- 不要把 Android 依赖放进 `core`。

### 5.2 InMemoryProductRepository 增加索引

当前已有：

```java
private final Map<String, Product> byId;
private final Map<String, Product> byBarcode;
private final List<Product> products;
```

建议新增：

```java
private final Map<String, ProductSearchEntry> searchEntryById = new HashMap<String, ProductSearchEntry>();
private final List<ProductSearchEntry> searchEntries = new ArrayList<ProductSearchEntry>();
```

更新规则：

- `replaceAll()` 清空并重建全部索引。
- `upsert()` 替换商品时同步替换 search entry。
- `deleteById()` 删除商品时同步删除 search entry。

建议拆分函数：

```java
private void clearIndexes();
private void addToIndexes(Product product);
private void removeFromIndexes(Product product);
private ProductSearchEntry buildSearchEntry(Product product);
private String normalizeSearchText(String value);
private String[] tokenize(String normalizedText);
```

### 5.3 搜索打分改用 SearchEntry

当前：

```java
private int scoreProduct(Product product, String normalizedQuery, String[] keywords)
```

建议改为：

```java
private int scoreEntry(ProductSearchEntry entry, String normalizedQuery, String[] keywords)
```

避免在打分里重复：

```java
normalizeSearchText(product.name())
normalizeSearchText(product.barcode())
normalizeSearchText(product.category())
normalizeSearchText(product.unitName())
name.split("\\s+")
```

## 6. 后台搜索执行层

### 6.1 UI 不直接阻塞搜索

收银页和商品编辑页都应把搜索放到后台线程。

可以先使用 Android 标准能力：

```java
ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
Handler mainHandler = new Handler(Looper.getMainLooper());
```

推荐在 app 层新增：

```text
app/SearchTaskRunner.java
```

建议职责：

- 接收搜索任务。
- 在后台线程执行。
- 回到主线程交付结果。
- 防止旧搜索结果覆盖新搜索结果。

建议 API：

```java
public final class SearchTaskRunner {
    public interface SearchWork<T> {
        T run();
    }

    public interface SearchCallback<T> {
        void onResult(T result);
    }

    public void runLatest(SearchWork<List<Product>> work, SearchCallback<List<Product>> callback);
    public void shutdown();
}
```

实现要点：

- 使用递增 `generation` 或 `requestId`。
- 每次搜索记录最新 id。
- 后台任务完成时，如果不是最新 id，则丢弃结果。
- Activity/Screen 销毁时关闭 executor 或由 `AppServices` 统一持有。

### 6.2 搜索中状态

点击搜索后应立即反馈：

- 禁用搜索按钮。
- 显示 Toast 或小提示：`搜索中... / Buscando...`。
- 或先弹出一个轻量 loading dialog，搜索完成后替换为结果 dialog。

不要让用户误以为没点到。

## 7. 虚拟列表结果 UI 层

### 7.1 不再为每个结果创建 Button

当前做法：

```java
for (Product product : results) {
    Button item = Views.button(...);
    list.addView(item);
}
```

这个方案在结果很多时一定会卡。

建议改为：

```text
AlertDialog
└── ListView
    └── ProductSearchResultAdapter
```

`ListView` 只创建当前屏幕可见行，滑动时复用 view。

### 7.2 新增通用 adapter

建议在 app UI 层新增：

```text
app/src/main/java/com/espsa/mobilepos/ui/ProductSearchResultAdapter.java
```

职责：

- 接收 `List<Product>`。
- 渲染商品名、条码、售价、分类、单位。
- 复用 `convertView`。
- 不持有业务服务。

建议函数拆分：

```java
public View getView(int position, View convertView, ViewGroup parent);
private View createRow(ViewGroup parent);
private void bindRow(View row, Product product);
private String primaryLine(Product product);
private String secondaryLine(Product product);
```

### 7.3 收银页和商品编辑页复用结果弹窗

建议新增：

```text
app/src/main/java/com/espsa/mobilepos/ui/ProductSearchResultDialog.java
```

职责：

- 构建结果弹窗。
- 显示总匹配数量。
- 用 `ListView` 展示全部结果。
- 点击商品后回调。

建议 API：

```java
public final class ProductSearchResultDialog {
    public interface ProductSelectedCallback {
        void onProductSelected(Product product);
    }

    public static AlertDialog show(
            Context context,
            AppLanguage language,
            List<Product> products,
            ProductSelectedCallback callback
    );
}
```

收银页回调：

```java
cart.addProduct(product, 1);
refreshCart();
```

商品编辑页回调：

```java
openEdit(product);
```

## 8. API 设计建议

保留现有 API：

```java
List<Product> searchByName(String query, int limit);
```

新增不限制结果 API：

```java
List<Product> searchByName(String query);
```

如果已有该方法，内部可以继续调用：

```java
return productRepository.searchByName(query, Integer.MAX_VALUE);
```

但在实现上要注意：

- 搜索层可以返回全部结果。
- UI 层必须用虚拟列表。
- 如果未来遇到极端数据量，可以再加分页 API，但第一版 1 万多商品暂时不需要复杂分页。

## 9. 代码模块化要求

实现时必须保持小模块、小函数，不要把优化逻辑堆到 `CheckoutScreen` 或 `ProductEditScreen`。

### 9.1 不能做的写法

不要在 `CheckoutScreen.showSearchDialog()` 里同时做：

```text
读取输入
校验
后台线程
搜索
排序
创建上千个按钮
弹窗
点击加入购物车
错误处理
```

### 9.2 推荐拆分

`CheckoutScreen` 中只保留流程控制：

```java
private void handleSearchClick(String query);
private void showSearchLoading();
private void hideSearchLoading();
private void showSearchResults(List<Product> products);
private void addSearchResultToCart(Product product);
```

`ProductEditScreen` 中只保留流程控制：

```java
private void handleKeywordSearch(String query);
private void showKeywordSearchLoading();
private void showKeywordSearchResults(List<Product> products);
private void openSearchResult(Product product);
```

搜索索引放在：

```text
core.catalog.InMemoryProductRepository
```

结果列表 UI 放在：

```text
ui.ProductSearchResultAdapter
ui.ProductSearchResultDialog
```

后台任务放在：

```text
app.SearchTaskRunner
```

## 10. 给后续 Agent 的提示词

后续让 agent 实现时，可以直接使用下面提示词：

```text
请按 E:\手机收银软件开发\修改方案\search_optimization_plan.md 优化商品搜索体验。

重点要求：
1. 保持项目现有原生 Android Java、app/core 分层，不引入 Room、Compose 或大型搜索库。
2. 搜索结果不要硬限制数量，但 UI 必须使用 ListView 或 RecyclerView 这种虚拟列表，不能一次性为所有结果创建 Button。
3. InMemoryProductRepository 要维护 ProductSearchEntry 预处理索引，商品导入、upsert、delete 时同步更新索引。
4. 搜索时不要反复 normalize 每个商品字段，不要在 score 函数里重复 split token。
5. CheckoutScreen 和 ProductEditScreen 不能堆大函数，只保留流程控制；搜索索引、后台执行、结果弹窗分别拆成独立模块。
6. 搜索必须从 UI 线程移到后台线程，完成后再回主线程显示结果。
7. 防止连续搜索时旧结果覆盖新结果。
8. 保持现有搜索能力：大小写无关、重音符号无关、多关键词乱序、忽略 de/del/el/la/los/las。
9. 不改动购物车、价格计算、促销计算、销售记录逻辑。
10. 完成后运行 CoreSmokeTest 和完整 debug APK 构建。

实现时请遵守小函数原则：
- UI render 方法尽量控制在 80 行以内。
- 搜索打分方法控制在 40 行以内。
- 线程调度和回调处理单独拆分。
- 弹窗构建和列表 adapter 单独拆分。
- 不要把 UI、搜索算法、线程、业务动作写进同一个方法。
```

## 11. 开发步骤

### 11.1 后端开发 Agent 任务

后端范围：

```text
core/src/main/java/com/espsa/mobilepos/core/catalog
core/src/main/java/com/espsa/mobilepos/core/editing
core/src/test/java/com/espsa/mobilepos/core
```

后端目标：

- 搜索时不再对每个商品重复 normalize。
- 搜索结果可以返回全部匹配商品。
- 保持现有搜索排序和匹配语义。
- 不引入 Android 依赖。

#### Backend Step 1：建立搜索索引

- 新增 `ProductSearchEntry`。
- 修改 `InMemoryProductRepository`，维护 `searchEntries`。
- `replaceAll()`、`upsert()`、`deleteById()` 同步维护索引。
- 搜索打分使用预处理字段。

#### Backend Step 2：保持 API 稳定

- 保留 `ProductRepository.searchByName(String query, int limit)`。
- 保留或确认 `ProductCatalogService.searchByName(String query)`。
- 保留或确认 `ProductEditingService.searchByKeyword(String query)`。
- 如果新增内部方法，不要强迫 UI 大面积改名。

#### Backend Step 3：补充搜索测试

至少验证：

- `huevo` 可匹配。
- `maple huevo` 乱序匹配。
- `maple de huevo` 忽略连接词。
- 条码完全匹配排序靠前。
- `upsert()` 后新商品可搜索。
- `deleteById()` 后商品不可搜索。
- 修改商品名称后旧名称不再匹配、新名称可匹配。

后端交付物：

```text
ProductSearchEntry
InMemoryProductRepository 预索引实现
必要的 core 搜索测试
```

后端不要做：

- 不改收银页弹窗布局。
- 不写 Android `Handler` / `ExecutorService` UI 逻辑。
- 不创建结果列表 adapter。

### 11.2 前端开发 Agent 任务

前端范围：

```text
app/src/main/java/com/espsa/mobilepos
app/src/main/java/com/espsa/mobilepos/ui
app/src/main/java/com/espsa/mobilepos/ui/screens
```

前端目标：

- 搜索不阻塞 UI 线程。
- 搜索结果列表不一次性创建所有按钮。
- 收银页和商品编辑页复用同一套结果弹窗。
- 结果数量可以不硬限制。

#### Frontend Step 1：新增虚拟列表 UI

- 新增 `ProductSearchResultAdapter`。
- 新增 `ProductSearchResultDialog`。
- 用 `ListView` 展示结果。
- 点击结果通过 callback 交给收银页或商品编辑页。

#### Frontend Step 2：后台搜索

- 新增 `SearchTaskRunner`。
- 收银页和商品编辑页点击搜索后用后台线程执行。
- 搜索完成后回主线程弹窗。
- 增加 latest request 防护。

#### Frontend Step 3：接入收银页

- 改造 `CheckoutScreen.showSearchDialog()`。
- 保留原有加入购物车行为。
- 不改变 cart 和 pricing。

#### Frontend Step 4：接入商品编辑页

- 改造 `ProductEditScreen.searchKeyword()`。
- 一个结果可直接进入编辑。
- 多个结果用通用结果弹窗。
- 无结果仍显示无匹配提示。

前端交付物：

```text
SearchTaskRunner
ProductSearchResultAdapter
ProductSearchResultDialog
CheckoutScreen 搜索接入
ProductEditScreen 搜索接入
```

前端不要做：

- 不改搜索打分算法。
- 不把搜索 normalize/token 逻辑复制到 UI。
- 不一次性创建所有结果按钮。
- 不改购物车、价格计算、促销计算、销售记录逻辑。

### 11.3 联合验证

验证清单：

- 搜索 `huevo`，结果正常。
- 搜索 `maple huevo`，乱序匹配正常。
- 搜索 `maple de huevo`，忽略连接词正常。
- 搜索宽泛关键词时，弹窗快速出现，列表可流畅滚动。
- 收银页点击结果可加入购物车。
- 商品编辑页点击结果可进入编辑页。
- 连续快速点击不同搜索词时，不显示旧搜索结果。
- `CoreSmokeTest` 通过。
- 完整 debug APK Gradle 构建成功。

## 12. 验收标准

功能标准：

- 搜索结果数量不做硬限制。
- 搜索弹窗显示总匹配数量。
- 搜索结果列表可滚动查看全部匹配商品。
- 收银和商品编辑两个入口都使用同一套优化后的结果列表。

体验标准：

- 点击搜索后立即有反馈。
- UI 不出现明显冻结。
- 结果很多时列表滚动仍然流畅。

工程标准：

- `CheckoutScreen` 和 `ProductEditScreen` 不新增大段搜索算法代码。
- 搜索索引逻辑集中在 repository。
- 结果列表渲染集中在 adapter/dialog。
- 后台线程调度集中在 task runner。
- 不改变购物车、销售、价格计算行为。

## 13. 多 Agent 交接检查清单

后端 Agent 完成后，应向前端 Agent 说明：

- `ProductRepository` 暴露的搜索方法是否保持原签名。
- 搜索结果是否默认按匹配度排序。
- 搜索是否允许返回全部结果。
- `replaceAll()`、`upsert()`、`deleteById()` 是否都已同步搜索索引。
- 是否有新增测试覆盖搜索索引更新。

前端 Agent 开始前，应确认：

- 后端搜索 API 已编译通过。
- 收银页使用 `services.catalog().searchByName(...)`。
- 商品编辑页使用 `services.productEditing().searchByKeyword(...)`。
- 两个入口都可以接入同一个结果弹窗 callback。

联合合并前，应确认：

- 没有两个 agent 同时大改同一个文件而互相覆盖。
- `CheckoutScreen` 中没有新增大块搜索算法。
- `ProductEditScreen` 中没有新增大块搜索算法。
- `InMemoryProductRepository` 中没有 Android UI 依赖。
- `ProductSearchResultAdapter` 和 `ProductSearchResultDialog` 不依赖具体业务动作，只通过 callback 返回商品。
- `CoreSmokeTest` 通过。
- 完整 debug APK Gradle 构建成功。
