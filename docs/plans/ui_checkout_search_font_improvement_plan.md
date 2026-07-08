# UI、收银找零、搜索交互改进开发方案

更新时间：2026-07-08  
适用项目：`android-emergency-pos`  
目标读者：后端 agent、前端 agent、验收 agent

## 1. 背景

当前项目已经完成商品搜索优化：搜索索引、异步搜索、无限结果弹窗、商品编辑和收银共用搜索结果弹窗都已落地。

本轮需要继续改进 4 个体验点：

1. 离开页面时取消仍在执行的搜索，避免旧页面搜索结果回调到新页面。
2. 设置页增加字体大小调节，调节后所有界面自动适应。
3. 收银结账时，如果是默认/现金结账，弹窗输入客户付款金额，并显示应找金额。
4. 商品编辑和收银搜索商品时，输入后按回车也能直接进行关键词匹配搜索。

## 2. 架构拆分说明

本项目不是 Web 前后端项目，而是原生 Android Java 项目。这里的“前后端分离”按以下方式理解：

- 后端/业务层：`core` 模块，以及 `app` 里的服务、偏好存储、任务调度。
- 前端/UI 层：`app/src/main/java/com/espsa/mobilepos/ui` 和 `ui/screens` 下的页面、弹窗、控件工具。

开发时必须保持模块化：

- 业务计算放在 `core`，不要写进 Android 页面类。
- UI 输入、弹窗、按钮、键盘事件放在 `app/ui`，不要污染 core。
- 通用 UI 行为抽 helper，不要在多个 screen 复制粘贴。
- 大方法要拆成小函数，每个函数只做一件事。
- 不要把新逻辑全部塞进 `CheckoutScreen` 或 `ProductEditScreen`。

## 3. 总体开发顺序

建议按以下顺序开发，方便多 agent 并行和逐步验收：

1. 后端 agent：实现现金找零计算类和测试。
2. 后端 agent：完善字体大小偏好存储。
3. 前端 agent：实现离开页面取消搜索。
4. 前端 agent：实现回车触发搜索。
5. 前端 agent：实现现金找零弹窗。
6. 前端 agent：实现设置页字体大小调节和全局字号适配。
7. 验收 agent：运行 core 烟测、APK 构建、手工验收关键流程。

其中第 1、2 步可以和第 3、4 步并行。第 6 步触碰 UI 面最广，建议最后做。

## 4. 后端开发任务

### 4.1 现金找零计算

建议新增文件：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/checkout/CashChangeCalculator.java
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/checkout/CashChangeResult.java
```

职责：

- `CashChangeCalculator` 只负责计算，不依赖 Android。
- 输入应收金额 `Money total` 和客户付款金额 `Money received`。
- 如果 `received < total`，返回明确错误或抛出 `IllegalArgumentException`。
- 如果金额足够，返回 `CashChangeResult`，包含：
  - `total`
  - `received`
  - `change`

推荐接口：

```java
public final class CashChangeCalculator {
    public CashChangeResult calculate(Money total, Money received);
}
```

```java
public final class CashChangeResult {
    private final Money total;
    private final Money received;
    private final Money change;
}
```

注意：

- 本轮建议只用于弹窗显示找零，不修改 `Sale`、`SaleRepository`、CSV 导出结构。
- 如果以后需要记录“实收金额”和“找零金额”，再单独做销售模型升级。
- 不要在 `CheckoutScreen` 里手写 `received - total` 的业务规则。

### 4.2 现金找零测试

修改文件：

```text
android-emergency-pos/core/src/test/java/com/espsa/mobilepos/core/CoreSmokeTest.java
```

新增测试点：

- 应收 1000，实收 1000，找零 0。
- 应收 1000，实收 1500，找零 500。
- 应收 1000，实收 999，不能通过。
- 应收为空或实收为空时不能通过。

### 4.3 字体大小偏好存储

建议新增文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/TextScale.java
```

推荐枚举：

```java
public enum TextScale {
    SMALL(0.90f),
    NORMAL(1.00f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f)
}
```

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/UserPreferencesStore.java
```

新增方法：

```java
public TextScale loadTextScale(Context context);
public void saveTextScale(Context context, TextScale textScale);
```

要求：

- 默认值使用 `TextScale.NORMAL`。
- 读取到非法值时回退 `NORMAL`。
- 保持和现有语言偏好同一个 SharedPreferences，不要新增多套偏好文件。

## 5. 前端开发任务

### 5.1 离开页面取消搜索

相关文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/SearchTaskRunner.java
```

现状：

- `SearchTaskRunner` 已有 `cancelPending()`。
- `MainActivity` 页面切换、返回首页、切换语言、导入后刷新时没有统一取消搜索。

实现方法：

- 在 `MainActivity` 增加小函数：

```java
private void cancelPendingUiTasks() {
    services.searchTaskRunner().cancelPending();
}
```

- 在页面重绘前调用，比如：
  - `renderShell()` 开始处，注意 `services` 非空。
  - 或 `navigateTo(...)` 确认离开后、`screen = target` 前。
  - `toggleLanguage()` 重绘前。
  - 导入成功后 `renderShell()` 前。

推荐更稳的方式：

- 在 `renderShell()` 开始处统一取消 pending search。
- 避免每个按钮单独调用，减少遗漏。

验收标准：

- 在商品编辑搜索中输入关键词并点击搜索，立刻返回首页，不应再弹出旧搜索结果。
- 在收银搜索中输入关键词并点击搜索，立刻切到设置页，不应再弹出旧搜索结果。

### 5.2 回车触发搜索

建议新增文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/KeyboardActions.java
```

职责：

- 统一绑定输入框的 Enter / IME_ACTION_SEARCH / IME_ACTION_DONE。
- 避免 `CheckoutScreen` 和 `ProductEditScreen` 各写一套监听。

推荐接口：

```java
public final class KeyboardActions {
    public static void bindSearchAction(EditText input, Runnable action);
    public static void bindDoneAction(EditText input, Runnable action);
}
```

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ProductEditScreen.java
```

收银页要求：

- 商品输入框 `barcode` 当前既可扫条码、输入条码，也用来关键词搜索。
- 按“搜索”按钮仍执行 `handleSearchClick(...)`。
- 按回车时，执行关键词搜索，即复用 `handleSearchClick(barcode.getText().toString(), searchButton)`。
- 不要影响“加入”按钮的条码加入逻辑。

商品编辑页要求：

- 关键词输入框 `keyword` 按回车，执行 `handleKeywordSearch(...)`。
- 条码输入框 `barcode` 可以按回车执行查找/新建，即复用 `lookupBarcode(...)`。
- 不要把条码回车误接成关键词搜索。

验收标准：

- 收银页输入 `huevo` 后按回车，弹出匹配商品列表。
- 商品编辑页关键词输入 `huevo` 后按回车，弹出匹配商品列表。
- 商品编辑页条码输入 `001` 后按回车，仍走查找/新建。

### 5.3 现金付款找零弹窗

相关文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutScreen.java
```

建议新增 UI helper：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/CashPaymentDialog.java
```

职责：

- 显示应收金额。
- 输入客户付款金额。
- 金额不足时提示，不保存销售。
- 金额足够时显示找零金额，再确认结账。

推荐流程：

1. 用户点击收银页“结账”。
2. `checkout()` 内先计算当前购物车预览金额：

```java
CartPriceResult price = services.checkout().preview(cart);
```

3. 如果 `selectedPaymentMethod() == PaymentMethod.CASH`，打开现金付款弹窗。
4. 弹窗确认通过后，再调用：

```java
services.checkout().checkout(cart, PaymentMethod.CASH);
```

5. 非现金支付保持现有逻辑。

弹窗行为：

- 标题：`现金结账 / Pago en efectivo`
- 内容：
  - 应收：`$total`
  - 客户付款输入框
  - 找零：`$change`
- 输入不足：
  - 不关闭弹窗。
  - 显示“付款金额不足 / Pago insuficiente”。
- 输入足够：
  - 显示找零。
  - 用户点“确认结账”后保存销售。

注意：

- 金额解析要拆成小函数，比如 `parseMoneyAmount(String value)`。
- 不要把 AlertDialog 的全部逻辑塞进 `checkout()`。
- 不要改变非现金支付流程。

### 5.4 设置页字体大小调节

相关文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/Views.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/StyleGuide.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/SettingsScreen.java
```

推荐实现：

- 在 `StyleGuide` 中保存当前字体倍率。
- 所有 `Views.text(context, value, sizeSp, color)` 统一调用 `StyleGuide.scaledSp(sizeSp)`。
- `Views.button(...)` 设置统一按钮字号。
- `StyleGuide.pageTitle(...)`、`StyleGuide.total(...)`、`StyleGuide.label(...)` 也统一走缩放。
- `MainActivity.onCreate()` 读取偏好并配置 `StyleGuide`。
- 设置页点击字体档位后保存偏好，调用一个 `onTextScaleChanged` 回调，让 `MainActivity` 重新渲染页面。

推荐接口：

```java
public final class StyleGuide {
    public static void setTextScale(TextScale scale);
    public static float scaledSp(float baseSp);
}
```

`SettingsScreen` 构造函数建议扩展为：

```java
public SettingsScreen(
        Context context,
        AppServices services,
        AppLanguage language,
        Runnable onToggleLanguage,
        TextScale currentTextScale,
        TextScaleChangeHandler onTextScaleChanged
)
```

也可以定义：

```java
public interface TextScaleChangeHandler {
    void onTextScaleChanged(TextScale textScale);
}
```

设置页 UI：

- 增加信息行：当前字体大小。
- 增加 4 个按钮：
  - 小
  - 标准
  - 大
  - 特大
- 当前选中的档位按钮禁用或加文字标记。

注意：

- 不建议用任意滑杆，防止用户调到过大导致布局崩坏。
- 调整后必须 `renderShell()`，让所有页面重新创建。
- 后续可以新增 `Views.editText(...)`，让输入框字号也统一；本轮至少要覆盖主要 TextView 和 Button。

验收标准：

- 设置页切换“大”后，首页、收银、商品编辑、导入、每日总账字号变大。
- 切换“小”后字号变小。
- 页面不应出现明显文字重叠、按钮文字被严重截断。
- 重启 app 后仍保持上次选择。

## 6. 多 agent 分工建议

### 后端 agent 提示词

```text
你负责 android-emergency-pos 的后端/业务层改动。

请只处理 core 业务计算、UserPreferencesStore 偏好存储和对应测试。
不要修改 Android 页面布局，不要改 CheckoutScreen 的 UI。

必须保持模块化和函数拆分：
- 现金找零计算放在 core/checkout 的独立类中。
- 找零结果用独立值对象表达。
- 字体大小档位用 TextScale enum 表达。
- 偏好读取/保存只放在 UserPreferencesStore。
- CoreSmokeTest 增加明确测试，不要只靠手测。

不要修改 Sale、SaleRepository、CSV 导出结构，除非另有明确要求。
完成后运行 core/scripts/compile-and-test.ps1。
```

### 前端 agent 提示词

```text
你负责 android-emergency-pos 的前端/UI 层改动。

请实现：
1. 页面离开时取消 pending 搜索。
2. 收银和商品编辑输入框回车触发搜索/查找。
3. 现金结账弹窗输入客户付款金额并显示找零。
4. 设置页字体大小调节，并让所有界面自动适配。

必须保持模块化和函数拆分：
- 不要把所有逻辑堆进 CheckoutScreen。
- 键盘回车行为抽成 KeyboardActions helper。
- 现金付款弹窗优先抽成 CashPaymentDialog 或独立小方法。
- 字号缩放集中在 StyleGuide/Views，不要每个页面单独乘倍率。
- 页面切换取消搜索通过 MainActivity 的统一入口处理。

不要改变已有商品搜索匹配规则。
不要改变非现金结账流程。
不要改变商品编辑保存/删除逻辑。
完成后运行 APK 构建脚本 scripts/build-debug-apk.ps1。
```

### 验收 agent 提示词

```text
你负责验收本轮改动，不做实现。

请检查：
- core 烟测是否通过。
- debug APK 是否能构建。
- 离开页面后旧搜索结果不会再弹出。
- 收银和商品编辑按回车可以搜索。
- 商品编辑条码输入框按回车仍是查找/新建。
- 现金结账金额不足不能保存销售，金额足够显示正确找零并能保存销售。
- 非现金支付流程不受影响。
- 字体大小设置能保存、重启后仍生效、主要页面自动适配。

如发现问题，请按严重程度列出文件和行号。
```

## 7. 文件改动清单

预计新增：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/checkout/CashChangeCalculator.java
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/checkout/CashChangeResult.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/TextScale.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/KeyboardActions.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/CashPaymentDialog.java
```

预计修改：

```text
android-emergency-pos/core/src/test/java/com/espsa/mobilepos/core/CoreSmokeTest.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/UserPreferencesStore.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/Views.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/StyleGuide.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/SettingsScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ProductEditScreen.java
```

## 8. 验收命令

后端烟测：

```powershell
powershell -ExecutionPolicy Bypass -File .\core\scripts\compile-and-test.ps1
```

APK 构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug-apk.ps1
```

运行目录：

```text
E:\手机收银软件开发\android-emergency-pos
```

## 9. 手工验收流程

### 9.1 搜索取消

1. 打开商品编辑页。
2. 输入一个会匹配很多商品的关键词。
3. 点击搜索后立刻返回首页。
4. 确认不会弹出旧搜索结果。
5. 在收银页重复同样流程。

### 9.2 回车搜索

1. 收银页输入关键词，按回车。
2. 确认弹出商品列表。
3. 商品编辑页关键词输入框输入关键词，按回车。
4. 确认弹出商品列表。
5. 商品编辑页条码输入框输入条码，按回车。
6. 确认仍执行查找/新建。

### 9.3 现金找零

1. 加入商品到购物车。
2. 支付方式保持默认/现金。
3. 点击结账。
4. 输入小于应收金额的付款金额，确认不能结账。
5. 输入大于应收金额的付款金额，确认显示正确找零。
6. 确认结账后销售保存，购物车清空。
7. 切换到 Mercado Pago、借记卡、信用卡、转账，确认非现金流程不弹找零框。

### 9.4 字体大小

1. 打开设置页。
2. 切换到“大”或“特大”。
3. 确认当前页立即刷新。
4. 进入首页、收银、商品编辑、导入、每日总账，确认字体变化。
5. 重启 app 后确认字体设置仍保留。

## 10. 风险和约束

- 字体大小会影响所有页面布局，必须最后统一手测。
- 收银现金找零本轮不写入销售记录，因此日报和导出不会显示实收/找零。
- 如果以后需要记录实收和找零，必须另开任务升级 `Sale`、导出、日报展示。
- 搜索取消只取消回调有效性，不一定强制中断已经在后台执行的 Java 搜索循环；对用户体验足够，因为旧结果不会再显示。
- 回车搜索不能破坏扫码/条码加入商品的原有流程。

## 11. 完成定义

本轮开发完成必须满足：

- 后端烟测通过。
- debug APK 构建通过。
- 4 个需求均可手工复现并通过。
- 代码保持模块化，没有把新逻辑集中堆到单个大方法。
- 商品搜索匹配规则、商品编辑保存逻辑、非现金结账逻辑没有回归。
