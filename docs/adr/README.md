# Architecture Decision Records

## 什么是 ADR

ADR（Architecture Decision Record，架构决策记录）用于保存一项重要技术决策发生时的背景、问题、候选方案、最终选择、理由和后果。它记录的是“为什么这样设计”，不是功能说明或会议流水账。

## 什么时候创建 ADR

满足以下一种或多种情况时，可以创建 ADR：

- 决策会长期影响系统架构、领域模型或数据兼容性。
- 存在多个合理候选方案，且取舍并不显然。
- 决策会引入重要依赖、基础设施或运维成本。
- 决策难以低成本回退。
- 团队未来很可能再次询问当时的选择理由。
- 决策需要明确复审条件或演进路径。

普通代码实现细节、小范围重构、尚未讨论的问题或没有真实结论的设想，不创建 ADR。

## 文件命名规范

```text
ADR-XXX-short-decision-title.md
```

- `XXX` 使用三位连续编号，例如 `001`、`002`。
- 标题使用简短、可搜索的英文小写 kebab-case。
- 文件中的标题应与文件名表达同一项决策。
- ADR 创建后编号不复用；被替代的 ADR 保留原文件并更新状态。

示例：

```text
ADR-001-primary-database.md
ADR-002-clustering-strategy.md
```

## ADR 模板

```markdown
# ADR-XXX: Decision Title

## Status

Proposed | Accepted | Deprecated | Superseded

## Date

YYYY-MM-DD

## Context

## Problem

## Options

## Decision

## Rationale

## Consequences

## Future Revisit Conditions
```

## ADR 写作规则

1. 一份 ADR 只记录一项核心决策。
2. 先写事实背景和约束，再写方案偏好。
3. 至少说明实际考虑过的候选方案及其关键取舍。
4. `Decision` 必须清楚说明选择了什么，也应说明没有选择什么。
5. `Rationale` 解释选择如何满足当前约束，避免使用空泛表述。
6. `Consequences` 同时记录收益、代价、风险和后续工作。
7. `Future Revisit Conditions` 使用可观察条件说明何时需要重新评估。
8. 不修改历史来伪装决策从未变化；使用 `Deprecated` 或 `Superseded` 保留演进记录。
9. ADR 状态变更后，同步更新 `docs/decision-log.md`。
10. 不创建空 ADR，也不为了装饰目录而创建 ADR。

