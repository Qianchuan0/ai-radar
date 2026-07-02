# AI Radar 项目上下文

## 项目名称

**AI Radar**

## 项目定位

AI Radar 是 AI 行业情报监控与趋势分析 Agent 平台。系统从论文平台、开发者社区和开源平台持续采集 AI 相关信息，保留原始证据，并通过标准化、去重聚类和热点评分形成事件级行业热点。

## 目标用户

- 需要跟踪技术方向的 AI 应用开发者和技术负责人。
- 需要判断产品机会和行业变化的 AI 产品经理。
- 需要研究公司、模型、论文和开源项目动态的行业研究人员。

## 解决的问题

- AI 信息分散在不同平台，人工跟踪成本高。
- 同一事件会被多个来源重复发布，单条信息列表噪声大。
- 不同来源的互动指标不可直接比较。
- 仅按发布时间或单平台热度排序，难以反映事件的综合影响。
- LLM 摘要可能脱离证据，缺少可追溯性和效果评估。

## 为什么不是普通新闻聚合器

AI Radar 不以“抓取后生成摘要”为终点，而以事件级热点识别为核心。系统保存原始数据，将异构内容转换为统一模型，把同一事件的多个来源聚合为 `hot_cluster`，并通过可解释信号计算 `hot_score`。后续分析、告警和日报都需要引用来源证据。

## 核心流程

```text
多源采集
→ 原始数据保存
→ 内容清洗与标准化
→ 去重与事件聚类
→ 热点评分
→ 结构化影响分析
→ 用户订阅与告警
→ 每日简报
→ 效果评测
```

## MVP 数据源

### arXiv

作用：观察 AI 论文、研究主题和技术趋势。

计划采集字段：

- `title`
- `abstract`
- `authors`
- `categories`
- `published_at`
- `arxiv_id`
- `pdf_url`
- `source_url`

首批关注范围包括 `cs.AI`、`cs.CL`、`cs.LG`、`cs.CV`，以及 LLM、Agent、RAG、MCP、Multimodal、Reasoning、Diffusion、Evaluation 等关键词。

### Hacker News

作用：观察开发者社区对 AI 产品、工具和技术事件的讨论热度。

计划采集字段：

- `title`
- `url`
- `hn_item_id`
- `points`
- `comments_count`
- `author`
- `published_at`
- 评论摘要（后续能力）

首批关注 Show HN、AI tools、LLM、Agent、OpenAI、Anthropic、Google、GitHub repository 和 developer tools 等内容。

### GitHub

作用：观察 AI 开源项目、开发者采用情况和项目活跃度。

计划采集字段：

- `repo_full_name`
- `repo_name`
- `owner`
- `description`
- `url`
- `stars`
- `forks`
- `open_issues`
- `watchers`
- `topics`
- `language`
- `README`
- `latest_release`
- `updated_at`

首批关注 llm、agent、rag、mcp、ai-agent、workflow、multimodal、langchain、spring-ai 等 topic 或关键词。

## MVP 功能模块

1. 数据源配置与启停。
2. 定时或手动采集任务及任务状态记录。
3. 原始数据持久化。
4. 异构内容标准化。
5. 内容去重和事件级聚类。
6. 可解释热点评分。
7. LLM 结构化影响分析。
8. 用户订阅和告警记录。
9. 每日简报生成与历史查询。
10. 采集、聚类、评分、分析和告警效果评测。
11. 热点榜单、热点详情及管理页面。

各模块按路线图逐阶段实现，不在项目初始化阶段编写业务代码。

## 第一阶段最小闭环

```text
source_config
→ manual crawl
→ crawl_task
→ raw_item
→ hot_item
→ hot_cluster
→ hot_score
→ hot cluster APIs
→ frontend hot list
```

该闭环应先以一个数据源真实跑通，再验证采集器抽象并扩展其他数据源。

## 核心领域模型

### `source_config`

记录数据源类型、访问配置、关键词、启停状态和采集间隔。

### `crawl_task`

记录一次采集的生命周期，包括状态、数量、耗时和失败原因。

### `raw_item`

保存外部来源的原始响应或完整原始记录。它是追溯来源、重新执行处理逻辑和构造评测样本的基础，不应被加工结果替代。

### `hot_item`

将论文、社区帖子和代码仓库映射为统一内容模型，承载标准化字段、标签、实体和来源互动数据。

### `hot_cluster`

表示由一个或多个相关 `hot_item` 组成的事件级热点。它是榜单、分析、告警和日报的主要业务对象。

### `hot_score`

事件级热点的可解释评分。候选信号包括来源权重、互动数据、新鲜度、多源共现、关键词命中和用户兴趣；具体公式需要单独决策和验证。

## 计划技术栈

当前已接受的基础选型：

- 后端：Java 17、Spring Boot 3.5.15、MyBatis-Plus。
- 数据库：PostgreSQL，使用 Flyway 管理版本化 SQL 迁移。
- API 契约：OpenAPI 3.1。
- 本地数据库：Docker Compose。
- 聚类基线：确定性去重与规则聚类。
- 评分基线：保存分项与版本的可解释规则评分。

当前延后决定：

- pgvector：等规则聚类基线和标注样本能够量化收益后评估。
- Redis：等出现已测量的缓存或分布式协调需求后评估。
- 调度：第一条闭环只做手动采集，周期采集出现后优先评估 Spring Scheduler。
- LLM 集成：在 Phase 5 前比较 Spring AI 和 LangChain4j。
- 前端：在 Phase 3 前确定 Vue 3 或 React，统一使用 TypeScript。

最终选型必须与 `docs/decision-log.md` 和实际 ADR 保持一致。

## 设计原则

- 以事件而不是单条内容为核心。
- 原始证据和处理结果分离保存。
- 规则、统计和模型各自承担适合的职责。
- LLM 输出必须结构化、受约束且能追溯到输入证据。
- 热点评分需要可解释、可回放和可评估。
- 先实现最小闭环，再扩展数据源和高级能力。
- 外部调用必须可观测，并具备失败记录和重试设计。
- 技术选型服务于当前规模，不提前引入重型基础设施。

## 当前阶段边界

Phase 1 已完成后端与数据库基础。Phase 2 正在用 Hacker News 跑通第一条真实数据闭环，当前已实现：

- Hacker News Top Stories 数据源配置和同步手动采集。
- `crawl_task` 生命周期、计数、幂等键和逐条失败记录。
- 每次采集的 `raw_item` 快照，以及命中关键词内容的 `hot_item` 标准化。
- 基于规范化 URL 的确定性聚类和单条事件兜底。
- 带版本和分项的追加式 `hot_score`。
- 热点列表和热点详情 API。

当前仍不实现：

- Hacker News 评论树和外链正文抓取。
- arXiv 与 GitHub 采集器。
- 定时调度、异步任务队列和自动任务重试。
- embedding 或 LLM 聚类、LLM 结构化分析。
- Vue 或 React 前端。
- 订阅、告警、日报和完整评测体系。
- Redis、Kafka、XXL-JOB、Milvus、Elasticsearch 等非必要基础设施。
