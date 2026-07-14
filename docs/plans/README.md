# 修改方案进度索引

最后同步：2026-07-14（阿根廷时间）

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

## 当前处于规划和证据收集

- `ms2011_live_product_promotion_sync_plan.md`
- `ms2011_live_product_promotion_sync_implementation_plan.md`

这两份 MS2011 方案已经完成架构拆分、模块边界、任务编号、只读安全约束和验收标准，但目前不能标记为“已实现”。正式开发前仍需取得：真实数据库只读 schema、商品/分类/单位/停用状态字段证据、促销字段含义、促销优先级/叠加规则、日期与时段边界、浮点金额舍入方式，以及真实收银电脑上的低干扰验证条件。

在这些证据完成前：

- 不连接或写入鸣盛数据库。
- 不猜测促销公式，不把未验证促销静默当作原价结账。
- 不修改现有 v1 HTTP、AGT_MAIN 导入和离线商品库流程。
- 不宣称 MS2011 商品/促销实时同步已经完成。

## 统一验收入口

完整状态见：`docs/IMPLEMENTATION_STATUS.md`、`docs/PROJECT_STATUS.md` 和 `docs/PROJECT_LOG.md`。

当前已知剩余工作是目标设备端到端联调，以及 MS2011 方案所需的真实只读证据收集。
