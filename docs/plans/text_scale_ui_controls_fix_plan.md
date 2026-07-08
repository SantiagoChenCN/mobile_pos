# 字体大小适配遗漏修复方案

更新时间：2026-07-08  
适用项目：`android-emergency-pos`  
任务类型：前端/UI 层修复  
目标读者：前端 agent、验收 agent

## 1. 问题背景

本轮开发已经实现：

- `TextScale` 字体档位。
- `UserPreferencesStore.loadTextScale/saveTextScale`。
- `StyleGuide.setTextScale(...)` 和 `StyleGuide.scaledSp(...)`。
- `Views.text(...)` 和 `Views.button(...)` 已经走统一字号缩放。
- 设置页可以切换字体大小并重新渲染界面。

但验收发现还有遗漏：

- 多个 `EditText` 仍然直接 `new EditText(context)`。
- `Spinner` 仍然直接使用默认 adapter 样式。
- 因此设置字体大小后，普通文本和按钮会变，但输入框、付款金额输入框、支付方式下拉框、商品表单输入框可能不会同步变化。

这属于前端/UI 层问题，不需要修改 core 业务逻辑。

## 2. 修复目标

设置页切换字体大小后，以下控件都必须自动适配：

- 所有页面标题、普通文本、按钮。
- 收银页商品输入框。
- 商品编辑页条码输入框和关键词输入框。
- 收银现金付款弹窗的付款金额输入框。
- 收银折扣、改价、手动价格等弹窗输入框。
- 商品表单里的所有输入框。
- 支付方式下拉框。
- 商品表单分类/单位等下拉框。

## 3. 开发原则

必须保持模块化和函数拆分：

- 不要在每个 screen 里手写 `input.setTextSize(...)`。
- 不要到处复制 Spinner adapter 代码。
- 字号适配逻辑集中放在 `Views` 或独立 UI helper。
- 页面类只负责调用统一 helper 创建控件。
- 不要修改现金找零业务类。
- 不要修改 `CheckoutService`、`Sale`、`SaleRepository`。

## 4. 推荐修复方案

### 4.1 扩展 Views 工具类

修改文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/Views.java
```

新增方法：

```java
public static EditText editText(Context context) {
    EditText input = new EditText(context);
    input.setTextSize(StyleGuide.scaledSp(16));
    return input;
}
```

可选新增：

```java
public static EditText numberEditText(Context context) {
    EditText input = editText(context);
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    return input;
}
```

注意：

- 如果添加 `numberEditText`，需要 import `android.text.InputType`。
- `Views.editText(...)` 不设置 hint、不设置业务输入类型，只设置通用 UI 样式。
- 具体 hint、singleLine、inputType 仍由 screen 决定。

### 4.2 新增统一 Spinner adapter

建议在 `Views.java` 中新增：

```java
public static ArrayAdapter<String> spinnerAdapter(Context context, String[] labels) {
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            context,
            android.R.layout.simple_spinner_item,
            labels
    ) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTextSize(StyleGuide.scaledSp(16));
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            view.setTextSize(StyleGuide.scaledSp(16));
            return view;
        }
    };
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    return adapter;
}
```

需要 import：

```java
import android.widget.ArrayAdapter;
import android.view.ViewGroup;
```

注意：

- 所有 `Spinner` 都使用这个 adapter。
- 不要每个页面单独写匿名 adapter，避免以后字号规则分裂。

## 5. 需要替换的位置

### 5.1 CheckoutScreen

文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/CheckoutScreen.java
```

需要替换：

- `new EditText(context)` -> `Views.editText(context)`
- `new Spinner(context)` 保留可以，但 adapter 改为 `Views.spinnerAdapter(...)`

重点位置：

- 收银页商品输入框。
- 手动价格弹窗输入框。
- 修改单价弹窗输入框。
- 百分比折扣弹窗输入框。
- 固定金额折扣弹窗输入框。
- 支付方式下拉框。

示例：

```java
EditText barcode = Views.editText(context);
```

```java
paymentSpinner = new Spinner(context);
paymentSpinner.setAdapter(Views.spinnerAdapter(context, paymentLabels()));
```

### 5.2 ProductEditScreen

文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ProductEditScreen.java
```

需要替换：

- 条码输入框：`new EditText(context)` -> `Views.editText(context)`
- 关键词输入框：`new EditText(context)` -> `Views.editText(context)`

注意：

- 条码框仍保持数字输入。
- 关键词框仍保持文本输入。
- 回车绑定逻辑不能丢。

### 5.3 CashPaymentDialog

文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/CashPaymentDialog.java
```

需要替换：

- `EditText receivedInput = new EditText(context);`
- 改为：

```java
EditText receivedInput = Views.editText(context);
```

注意：

- 仍然设置 `InputType.TYPE_CLASS_NUMBER`。
- 不改变金额不足不结账逻辑。

### 5.4 ProductFormScreen

文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ProductFormScreen.java
```

需要替换：

- 所有表单输入框：`new EditText(context)` -> `Views.editText(context)`
- 所有 Spinner adapter：改用 `Views.spinnerAdapter(...)`

注意：

- 不改变商品保存、校验、删除逻辑。
- 不改变分类、单位、促销字段的业务含义。

## 6. 验收标准

### 6.1 代码检查

运行：

```powershell
rg -n "new EditText\\(|new Spinner\\(|new ArrayAdapter" android-emergency-pos\app\src\main\java\com\espsa\mobilepos
```

允许：

- `Views.java` 内部可以有 `new EditText(...)`、`new ArrayAdapter(...)`。
- 页面类中不应再直接创建未缩放的 `EditText`。
- 页面类中不应再直接使用默认 `ArrayAdapter` 给 Spinner。

### 6.2 功能检查

1. 打开设置页。
2. 切换字体大小为“特大”。
3. 检查以下控件字号是否一起变大：
   - 收银页商品输入框。
   - 收银页支付方式下拉框。
   - 现金结账弹窗付款金额输入框。
   - 商品编辑条码输入框。
   - 商品编辑关键词输入框。
   - 商品表单所有输入框。
   - 商品表单下拉框。
4. 切换回“标准”，确认界面恢复正常。
5. 重启 app 后确认字体设置仍保留。

### 6.3 构建检查

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

## 7. 不要改的内容

本修复只处理 UI 字号适配，不要改：

- 商品搜索规则。
- 搜索异步逻辑。
- 现金找零计算规则。
- 结账保存逻辑。
- 商品编辑保存/删除逻辑。
- 鸣盛数据库导入逻辑。
- 销售记录结构和 CSV 导出结构。

## 8. 完成定义

完成后必须满足：

- 设置字体大小后，输入框和下拉框也跟随变化。
- `rg` 检查中，页面类不再散落未缩放的 `new EditText(...)` 和默认 Spinner adapter。
- core 烟测通过。
- debug APK 构建通过。
- 没有引入业务逻辑变更。
