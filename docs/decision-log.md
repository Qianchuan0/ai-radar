# AI Radar Decision Log

本文件是技术与产品决策的入口。简短且已明确的决策可以直接记录在这里；需要完整背景、候选方案比较、后果和复审条件的决策，应在 `docs/adr/` 创建 ADR，并从本文件链接。

## Accepted Decisions

1. **AI Radar 不是普通新闻聚合器，而是事件级 AI 情报监控平台。**
   - 系统以 `hot_cluster` 作为核心业务对象，而不是只展示单条抓取内容。

2. **MVP 从 arXiv、Hacker News、GitHub 三个数据源开始。**
   - 三类来源分别代表论文研究、开发者讨论和开源项目动态。

3. **第一阶段先完成最小闭环，不急着做 LLM、订阅告警、日报和评测。**
   - 优先验证从数据源配置到热点榜单的真实纵向数据流。

4. **后端基础使用 Java 17、Spring Boot 3.5.15、MyBatis-Plus 与 Flyway。**
   - MyBatis-Plus 处理常规持久化并保留自定义 SQL 能力。
   - Flyway 版本化 SQL 是数据库结构变更的事实来源。

5. **主数据库使用 PostgreSQL，当前不启用 pgvector。**
   - 关系约束、事务和 `jsonb` 满足第一条数据流；向量能力等待评测证据。
   - 详见 [ADR-001](adr/ADR-001-primary-database.md)。

6. **保留每次采集的不可变 `raw_item` 原始快照。**
   - 用于来源追溯、处理重跑、问题诊断和评测。
   - 详见 [ADR-002](adr/ADR-002-raw-item-retention.md)。

7. **第一版聚类采用确定性去重和规则基线。**
   - 记录规则版本、成员关系和合并原因；LLM 不作为唯一决策者。
   - 详见 [ADR-003](adr/ADR-003-rule-based-clustering-baseline.md)。

8. **第一版热点评分采用可解释的规则与统计信号。**
   - 评分结果追加保存总分、分项和版本，不覆盖历史。
   - 详见 [ADR-004](adr/ADR-004-rule-based-hot-scoring-baseline.md)。

9. **第一条数据流先支持手动采集。**
   - 周期采集出现后优先评估 Spring Scheduler；当前不引入 Quartz 或 XXL-JOB。

10. **第一阶段 API 契约使用 OpenAPI 3.1。**
    - Phase 1 只定义契约，不实现 Phase 2 业务接口。

## Pending Decisions

1. **何时启用 pgvector 或其他 embedding 检索能力。**
   - 等规则聚类基线和人工标注样本可以量化语义聚类收益后再决定。

2. **周期任务是否超出 Spring Scheduler 的能力边界。**
   - 需要出现持久化调度、复杂依赖、多实例协调等实际需求后重新评估。

3. **LLM 框架用 Spring AI 还是 LangChain4j。**
   - 在进入结构化分析阶段前，根据模型支持、结构化输出和可观测性进行决策。

4. **前端使用 Vue 3 还是 React。**
   - 在初始化前端工程前，根据团队熟悉度、组件生态和展示需求进行决策。

## ADR Index

- [ADR-001: Use PostgreSQL as the Primary Database](adr/ADR-001-primary-database.md)
- [ADR-002: Retain Immutable Raw Item Snapshots](adr/ADR-002-raw-item-retention.md)
- [ADR-003: Start with a Rule-Based Clustering Baseline](adr/ADR-003-rule-based-clustering-baseline.md)
- [ADR-004: Start with Explainable Rule-Based Hot Scoring](adr/ADR-004-rule-based-hot-scoring-baseline.md)
