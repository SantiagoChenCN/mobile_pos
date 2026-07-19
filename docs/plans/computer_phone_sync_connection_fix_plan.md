# 电脑端与手机端同步连接失败修复方案

更新时间：2026-07-11  
任务类型：电脑端后端、电脑端前端、手机端后端、手机端前端、打包与端到端验收  
目标读者：多 Agent 开发人员、验收 Agent

## 1. 本轮目标

修复手机端手动填写电脑 IP、端口和 Token 后，点击“保存并测试连接”出现以下提示的问题：

> 电脑同步失败，无法读取电脑同步工具响应

修复后的目标：

1. 手机与电脑位于同一局域网时，可以通过电脑局域网 IPv4、端口和 Token 建立连接。
2. 电脑端 HTTP 服务能够接受局域网内其他设备的请求，而不是只允许电脑自身访问。
3. Android 可以访问本项目采用的局域网明文 HTTP 服务。
4. 连接失败时显示可执行的具体原因，不再把所有异常都显示为“无法读取响应”。
5. 保持电脑端、手机端分离，保持前端/UI 与后端/业务逻辑分离。
6. 不修改、不移动、不锁定、不覆盖鸣盛软件及其原始数据库文件。

## 2. 已确认的根因

### 2.1 手机端阻止明文 HTTP

当前 Android 项目：

- `compileSdk 35`
- `targetSdk 35`
- 同步地址使用 `http://电脑IP:端口`
- `AndroidManifest.xml` 尚未允许明文 HTTP

在较新 Android 系统中，明文 HTTP 默认可能被系统安全策略阻止。当前异常在 `ComputerSyncClient.getJson()` 中被统一包装成“无法读取电脑同步工具响应”，导致用户看不到真正原因。

### 2.2 电脑端默认绑定 `127.0.0.1`

电脑端当前默认配置为：

```text
selected_host = 127.0.0.1
port = 8765
```

服务启动时又直接绑定 `selected_host`。当地址为 `127.0.0.1` 时，只有电脑自身可以访问，手机无法连接。

`127.0.0.1` 在手机上代表手机自身，并不代表收银电脑。

### 2.3 Windows 防火墙可能阻止入站连接

即使服务正确监听局域网地址或 `0.0.0.0`，Windows 防火墙仍可能阻止手机访问 TCP 端口 `8765`。程序不能静默修改防火墙；如需放行，必须向用户明确说明并由用户确认。

### 2.4 当前错误提示粒度不足

以下问题目前可能被显示成同一句“无法读取响应”：

- Android 明文 HTTP 被阻止。
- 电脑工具未启动。
- HTTP 服务未启动。
- IP 地址填错。
- 端口填错或未监听。
- Windows 防火墙阻止连接。
- 手机与电脑不在同一局域网。
- 路由器开启客户端隔离/AP 隔离。
- 连接超时、连接被拒绝、DNS/主机名解析失败。
- 电脑返回了非 JSON 或不完整响应。

Token 错误不属于本次已观察到的主要根因。Token 错误应收到 HTTP 403，并显示单独的令牌错误。

## 3. 修复后的架构约定

### 3.1 监听地址与展示地址必须分离

电脑端定义两个不同概念：

```text
bind_host       = 0.0.0.0
advertised_host = 用户选择的局域网 IPv4，例如 192.168.1.35
```

- `bind_host`：HTTP 服务实际监听地址，固定使用 `0.0.0.0`，允许所有本机 IPv4 网卡接收请求。
- `advertised_host`：展示给用户、供手机输入的电脑局域网地址。
- 界面绝不能把 `0.0.0.0` 或 `127.0.0.1` 作为推荐的手机连接 IP。
- `/health` 返回的 `host` 应为 `advertised_host`，不能返回 `0.0.0.0`。

### 3.2 保持现有 HTTP 协议

本轮不修改现有接口和 Token 传递规则：

```text
GET /health?token=TOKEN
GET /manifest.json?token=TOKEN
GET /latest.db?token=TOKEN
```

健康检查成功响应：

```json
{
  "ok": true,
  "app": "MobilePosSync",
  "version": "1.0",
  "host": "192.168.1.35",
  "port": 8765
}
```

### 3.3 Android 局域网 HTTP 策略

本项目当前架构明确使用局域网 HTTP，因此手机端应在应用配置中允许明文 HTTP。

推荐最小改动：

```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

注意：

- 不允许把电脑端端口映射到公网。
- 不允许关闭 Token 校验。
- 不允许把 Token 写入普通 UI 日志、异常堆栈展示或电脑端请求日志。
- 后续如果增加互联网同步，应单独设计 HTTPS，不应沿用本轮局域网 HTTP 配置。

## 4. 推荐开发顺序

电脑端后端和手机端后端可以并行开发，前端在对应后端接口稳定后接入。

1. 手机端后端 Agent：允许局域网 HTTP，拆分连接异常类型，补充配置校验和测试。
2. 电脑端后端 Agent：分离监听地址与展示地址，增加网络诊断结果，补充 HTTP 测试。
3. 电脑端前端 Agent：展示真实服务状态、局域网地址和防火墙提示。
4. 手机端前端 Agent：接入结构化错误结果，优化输入校验和失败提示。
5. 两端分别执行自动化测试与构建。
6. 重新打包电脑端 EXE，重新构建并安装 APK。
7. 验收 Agent：先做电脑本机测试，再做真实手机与电脑局域网联调。

禁止只修改错误文案后就宣布完成。必须修复 Android HTTP 策略和电脑端监听地址。

## 5. 电脑端后端开发任务

涉及范围：

- `pc-sync-tool/src/http_server.py`
- `pc-sync-tool/src/ui/controller.py`
- `pc-sync-tool/src/config.py`
- `pc-sync-tool/src/connection_info.py`
- `pc-sync-tool/src/ui/network.py`
- 必要时新增独立网络诊断模块
- `pc-sync-tool/tests/`

### 5.1 分离 HTTP 监听地址和连接展示地址

要求：

1. `SyncHttpService` 默认或由 controller 明确传入 `bind_host="0.0.0.0"`。
2. `UiController.start_service()` 不再使用 `config.selected_host` 作为服务监听地址。
3. `config.selected_host` 继续表示供手机连接的 `advertised_host`。
4. `/health` 中的 `host` 返回 `config.selected_host`。
5. 服务状态应能同时返回：

```text
监听：0.0.0.0:8765
手机连接：192.168.1.35:8765
```

建议增加常量或小型值对象，避免继续混用两个地址：

```python
HTTP_BIND_HOST = "0.0.0.0"
```

不要为了该改动重写备份、manifest 或文件复制模块。

### 5.2 改进局域网地址发现

`candidate_lan_hosts()` 应满足：

- 返回可用 IPv4 地址。
- 排除 `127.0.0.1` 作为默认推荐项。
- 排除 `169.254.x.x` 自动分配地址。
- 优先显示常见局域网地址：`192.168.x.x`、`10.x.x.x`、`172.16.x.x` 至 `172.31.x.x`。
- 没有找到局域网地址时，可以保留 `127.0.0.1` 仅用于电脑本机诊断，但必须标注“手机不可用”。
- 不要把 `0.0.0.0` 放进可复制的连接信息。

首次生成配置时，如果发现有效局域网 IPv4，可将其作为默认 `selected_host`。如果不能可靠确定，保持配置可保存，但 UI 必须明确提示用户选择局域网地址。

### 5.3 增加后端网络诊断模型

建议新增独立模块，例如：

```text
pc-sync-tool/src/network_diagnostics.py
```

职责仅包括：

- 判断 HTTP 服务是否正在运行。
- 返回实际监听地址和端口。
- 判断展示地址是否为回环地址或无效地址。
- 在电脑本机调用 `/health` 验证服务响应。
- 返回结构化诊断结果给 UI。

建议结果字段：

```python
NetworkDiagnosticResult(
    service_running=True,
    local_health_ok=True,
    bind_host="0.0.0.0",
    advertised_host="192.168.1.35",
    port=8765,
    warning_code="",
    message="电脑端服务运行正常",
)
```

诊断日志不得包含完整 Token，也不得读取或修改鸣盛源数据库。

### 5.4 Windows 防火墙处理边界

本轮推荐行为：

- 程序检测到服务本机正常、手机仍无法访问时，向 UI 返回防火墙检查提示。
- 可以提供明确的人工操作说明或“复制防火墙命令”能力。
- 如果增加自动放行按钮，点击后必须再次确认，并由 Windows 管理员授权。
- 程序启动时不得静默添加、删除或修改防火墙规则。
- 不得为了联调而关闭整个 Windows 防火墙。

如增加规则，规则范围只允许本工具使用的 TCP 端口或当前 EXE，不得创建全端口放行规则。

### 5.5 HTTP 请求日志

健康检查到达电脑端后，应写入不含 Token 的简短事件，例如：

```text
HTTP /health success from 192.168.1.80
HTTP request rejected: invalid token
```

不要记录查询字符串，因为查询字符串包含 Token。

### 5.6 电脑端后端测试

至少覆盖：

1. 服务默认监听 `0.0.0.0`。
2. `/health` 返回选中的局域网展示地址。
3. 正确 Token 返回 HTTP 200 和合法 JSON。
4. 错误 Token 返回 HTTP 403。
5. `127.0.0.1` 不会作为推荐手机连接地址。
6. `0.0.0.0` 不会出现在复制给手机的连接信息中。
7. 修改展示地址后，连接信息更新，但监听地址仍为 `0.0.0.0`。
8. 网络诊断不会访问鸣盛数据库源文件。

## 6. 电脑端前端开发任务

涉及范围：

- `pc-sync-tool/src/ui/main_window.py`
- 必要的独立 UI 小组件

### 6.1 连接信息卡片

卡片应显示：

- 电脑局域网 IP。
- HTTP 端口。
- Token。
- 服务状态：运行中/未运行/启动失败。
- 实际监听状态，例如 `0.0.0.0:8765`。

复制给手机的信息只能包含局域网 IP、端口和 Token，不得复制 `0.0.0.0` 或 `127.0.0.1`。

### 6.2 无效地址提示

当当前地址是以下值时，使用明显但不过度打扰的警告：

```text
127.0.0.1
0.0.0.0
169.254.x.x
```

建议文案：

> 当前地址不能供手机连接，请选择电脑的局域网 IP。

### 6.3 服务与防火墙提示

- HTTP 服务未运行：提示先启动服务。
- 端口被占用：显示端口占用错误，不显示模糊的“启动失败”。
- 本机健康检查成功但手机失败：提示检查 Windows 防火墙、同一 Wi-Fi 和路由器客户端隔离。
- UI 只调用 controller/diagnostics 提供的接口，不直接创建 socket，不直接操作防火墙。

### 6.4 电脑端前端验收

1. 工具启动后能看到服务是否运行。
2. 局域网 IP 与监听地址的用途不会混淆。
3. `127.0.0.1` 时不会引导用户复制给手机。
4. Token 不会出现在事件日志和错误详情中。
5. 窗口缩放后连接信息完整可见。

## 7. 手机端后端开发任务

涉及范围：

- `android-emergency-pos/app/src/main/AndroidManifest.xml`
- `app/sync/ComputerSyncClient.java`
- `app/sync/ComputerSyncService.java`
- `app/sync/ComputerSyncConfig.java`
- 必要时新增错误类型/结果模型
- `app/src/test/`

### 7.1 允许局域网 HTTP

在 `<application>` 中增加：

```xml
android:usesCleartextTraffic="true"
```

验收时必须确认最终 APK 合并后的 manifest 包含该配置，不能只检查源码。

### 7.2 拆分连接失败原因

建议新增枚举或结构化错误类型，例如：

```java
enum ComputerSyncFailureReason {
    CLEAR_TEXT_BLOCKED,
    CONNECTION_TIMEOUT,
    CONNECTION_REFUSED,
    UNKNOWN_HOST,
    INVALID_TOKEN,
    HTTP_ERROR,
    INVALID_RESPONSE,
    INVALID_CONFIG,
    UNKNOWN
}
```

`ComputerSyncClient` 负责把底层异常转换成稳定的错误类型，UI 不直接解析异常字符串。

建议映射：

| 后端错误 | 用户含义 |
| --- | --- |
| `CLEAR_TEXT_BLOCKED` | 应用网络配置错误，局域网 HTTP 被系统阻止 |
| `CONNECTION_TIMEOUT` | IP 不可达、防火墙拦截或不在同一网络 |
| `CONNECTION_REFUSED` | 电脑工具或 HTTP 服务未运行，或端口错误 |
| `UNKNOWN_HOST` | IP/主机地址格式错误 |
| `INVALID_TOKEN` | Token 不正确，电脑返回 HTTP 403 |
| `HTTP_ERROR` | 电脑返回其他非成功状态码 |
| `INVALID_RESPONSE` | 已连接电脑，但响应不是预期 JSON |
| `INVALID_CONFIG` | IP、端口或 Token 未正确填写 |

错误对象可携带内部 cause 供开发调试，但生产 UI 不显示堆栈，也不显示带 Token 的完整 URL。

### 7.3 手动连接配置校验

在 service/config 层完成校验：

- host 不能为空。
- port 必须在 `1..65535`。
- Token 不能为空并自动去除首尾空格。
- 明确拒绝 `127.0.0.1`、`localhost` 和 `0.0.0.0` 作为手机连接地址。
- 当前版本按 IPv4 联调；不要在未实现 IPv6 URL 括号处理时宣称支持 IPv6。

建议错误文案：

> 127.0.0.1 代表手机自身，请输入电脑工具显示的局域网 IP。

### 7.4 健康响应校验

`testConnection()` 不应只判断“收到了 JSON”，还应确认：

- HTTP 状态为 200。
- JSON 中 `ok == true`。
- `app == "MobilePosSync"`。
- 必要字段类型正确。

这样可以避免同端口上的其他 HTTP 服务被误判为电脑同步工具。

### 7.5 手机端后端测试

至少覆盖：

1. manifest 允许明文 HTTP。
2. 正确 `/health` 响应测试成功。
3. HTTP 403 映射为 `INVALID_TOKEN`。
4. 超时映射为 `CONNECTION_TIMEOUT`。
5. 连接拒绝映射为 `CONNECTION_REFUSED`。
6. 非 JSON 响应映射为 `INVALID_RESPONSE`。
7. `127.0.0.1`、`localhost`、`0.0.0.0` 被拒绝。
8. 正常局域网 IPv4、端口、Token 可以保存。
9. 错误输出不包含 Token。

## 8. 手机端前端开发任务

涉及范围：

- `android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ImportScreen.java`
- 必要的连接状态 UI helper

### 8.1 输入界面

保持现有手动输入方式：

- 电脑 IP。
- 端口。
- Token。
- “保存并测试连接”按钮。

输入框提示必须使用局域网示例，例如 `192.168.1.35`，不能使用 `127.0.0.1`。

### 8.2 结构化错误展示

UI 根据后端错误类型显示对应提示，不允许通过 `message.contains(...)` 判断错误类型。

推荐提示：

| 情况 | 手机端提示 |
| --- | --- |
| 超时 | 无法到达电脑，请确认手机和电脑在同一 Wi-Fi，并检查 Windows 防火墙 |
| 拒绝连接 | 已找到电脑，但同步服务未运行或端口不正确 |
| Token 错误 | Token 不正确，请重新输入电脑工具显示的 Token |
| 无效 IP | 请输入电脑工具显示的局域网 IP，不能填写 127.0.0.1 |
| 响应无效 | 已连接该地址，但它不是有效的 MobilePosSync 服务 |
| Android HTTP 配置异常 | 当前应用未允许局域网 HTTP，请安装修复后的 APK |

### 8.3 交互要求

- 测试期间禁用重复提交并显示轻量加载状态。
- 成功后显示电脑工具版本、IP 和端口。
- 失败后保留用户输入，方便修改，不能清空 Token。
- 离开页面时取消尚未完成的测试任务，避免过期回调弹窗。
- UI 不直接创建 HTTP 连接，只调用 `ComputerSyncService`。

## 9. 多 Agent 边界

### 9.1 电脑端后端 Agent

可以修改：

- Python HTTP 服务、配置、controller、网络诊断、测试。

不要修改：

- PySide6 页面布局。
- Android 项目。
- 鸣盛软件及数据库源文件。

### 9.2 电脑端前端 Agent

可以修改：

- PySide6 连接信息、状态和错误提示。

不要修改：

- HTTP 协议和备份逻辑。
- 直接读写 config JSON。
- 直接创建 HTTP 服务或 socket。
- 静默修改 Windows 防火墙。

### 9.3 手机端后端 Agent

可以修改：

- Android manifest、同步 client/service/config、错误模型、测试。

不要修改：

- 导入页视觉布局。
- 商品导入解析、收银、商品编辑等无关功能。

### 9.4 手机端前端 Agent

可以修改：

- 导入页手动连接弹窗、状态展示和错误提示。

不要修改：

- 自己实现 HTTP 请求。
- 自己解析底层异常文本。
- 修改电脑端协议。

### 9.5 验收 Agent

只负责检查代码、测试、构建和真实联调。发现问题后按电脑端/手机端、前端/后端归类，不直接把所有修复堆进同一个文件。

## 10. 模块化和函数拆分强制要求

所有开发 Agent 必须遵守以下提示词：

> 请保持模块化开发和函数拆分。不要把网络监听、地址发现、配置保存、HTTP 请求、异常分类、UI 状态和提示文案堆到一个页面类或一个大函数中。每个模块只负责一种职责，通过明确接口传递结构化数据。前端只负责输入、状态和展示；后端只负责配置校验、网络访问、诊断和错误分类。新增方法应短小、可单测、命名明确。不要进行与本轮连接修复无关的重构。

电脑端建议模块边界：

```text
http_server.py             HTTP 监听和接口响应
network.py                 局域网地址发现
network_diagnostics.py     服务与地址诊断
connection_info.py         手机连接信息格式化
controller.py              用例编排
main_window.py             UI 展示和用户操作
```

手机端建议模块边界：

```text
ComputerSyncClient         HTTP 请求和底层异常映射
ComputerSyncService        配置、健康检查用例编排
ComputerSyncConfig         连接配置模型
ComputerSyncFailureReason  稳定错误分类
ComputerSyncStore          本地配置保存
ImportScreen               输入和结果展示
```

## 11. 自动化验收

### 11.1 电脑端

在 `pc-sync-tool` 目录运行：

```powershell
python -m unittest discover -s tests -v
python -m py_compile src\*.py src\ui\*.py
```

检查监听状态：

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8765
```

预期 `LocalAddress` 为 `0.0.0.0` 或可接受局域网访问的地址，不能只有 `127.0.0.1`。

### 11.2 手机端

执行现有 core smoke test、app 同步测试和 Debug APK 构建。必须验证最终 APK 的合并 manifest，而不只检查源 manifest。

至少确认：

- APK 构建成功。
- core smoke test 通过。
- `ComputerSyncService` 配置测试通过。
- `ComputerSyncClient` 错误映射测试通过。
- APK 中允许局域网 HTTP。

## 12. 真实端到端验收顺序

### 12.1 电脑本机验收

1. 启动打包后的 `MobilePosSync.exe`。
2. 确认界面显示 HTTP 服务运行中。
3. 确认显示的是局域网 IPv4，而不是 `127.0.0.1`。
4. 确认端口处于监听状态。
5. 使用电脑本机请求 `/health`，确认返回 HTTP 200 和正确 JSON。
6. 使用错误 Token 请求，确认返回 HTTP 403。

### 12.2 手机浏览器隔离测试

在安装 APK 前，可以先用手机浏览器访问：

```text
http://电脑局域网IP:8765/health?token=电脑工具显示的Token
```

- 浏览器也打不开：优先检查电脑服务、IP、端口、防火墙、Wi-Fi 和 AP 隔离。
- 浏览器能打开但 App 失败：问题在 Android 应用网络策略或 App 请求代码。

测试 URL 包含 Token，测试后不要截图公开，也不要发送给无关人员。

### 12.3 手机 App 验收

1. 安装本轮重新构建的 APK，不能继续使用旧 APK。
2. 手机与电脑连接同一局域网。
3. 输入电脑工具显示的局域网 IP、端口和 Token。
4. 点击“保存并测试连接”。
5. 确认显示连接成功。
6. 检查 manifest。
7. 下载最新数据库副本。
8. 校验 SHA-256 并完成导入。
9. 确认整个过程只读取电脑工具生成的备份副本，没有修改鸣盛原始数据库。

### 12.4 失败场景验收

逐项制造并确认提示准确：

1. 停止电脑 HTTP 服务。
2. 输入错误端口。
3. 输入错误 Token。
4. 输入 `127.0.0.1`。
5. 手机切换到不同网络。
6. 临时阻止端口入站连接。
7. 在目标端口运行一个返回非 JSON 的普通 HTTP 服务。

每种场景必须得到不同且可执行的提示。

## 13. 完成标准

只有同时满足以下条件才能宣布完成：

- 电脑端不再只监听 `127.0.0.1`。
- 手机连接信息不显示或复制 `0.0.0.0`、`127.0.0.1`。
- Android 最终 APK 允许本项目的局域网 HTTP。
- 手机端可以区分超时、拒绝连接、Token 错误和无效响应。
- 电脑端和手机端自动化测试通过。
- 重新生成电脑端 EXE/ZIP 和手机端 APK。
- 在当前电脑完成一次真实手机联调。
- 在目标收银电脑完成一次真实手机联调。
- 同步全过程没有修改鸣盛软件及其原始数据库文件。

## 14. 可直接交给 Agent 的提示词

### 14.1 电脑端后端 Agent

```text
你负责 pc-sync-tool 的电脑端后端连接修复。

先阅读 docs/plans/computer_phone_sync_connection_fix_plan.md、现有 HTTP 服务、controller、配置和测试。

核心任务：
1. 将 HTTP 实际监听地址与展示给手机的局域网地址分离。
2. HTTP 服务监听 0.0.0.0，连接信息继续使用用户选中的局域网 IPv4。
3. 改进局域网地址发现和结构化网络诊断。
4. 补充健康检查、错误 Token、地址选择和监听行为测试。

必须保持模块化和函数拆分。不要把地址发现、HTTP 服务、诊断、配置和 UI 写进一个文件。不得修改、移动或锁定鸣盛软件及其数据库文件。不要修改 Android 或 PySide6 页面布局。完成后报告测试结果和仍需人工验收的项目。
```

### 14.2 电脑端前端 Agent

```text
你负责 pc-sync-tool 的 PySide6 前端连接状态修复。

先阅读 docs/plans/computer_phone_sync_connection_fix_plan.md，并等待电脑端后端接口稳定。

核心任务：
1. 分别显示实际监听地址和供手机输入的局域网地址。
2. 禁止把 127.0.0.1、0.0.0.0 或 169.254.x.x 作为推荐连接信息。
3. 展示服务未启动、端口占用、防火墙/同网段检查等明确提示。
4. 保证窗口缩放时连接信息完整可见。

必须保持模块化和函数拆分。UI 只调用 controller 和 diagnostics，不直接创建 socket、不直接修改配置文件、不静默修改防火墙、不接触鸣盛数据库文件。
```

### 14.3 手机端后端 Agent

```text
你负责 android-emergency-pos 的手机端同步后端修复。

先阅读 docs/plans/computer_phone_sync_connection_fix_plan.md、ComputerSyncClient、ComputerSyncService、ComputerSyncConfig 和 AndroidManifest.xml。

核心任务：
1. 允许当前局域网 HTTP 架构，并验证最终 APK 合并 manifest。
2. 将超时、拒绝连接、未知地址、HTTP 403、无效 JSON 等转换成结构化错误类型。
3. 拒绝 127.0.0.1、localhost 和 0.0.0.0 作为手机连接地址。
4. 校验 /health 确实来自 MobilePosSync。
5. 补充自动化测试。

必须保持模块化和函数拆分。不要把网络代码写进 ImportScreen，不要依赖 UI 文案判断错误类型，不要把 Token 放入日志。不要修改无关的导入、收银或商品编辑逻辑。
```

### 14.4 手机端前端 Agent

```text
你负责 android-emergency-pos 导入页的电脑连接 UI 修复。

先阅读 docs/plans/computer_phone_sync_connection_fix_plan.md，并等待手机端后端结构化错误接口稳定。

核心任务：
1. 保持 IP、端口、Token 手动输入。
2. 根据后端错误类型显示可执行的具体提示。
3. 测试期间防止重复提交，离开页面取消未完成任务。
4. 失败时保留输入，成功时显示电脑工具版本和连接地址。

必须保持模块化和函数拆分。UI 不直接发 HTTP 请求，不解析异常字符串，不修改电脑同步协议，不影响商品导入以外的页面。
```

### 14.5 验收 Agent

```text
你负责验收电脑端与手机端同步连接修复，不负责实现。

先阅读 docs/plans/computer_phone_sync_connection_fix_plan.md。

按以下顺序验收：
1. 静态检查电脑端监听地址与展示地址是否分离。
2. 静态检查 Android 最终 APK 是否允许局域网 HTTP。
3. 运行电脑端单元测试、Python 编译检查、Android 测试和 APK 构建。
4. 检查新 EXE/ZIP 和 APK 的时间戳。
5. 先做电脑本机 health 测试，再做手机浏览器测试，最后做 App 真实同步。
6. 在当前电脑和目标收银电脑分别联调。
7. 确认没有修改鸣盛软件及其原始数据库文件。

发现问题时必须按电脑端后端、电脑端前端、手机端后端、手机端前端分类报告，并提供文件和行号证据。
```
