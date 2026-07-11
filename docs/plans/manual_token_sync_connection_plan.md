# 电脑端与手机端手动 Token 连接替代二维码方案

更新时间：2026-07-11  
适用项目：`pc-sync-tool` + `android-emergency-pos`  
任务类型：电脑端前端/后端、手机端前端/后端、打包验收  
目标读者：电脑端 agent、手机端后端 agent、手机端前端 agent、验收 agent

## 1. 背景

电脑端同步工具已经完成 PySide6 桌面程序、HTTP 服务、Token 鉴权、定时只读备份、手机端主动拉取同步等功能。  
当前连接方式使用二维码展示 `mobilepos-sync://setup?...`，但在另一台电脑上出现二维码显示不完整、页面缩放异常的问题。

本轮目标是彻底降低连接复杂度：取消二维码作为主流程，改成电脑端直接显示 `电脑IP + 端口 + Token`，手机端手动输入并测试连接。

重要边界：

- 不修改鸣盛软件任何文件。
- 不修改鸣盛数据库原文件。
- 电脑端只读读取源数据库，并复制备份副本到工具自己的目录。
- HTTP 同步协议保持稳定，手机端仍通过现有 manifest 和 latest db 接口拉取。
- 本轮只替换“连接配置入口”，不重做数据库导入、备份、hash 校验和商品映射逻辑。

## 2. 总体方案

电脑端显示三项连接信息：

```text
电脑IP：192.168.1.35
端口：8765
Token：WSWSBWQM
```

手机端在“电脑同步”区域提供“手动连接电脑工具”按钮，弹窗输入：

```text
电脑IP
端口
Token
```

点击“保存并测试连接”后：

1. 手机端校验输入。
2. 保存为 `ComputerSyncConfig`。
3. 调用现有 `ComputerSyncService.testConnection(...)`。
4. 成功则显示连接成功并刷新同步状态。
5. 失败则显示具体错误，并保留输入方便用户修正。

HTTP 访问方式保持不变：

```text
GET http://{host}:{port}/health.json?token={token}
GET http://{host}:{port}/manifest.json?token={token}
GET http://{host}:{port}/latest.db?token={token}
```

## 3. 推荐开发顺序

建议按以下顺序开发，方便多 agent 分工和分阶段验收：

1. 手机端后端 agent：新增手动配置 API，复用现有 `ComputerSyncConfig`。
2. 手机端前端 agent：把扫码入口替换成手动输入弹窗。
3. 电脑端后端 agent：整理连接信息生成逻辑，弱化或移除二维码 URL 逻辑。
4. 电脑端前端 agent：删除二维码卡片，改成连接信息卡片。
5. 打包 agent：清理 `qrcode` 依赖和 PyInstaller 配置，重新打包 exe。
6. 验收 agent：跑单元/烟测、构建 APK、打包 exe、真实两机局域网联调。

其中第 1、2 步可以优先做，因为手机端只要能手动保存 `host + port + token`，当前 HTTP 服务即可继续工作。

## 4. 模块化和函数拆分要求

所有 agent 必须遵守：

- 不要把新逻辑全部塞进一个大方法。
- UI 只负责收集输入、展示状态和触发动作。
- 业务校验、配置保存、HTTP 测试放在 service/controller 层。
- 可复用的 UI 控件创建逻辑放到现有 helper，避免复制粘贴。
- 错误提示集中从业务异常转换，不要在多个页面散落同一段判断。
- 保持电脑端和手机端分离；保持前端和后端/业务层分离。

可直接给 agent 的提示词：

```text
请保持模块化开发，不要把所有逻辑写进页面类或一个大函数。
后端/业务层只负责配置校验、保存、HTTP 测试和状态返回。
前端/UI 层只负责输入框、按钮、弹窗和结果展示。
把重复的输入校验、连接信息格式化、错误提示格式化拆成小函数。
不要修改鸣盛软件和鸣盛数据库原文件；电脑端只能只读源文件并写入工具自己的备份目录。
```

## 5. 电脑端后端任务

项目目录：

```text
pc-sync-tool/
```

重点文件：

```text
pc-sync-tool/src/config.py
pc-sync-tool/src/ui/controller.py
pc-sync-tool/src/qr_code.py
pc-sync-tool/src/app.py
pc-sync-tool/requirements.txt
pc-sync-tool/scripts/build_exe.ps1
```

### 5.1 保留现有 HTTP 鉴权

不修改：

- `SyncHttpService`
- token query 校验
- `/health.json`
- `/manifest.json`
- `/latest.db`

这些接口已经能满足手动连接方式。

### 5.2 新增连接信息格式化

建议在 `UiController` 或独立模块中新增方法：

```python
def connection_host(self) -> str
def connection_port(self) -> int
def connection_token(self) -> str
def connection_summary(self) -> str
```

推荐输出：

```text
电脑IP：192.168.1.35
端口：8765
Token：WSWSBWQM
```

用途：

- 前端显示。
- “复制全部连接信息”按钮。
- 托盘菜单复制。
- 命令行调试输出。

### 5.3 弱化或删除二维码 URL

推荐做法：

- 删除 `qr_code.py`，或保留但不再被 UI 调用。
- `app.py --print-setup-url` 可以改为兼容输出连接信息，或者新增 `--print-connection-info`。
- 如果保留旧参数，输出内容不要再强调二维码。

兼容建议：

```text
--print-connection-info    输出 IP、端口、Token
--print-setup-url          可保留一版，避免旧脚本报错，但不作为主流程
```

### 5.4 清理依赖

如果电脑端 UI 不再生成二维码：

- 从 `requirements.txt` 移除 `qrcode[pil]`。
- 从 `build_exe.ps1` 移除 `--hidden-import qrcode.image.pil`。
- 删除 `main_window.py` 中对 `qrcode`、`PIL`、`QPixmap`、`io` 的依赖。

注意：虚拟环境里已安装的 `qrcode` 不需要强行卸载；只要项目依赖和打包脚本不再要求即可。

## 6. 电脑端前端任务

重点文件：

```text
pc-sync-tool/src/ui/main_window.py
pc-sync-tool/src/ui/controller.py
```

### 6.1 删除二维码卡片

删除或替换：

```python
_qr_group()
refresh_qr()
_qr_pixmap()
qr_label
qr_frame
qr_status_label
```

状态区里的“二维码”字段改为“连接信息”或“手机连接”。

### 6.2 新增“手机连接信息”卡片

推荐卡片标题：

```text
手机连接信息
```

卡片内容：

- 当前电脑 IP。
- HTTP 端口。
- Token。
- 当前 HTTP 状态。
- 简短提示：手机和电脑必须在同一局域网。

按钮：

- `复制全部连接信息`
- `复制IP`
- `复制Token`
- `重新生成Token`

显示建议：

- IP、端口、Token 用只读 `QLineEdit`，方便复制。
- Token 可用等宽字体或普通只读输入框。
- 不要再依赖固定 220x220 图片区域，避免 Windows 缩放问题。

### 6.3 状态文字调整

原来的 `qr_status_text()` 建议改名：

```python
connection_status_text()
```

状态规则：

- HTTP 未运行：`连接信息已生成，但 HTTP 服务未运行`
- HTTP 运行且绑定一致：`可用，指向 {host}:{port}`
- HTTP 运行但绑定不一致：`需检查：配置 {host}:{port}，服务 {actual_host}:{actual_port}`

### 6.4 Token 重新生成提示

原提示中“需要重新扫码连接”改成：

```text
重新生成后，手机端需要重新输入新的 Token。
```

## 7. 手机端后端任务

项目目录：

```text
android-emergency-pos/
```

重点文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncService.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncConfig.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncClient.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncStore.java
```

### 7.1 新增手动配置方法

在 `ComputerSyncService` 中新增：

```java
public ComputerSyncConfig configureManual(
        Context context,
        String host,
        int port,
        String token
) throws ComputerSyncException
```

职责：

1. trim host 和 token。
2. 校验 host 非空。
3. 校验 port 在 `1..65535`。
4. 校验 token 非空。
5. 创建 `ComputerSyncConfig`。
6. 调用 `configured()` 二次确认。
7. 保存到 `ComputerSyncStore`。
8. 返回保存后的 config。

不要让 UI 直接 new `ComputerSyncConfig` 并保存。

### 7.2 保留或降级扫码解析

`configureFromSetupUri(...)` 可以保留，避免旧代码或测试直接失败。  
但 UI 不再调用扫码入口。

错误提示需要改掉：

```text
访问令牌不正确，请重新扫码
```

改成：

```text
访问令牌不正确，请检查电脑端 Token
```

类似“二维码缺少 host/port/token”的错误也改成“连接信息缺少 IP、端口或 Token”。

### 7.3 测试点

在现有 smoke test 或 sync 测试中覆盖：

- host 为空，失败。
- port 为 0，失败。
- port 大于 65535，失败。
- token 为空，失败。
- 合法 host/port/token 保存成功。
- 保存后 `config.baseUrl()` 正确。

## 8. 手机端前端任务

重点文件：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/screens/ImportScreen.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/Views.java
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/MainActivity.java
```

### 8.1 替换扫码按钮

当前按钮：

```text
扫码连接电脑工具
```

替换为：

```text
手动连接电脑工具
```

点击后弹窗输入连接信息。

### 8.2 新增手动连接弹窗

建议在 `ImportScreen` 中拆成小函数：

```java
private void showManualComputerSyncDialog()
private View manualSyncForm(...)
private void saveAndTestManualSync(...)
private String cleanHost(String value)
private int parsePortInput(String value)
```

如果 `ImportScreen` 已经过大，可以新增独立 UI helper：

```text
android-emergency-pos/app/src/main/java/com/espsa/mobilepos/ui/ComputerSyncConfigDialog.java
```

推荐弹窗字段：

- 电脑IP，默认填当前 config.host。
- 端口，默认填当前 config.port，如果无配置则 `8765`。
- Token，默认填当前 config.token。

按钮：

- `取消`
- `保存并测试连接`

成功：

- Toast 或对话框显示 `连接成功`。
- 刷新当前页面。

失败：

- 显示错误原因。
- 不清空输入内容。

### 8.3 当前配置展示

电脑同步卡片继续展示：

- 状态：已配置/未配置。
- 地址：`http://host:port`。
- 上次检查。
- 上次同步。

可新增一行：

```text
Token：已设置
```

不要明文展示手机端已保存 token，避免旁人看到；电脑端可以显示 token，因为它是配置源。

### 8.4 清理扫码入口

如果 `ScanGateway` 只用于电脑同步二维码，本轮可以移除相关 UI 调用。  
如果扫码还被商品条码或其他功能使用，不要删除全局扫描能力，只移除“电脑同步扫码连接”按钮。

`ImportScreen.addScannedBarcode(...)` 可保留但不作为主流程；如果删除，要同步检查 `MainActivity` 中是否仍会调用。

## 9. 打包任务

电脑端修改完成后重新打包：

```powershell
powershell -ExecutionPolicy Bypass -File "E:\手机收银软件开发\pc-sync-tool\scripts\build_exe.ps1"
```

打包后检查：

```text
pc-sync-tool/dist/MobilePosSync/MobilePosSync.exe
pc-sync-tool/dist/MobilePosSync-windows.zip
```

如果已移除 `qrcode` 依赖，最终包体应比包含二维码依赖时更小。  
注意 exe 运行需要整个 `MobilePosSync` 文件夹，不能只复制单个 exe。

手机端修改完成后重新构建 APK：

```powershell
powershell -ExecutionPolicy Bypass -File "E:\AndroidEmergencyPos\scripts\build-debug-apk.ps1"
```

如本地构建目录仍使用 `E:\AndroidEmergencyPos` 镜像目录，先同步源码再构建，避免构建旧代码。

## 10. 验收标准

### 10.1 电脑端验收

- 启动电脑端工具，不再显示二维码区域。
- 能看到 IP、端口、Token。
- `复制全部连接信息` 能复制完整三项。
- `复制Token` 能单独复制 token。
- `重新生成Token` 后，界面 token 更新，HTTP 服务继续可用或自动重启。
- Windows 显示缩放 100%、125%、150% 下界面不出现内容截断或图片显示问题。
- 托盘菜单仍可打开工具、立即备份、复制连接信息、退出。
- 不修改鸣盛软件目录下任何文件。

### 10.2 手机端验收

- “电脑同步”区域不再要求扫码。
- 点击“手动连接电脑工具”能弹出输入框。
- 输入正确 IP、端口、Token 后，保存并测试成功。
- 输入错误 token，提示检查电脑端 Token。
- 输入错误 IP 或电脑端 HTTP 未运行，提示无法连接电脑同步工具。
- 保存成功后，“测试连接”“检查新版本”“立即同步”继续复用现有流程。
- 同步导入前仍有确认弹窗。
- 手机端本地有手动修改时，仍然二次确认覆盖。

### 10.3 端到端验收

1. 电脑和手机连接同一个 Wi-Fi 或同一局域网。
2. 电脑端选择鸣盛 `.db` 或鸣盛软件目录。
3. 电脑端执行一次备份。
4. 手机端手动输入电脑端显示的 IP、端口、Token。
5. 手机端测试连接成功。
6. 手机端检查新版本能看到 manifest。
7. 手机端立即同步后商品库更新。
8. 断开网络或改错 token 后，手机端能显示清晰错误。

## 11. 不做事项

本轮不要做：

- 不做二维码修复。
- 不做二维码缩放适配。
- 不做手机摄像头扫码优化。
- 不改 HTTP 路径和 manifest 格式。
- 不改鸣盛数据库解析规则。
- 不改商品导入映射逻辑。
- 不做电脑主动推送到手机。
- 不做公网同步、云同步、穿透、远程访问。

## 12. 风险和注意事项

- IP 可能因网络变化而变化，电脑端必须显示当前候选局域网 IP。
- 手机和电脑必须在同一局域网，否则无法连接。
- Windows 防火墙可能阻止手机访问电脑端端口，需要在真实联调时允许该 exe 访问专用网络。
- Token 重新生成后，手机端必须重新输入新 token。
- 如果电脑端绑定 `127.0.0.1`，手机无法访问；真实联调必须选择局域网 IP，例如 `192.168.x.x`。

## 13. 建议提交拆分

为了方便回滚和验收，建议分成以下提交或开发任务：

1. `android sync manual config backend`
2. `android sync manual config ui`
3. `pc sync connection info ui`
4. `pc sync remove qr dependency`
5. `package and validate manual sync builds`

每个任务完成后都要写明：

- 修改了哪些文件。
- 是否影响 HTTP 协议。
- 是否影响鸣盛文件安全边界。
- 运行了哪些测试。
- 未覆盖的人工验收项。
