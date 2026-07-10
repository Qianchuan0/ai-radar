当前方案会基于 `D:\AiProgram\ai-radar` 现有工作树继续；注意工作树里已有大量 Phase 6/7 未提交改动，我会保留并基于它们实现，不回退、不清理、不 commit、不 push。

**Phase 8 设计方案**

目标：做一个“手动触发、可追溯、可前端展示”的 Evaluation 最小闭环，覆盖 roadmap 的三项交付物：

- labeled evaluation dataset
- quality metrics
- evaluation report and error analysis

**模块边界**

Phase 8 只负责评估现有数据流质量，不改造现有采集、聚类、评分、分析、告警、日报算法。

不做：

- 不引入调度器、队列、Kafka、向量库、外部评测平台
- 不引入 LLM 作为评测裁判
- 不自动调整聚类/评分/订阅规则
- 不做完整标注平台，只做可维护的 labeled dataset API 和可见评估结果页

**后端设计**

新增 `evaluation` 领域模块，沿用现有分层：

- `controller`
- `service`
- `entity`
- `mapper`
- `model`
- `dto`
- `vo`

新增 Flyway `V6__add_evaluation.sql`，建议 4 张表：

- `evaluation_dataset`
  - 评估集元信息：名称、描述、版本、启用状态
- `evaluation_case`
  - 人工标注 case：case code、case type、target payload、expected payload、notes、enabled
- `evaluation_run`
  - 一次手动评估运行：dataset、状态、总数、通过数、失败数、metrics payload、error analysis payload
- `evaluation_case_result`
  - 每条 case 的实际结果：status、actual payload、failure reason、evaluated_at

初始 case type 建议：

- `CRAWL_ITEM_PRESENT`
  - 验证某个 `sourceType + externalId` 是否存在于 `raw_item` / `hot_item`
- `CLUSTER_MEMBERSHIP`
  - 验证两个 item 是否应该同簇或不应同簇
- `SCORE_THRESHOLD`
  - 验证 cluster 的总分或组件分是否达到人工期望
- `ANALYSIS_REQUIRED_FIELDS`
  - 验证 latest succeeded analysis 是否存在，并包含 evidence refs、headline、brief 等关键字段
- `ALERT_EXPECTED_RECORD`
  - 验证某订阅规则和某 cluster 是否产生了预期 alert 记录

评估输出：

- run 级 metrics：
  - totalCases
  - passedCases
  - failedCases
  - errorCases
  - passRate
  - byCaseType
- error analysis：
  - failed case 列表
  - case type 分布
  - expected / actual 摘要
  - failure reason

**API 设计**

新增 `/api/v1/evaluation`：

- `POST /api/v1/evaluation/datasets`
  - 创建评估集
- `GET /api/v1/evaluation/datasets`
  - 查询评估集
- `POST /api/v1/evaluation/datasets/{datasetId}/cases`
  - 新增标注 case
- `GET /api/v1/evaluation/datasets/{datasetId}/cases`
  - 查看 case
- `POST /api/v1/evaluation/runs?datasetId=...`
  - 手动触发一次评估
- `GET /api/v1/evaluation/runs`
  - 查看历史 run
- `GET /api/v1/evaluation/runs/{runId}`
  - 查看 run 详情、metrics、error analysis、case results

错误处理新增：

- `EVALUATION_DATASET_NOT_FOUND`
- `EVALUATION_CASE_NOT_FOUND`
- `EVALUATION_EMPTY_DATASET`

**前端设计**

新增 `/evaluation` 页面，并把现有 Daily Reports 里的 “Evaluation” 占位改成真实路由链接。

页面内容：

- dataset 列表/选择
- 手动运行按钮
- latest run 状态
- metrics cards：通过率、case 数、失败数、错误数
- by-case-type 表格
- failed/error case 列表，可看到 expected、actual、reason
- 空状态、加载状态、错误状态

这会让 Phase 8 不只是后端代码可跑，而是前端可见。

**文档同步计划**

实现完成后按 `AGENTS.md` 检查这些文档：

- `README.md`
  - 只有在 Phase 8 可运行能力确实完成后，更新 verified path 和 Current Status
- `docs/project-context.md`
  - 补充 Evaluation 模块在核心流程中的作用
- `docs/roadmap.md`
  - Phase 8 从 `Planned` 改为 `In Progress`
  - 不自动改为 `Completed`，除非你明确验收通过
- `docs/decision-log.md`
  - 新增一条 accepted decision：Phase 8 首个评估闭环采用手动、规则化、基于 persisted data 和 labeled cases 的评估，不引入外部评测基础设施
- `docs/api/phase-one-openapi.yaml`
  - 同步新增 evaluation API
- `docs/adr/`
  - 暂不新增 ADR，除非实现中出现真实技术取舍。目前方案是延续既有架构，不需要装饰性 ADR

**实现顺序**

1. 先跑基线验证：
   - `backend\mvnw.cmd test`
   - `npm run build`
   - `npm test`
2. 新增数据库迁移和后端 evaluation 实体/Mapper/Service/Controller。
3. 添加集成测试，覆盖：
   - 创建 dataset/case
   - 手动 run
   - 至少一个通过 case、一个失败 case
   - metrics 和 error analysis 返回正确
4. 同步 OpenAPI、前端 contracts/API。
5. 新增 Evaluation 页面和路由。
6. 跑完整自检。
7. 按 `AGENTS.md` 输出任务完成报告。

