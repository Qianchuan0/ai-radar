# V1 聚类与评分行为基线

**Date:** 2026-07-15
**Version:** hn-rule-v1 (clustering) + hn-score-v1 (scoring)

本文档记录 AI Radar V1 版本的聚类和评分行为，作为未来 V2 改进的对照基线。

## V1 聚类 (hn-rule-v1)

### 实现位置

`com.airadar.clustering.service.RuleBasedClusterService`

### 聚类规则

1. **Canonical URL 去重**
   - 相同 canonical URL 的多个 hot item 进入同一个 cluster
   - canonical URL 计算来源：`sourceUrl` 标准化（移除 tracking 参数、统一路径格式）

2. **相同事件拆分**
   - 不同 canonical URL 的同一事件仍会被拆开为不同 cluster
   - 例如：同一产品在不同网站的报道会形成多个 cluster

3. **来源隔离**
   - 每个来源的 item 先独立聚类
   - 跨来源合并仅通过 canonical URL 匹配

### 已知限制

- 不支持语义相似度匹配（需 URL 完全一致或高度相似）
- 不支持跨语言事件聚合
- 时间窗口未被用于聚类边界
- 不同 URL 的同一事件无法识别为同一个 cluster

## V1 评分 (hn-score-v1)

### 实现位置

`com.airadar.scoring.service.RuleBasedScoringService`

### 评分公式

总分 = points 分数 + comments 分数 + freshness 分数 + keywords 分数 + clusterEvidence 分数

#### 分项计算

1. **Points 分数**（最高 35 分）
   ```
   logScore = ln(1 + min(points, 500)) / ln(1 + 500) * 35
   ```
   - 来源：`metrics.points` 字段
   - 上限：500 points（超过仍按 500 计算）
   - 对数压缩：避免大值主导

2. **Comments 分数**（最高 20 分）
   ```
   logScore = ln(1 + min(comments, 200)) / ln(1 + 200) * 20
   ```
   - 来源：`metrics.commentsCount` 字段
   - 上限：200 comments

3. **Freshness 分数**（最高 30 分）
   ```
   ageHours = max(0, now - publishedAt).toHours()
   freshness = max(0, 30 * (1 - ageHours / 72))
   ```
   - 来源：`publishedAt` 字段
   - 线性衰减：72 小时内从 30 降到 0

4. **Keywords 分数**（最高 10 分）
   ```
   keywordScore = min(10, keywordCount * 2)
   ```
   - 来源：`tags` 数组大小
   - 每个关键词 2 分，最高 10 分

5. **Cluster Evidence 分数**（最高 5 分）
   ```
   evidenceScore = min(5, max(0, (itemCount - 1) * 2.5))
   ```
   - 来源：cluster 中的 item 数量
   - 2 个 item 开始得分，4 个 item 满分

### ScoreBreakdown 结构

```
{
  "points": 25.3,        // points 分数
  "comments": 12.1,      // comments 分数
  "freshness": 28.5,     // freshness 分数
  "keyword": 6.0,        // keywords 分数
  "clusterEvidence": 2.5  // cluster evidence 分数
}
```

总分 = 分项之和（示例：25.3 + 12.1 + 28.5 + 6.0 + 2.5 = 74.4）

### 来源指标映射

各来源将原始指标映射到 `metrics.points` 和 `metrics.commentsCount`：

| 来源 | Points 来源 | CommentsCount 来源 |
|------|------------|-------------------|
| HackerNews | score | descendants |
| GitHub | stargazersCount | forksCount |
| HuggingFace | downloads | likes |
| Bing/DuckDuckGo | max(1, totalCount-rank+1) | 0 |
| Weibo | num | 0 |
| Twitter | like_count | retweet_count |
| Sogou | score | 0 |
| Arxiv | - | - |

### 已知限制

- 所有来源的指标都压缩到 `points` 和 `commentsCount`，丢失来源特定语义
- GitHub stars 和 HF downloads 的影响通过对数压缩被削弱
- 搜索源的排名信号不区分为社会热度
- 微博热度的数值与 HN points 的量纲不同，但使用同一套公式

## V1 可重复性验证

### 验收测试状态（2026-07-15）

- 后端测试：156/156 通过
  - 单元测试：覆盖评分公式、聚类规则、指标映射
  - 集成测试：覆盖端到端数据流（crawl → raw → hot → cluster → score）
- 前端测试：17/17 通过
- 前端构建：成功

### 关键集成测试

- `CrossSourceClusterIntegrationTest`: 验证跨来源聚类（HN + arXiv、HN + GitHub）
- `EvaluationFlowIntegrationTest`: 验证评价数据集、用例、运行器完整流程
- 各来源 `RawDataFlowIntegrationTest`: 验证每个来源的端到端流程

## V2 对照目标

V2 应在保持可重复性的前提下改进 V1 的限制：

1. **信号层分离**：引入 `NormalizedSignal` 区分来源语义（Phase 13B）
2. **增长趋势**：增加时间序列快照和增长计算（Phase 14）
3. **评分 V2**：基于标准信号重新设计评分公式（Phase 15）
4. **聚类 V2**：引入语义相似度匹配（Phase 16）

V1 基线将保留为"shadow mode"，确保 V2 改进不引入回退。

## Phase 13A Replay Fixture

`V1BaselineReplayIntegrationTest` is the repeatable V1 replay fixture for the frozen baseline.

- It creates two fixed Hacker News hot items with the same `sourceUrl`.
- The first item creates a `SINGLETON` cluster membership.
- The second item joins the same cluster through `CANONICAL_URL`.
- Membership rows keep `hn-rule-v1`.
- Scoring keeps `hn-score-v1`.
- Score components are checked for `points`, `comments`, `freshness`, `keyword`, and `clusterEvidence`.

Run it with:

```powershell
cd backend
.\mvnw.cmd "-Dtest=com.airadar.baseline.V1BaselineReplayIntegrationTest" test
```
