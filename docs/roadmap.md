# AI Radar Development Roadmap

## Phase 0: Project Initialization

**Status:** In Progress

### Goals

- 建立仓库目录和工程协作规则。
- 固化项目上下文、阶段边界和技术决策记录方式。
- 为后续设计提供单一、可维护的文档入口。

### Deliverables

- 根目录说明与 Agent 协作规则。
- `docs/` 项目上下文、路线图、决策日志和 ADR 规范。
- 空的 `backend/`、`frontend/`、`evaluation/` 目录。
- 本地学习文件的 Git 忽略规则。

## Phase 1: Backend Foundation

**Status:** Planned

### Goals

- 确定后端、数据库和基础工程技术选型。
- 建立后端分层、配置、异常处理和数据库迁移基础。
- 明确第一条数据流需要的表结构和接口契约。

### Deliverables

- 相关技术决策和必要 ADR。
- 后端工程骨架。
- 数据库表结构初稿与迁移机制。
- 统一响应、异常、日志和配置规范。
- 第一阶段 API 契约。

## Phase 2: First Data Flow Closed Loop

**Status:** Planned

### Goals

- 选择一个数据源跑通真实纵向闭环。
- 验证采集、原始保存、标准化、聚类和评分边界。
- 保证流程可追溯、可重试且评分可解释。

### Deliverables

- 数据源配置与手动采集。
- `crawl_task` 状态和统计记录。
- `raw_item` 与 `hot_item` 持久化。
- 第一版 `hot_cluster` 生成策略。
- 第一版 `hot_score` 及分数明细。
- Hot cluster 查询 API。
- 自动化测试与真实运行验证记录。

## Phase 3: Frontend MVP

**Status:** Planned

### Goals

- 用真实后端数据展示事件级热点价值。
- 提供基本筛选、详情和来源证据访问。

### Deliverables

- 前端工程骨架。
- 热点榜单页面。
- 热点详情、来源证据和评分明细。
- 加载、空数据和失败状态。

## Phase 4: Additional Data Sources

**Status:** Planned

### Goals

- 将采集器抽象扩展到其余 MVP 数据源。
- 验证跨来源标准化和多源共现。

### Deliverables

- arXiv、Hacker News、GitHub 三类采集器。
- 来源级限流、失败记录和重试策略。
- 跨来源字段映射和规范化测试。
- 多源事件聚类验证样本。

## Phase 5: LLM Structured Analysis

**Status:** Planned

### Goals

- 基于事件证据生成受约束的结构化影响分析。
- 记录调用输入、输出、模型信息和失败状态。

### Deliverables

- LLM 集成技术决策。
- 结构化输出 Schema 与校验。
- 影响分析服务和调用日志。
- 证据不足、输出非法和模型失败处理。

## Phase 6: Subscription and Alerts

**Status:** Planned

### Goals

- 支持用户按关注方向订阅事件。
- 生成可追溯、可去重的告警记录。

### Deliverables

- 订阅规则模型与管理接口。
- 规则匹配服务。
- 告警记录、已读状态和重复抑制。
- 告警准确性测试样本。

## Phase 7: Daily Report

**Status:** Planned

### Goals

- 从每日事件级热点生成有证据的简报。
- 支持历史日报查询和 Markdown 展示。

### Deliverables

- 日报及日报条目模型。
- 日报生成流程。
- 每日 Top 热点、来源证据和跟进建议。
- 历史日报 API 与页面。

## Phase 8: Evaluation

**Status:** Planned

### Goals

- 用明确样本和指标验证采集、聚类、评分、分析和告警效果。
- 暴露错误案例并支持算法迭代。

### Deliverables

- 评测数据集和人工标注规范。
- 采集成功率与延迟指标。
- 聚类 precision、recall 和重复合并准确率。
- Top-N 热点评分指标。
- 结构化分析人工评分。
- 告警 precision、recall 和误报率。
- 评测报告与错误案例分析。

