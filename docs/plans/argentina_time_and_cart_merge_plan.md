# 阿根廷时间统一与购物车同商品合并开发方案

更新时间：2026-07-11  
适用项目：`pc-sync-tool`、`android-emergency-pos`  
目标读者：电脑端后端 Agent、电脑端前端 Agent、手机端后端 Agent、手机端前端 Agent、验收 Agent

## 1. 本轮目标

本轮解决两个独立问题：

1. 电脑端和手机端显示的同步、备份、导入、日志等时间统一显示为阿根廷时间。
2. 手机收银时重复加入同一个正式商品，不再新增重复购物车行，而是在原行上累加数量。

示例：

```text
第一次加入 商品A：商品A x1
第二次加入 商品A：商品A x2
第三次加入数量 3：商品A x5
```

## 2. 已确认的项目现状

### 2.1 时间现状

电脑端：

- `manifest.py` 使用 UTC ISO 时间，例如 `2026-07-11T04:10:00Z`。
- manifest 的 `createdAt`、事件日志的 `time`、备份历史文件名都基于 UTC。
- `UiController.latest_backup_text()`、`latest_request_text()` 和主窗口日志列表直接显示原始 UTC 字符串。

手机端：

- 同步检查和同步完成时间通过 `Instant.now().toString()` 保存为 UTC。
- manifest 时间、导入快照时间、上次检查和上次同步时间直接显示原始 ISO 字符串。
- 销售记录使用 `Instant` 保存，但 `LedgerService` 和“今天”查询部分仍使用设备默认时区。

### 2.2 购物车现状

`Cart.addProduct(product, quantity)` 每次都会创建新的 `CartLine`，因此同一商品通过扫码、条码输入或搜索重复加入时会出现多行。

目前所有正式商品加入入口最终都会经过以下之一：

```text
CheckoutService.addProductByBarcode(...)
Cart.addProduct(...)
```

因此合并逻辑应放在 `core` 的 `Cart` 中，而不是分别写进扫码、搜索和 UI 页面。

## 3. 已确认的产品规则

### 3.1 阿根廷时间规则

- 数据库存储、manifest、HTTP 协议和日志文件继续保存 UTC/带时区 ISO 时间。
- 只在展示给用户时转换为阿根廷时间。
- 阿根廷业务时区统一定义为：

```text
America/Argentina/Buenos_Aires
当前偏移：UTC-03:00
显示后缀：ART
```

- 推荐统一显示格式：

```text
yyyy-MM-dd HH:mm:ss ART
```

示例：

```text
原始 UTC：2026-07-11T04:10:00Z
界面显示：2026-07-11 01:10:00 ART
```

### 3.2 同商品合并规则

- 正式商品按稳定的 `product.id` 判断是否相同。
- 同一个正式商品在购物车中始终只保留一行。
- 再次加入时，在原数量上累加本次数量。
- 合并后保留原行 ID，保证 UI 操作引用不失效。
- 合并后保留原行已有的手动单价和单行折扣。
- 自动促销按合并后的新数量重新计算。
- 手动输入价格的 `almacen` 临时商品不自动合并，即使价格相同也保持独立行。
- 不使用商品名称判断相同商品。
- 不只使用条码判断；条码可以被编辑，而 `product.id` 才是当前模型中的稳定身份。

示例：

```text
商品A x1，手动价 1500
再次扫描商品A
结果：商品A x2，手动价仍为 1500
```

## 4. 明确边界

### 4.1 本轮必须修改

- 电脑端所有面向用户的同步、备份、请求和事件日志时间显示。
- 手机端同步、manifest、导入快照和销售相关时间显示。
- 手机端“今天”的销售查询与日账日期应使用阿根廷业务时区。
- `Cart.addProduct()` 的同商品合并行为。
- 对应自动化测试和手工验收。

### 4.2 本轮禁止修改

- 不把 manifest、日志、商品快照或销售模型中的 UTC 时间改成本地无时区字符串。
- 不修改 manifest 字段名、HTTP API 或 Token 规则。
- 不根据电脑系统时区或手机系统时区决定业务时区。
- 不修改鸣盛软件和鸣盛原始数据库。
- 不修改商品价格、促销规则或折扣计算公式。
- 不合并手动 `almacen` 临时商品。
- 不使用商品名称进行合并。
- 不在 `CheckoutScreen` 的多个按钮回调中复制合并算法。
- 不进行与本轮无关的大型重构。

## 5. 推荐开发顺序

1. 手机端后端 Agent：新增统一阿根廷时区策略和格式化工具。
2. 电脑端后端 Agent：新增 UTC ISO 到阿根廷展示时间的格式化模块。
3. 手机端后端 Agent：修改 `Cart.addProduct()`，完成同商品合并和 core 测试。
4. 电脑端前端 Agent：把所有原始时间字符串接入电脑端格式化模块。
5. 手机端前端 Agent：把同步、导入快照、manifest、销售时间接入手机端格式化工具。
6. 手机端后端 Agent：把日账和“今天销售”查询固定到阿根廷业务日期。
7. 验收 Agent：运行电脑测试、core smoke、Android 构建，并进行真实 UI 手工验收。

时间模块与购物车模块互不依赖，可以由不同 Agent 并行开发。

## 6. 电脑端后端任务：阿根廷时间格式化

### 6.1 建议新增文件

```text
pc-sync-tool/src/time_display.py
```

该模块只负责解析时间和生成展示字符串，不读写配置、不操作数据库、不依赖 PySide6。

### 6.2 建议公开接口

```python
def format_argentina_time(value: object, empty_text: str = "-") -> str:
    """把 UTC/带偏移 ISO 时间转换为 yyyy-MM-dd HH:mm:ss ART。"""
```

可以补充内部函数：

```python
def parse_iso_datetime(value: object) -> datetime | None:
    ...
```

### 6.3 实现要求

优先尝试标准库 IANA 时区：

```python
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

ZoneInfo("America/Argentina/Buenos_Aires")
```

考虑到 Windows/PyInstaller 环境可能没有 IANA 时区数据库，必须提供无第三方依赖的后备：

```python
timezone(timedelta(hours=-3), name="ART")
```

解析规则：

1. `None`、空字符串返回 `empty_text`。
2. 支持末尾 `Z`，解析时转换成 `+00:00`。
3. 支持已经带 `+00:00`、`-03:00` 等偏移的 ISO 字符串。
4. 支持传入带时区的 `datetime`。
5. 无时区的 datetime 不应静默按电脑本地时区解释；推荐按 UTC 兼容旧数据，并在代码注释中说明。
6. 无法解析时不要抛异常导致 UI 崩溃；返回原始文本或统一的 `-`，两者选其一并写测试。推荐返回原始文本，便于发现历史脏数据。
7. 输出固定为 `yyyy-MM-dd HH:mm:ss ART`。

### 6.4 保持 UTC 的模块

以下代码继续生成 UTC，不要改为阿根廷本地字符串：

- `manifest.utc_now_iso()`
- manifest `createdAt`
- `EventLog.append()` 写入的 `time`
- `BackupWorker` 历史文件名
- manifest `version`

理由：这些字段参与排序、比较、版本标识和跨设备传输，必须保持绝对时间。

### 6.5 电脑端后端测试

新增 `tests/test_time_display.py`，至少覆盖：

```text
2026-07-11T04:10:00Z       -> 2026-07-11 01:10:00 ART
2026-07-11T04:10:00+00:00  -> 2026-07-11 01:10:00 ART
2026-07-11T01:10:00-03:00  -> 2026-07-11 01:10:00 ART
空值                           -> -
非法文本                        -> 不崩溃并按约定回退
跨 UTC 日期边界                 -> 日期正确减一天
```

测试不得依赖当前电脑系统时区。

## 7. 电脑端前端任务：替换原始时间展示

### 7.1 需要检查的显示位置

- `UiController.latest_backup_text()` 中 manifest `createdAt`。
- `UiController.latest_request_text()` 中事件 `time`。
- `UiController._format_event()`。
- `MainWindow._refresh_log()` 中日志列表时间。
- 主窗口其他新增的同步/备份时间字段。

### 7.2 接入方式

所有展示统一调用：

```python
format_argentina_time(raw_time)
```

不要在多个 UI 方法中分别写：

```python
datetime.fromisoformat(...)
timedelta(hours=-3)
```

### 7.3 前端验收

- 主窗口“最近备份”显示 `ART`。
- 主窗口“最近请求”显示 `ART`。
- 事件日志每行显示 `ART`。
- 原始 JSON 日志和 manifest 仍保存 UTC `Z` 时间。
- 电脑系统时区改成中国或其他时区后，界面仍显示相同阿根廷时间。

## 8. 手机端后端任务：统一阿根廷业务时区

### 8.1 建议新增文件

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/time/ArgentinaTime.java
```

建议职责：

- 提供唯一业务时区。
- 将 `Instant` 或 ISO 字符串格式化为阿根廷时间。
- 返回阿根廷当前业务日期。
- 对空值和无效历史数据安全回退。

### 8.2 建议接口

```java
public final class ArgentinaTime {
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public static String formatInstant(Instant instant);
    public static String formatIso(String isoValue);
    public static LocalDate today();
}
```

显示格式：

```java
DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'ART'")
        .withZone(ZONE);
```

### 8.3 存储和传输要求

以下内容继续使用 `Instant` 或 UTC ISO 存储：

- `Sale.createdAt`
- `ProductImportResult.importedAt`
- `ImportSnapshotInfo.importedAtIso`
- `ComputerSyncConfig.lastCheckedAt`
- `ComputerSyncConfig.lastSyncedAt`
- `ComputerSyncManifest.createdAt`

禁止把这些字段改成 `LocalDateTime` 或不带时区的字符串。

### 8.4 日账与今天销售

当前 `AppServices` 创建 `LedgerService` 时使用 `ZoneId.systemDefault()`，应改为统一的阿根廷时区：

```java
new LedgerService(saleRepository, ArgentinaTime.ZONE)
```

`SalesScreen` 和 `DailySummaryScreen` 当前使用 `LocalDate.now()`，应改为：

```java
ArgentinaTime.today()
```

这样即使手机系统时区设置为中国，阿根廷凌晨前后的销售仍会归入正确的阿根廷营业日。

### 8.5 销售单号时间

`CheckoutService.SALE_ID_TIME` 当前使用 `ZoneId.systemDefault()`。

推荐改法：让 `CheckoutService` 接收业务 `ZoneId`，由 `AppServices` 传入 `ArgentinaTime.ZONE`。不要让 `core` 模块反向依赖 Android `app/time` 包。

可选兼容构造：

```java
public CheckoutService(...existing args...) {
    this(...existing args..., ZoneId.systemDefault());
}

public CheckoutService(...existing args..., ZoneId businessZone) {
    ...
}
```

App 正式装配必须调用带 `ArgentinaTime.ZONE` 的构造函数。core 测试可以传固定时区，避免测试结果受电脑时区影响。

### 8.6 手机端时间测试

新增纯 Java 测试，至少覆盖：

- UTC 转阿根廷时间。
- 跨日期边界转换。
- 空字符串和非法 ISO 不崩溃。
- `LedgerService` 在阿根廷日期边界按正确日期查询。
- 销售单号使用传入的业务时区，而不是系统默认时区。

## 9. 手机端前端任务：所有时间统一展示

### 9.1 导入/同步页

修改 `ImportScreen`：

- “上次检查”使用 `ArgentinaTime.formatIso(config.lastCheckedAt())`。
- “上次同步”使用 `ArgentinaTime.formatIso(config.lastSyncedAt())`。
- manifest 摘要时间使用 `ArgentinaTime.formatIso(manifest.createdAt())`。
- 最近 5 次导入快照使用 `ArgentinaTime.formatIso(snapshot.importedAtIso())`。

### 9.2 销售与日账页

- 如果交易列表或交易详情显示 `Sale.createdAt`，必须通过 `ArgentinaTime.formatInstant()`。
- “今天”对应 `ArgentinaTime.today()`，不能使用设备默认日期。
- 后续新增任何用户可见时间，必须复用 `ArgentinaTime`。

### 9.3 UI 边界

- UI 只负责调用格式化工具和显示结果。
- UI 不直接做 `minusHours(3)`。
- UI 不解析 UTC 字符串。
- UI 不修改保存的数据。

## 10. 手机端后端任务：购物车同商品合并

### 10.1 唯一修改入口

主要修改：

```text
android-emergency-pos/core/src/main/java/com/espsa/mobilepos/core/checkout/Cart.java
```

不要分别修改扫码、搜索、条码按钮的合并逻辑。

### 10.2 推荐实现流程

将 `Cart.addProduct(Product product, int quantity)` 改为：

1. 校验 `product` 不为空。
2. 校验 `quantity > 0`。
3. 如果 `product.isManualPriceProduct()`，直接创建新行，不查找合并目标。
4. 遍历现有 `lines`。
5. 找到 `line.product().id().equals(product.id())` 的正式商品行。
6. 使用安全加法计算新数量。
7. 通过 `line.withQuantity(newQuantity)` 生成更新行。
8. 在原索引位置替换，保持购物车顺序和行 ID。
9. 返回更新后的 `CartLine`。
10. 如果没有找到，创建新 `CartLine` 并追加。

伪代码：

```java
public CartLine addProduct(Product product, int quantity) {
    Objects.requireNonNull(product, "product");
    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be greater than zero");
    }

    if (!product.isManualPriceProduct()) {
        for (int index = 0; index < lines.size(); index++) {
            CartLine existing = lines.get(index);
            if (sameProduct(existing.product(), product)) {
                int mergedQuantity = Math.addExact(existing.quantity(), quantity);
                CartLine updated = existing.withQuantity(mergedQuantity);
                lines.set(index, updated);
                return updated;
            }
        }
    }

    CartLine added = new CartLine(product, quantity);
    lines.add(added);
    return added;
}
```

建议私有函数：

```java
private boolean sameProduct(Product left, Product right) {
    return left.id().equals(right.id());
}
```

### 10.3 必须保留的状态

使用 `existing.withQuantity(...)` 会自然保留：

- `CartLine.id`
- 商品快照
- `manualUnitPrice`
- `lineDiscount`

禁止通过 `new CartLine(product, mergedQuantity)` 替换已有行，因为那会丢失手动价格、折扣和行 ID。

### 10.4 自动促销行为

合并后 `DefaultPriceCalculator` 会根据新数量重新计算数量促销。

示例：

```text
商品促销：买 3 件使用促销价
购物车原数量：2
再次扫码：+1
合并后数量：3
预览价格：自动进入数量促销
```

不要在 `Cart` 中计算价格或促销；`Cart` 只维护商品行与数量。

### 10.5 手动 almacen 商品

`CheckoutService.addManualAlmacenItem()` 创建的是手动价格商品。

规则：

```text
手动商品 $1000 x1
再次输入手动商品 $1000 x1
结果仍为两行
```

原因：两次手动输入可能代表不同散装商品，只有价格相同不能证明它们是同一商品。

## 11. 手机端前端任务：购物车显示与交互

正常情况下，core 修改后现有所有入口会自动获得合并行为：

- 扫码加入。
- 手动输入条码加入。
- 搜索结果选择加入。
- 其他调用 `cart.addProduct()` 的正式商品入口。

前端 Agent 只需检查：

- 每次加入后页面重新渲染。
- 同一正式商品只显示一行。
- 数量文本显示合并值，例如 `x2`。
- 小计按新数量更新。
- 操作弹窗继续引用合并后的原行 ID。
- 删除、减数量、改数量、手动改价、折扣和撤回调整仍正常。

如果 UI 已正确调用 `render()` 或 refresh，不要为了“看起来有改动”而重写页面。

## 12. 购物车自动化测试

在 `CoreSmokeTest` 或独立 `CartTest` 中至少覆盖：

1. 同一正式商品连续加入两次，`cart.lines().size() == 1`。
2. 数量 `1 + 1 == 2`。
3. 数量 `2 + 3 == 5`。
4. 不同商品保持两行。
5. 同商品合并后行 ID 不变。
6. 同商品合并后手动价格保留。
7. 同商品合并后单行折扣保留。
8. 合并达到促销门槛后自动促销生效。
9. 两个手动 `almacen` 商品保持两行。
10. 数量 0 或负数被拒绝。
11. 数量溢出通过 `Math.addExact` 被拒绝，不产生负数量。
12. `CheckoutService.addProductByBarcode()` 重复调用也只生成一行。

## 13. 多 Agent 分工边界

### 13.1 电脑端后端 Agent

负责：

- `time_display.py`
- 时间解析、时区转换和单元测试

不负责：

- PySide6 控件布局
- Android 时间工具
- 购物车逻辑
- manifest 存储格式变更

### 13.2 电脑端前端 Agent

负责：

- controller 和主窗口中用户可见时间接入统一格式化器

不负责：

- 自己实现时区换算
- 修改 manifest 和日志 JSON
- 修改备份文件名

### 13.3 手机端后端 Agent

负责：

- `ArgentinaTime`
- AppServices 的阿根廷业务时区装配
- Cart 同商品合并
- core/app 测试

不负责：

- 把格式化逻辑写进页面
- 修改鸣盛导入字段
- 修改折扣和促销公式

### 13.4 手机端前端 Agent

负责：

- 同步、快照、manifest、销售时间的展示接入
- 购物车合并后的 UI 回归检查

不负责：

- 在扫码/搜索按钮中实现合并
- 修改购物车业务规则
- 改写 UTC 存储值

## 14. 模块化和函数拆分强制提示词

所有 Agent 必须遵守：

> 保持模块化和函数拆分。时间存储、时间解析、时区转换、UI 展示、购物车合并、价格计算必须分别放在各自职责模块中。不要在页面类中直接解析 ISO 时间或写 UTC-3 运算，不要在扫码、搜索、条码输入三个入口复制同商品合并逻辑。电脑端统一使用 time_display，手机端统一使用 ArgentinaTime，购物车统一由 core/Cart 决定是否合并。新增函数保持短小、单一职责、可单测，不进行与本轮无关的重构。

## 15. 验收命令

### 15.1 电脑端

```powershell
cd E:\手机收银软件开发\pc-sync-tool
& 'E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe' -m unittest discover -s tests -v
& 'E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe' -m compileall -q src tests
```

### 15.2 手机端

1. 同步源码到 `E:\AndroidEmergencyPos`。
2. 运行 `CoreSmokeTest`。
3. 运行新增时间和购物车测试。
4. 构建 Debug APK。
5. 检查项目 `dist` APK 与构建输出 SHA-256 一致。

## 16. 手工验收清单

### 16.1 时间

- 电脑端最近备份显示 `ART`。
- 电脑端最近请求显示 `ART`。
- 电脑端日志列表显示 `ART`。
- 手机端上次检查显示 `ART`。
- 手机端上次同步显示 `ART`。
- 手机端 manifest 时间显示 `ART`。
- 手机端最近导入快照显示 `ART`。
- 手机系统切换成中国时区后，以上时间仍显示阿根廷时间。
- 原始 manifest 和事件日志仍保存 UTC `Z` 时间。

### 16.2 购物车

- 扫描商品 A 两次，显示一行 `A x2`。
- 输入商品 A 条码三次，显示一行 `A x3`。
- 搜索选择商品 A 后再扫码 A，仍是一行并累加。
- 商品 A 手动改价后再次加入，数量累加且手动价保留。
- 商品 A 加单行折扣后再次加入，数量累加且折扣保留。
- 商品 A 数量累加达到促销门槛时，促销自动生效。
- 商品 A 和商品 B 保持两行。
- 两次手动 `almacen` 商品保持两行。
- 减数量、删除、撤回改价和折扣仍正常。

## 17. 完成标准

只有同时满足以下条件才算完成：

- 所有用户可见同步/备份/导入时间均为阿根廷时间并带 `ART`。
- UTC 存储与同步协议没有改变。
- 阿根廷日期边界不受设备系统时区影响。
- 同一正式商品所有加入入口最终只保留一行。
- 合并数量、行 ID、手动价格和单行折扣正确保留。
- 手动 `almacen` 商品不合并。
- 电脑端测试、core 测试、Android 构建全部通过。
- 新 EXE/ZIP 和 APK 在代码验收后重新生成。
- 未修改鸣盛软件和鸣盛原始数据库。

## 18. 可直接交给 Agent 的提示词

### 18.1 电脑端后端 Agent

```text
阅读 修改方案/argentina_time_and_cart_merge_plan.md。
你负责 pc-sync-tool 的阿根廷时间后端工具和测试。新增独立 time_display.py，把 UTC/带偏移 ISO 时间安全转换成 yyyy-MM-dd HH:mm:ss ART。优先使用 America/Argentina/Buenos_Aires，并为 Windows/PyInstaller 缺少时区数据库提供固定 UTC-03 后备。不要修改 manifest、日志 JSON、备份时间和鸣盛数据库。保持模块化和函数拆分，完成后运行完整 Python 测试与 compileall。
```

### 18.2 电脑端前端 Agent

```text
阅读 修改方案/argentina_time_and_cart_merge_plan.md。
你负责 pc-sync-tool 用户可见时间接入。等待 time_display.py 接口稳定后，将最近备份、最近请求和日志列表的原始时间统一通过 format_argentina_time() 展示。UI 不解析 ISO，不直接减 3 小时，不修改存储数据。保持模块化，完成后补充 UI/展示测试。
```

### 18.3 手机端后端 Agent

```text
阅读 修改方案/argentina_time_and_cart_merge_plan.md。
你负责 android-emergency-pos 的阿根廷业务时区和购物车合并。新增 ArgentinaTime，保持 UTC Instant/ISO 存储，仅提供阿根廷显示和 today()。AppServices、LedgerService 和 CheckoutService 使用 America/Argentina/Buenos_Aires。修改 core/Cart.addProduct：正式商品按 product.id 合并数量，保留原行 ID、手动单价和折扣；手动 almacen 商品不合并。合并逻辑只放 core，不写入 UI。补齐日期边界、重复商品、促销、调整保留和手动商品测试。
```

### 18.4 手机端前端 Agent

```text
阅读 修改方案/argentina_time_and_cart_merge_plan.md。
你负责 android-emergency-pos 的时间展示和购物车 UI 回归。将上次检查、上次同步、manifest、导入快照和销售时间统一调用 ArgentinaTime；今天销售和日账使用 ArgentinaTime.today()。不要解析 ISO 或直接做 UTC-3。购物车合并由 core 完成，前端只确认重复扫码/输入/搜索后重新渲染为单行累加数量，并回归改价、折扣、删除和减数量。
```

### 18.5 验收 Agent

```text
阅读 修改方案/argentina_time_and_cart_merge_plan.md，不做实现。
检查 UTC 是否仍用于存储和传输，所有用户可见时间是否统一为 ART；检查购物车合并是否只在 core/Cart 实现，正式商品按 product.id 合并并保留调整，手动 almacen 不合并。运行电脑端完整测试、core smoke、时间/购物车测试和 Android 构建。最后检查新 EXE/ZIP/APK 时间戳，并按文档完成时区切换和重复商品手工验收。
```
