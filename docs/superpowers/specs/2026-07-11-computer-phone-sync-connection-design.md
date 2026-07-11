# 手机端同步连接修复设计

## 范围

仅修改 Android 手机端同步后端和测试。不修改电脑端、导入页面视觉布局、商品导入解析或既有 HTTP 协议。

## 模块边界

- `ComputerSyncFailureReason` 定义稳定的连接失败分类。
- `ComputerSyncException` 携带失败分类与内部原因；调用方不需要解析异常文本。
- `ComputerSyncClient` 负责 HTTP 调用、响应验证和底层网络异常分类。
- `ComputerSyncService` 负责手动连接配置校验和健康检查用例编排。
- `ComputerSyncConfig` 继续只表示已清理的连接配置。
- `ui.sync.ComputerSyncErrorPresenter` 把同步异常映射为 UI 可展示的标题、说明和建议，不解析异常文本。

## 行为

- Manifest 显式允许本应用访问局域网明文 HTTP。
- 手动配置仅接受 IPv4、端口 `1..65535` 和非空 Token；拒绝 `127.0.0.1`、`localhost`、`0.0.0.0`。
- `/health` 必须是 HTTP 200，并包含 `ok: true`、`app: "MobilePosSync"`、有效的版本、IPv4 host 和端口。
- 客户端分别分类明文 HTTP 被阻止、超时、连接拒绝、主机无效、Token 无效、HTTP 失败、响应无效、配置无效和未知错误。
- 异常文本和测试输出不得包含 Token 或带 Token 的完整 URL。
- UI 通过 `ComputerSyncErrorPresentation` 消费稳定失败类型和双语文案。

## 验证

增加同步服务和客户端的可独立运行 smoke test，覆盖配置拒绝、健康响应校验和失败分类；构建 Debug APK 并检查合并后的 Manifest 包含明文 HTTP 配置。
