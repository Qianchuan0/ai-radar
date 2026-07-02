# AI Radar

## 项目定位

AI Radar 是一个 AI 行业情报监控与趋势分析 Agent 平台。它面向持续关注 AI 行业变化的用户，从多个公开数据源采集信息，并将分散内容加工为有来源证据、可解释的事件级热点。

## 为什么做这个项目

AI 信息分散在论文平台、开发者社区和开源平台中，同一事件可能以不同形式反复出现。仅靠人工浏览，容易受到信息重复、来源割裂和热度噪声影响。AI Radar 希望建立一条可追溯、可评估的信息处理流程，帮助用户识别真正值得关注的变化。

## 为什么不是普通新闻聚合器

普通聚合器通常以单条内容为中心，完成抓取、摘要和列表展示。AI Radar 以事件级热点为中心：

- 保留外部来源的原始数据和处理证据。
- 将异构数据标准化为统一内容模型。
- 对相似内容去重并聚合为同一热点事件。
- 使用可解释的规则和信号计算热度，而不是只按发布时间排序。
- 后续结构化分析、订阅告警和日报均围绕事件级热点工作。

## MVP 范围

MVP 计划接入以下数据源：

- arXiv：观察 AI 论文与技术方向。
- Hacker News：观察开发者社区讨论热度。
- GitHub：观察 AI 开源项目及其活跃度。

MVP 计划覆盖数据源配置、采集任务、原始数据保存、内容标准化、去重聚类、热点评分、热点查询与展示，以及后续的结构化分析、订阅告警、日报和评测。

## 核心概念

- `raw_item`：外部来源返回的原始记录，用于追溯、重跑和评测。
- `hot_item`：由原始记录标准化得到的统一内容记录。
- `hot_cluster`：由一个或多个相关内容组成的事件级热点，是系统的核心领域模型。
- `hot_score`：根据来源、互动、新鲜度、多源共现等信号计算的可解释热度分数。

## 计划技术栈

当前已经确认的基础技术：

- 后端：Java 17、Spring Boot 3.5.15、MyBatis-Plus。
- 数据存储：PostgreSQL；使用 Flyway 管理版本化迁移。
- API 契约：OpenAPI 3.1。
- 本地运行：Docker Compose 提供 PostgreSQL。

聚类先建立确定性规则基线，热点评分先使用可解释规则。pgvector、Redis、LLM 框架和前端框架将在出现对应阶段的真实需求和评测依据后决定。

## 本地启动

先在仓库根目录启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

再启动后端：

```powershell
cd backend
.\mvnw.cmd "-Dmaven.repo.local=.m2repo" spring-boot:run
```

应用启动时会自动执行 Flyway 迁移。健康检查地址为：

```text
GET http://localhost:8080/api/health
```

更完整的配置、迁移和日志规则见 [`docs/backend-foundation.md`](docs/backend-foundation.md)。

## 当前状态

**Backend foundation implemented / Phase 1 awaiting acceptance**
