# 修改方案进度索引

最后同步：2026-07-19（阿根廷时间）

## 已完成开发并验收

- `product_editing_plan.md`
- `search_optimization_plan.md`
- `ui_checkout_search_font_improvement_plan.md`
- `text_scale_ui_controls_fix_plan.md`
- `ui_cards_and_multi_format_import_plan.md`
- `pc_sync_http_tool_plan.md`
- `manual_token_sync_connection_plan.md`
- `computer_phone_sync_connection_fix_plan.md`
- `argentina_time_and_cart_merge_plan.md`

以上方案对应的功能已经进入 Android 手机端或 `pc-sync-tool`，并在项目日志中记录了测试、构建和打包证据。阿根廷时间与购物车合并方案的剩余事项是目标手机和收银电脑上的人工联调，不是代码实现空缺。

## 当前处于分阶段实施和证据收集

- `ms2011_live_product_promotion_sync_plan.md`
- `ms2011_live_product_promotion_sync_implementation_plan.md`

这两份 MS2011 方案已经进入 `L3/S10`，但当前按用户要求暂停，整体仍不能标记为“已实现”。EV-01、EV-03、EV-02、EV-04 已通过；EV-05 目标取证结论为 `WRITE_CAPABILITY_PRESENT`，所以 G0B 继续锁定。S02 至 S09 已完成；S10 的 MB-07、CB-01、CB-02、CB-03 和主机侧离线 G4 已 PASS。MF-05 的 `MainActivity.java` 与生命周期测试只有部分修改，尚未编译/验收；MF-02 因 stale 年龄和连续失败阈值未冻结而阻塞，MF-03 未解锁。当前电脑端完整回归 `226/226` 通过，`compileall` 通过；本批次未构建 Android/PC 制品、未连接真实 SQL、未生成生产快照或进行真机/LAN 验收。

在剩余证据完成前：

- 除已审批的固定 QueryId 只读证据探针外，正式应用不得连接鸣盛数据库；任何数据库写入都禁止。
- 不猜测促销公式，不把未验证促销静默当作原价结账。
- 不修改现有 v1 HTTP、AGT_MAIN 导入和离线商品库流程。
- 不宣称 MS2011 商品/促销实时同步已经完成。

## 统一验收入口

完整状态见：`docs/IMPLEMENTATION_STATUS.md`、`docs/PROJECT_STATUS.md` 和 `docs/PROJECT_LOG.md`。

当前已知剩余工作是 MF-05 验收、MF-02 阈值决定、后续 MF-03 和 S10 阶段门禁，以及目标设备端到端联调、由外部管理员另行配置并人工复核的生产只读身份和逐类促销黑盒语义验证。G0B 未通过前，真实自动读取和生产发布保持禁用；工具不会自动修改 SQL 权限。
