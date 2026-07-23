# Phase 20: Authoritative Official Sources

## Overall Context

`docs/plan/最新分析.md` 判断：当前来源数量已经足够多，继续增加普通搜索源的边际收益低。真正缺的是权威官方源，但它应该排在 V2 主链路闭环之后。

原因是：如果 V2 聚类、趋势、评分和前端解释还没有闭环，新增官方源也只会进入旧的 URL 聚类和 V1 排序，不能充分发挥价值。

## Current Gap

当前来源覆盖社区、搜索、开源平台和社交平台较多，但缺少独立官方连接器：

- AI 公司官方博客。
- API Changelog。
- Model/System Card。
- GitHub Releases。
- Official Status Page。
- OpenReview。
- Semantic Scholar。
- Product Hunt。
- 政策监管源。
- AI 安全公告源。

搜索引擎偶然搜到官方公告，不等于持续监控官方公告。

## Goals

- 接入第一批权威官方源。
- 为 authority score 提供更可信证据。
- 提升重大模型发布、API 变更、安全公告、官方价格调整的发现能力。
- 保持 source connector 模板一致，不引入重型抓取基础设施。

## Scope

Included first:

- `OFFICIAL_RSS`（用于官方博客 / 官方 feed，第一版不单独拆分 `OFFICIAL_BLOG_RSS`）
- `OFFICIAL_CHANGELOG`
- `GITHUB_RELEASE`

Candidate later:

- `OPENREVIEW`
- `SEMANTIC_SCHOLAR`
- `PRODUCT_HUNT`
- `OFFICIAL_STATUS_PAGE`
- policy/safety announcement sources

Explicitly not included:

- 不接浏览器自动化。
- 不接代理池。
- 不抓需要登录的内容。
- 不做大规模网页爬虫。
- 不把搜索结果当成官方源。

## Source Design

官方源应具有更高 authority，但不能自动代表“热门”。建议 source role：

```text
AUTHORITY
```

字段映射建议：

```text
external_id: stable URL or feed guid
source_url: official article/release URL
title
summary/content
published_at
author/publisher
raw_payload
item_type: OFFICIAL_ANNOUNCEMENT / RELEASE / CHANGELOG / STATUS
```

## Backend Plan

- 基于 `docs/source-connector-template.md` 新增官方源模板。
- 新增 source type：
  - `OFFICIAL_RSS`
  - `OFFICIAL_CHANGELOG`
  - `GITHUB_RELEASE`
- 每个 source 独立 collector、client、normalizer、signal adapter。
- RSS/Atom 解析优先使用轻量依赖或现有 XML 解析能力。
- GitHub Releases 可复用已有 GitHub client/auth 配置，但保持 source semantics 独立于 repo search。
- authority calculator 将官方源作为最高权威证据之一。

## API Plan

优先复用现有 source config、manual crawl、scheduled crawl、hot cluster APIs。

如 source config 需要模板化，可新增：

```http
GET /api/v1/sources/templates?sourceType=OFFICIAL_RSS
```

但第一版不强制新增。

## Frontend Plan

- source label 增加官方源名称。
- source management 页支持配置官方 feed/changelog URL。
- detail evidence 中清晰标识“官方来源”。
- 不在前端把官方来源直接等同于事实结论，仍以 raw evidence 链接为准。

## Verification

验收脚本建议：

```powershell
.\scripts\accept-phase-20.ps1
```

检查点：

- 官方 RSS 可离线 mock 解析。
- 官方 Changelog 可离线 mock 解析。
- GitHub Release 可离线 mock 解析。
- raw_item 保留原始 payload。
- hot_item 标准化字段正确。
- source role 为 AUTHORITY。
- authority score 可读取官方源。
- `AuthorityScoreCalculator` 中 AUTHORITY 基线高于 PRIMARY。
- 官方源在 V1 中保持低 `points`，避免干扰旧版热度排序。
- 官方源失败不影响其他 source。
- 缺少可选凭据时应用仍可启动。

## Documentation Sync

实现完成后检查：

- `docs/roadmap.md`: 新增 Phase 20。
- `docs/project-context.md`: MVP sources 或后续 sources 增加官方源说明。
- `docs/source-connector-template.md`: 补充官方源 checklist。
- `docs/decision-log.md`: 记录为什么官方源在 V2 闭环后接入。
- `README.md`: 维护者验收后再加入 Current verified source expansion。
- `docs/adr/`: 通常不需要，除非引入新的抓取架构。

## Risks

- 官方源数量多但格式各异，第一版要从 RSS/changelog/release 这类稳定结构开始。
- 官方源 authority 高，但不代表热度高，不能让 authority 单独主导总分。
- 官方源和搜索源可能指向同一 URL，需要 evidence dedup。

## Local Review

维护者复核重点：

- 官方源是否真的是源头，而不是搜索结果包装。
- source config 是否能被本地复核。
- 新 source 是否进入现有 raw -> hot -> cluster -> score -> frontend 闭环。
