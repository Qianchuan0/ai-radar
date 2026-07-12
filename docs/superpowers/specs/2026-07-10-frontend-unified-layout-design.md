# 前端统一布局与语言对齐设计

日期：2026-07-10
状态：已与维护者确认设计，待实现

## 背景

当前前端 6 个页面存在两类问题：

1. **风格不一致**：每个页面各自实现了一套 sidebar + topbar 布局，且演进出两套完全不同的视觉风格。
   - 浅色专业风（`HotClusterListPage` / `HotClusterDetailPage`）：白色渐变 sidebar、雷达 brand-mark、主色 `#1769f5`。
   - 深色现代风（`AlertsPage` / `DailyReportsPage` / `EvaluationPage`）：深色 sidebar `#101828`、彩色光晕渐变背景、彩色方块 brand-mark，且三页光晕色互不相同。
   - `HotClusterStatePage` 完全没有 sidebar/topbar，脱离整体布局。
   - `styles.css` 中已设计一套 `radar-*` 设计令牌（第 52-370 行），但从未被任何页面使用。
2. **语言不一致**：列表页、详情页（大部分）、告警页、状态页是中文；日报页、评测页整页英文；详情页 "Structured analysis" 区块在中文区里夹了英文。`main.ts` 未引入 ant-design-vue 中文 locale，组件库内置文案（Empty、Pagination 等）默认英文。

根因：没有统一的应用外壳（App Shell）布局组件，每个页面自己重复实现布局并各自演进，导致风格与语言持续漂移。

## 目标

- 抽取统一的 AppShell 布局组件，所有页面共用，消除重复。
- 统一视觉风格为浅色专业风。
- 统一界面语言为中文，配置 ant-design-vue 中文 locale。
- 统一菜单项与面包屑，由路由 meta 驱动，只声明一次。
- 统一时间格式化工具，消除各页面重复实现且语言不同的 relativeTime/formatDateTime。

## 关键决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 统一方向 | 抽取统一 AppShell 布局组件 | 治本，消除每页重复实现布局导致的漂移 |
| 语言基准 | 中文 + ant-design-vue zh_CN locale | 与 AGENTS.md 及维护者中文交流习惯一致 |
| 菜单结构 | 只保留有真实路由的菜单项 | 遵循 AGENTS.md 的 YAGNI 原则，去掉占位的"数据源/系统设置/仪表盘" |
| 视觉基准 | 浅色专业风 | 最早的视觉基准，与 styles.css radar-* 设计意图一致，符合专业情报平台定位 |

## 架构设计

### 新增组件

**`frontend/src/layouts/AppLayout.vue`** — 统一应用外壳

职责：承载固定结构（brand + nav + account + topbar + breadcrumb + main slot），不依赖任何业务数据。菜单激活态与面包屑由当前路由 meta 计算。

内部结构：
```
<div class="layout">
  <aside class="sidebar">
    <div class="brand">…</div>
    <nav class="nav">
      <RouterLink v-for="item in menuItems" …/>   ← menuItems 由路由 meta 过滤生成
    </nav>
    <section class="account">AI Radar 团队 + MVP pill</section>
  </aside>
  <header class="topbar">
    <div class="crumbs">面包屑（由 route.meta.breadcrumb 渲染）</div>
    <div class="top-actions"><slot name="topbar-actions" /></div>
  </header>
  <main class="main"><slot /></main>
</div>
```

样式用 scoped style，尺寸与颜色用具体值（不依赖易冲突的 CSS 变量），视觉参数取自 `hot-cluster-list.css`。

**`frontend/src/shared/utils/datetime.ts`** — 统一中文时间格式化

```ts
export function relativeTime(value: string | null | undefined): string
// 无值返回 "--"；否则 "刚刚" / "X 分钟前" / "X 小时前" / "X 天前"
export function formatDateTime(value: string | null | undefined): string
// 无值或无效返回 "--"；否则 "YYYY-MM-DD HH:mm"
```

接受 `null | undefined` 是因为评测页 `latestRun.finishedAt`、日报页字段可能为空。

消除 6 个页面里各自重复且语言不同的实现。

### 路由增强（`router/index.ts`）

每条路由增加 `meta`：

| route name | menuText | breadcrumb | inMenu |
|---|---|---|---|
| `clusters` | 热点榜单 | ["热点榜单", "事件级热点"] | true |
| `cluster-states` | — | ["热点榜单", "状态页"] | false |
| `cluster-detail` | — | ["热点榜单", "热点详情"] | false |
| `alerts` | 订阅告警 | ["热点榜单", "订阅告警"] | true |
| `daily-reports` | 日报 | ["热点榜单", "日报"] | true |
| `evaluation` | 评测 | ["热点榜单", "评测"] | true |

`menuItems` 由 `inMenu === true` 的路由生成。面包屑第一级"热点榜单"作为可点击主入口（链接到 `/clusters`）。

菜单激活态规则：
1. 精确匹配 `route.name` 命中某菜单项 → 高亮该项
2. 未精确命中时，按路由前缀归并：`cluster-detail` / `cluster-states` 归并到 `clusters`，高亮"热点榜单"

这样从榜单进入详情页或状态页时，"热点榜单"保持高亮。

### App.vue

```vue
<a-config-provider :locale="zhCN">
  <AppLayout>
    <RouterView />
  </AppLayout>
</a-config-provider>
```

`zhCN` 从 `ant-design-vue/es/locale/zh_CN` 引入。Empty / Pagination 等组件内置文案随之转中文。`main.ts` 追加注册 `ConfigProvider`。

### 组件边界：status-card 移出 AppShell

当前各页面 sidebar 底部的 status-card 内容各不相同（列表页是"数据来源/刷新"、告警页是"规则数/最新匹配"、日报页是"Selected date/History items"、评测页是"Dataset/Cases"）。这是页面级业务状态，若塞进 AppShell，外壳就得知道每个页面的业务语义，边界混乱。

决策：AppShell 只承载固定结构，status-card 由各页面移到自己的 `<main>` 内容区作为普通面板渲染。账号区统一显示"AI Radar 团队" + `MVP` pill（去掉飘忽的 Phase 6/7/8 pill）。

## 样式整合方案

### AppLayout.vue 布局规则

scoped style 承载全部布局，用具体值（避免 CSS 变量冲突）：
- sidebar：固定 256px 宽，白色渐变背景 `linear-gradient(180deg, #fff, #fbfdff)`，右侧 `1px solid #e5ebf3` 边框
- brand：雷达图标（圆形描边 mark）+ "AI Radar" 文字
- nav：浅色项，激活态蓝色 `#1769f5` + 左侧蓝色指示条
- topbar：固定 64px 高，毛玻璃白底
- main：`margin-left: 256px`，`padding: 86px 40px 42px`

### 各页面 CSS 清理（5 个文件统一处理）

**删除**（每页重复的全局与布局规则）：
- `:root {…}` 变量块、`body{…}`、`svg{…}`、`*{box-sizing}` 全局重置（归 `styles.css`）
- 根容器：`.app` / `.alerts-app` / `.reports-app` / `.evaluation-app`
- 布局 class：`.sidebar` `.brand` `.brand-mark` `.brand-dot` `.nav` `.nav-item` `.status-card` `.status-title` `.live` `.source-list` `.source-row` `.source-icon` `.hn` `.arxiv` `.github` `.status-link` `.account` `.avatar` `.account-copy` `.account-name` `.pill` `.topbar` `.crumbs` `.crumb-current` `.top-actions` `.search-shell` `.kbd` `.icon-button` `.main` `.page-title` `.subtitle`
- 告警/日报/评测页的深色风格规则（深色 sidebar 背景、彩色光晕 radial-gradient、彩色渐变 brand-mark）整段删除

**保留**（页面业务专属样式）：
- 列表页：`.filter-panel` `.filters` `.field-label` `.control` `.button` `.metrics` `.metric-card` `.table-panel` `.ranking-state` `.medal` `.footer` `.pager` 等
- 详情页：`.hero` `.tabs` `.content-grid` `.card` `.score-list` `.timeline` `.evidence-list` `.analysis-*` 等
- 告警页：`.form-panel` `.form-grid` `.rule-card` `.alert-card` `.chip` `.state-banner` 等
- 日报页：`.report-card` `.cluster-card` `.score-box` `.history-card` 等
- 评测页：`.case-card` `.case-type-table` `.metric-grid` `.payload-box` 等

### source-icon 处理

`.source-icon` / `.hn` / `.arxiv` / `.github` 三色块是业务样式（列表/详情/告警页都用），`styles.css` 第 204-225 行已有定义，统一用它，各页面 css 里的重复定义删除。

### styles.css

本次不动。里面的 radar-* 虽是死代码，但清理它超出当前任务范围，留待后续单独处理。

## 语言统一方案

### ant-design-vue 中文 locale

`App.vue` 用 `<a-config-provider :locale="zhCN">` 包裹根节点，`zhCN` 从 `ant-design-vue/es/locale/zh_CN` 引入。`main.ts` 追加注册 `ConfigProvider`。

### 文案统一对照（代表性示例，完整版在实现时逐条处理）

**日报页（DailyReportsPage）**：
- Report Status → 报告状态
- Generate report / Generating... → 生成报告 / 生成中...
- Evidence-backed daily report → 基于证据的日报
- Report output → 报告内容
- Score breakdown → 评分明细
- Latest analysis → 最新分析
- History → 历史报告
- Prev / Next → 上一页 / 下一页
- just now / min ago / h ago / d ago → 采用 datetime.ts 中文版

**评测页（EvaluationPage）**：
- Evaluation Status → 评测状态
- Run evaluation / Running... → 运行评测 / 运行中...
- Pass rate → 通过率
- By case type → 按用例类型
- Failed and error cases → 失败与错误用例
- Actual payload / Expected payload → 实际结果 / 期望结果
- just now / min ago / h ago / d ago → 采用 datetime.ts 中文版

**详情页 analysis 区块（HotClusterDetailPage）**：
- Structured analysis → 结构化分析
- Generate analysis / Generating... → 生成分析 / 生成中...
- Confidence → 置信度
- Key signals → 关键信号
- Evidence refs → 证据引用
- No structured analysis yet... → 暂无结构化分析，可基于当前证据生成。
- Missing valid cluster id. → 缺少有效的热点 ID。

**菜单/nav aria-label**：`Primary` → `主导航`

**账号区**：`AI Radar Team` → `AI Radar 团队`（各页面统一）

### 时间格式化统一

6 个页面里重复的 `relativeTime` / `formatDateTime` 全部替换为 `shared/utils/datetime.ts` 的中文实现。删除各页面内的本地实现。

## 文件改动清单

### 新增
- `frontend/src/layouts/AppLayout.vue`
- `frontend/src/shared/utils/datetime.ts`

### 修改
- `frontend/src/App.vue` — 用 a-config-provider + AppLayout 包裹 RouterView
- `frontend/src/main.ts` — 追加注册 ConfigProvider
- `frontend/src/router/index.ts` — 每条路由加 meta
- `frontend/src/pages/HotClusterListPage.vue` — 删 sidebar/topbar 模板，status-card 移入 main，时间函数改用 datetime.ts
- `frontend/src/pages/HotClusterDetailPage.vue` — 删 sidebar/topbar 模板，analysis 区块英文转中文，时间函数改用 datetime.ts
- `frontend/src/pages/HotClusterStatePage.vue` — 内容不变，由 AppShell 提供布局
- `frontend/src/pages/AlertsPage.vue` — 删 sidebar/topbar 模板，status-card 移入 main
- `frontend/src/pages/DailyReportsPage.vue` — 删 sidebar/topbar 模板，status-card 移入 main，英文转中文，时间函数改用 datetime.ts
- `frontend/src/pages/EvaluationPage.vue` — 删 sidebar/topbar 模板，status-card 移入 main，英文转中文，时间函数改用 datetime.ts
- `frontend/src/styles/hot-cluster-list.css` — 删布局规则，留业务样式
- `frontend/src/styles/hot-cluster-detail.css` — 删布局规则，留业务样式
- `frontend/src/styles/alerts-page.css` — 删布局规则与深色风格，留业务样式
- `frontend/src/styles/daily-reports-page.css` — 删布局规则与深色风格，留业务样式
- `frontend/src/styles/evaluation-page.css` — 删布局规则与深色风格，留业务样式

### 不变
- 后端 API、路由路径、页面业务逻辑、数据契约
- `styles.css`（radar-* 死代码留待后续）
- `shared/api/*`、`shared/utils/query.ts`

## 不在范围内

- 不清理 `styles.css` 中的 radar-* 死代码（单独任务）
- 不做变量名大规模对齐（各页面 css 保留业务变量，只删布局规则）
- 不引入新的组件库或设计系统
- 不改变后端 API 或数据契约
- 不重写状态页的内部展示逻辑（只纳入 AppShell 布局）

## 验证方式

1. 构建通过：`cd frontend && npm run build`
2. 启动后逐页检查：6 个页面视觉风格一致（浅色专业风），菜单项一致（热点榜单/订阅告警/日报/评测），面包屑一致，语言全部中文
3. 切换页面时 sidebar/topbar 不闪烁、不重新渲染（因为是 AppShell 常驻）
4. ant-design-vue 组件内置文案变中文（如空态、分页）
5. 详情页 analysis 区块无英文残留
6. 日报页、评测页无英文残留

## 文档同步

实现完成后需在 `docs/decision-log.md` 追加一条决策：前端统一 AppShell 布局与中文 locale（对应原有第 11 条前端 MVP 技术栈的演进）。
