# Phase 18A: Cluster-Level Trend Model

**Status:** Completed

## Overall Context

Phase 14 已经提供 `hot_item_signal_snapshot` 和 24h item 级增长计算。`docs/plan/最新分析.md` 指出：当前趋势仍是第一版 MVP，缺少多窗口、原始指标 delta、增长率、加速度和 cluster 级趋势状态。

本阶段目标是从 item 级 24h delta 升级到 cluster 级多源趋势，支撑 Score V2 接管排序和前端解释。

## Current Gap

当前 `GrowthCalculationService` 只支持 `window=24h`，并主要对单个 `hot_item` 计算 normalized signal delta。Score V2 的 momentum 也仍读取 primary item 的 growth。

缺口：

- 没有 1h/6h/3d 多窗口。
- 没有原始指标 delta，如 star、download、comment、view、rank。
- 没有 growth rate、velocity、acceleration。
- 没有 cluster 级趋势聚合。
- `METRIC_RESET` 规则过粗，任一指标下降都可能触发 reset。

## Goals

- 支持多窗口趋势：1h、6h、24h、3d。
- 输出 source-aware raw metric deltas。
- 输出 normalized signal deltas。
- 计算 growthRate、velocity、acceleration。
- 聚合 cluster 级 trend state。
- 为 Score V2 提供 cluster-level momentum 输入。

## Scope

Included:

- 扩展 signal snapshot 读取与增长计算。
- 多窗口配置和校验。
- Source-specific metric semantics。
- Cluster trend aggregation service。
- Cluster trend API。
- 后端测试和验收脚本。

Explicitly not included:

- 不切换 Score V2 排序。
- 不做复杂前端趋势图。
- 不引入时序数据库。
- 不引入流式计算。
- 不做全量历史回填第一版。

## Data Design

优先复用 `hot_item_signal_snapshot` 的 `raw_metrics` 和 `normalized_signal`。

如需要缓存 cluster 趋势结果，再新增：

```text
hot_cluster_trend_snapshot
- id
- hot_cluster_id
- window
- trend_state: NEW / RISING / PEAKING / STABLE / COOLING / UNKNOWN
- momentum_score
- raw_metric_deltas jsonb
- normalized_deltas jsonb
- confidence
- calculated_at
- created_at
```

第一版可以先不落缓存，直接实时计算；若查询成本过高再加表。

## Backend Plan

- 扩展 `GrowthCalculationService` 支持 `1h`、`6h`、`24h`、`3d`。
- 为 `SourceSignalAdapter` 增加 metric semantics 描述：
  - monotonic cumulative
  - rank-like reversible
  - volatile social metric
  - relevance score
- 新增 `RawMetricDelta` 模型，明确输出 source-specific delta。
- 新增 `ClusterTrendService`：
  - 加载 cluster active items。
  - 按 source role 聚合增长。
  - 输出 cluster momentum。
  - 判断 trend state。
- 修改后续 Score V2 计划，使其读取 `ClusterTrendService` 而不是 primary item growth。

## API Plan

建议新增：

```http
GET /api/v1/hot-clusters/{id}/trends?windows=1h,6h,24h,3d
GET /api/v1/hot-items/{id}/trend?window=6h
```

返回字段建议：

```text
window
trendState
momentumScore
confidence
rawMetricDeltas
normalizedDeltas
contributingItems
calculatedAt
```

## Frontend Plan

本阶段前端只需要类型与 API 预留，不强制展示。真实展示放到 Phase 19A。

## Verification

验收脚本建议：

```powershell
.\scripts\accept-phase-18a.ps1
```

检查点：

- 1h/6h/24h/3d 均可计算。
- 无历史点返回 `UNKNOWN`，不伪装成 0。
- GitHub stars/downloads 等累计指标下降按 source semantics 判断异常。
- 搜索 rank 下降不直接等于 metric reset。
- 多 item cluster 能聚合为 `RISING`。
- primary item 无增长但其他证据增长时，cluster trend 仍能反映。

## Documentation Sync

实现完成后检查：

- `docs/roadmap.md`: 新增 Phase 18A。
- `docs/signal-layer-guide.md`: 更新多窗口、raw metrics、cluster trend 规则。
- `docs/decision-log.md`: 记录 source-specific metric semantics。
- `docs/adr/`: 不引入新存储时通常无需 ADR；如果新增趋势缓存表并改变查询模型，可考虑 ADR。
- `README.md`: 维护者验收后再更新可运行能力。

## Risks

- 不同平台指标语义差异大，不能用同一 reset 规则硬套。
- Cluster 趋势聚合容易被重复搜索源放大，需要和 evidence diversity 的 dedup 逻辑一致。
- 多窗口查询可能增加数据库压力，第一版要限制窗口和 item 数量。

## Local Review

维护者复核重点：

- trend state 是否能解释。
- raw delta 是否能追溯到 source/item。
- cluster trend 是否避免只看 primary item。
