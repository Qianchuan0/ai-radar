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

以下为候选方案，并不代表全部已经接受：

- 后端：Java 17+、Spring Boot 3、Spring Security、MyBatis-Plus。
- 数据库：PostgreSQL、MySQL，或需要向量检索时使用 PostgreSQL + pgvector。
- 缓存：Redis。
- 任务调度：Spring Scheduler、Quartz 或后续评估的任务平台。
- LLM 集成：Spring AI 或 LangChain4j。
- 前端：Vue 3 或 React、TypeScript、组件库、图表和 Markdown 渲染。
- 部署：Docker Compose、Nginx、前后端服务及数据库。

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

## 当前阶段不做什么

Phase 0 只初始化仓库结构和文档骨架，当前不做：

- 不初始化 Spring Boot。
- 不初始化 Vue 或 React。
- 不设计或创建实际数据库表。
- 不编写采集、标准化、聚类、评分等业务代码。
- 不接入 LLM。
- 不实现订阅、告警、日报或评测。
- 不创建 `docker-compose.yml`。
- 不引入 Kafka、XXL-JOB、Milvus、Elasticsearch 等重型组件。
- 不创建尚未真实发生的重要技术决策 ADR。

