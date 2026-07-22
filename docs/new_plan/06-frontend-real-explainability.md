# Phase 19A: Frontend Real Backend Explainability

## Status

已完成实现，并已通过本地验收。

本阶段不再是“待开发计划”，而是对当前代码状态的落地说明，供维护者理解前端真实解释能力已经如何接入。

## Overall Context

`docs/plan/最新分析.md` 曾指出一个明显产品缺口：前端详情页存在“评分明细”，但它是前端自行计算的解释视图，并不是后端真实 `score_components`。这会造成产品语义不一致：系统宣称可解释评分，但用户看到的解释不是评分服务真正使用的分量。

Phase 19A 已将该缺口补齐：详情页现在展示真实后端结果，而不是展示本地推导的近似说明。

## Delivered Changes

当前代码已完成以下能力：

- 详情页调用 `/api/v1/hot-clusters/{id}/scores`，展示真实 `score_components`。
- 详情页展示 V1 / V2 分数对比，并用 `当前线上` / `shadow 对照` 标签区分。
- 详情页调用趋势接口并展示 trend summary。
- 详情页调用 `/api/v1/hot-clusters/{id}/match-decisions`，在 evidence 视图中展示 V2 match decision。
- 前端已移除客户端自算 `scoreBreakdown` 近似解释，避免误导用户。
- scores / trends / match decisions 三类 explainability 数据均按 fail-soft 方式加载，接口缺失或失败时页面不会崩溃。

## Implemented Scope

Included:

- 前端 API types 已补齐：
  - `HotClusterScore`
  - `ScoreComponent`
  - `V2ScoreComponents`
  - `ClusterTrend`
  - `RawMetricDelta`
  - `ClusterMatchDecision`
  - `MatchDecisionOutcome`
- `frontend/src/shared/api/hotClusters.ts` 已实现：
  - `fetchHotClusterScores`
  - `fetchHotClusterTrends`
  - `fetchClusterMatchDecisions`
- `HotClusterDetailPage.vue` 已并行加载：
  - detail
  - latest analysis
  - scores
  - trends
  - match decisions
- score tab 已改为真实数据渲染：
  - 当前线上 score version
  - V1 / V2 total
  - V1 numeric map
  - V2 weighted dimensions with reasons
  - calculatedAt
- overview 已增加 24h 趋势摘要：
  - trendState
  - momentumScore
  - confidence
  - contributing / skipped item counts
- evidence tab 已增加 V2 decision 信息：
  - outcome
  - L1 / L2 / L3 method
  - match score
  - rule version
  - decidedAt
- 已补充纯解析工具与单测：
  - `shared/utils/scoreComponents.ts`
  - `shared/utils/clusterTrend.ts`
  - `shared/utils/matchDecision.ts`
  - 对应 vitest coverage
- 已提供验收脚本：
  - `scripts/accept-phase-19a.ps1`

Explicitly not included:

- 不做复杂 dashboard。
- 不做评测可视化。
- 不做完整治理工作台。
- 不把 LLM 分析展示为已验证事实。
- 不在前端重新计算后端分数。
- 不把 V2 自动提升为默认线上排序。

## Backend Surface In Use

当前实现依赖并已接入以下接口：

```http
GET /api/v1/hot-clusters/{id}/scores
GET /api/v1/hot-clusters/{id}/trends
GET /api/v1/hot-clusters/{id}/match-decisions
```

其中 `match-decisions` 已作为只读 explainability 接口落地，不再是待补依赖。

## UX Rules Now Enforced

- 不再用前端近似计算伪造评分解释。
- 用清晰标签区分：
  - 当前线上使用
  - shadow comparison
  - review required
- 对空数据给出真实状态，不伪造默认分数或默认趋势。
- score components 中未知字段按后端名称展示，不硬编码错误含义。
- trends / scores / decisions 任一数据源失败时，详情页保持可用，仅对应区块显示空态或不可用态。

## Verification

建议使用：

```powershell
.\scripts\accept-phase-19a.ps1
```

本地已验证通过的检查点：

- Phase 19A 所需后端与前端文件存在。
- `GET /api/v1/hot-clusters/{id}/match-decisions` 只读接口存在。
- `contracts.ts` 已暴露 score / trend / decision 相关类型。
- `hotClusters.ts` 已接入三类 explainability fetch helpers。
- `HotClusterDetailPage.vue` 已移除客户端 `scoreBreakdown`，改用真实后端 explainability。
- Phase 19A 工具层单测通过。
- 前端生产构建通过。

## Documentation Sync

当前仓库中已同步的文档包括：

- `docs/roadmap.md`
- `docs/project-context.md`
- `docs/api/phase-one-openapi.yaml`
- `docs/decision-log.md`

`README.md` 是否补充对外描述，仍可按维护者需要单独决定。

## Risks That Still Apply

- 后端 `score_components` schema 若继续演进，前端解析层仍需保持宽容兼容。
- 同时加载多接口会增加错误状态与空态分支复杂度。
- 用户仍可能误解 shadow score，因此必须持续保留明确版本标签。

## Maintainer Review Checklist

- 页面上每个评分解释都能追溯到后端返回。
- V1 / V2 被清楚区分，且不会把 shadow 结果误读为线上权威值。
- 趋势缺数据时展示空态，而不是伪装成稳定。
- evidence 中展示的是 V2 决策结果，不再让用户把 V1 membership 误认为 V2 confirmed match。
