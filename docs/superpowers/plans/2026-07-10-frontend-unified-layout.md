# 前端统一布局与语言对齐 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 抽取统一 AppShell 布局组件，消除 6 个前端页面各自重复的 sidebar/topbar 实现，统一视觉风格为浅色专业风、界面语言为中文。

**Architecture:** 新增 `AppLayout.vue` 承载固定外壳（sidebar + topbar + main slot），菜单与面包屑由路由 `meta` 驱动；`App.vue` 用 `a-config-provider` + `zhCN` locale 包裹根节点；新增 `shared/utils/datetime.ts` 统一中文时间格式化；6 个页面删除各自的 sidebar/topbar 模板，5 个页面 css 删除布局规则只留业务样式。

**Tech Stack:** Vue 3 + TypeScript + Vite + Vue Router + ant-design-vue 4.2.6 + vitest + @vue/test-utils + happy-dom

## Global Constraints

- 构建命令：`cd frontend && npm run build`（含 `vue-tsc --noEmit` 类型检查）
- 测试命令：`cd frontend && npm test`（vitest run）
- 开发预览：`cd frontend && npm run dev`
- 测试风格参考 `frontend/src/shared/utils/query.test.ts`（vitest describe/it/expect）
- **git commit 由维护者执行**（AGENTS.md 规定 Coding Agent 不自动 commit）。本计划每个任务的 commit 步骤提供 message 供维护者使用。
- 不改变后端 API、路由路径、数据契约。
- 不引入新依赖。
- `styles.css` 本次不动（radar-* 死代码留待后续）。
- 界面语言统一为中文，技术术语 `hot_cluster` / `raw_item` / `arXiv` / `GitHub` / `sourceType` 等保留原文（源自设计文档"中文基准"决策，但这些是技术标识符，非面向用户的英文文案）。

---

## File Structure

**新增：**
- `frontend/src/shared/utils/datetime.ts` — 统一中文时间格式化（relativeTime / formatDateTime）
- `frontend/src/shared/utils/datetime.test.ts` — datetime 单元测试
- `frontend/src/layouts/menu.ts` — 菜单派生逻辑（selectMenuItems / resolveActiveMenuName）
- `frontend/src/layouts/menu.test.ts` — menu 单元测试
- `frontend/src/layouts/AppLayout.vue` — 统一应用外壳组件

**修改：**
- `frontend/src/router/index.ts` — 每条路由加 meta（menuText / breadcrumb / inMenu）
- `frontend/src/App.vue` — a-config-provider + AppLayout 包裹
- `frontend/src/main.ts` — 注册 ConfigProvider
- `frontend/src/pages/HotClusterListPage.vue` — 删 sidebar/topbar，status-card 移入 main，用 datetime
- `frontend/src/pages/HotClusterDetailPage.vue` — 删 sidebar/topbar，analysis 区块中文化，用 datetime
- `frontend/src/pages/HotClusterStatePage.vue` — 去掉自带外壳依赖（内容由 AppShell 承载）
- `frontend/src/pages/AlertsPage.vue` — 删 sidebar/topbar，status-card 移入 main
- `frontend/src/pages/DailyReportsPage.vue` — 删 sidebar/topbar，英文转中文，用 datetime，status-card 移入 main
- `frontend/src/pages/EvaluationPage.vue` — 删 sidebar/topbar，英文转中文，用 datetime，status-card 移入 main
- `frontend/src/styles/hot-cluster-list.css` — 删布局规则，留业务样式
- `frontend/src/styles/hot-cluster-detail.css` — 删布局规则，留业务样式
- `frontend/src/styles/alerts-page.css` — 删布局规则与深色风格，留业务样式
- `frontend/src/styles/daily-reports-page.css` — 删布局规则与深色风格，留业务样式
- `frontend/src/styles/evaluation-page.css` — 删布局规则与深色风格，留业务样式

**文档同步：**
- `docs/decision-log.md` — 追加前端统一 AppShell 决策（最后任务）

---

## Task 1: datetime.ts 统一时间格式化工具

**Files:**
- Create: `frontend/src/shared/utils/datetime.ts`
- Test: `frontend/src/shared/utils/datetime.test.ts`

**Interfaces:**
- Produces: `relativeTime(value: string | null | undefined): string`、`formatDateTime(value: string | null | undefined): string`，供 Task 6-11 各页面使用。

- [ ] **Step 1: 写失败测试**

Create `frontend/src/shared/utils/datetime.test.ts`:

```ts
import { describe, expect, it, vi, afterEach } from "vitest";
import { formatDateTime, relativeTime } from "./datetime";

describe("datetime helpers", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("relativeTime returns placeholder for empty value", () => {
    expect(relativeTime(null)).toBe("--");
    expect(relativeTime(undefined)).toBe("--");
    expect(relativeTime("")).toBe("--");
    expect(relativeTime("not-a-date")).toBe("--");
  });

  it("relativeTime formats Chinese relative time buckets", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-07-10T12:00:00Z").getTime());

    const now = new Date("2026-07-10T12:00:00Z").toISOString();
    expect(relativeTime(now)).toBe("刚刚");

    const tenMinutesAgo = new Date("2026-07-10T11:50:00Z").toISOString();
    expect(relativeTime(tenMinutesAgo)).toBe("10 分钟前");

    const twoHoursAgo = new Date("2026-07-10T10:00:00Z").toISOString();
    expect(relativeTime(twoHoursAgo)).toBe("2 小时前");

    const threeDaysAgo = new Date("2026-07-07T12:00:00Z").toISOString();
    expect(relativeTime(threeDaysAgo)).toBe("3 天前");
  });

  it("formatDateTime returns placeholder for empty or invalid value", () => {
    expect(formatDateTime(null)).toBe("--");
    expect(formatDateTime(undefined)).toBe("--");
    expect(formatDateTime("not-a-date")).toBe("--");
  });

  it("formatDateTime formats as YYYY-MM-DD HH:mm", () => {
    const iso = new Date("2026-07-10T14:05:00").toISOString();
    const result = formatDateTime(iso);
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- datetime`
Expected: FAIL with "Failed to resolve import ./datetime"

- [ ] **Step 3: 写最小实现**

Create `frontend/src/shared/utils/datetime.ts`:

```ts
const PLACEHOLDER = "--";

export function relativeTime(value: string | null | undefined): string {
  if (!value) return PLACEHOLDER;
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return PLACEHOLDER;

  const diffMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (diffMinutes < 1) return "刚刚";
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} 小时前`;

  return `${Math.floor(diffHours / 24)} 天前`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return PLACEHOLDER;
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return PLACEHOLDER;

  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd frontend && npm test -- datetime`
Expected: PASS（5 个测试全过）

- [ ] **Step 5: Commit（维护者执行）**

```bash
git add frontend/src/shared/utils/datetime.ts frontend/src/shared/utils/datetime.test.ts
git commit -m "feat: add shared datetime utils for unified Chinese time formatting"
```

---

## Task 2: 路由 meta 增强

**Files:**
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Produces: 每条路由带 `meta: { menuText?, breadcrumb?, inMenu? }`，供 Task 3 `selectMenuItems` 与 Task 4 AppLayout 面包屑使用。

- [ ] **Step 1: 替换 router/index.ts 全文**

Replace `frontend/src/router/index.ts` with:

```ts
import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";

export const routes: RouteRecordRaw[] = [
    {
        path: "/",
        redirect: "/clusters"
    },
    {
        path: "/clusters",
        name: "clusters",
        component: () => import("../pages/HotClusterListPage.vue"),
        meta: { menuText: "热点榜单", breadcrumb: ["热点榜单", "事件级热点"], inMenu: true }
    },
    {
        path: "/clusters/states",
        name: "cluster-states",
        component: () => import("../pages/HotClusterStatePage.vue"),
        meta: { breadcrumb: ["热点榜单", "状态页"], inMenu: false }
    },
    {
        path: "/clusters/:clusterId",
        name: "cluster-detail",
        component: () => import("../pages/HotClusterDetailPage.vue"),
        meta: { breadcrumb: ["热点榜单", "热点详情"], inMenu: false }
    },
    {
        path: "/alerts",
        name: "alerts",
        component: () => import("../pages/AlertsPage.vue"),
        meta: { menuText: "订阅告警", breadcrumb: ["热点榜单", "订阅告警"], inMenu: true }
    },
    {
        path: "/reports/daily",
        name: "daily-reports",
        component: () => import("../pages/DailyReportsPage.vue"),
        meta: { menuText: "日报", breadcrumb: ["热点榜单", "日报"], inMenu: true }
    },
    {
        path: "/evaluation",
        name: "evaluation",
        component: () => import("../pages/EvaluationPage.vue"),
        meta: { menuText: "评测", breadcrumb: ["热点榜单", "评测"], inMenu: true }
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

export default router;
```

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit（维护者执行）**

```bash
git add frontend/src/router/index.ts
git commit -m "feat: add route meta for menu and breadcrumb derivation"
```

---

## Task 3: layouts/menu.ts 菜单派生逻辑

**Files:**
- Create: `frontend/src/layouts/menu.ts`
- Test: `frontend/src/layouts/menu.test.ts`

**Interfaces:**
- Consumes: `routes` 与 `RouteRecordRaw` from Task 2
- Produces: `selectMenuItems(routes)` → `MenuItem[]`、`resolveActiveMenuName(routeName)` → `string | undefined`，供 Task 4 AppLayout 使用。

```ts
export interface MenuItem {
  name: string;
  menuText: string;
}
```

- [ ] **Step 1: 写失败测试**

Create `frontend/src/layouts/menu.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import type { RouteRecordRaw } from "vue-router";
import { resolveActiveMenuName, selectMenuItems } from "./menu";

const routes: RouteRecordRaw[] = [
    { path: "/a", name: "clusters", component: {}, meta: { menuText: "热点榜单", inMenu: true } },
    { path: "/b", name: "alerts", component: {}, meta: { menuText: "订阅告警", inMenu: true } },
    { path: "/c", name: "cluster-detail", component: {}, meta: { inMenu: false } },
    { path: "/c2", name: "cluster-states", component: {}, meta: { inMenu: false } },
    { path: "/d", name: "hidden", component: {}, meta: { inMenu: false } }
];

describe("menu derivation", () => {
    it("selects only routes with inMenu true", () => {
        const items = selectMenuItems(routes);
        expect(items).toEqual([
            { name: "clusters", menuText: "热点榜单" },
            { name: "alerts", menuText: "订阅告警" }
        ]);
    });

    it("resolves exact match first", () => {
        expect(resolveActiveMenuName("alerts", routes)).toBe("alerts");
    });

    it("merges cluster-detail and cluster-states into clusters", () => {
        expect(resolveActiveMenuName("cluster-detail", routes)).toBe("clusters");
        expect(resolveActiveMenuName("cluster-states", routes)).toBe("clusters");
    });

    it("returns undefined when nothing matches", () => {
        expect(resolveActiveMenuName("unknown", routes)).toBeUndefined();
        expect(resolveActiveMenuName(undefined, routes)).toBeUndefined();
    });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- menu`
Expected: FAIL with "Failed to resolve import ./menu"

- [ ] **Step 3: 写最小实现**

Create `frontend/src/layouts/menu.ts`:

```ts
import type { RouteRecordRaw } from "vue-router";

export interface MenuItem {
    name: string;
    menuText: string;
}

interface MenuMeta {
    menuText?: string;
    inMenu?: boolean;
}

export function selectMenuItems(routes: RouteRecordRaw[]): MenuItem[] {
    return routes
        .filter((route) => (route.meta as MenuMeta | undefined)?.inMenu === true)
        .map((route) => ({
            name: String(route.name),
            menuText: (route.meta as MenuMeta).menuText ?? String(route.name)
        }));
}

const CLUSTER_FAMILY_PREFIX = "cluster-";

export function resolveActiveMenuName(
    routeName: string | symbol | undefined,
    routes: RouteRecordRaw[]
): string | undefined {
    if (!routeName) return undefined;
    const name = String(routeName);

    const exact = routes.find((route) => String(route.name) === name);
    if (exact && (exact.meta as MenuMeta | undefined)?.inMenu === true) {
        return name;
    }

    if (name.startsWith(CLUSTER_FAMILY_PREFIX)) {
        const clusters = routes.find((route) => route.name === "clusters");
        if (clusters && (clusters.meta as MenuMeta | undefined)?.inMenu === true) {
            return "clusters";
        }
    }

    return undefined;
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd frontend && npm test -- menu`
Expected: PASS（4 个测试全过）

- [ ] **Step 5: Commit（维护者执行）**

```bash
git add frontend/src/layouts/menu.ts frontend/src/layouts/menu.test.ts
git commit -m "feat: add menu derivation helpers for app shell"
```

---

## Task 4: AppLayout.vue 统一应用外壳

**Files:**
- Create: `frontend/src/layouts/AppLayout.vue`

**Interfaces:**
- Consumes: `routes` from Task 2，`selectMenuItems` / `resolveActiveMenuName` from Task 3
- Produces: 默认 slot 承载页面内容；命名 slot `topbar-actions` 承载页面级顶栏动作。

- [ ] **Step 1: 创建 AppLayout.vue**

Create `frontend/src/layouts/AppLayout.vue`:

```vue
<template>
  <div class="layout">
    <aside class="layout__sidebar">
      <div class="layout__brand">
        <div class="layout__brand-mark"><span class="layout__brand-dot" /></div>
        <span>AI Radar</span>
      </div>

      <nav class="layout__nav" aria-label="主导航">
        <RouterLink
          v-for="item in menuItems"
          :key="item.name"
          class="layout__nav-item"
          :class="{ active: activeMenuName === item.name }"
          :to="{ name: item.name }"
        >
          {{ item.menuText }}
        </RouterLink>
      </nav>

      <section class="layout__account">
        <div class="layout__avatar">A</div>
        <div class="layout__account-copy">
          <div class="layout__account-name">AI Radar 团队</div>
          <span class="layout__pill">MVP</span>
        </div>
      </section>
    </aside>

    <header class="layout__topbar">
      <nav class="layout__crumbs" aria-label="面包屑">
        <template v-for="(crumb, index) in breadcrumb" :key="`${crumb}-${index}`">
          <span v-if="index > 0" class="layout__crumb-sep">/</span>
          <RouterLink
            v-if="index === 0"
            class="layout__crumb-link"
            :to="{ name: 'clusters' }"
          >{{ crumb }}</RouterLink>
          <span v-else-if="index === breadcrumb.length - 1" class="layout__crumb-current">{{ crumb }}</span>
          <span v-else class="layout__crumb-link">{{ crumb }}</span>
        </template>
      </nav>

      <div class="layout__topbar-actions">
        <slot name="topbar-actions" />
      </div>
    </header>

    <main class="layout__main">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { routes } from "../router";
import { resolveActiveMenuName, selectMenuItems } from "./menu";

const route = useRoute();

const menuItems = computed(() => selectMenuItems(routes));
const activeMenuName = computed(() => resolveActiveMenuName(route.name, routes));
const breadcrumb = computed<string[]>(() => {
  const value = route.meta?.breadcrumb;
  return Array.isArray(value) ? value.map(String) : [];
});
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout__sidebar {
  position: fixed;
  z-index: 5;
  inset: 0 auto 0 0;
  width: 256px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border-right: 1px solid #e5ebf3;
}

.layout__brand {
  height: 70px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  color: #17213b;
  font-size: 26px;
  font-weight: 900;
}

.layout__brand-mark {
  width: 36px;
  height: 36px;
  position: relative;
  color: #1769f5;
}

.layout__brand-mark::before,
.layout__brand-mark::after {
  content: "";
  position: absolute;
  border: 4px solid currentColor;
  border-right-color: transparent;
  border-bottom-color: transparent;
  border-radius: 50%;
  transform: rotate(-45deg);
}

.layout__brand-mark::before { inset: 1px; }
.layout__brand-mark::after { inset: 11px; }

.layout__brand-dot {
  position: absolute;
  width: 8px;
  height: 8px;
  left: 14px;
  top: 14px;
  border-radius: 50%;
  background: currentColor;
}

.layout__nav {
  padding: 10px 12px;
}

.layout__nav-item {
  height: 46px;
  margin: 7px 0;
  padding: 0 18px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  gap: 13px;
  color: #485777;
  text-decoration: none;
  font-size: 14px;
  font-weight: 750;
  position: relative;
}

.layout__nav-item.active {
  color: #1769f5;
  background: linear-gradient(90deg, #e8f1ff 0%, #f3f7ff 100%);
}

.layout__nav-item.active::before {
  content: "";
  position: absolute;
  left: -1px;
  top: 9px;
  width: 3px;
  height: 28px;
  border-radius: 999px;
  background: #1769f5;
}

.layout__account {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 84px;
  padding: 15px 18px;
  border-top: 1px solid #e5ebf3;
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout__avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(160deg, #1682ff, #0057e7);
  font-size: 17px;
  font-weight: 900;
  box-shadow: 0 6px 16px rgba(23, 105, 245, 0.28);
}

.layout__account-copy {
  flex: 1;
  min-width: 0;
}

.layout__account-name {
  font-size: 14px;
  font-weight: 900;
  color: #1b2540;
}

.layout__pill {
  display: inline-block;
  margin-top: 2px;
  padding: 1px 7px 2px;
  border-radius: 999px;
  background: #e8f1ff;
  color: #1769f5;
  font-size: 11px;
  font-weight: 800;
}

.layout__topbar {
  position: fixed;
  z-index: 4;
  top: 0;
  left: 256px;
  right: 0;
  height: 60px;
  padding: 0 28px 0 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid #e5ebf3;
  backdrop-filter: blur(12px);
}

.layout__crumbs {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #64748b;
  font-size: 14px;
  font-weight: 750;
}

.layout__crumb-link {
  color: #64748b;
  text-decoration: none;
}

.layout__crumb-sep {
  color: #cbd5e1;
}

.layout__crumb-current {
  color: #0f172a;
  font-weight: 900;
}

.layout__topbar-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.layout__main {
  margin-left: 256px;
  padding: 84px 34px 32px;
}
</style>
```

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误

- [ ] **Step 3: Commit（维护者执行）**

```bash
git add frontend/src/layouts/AppLayout.vue
git commit -m "feat: add AppLayout shell with sidebar, topbar and breadcrumb"
```

---

## Task 5: App.vue + main.ts 接入 AppShell 与中文 locale

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/main.ts`

**Interfaces:**
- Consumes: AppLayout from Task 4，zhCN locale from ant-design-vue。

- [ ] **Step 1: 替换 App.vue 全文**

Replace `frontend/src/App.vue` with:

```vue
<template>
  <a-config-provider :locale="zhCN">
    <AppLayout>
      <RouterView />
    </AppLayout>
  </a-config-provider>
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import AppLayout from "./layouts/AppLayout.vue";
</script>
```

- [ ] **Step 2: 在 main.ts 注册 ConfigProvider**

Modify `frontend/src/main.ts`. 在已注册组件数组里加入 `ConfigProvider`：

```ts
import { createApp } from "vue";
import {
    Alert,
    Button,
    Card,
    ConfigProvider,
    Empty,
    Layout,
    Pagination,
    Select,
    Skeleton,
    Tag
} from "ant-design-vue";
import App from "./App.vue";
import router from "./router";
import "ant-design-vue/dist/reset.css";
import "./styles.css";

const app = createApp(App);

[
    Alert,
    Button,
    Card,
    ConfigProvider,
    Empty,
    Layout,
    Pagination,
    Select,
    Skeleton,
    Tag
].forEach((component) => app.use(component));

app.use(router).mount("#app");
```

- [ ] **Step 3: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过（此时页面仍能渲染，虽然子页面还各自带 sidebar，视觉会重复，下一任务开始清理）

- [ ] **Step 4: Commit（维护者执行）**

```bash
git add frontend/src/App.vue frontend/src/main.ts
git commit -m "feat: wrap app with ConfigProvider zhCN locale and AppLayout shell"
```

---

## Task 6: HotClusterListPage 改造

**Files:**
- Modify: `frontend/src/pages/HotClusterListPage.vue`
- Modify: `frontend/src/styles/hot-cluster-list.css`

**Scope:** 删除页面自带的 sidebar/topbar 模板，status-card 移入 main 内容区，本地 relativeTime 改用 shared datetime，清理 css 布局规则。

- [ ] **Step 1: 改造 HotClusterListPage.vue 模板**

把 `<template>` 内最外层 `<div class="app">…</div>` 改为只保留 `<main>` 内部内容，并去掉 `<main class="main">` 包裹（AppLayout 已提供 main）。原 sidebar 的 status-card 作为面板移到内容顶部。

新 `<template>` 结构（替换原 template 全部）：

```vue
<template>
  <section class="list-page">
    <section class="status-panel">
      <div class="status-title">数据更新 <span class="live">实时</span></div>
      <div>最后刷新：<span>{{ generatedAtLabel }}</span></div>
      <div class="status-sources">
        <span class="status-sources-label">数据来源：</span>
        <span v-for="source in sourceStatusRows" :key="source.name" class="status-source">
          {{ source.name }}
        </span>
      </div>
      <button class="status-refresh" type="button" :disabled="loading" @click="reload">
        {{ loading ? "加载中..." : "刷新真实数据" }}
      </button>
    </section>

    <h1 class="page-title">事件级热点榜单</h1>
    <p class="subtitle">展示真实 hot_cluster 数据，并在前端做轻量筛选与排序。</p>

    <!-- 原 filter-panel / metrics / table-panel 整段保留不变 -->
    <section class="filter-panel"> … </section>
    <section class="metrics"> … </section>
    <section class="table-panel"> … </section>
  </section>
</template>
```

注意：filter-panel / metrics / table-panel 三个 section 的内部内容**完全保留原样**（包括 loading/error/empty 三态和表格、footer 分页），只是它们现在直接是 `<section class="list-page">` 的子节点，不再嵌在 `<main class="main">` 下。

- [ ] **Step 2: 调整 script — 引用 shared datetime**

在 `<script setup lang="ts">` 顶部导入：

```ts
import { formatDateTime as formatDateTimeUtil, relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
```

删除页面内本地定义的 `function relativeTime(value: string): string { … }` 整个函数（约在原文件第 425-434 行）。

然后把模板里所有 `relativeTime(...)` 调用替换为 `relativeTimeUtil(...)`，`generatedAtLabel` 的计算保持用 relativeTimeUtil。

具体改 `generatedAtLabel`：

```ts
const generatedAtLabel = computed(() =>
  generatedAt.value ? relativeTimeUtil(generatedAt.value) : "等待加载"
);
```

模板里 `relativeTime(row.lastSeenAt)` 改为 `relativeTimeUtil(row.lastSeenAt)`。

- [ ] **Step 3: 清理 hot-cluster-list.css**

删除该文件中的以下规则段（行号近似，按实际匹配）：
- 第 1-46 行：`:root {…}`、`*{box-sizing}`、`body{…}`、`svg{…}`、`button,input{font:inherit}`
- 第 43-45 行：`.app {…}`
- 第 47-54 行：`.sidebar {…}`
- 第 56-65 行：`.brand {…}`
- 第 67-102 行：`.brand-mark`、`.brand-mark::before/after`、`.brand-dot`
- 第 104-141 行：`.nav`、`.nav-item`、`.nav-item.active` 等
- 第 143-155 行：`.status-card {…}`
- 第 157-165 行：`.status-title {…}`
- 第 167-185 行：`.live {…}`、`.live::before`
- 第 187-191 行：`.source-list {…}`
- 第 193-199 行：`.source-row {…}`
- 第 201-210 行：`.source-icon {…}`
- 第 212-222 行：`.hn`、`.arxiv`、`.github`
- 第 224-231 行：`.status-link {…}`
- 第 233-244 行：`.account {…}`
- 第 246-257 行：`.avatar {…}`
- 第 259-262 行：`.account-copy`
- 第 264-268 行：`.account-name`
- 第 270-279 行：`.pill {…}`
- 第 281-295 行：`.topbar {…}`
- 第 297-304 行：`.crumbs {…}`
- 第 306-310 行：`.crumb-current`
- 第 312-316 行：`.top-actions`
- 第 318-368 行：`.search-shell` 及其子选择器、`.kbd`
- 第 370-381 行：`.icon-button` 及其 svg
- 第 383-386 行：`.main {…}`

**保留**：第 388 行起的所有业务样式（`.page-title`、`.subtitle`、`.filter-panel`、`.filters`、`.field-label`、`.control`、`.button`、`.metrics`、`.metric-card`、`.table-panel`、`.ranking-state`、`.medal`、`.footer`、`.pager`、`.page`、`.page-size`、`.tag`、`.level`、`.score`、`.hot-title`、`.summary`、`.source-chip`、`.new-badge`、`.count`、`.time`、`.detail-jump`、`.state-link`、`.live-note`、`.source-statuses`、`.source-status`、`.spinner`、`.runtime-skeleton`、`.state-illustration` 及其子元素、`col.*`、`@keyframes`、`@media`）。

新增（放文件顶部，给页面级 status-panel 用）：

```css
.list-page {
  display: grid;
  gap: 16px;
}

.status-panel {
  padding: 16px 20px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(16, 24, 40, 0.04);
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.status-panel .status-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #18233f;
  font-size: 14px;
  font-weight: 900;
}

.status-panel .live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eaf8f0;
  color: #14a160;
  font-size: 11px;
  font-weight: 800;
}

.status-panel .live::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #27c678;
}

.status-sources {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.status-sources-label {
  color: #52607a;
}

.status-source {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 9px;
  border: 1px solid #e5ebf3;
  border-radius: 999px;
  background: #fbfdff;
  color: #43516d;
  font-size: 12px;
  font-weight: 800;
}

.status-source::before {
  content: "";
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #20b86a;
}

.status-refresh {
  margin-top: 12px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #1769f5;
  border-radius: 7px;
  background: linear-gradient(180deg, #267aff, #075be7);
  color: #fff;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.status-refresh:disabled {
  opacity: 0.7;
  cursor: wait;
}
```

- [ ] **Step 4: 删除页面 scoped style 里的 .status-link-button**

原页面 `<style scoped>` 里有 `.status-link-button` 规则（约第 462-469 行），该按钮已在模板中删除，scoped style 块整个可清空或保留空块。

- [ ] **Step 5: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过。列表页现在只剩主内容，sidebar/topbar 由 AppLayout 提供。

- [ ] **Step 6: Commit（维护者执行）**

```bash
git add frontend/src/pages/HotClusterListPage.vue frontend/src/styles/hot-cluster-list.css
git commit -m "refactor: move HotClusterListPage into AppLayout and clean layout css"
```

---

## Task 7: HotClusterDetailPage 改造与 analysis 中文化

**Files:**
- Modify: `frontend/src/pages/HotClusterDetailPage.vue`
- Modify: `frontend/src/styles/hot-cluster-detail.css`

**Scope:** 删 sidebar/topbar，analysis 区块英文转中文，本地 relativeTime/formatDateTime 改用 shared datetime，清理 css 布局规则。

- [ ] **Step 1: 改造模板 — 删除 sidebar 与 topbar**

删除原 `<template>` 中：
- `<aside class="sidebar">…</aside>` 整块（原第 3-37 行）
- `<header class="topbar">…</header>` 整块（原第 39-53 行）
- 最外层 `<div>` 包裹（改为 `<section class="detail-page">`）
- 原 `<main class="main">` 包裹去掉，其内部 loading/error/template 内容直接作为 `<section class="detail-page">` 子节点

注意保留详情页 hero / tabs / overview / score / evidence / timeline 各 section 的内部结构与 class 不变。

- [ ] **Step 2: analysis 区块英文转中文**

在模板的 analysis-card 区块内做以下替换：
- `<h2 class="card-title">Structured analysis</h2>` → `<h2 class="card-title">结构化分析</h2>`
- 按钮文案：`{{ analysisTriggering ? "Generating..." : "Generate analysis" }}` → `{{ analysisTriggering ? "生成中..." : "生成分析" }}`
- `<div v-if="analysisLoading" class="analysis-state">Loading the latest structured analysis.</div>` → `…>正在加载最新的结构化分析。</div>`
- `<div v-else-if="analysisError" …>{{ analysisError }}</div>` 保持
- FAILED 分支：`{{ analysis.failureMessage || "Structured analysis failed." }}` → `{{ analysis.failureMessage || "结构化分析失败。" }}`
- `<span class="pill">Confidence {{ analysis.result.confidence }}</span>` → `<span class="pill">置信度 {{ analysis.result.confidence }}</span>`
- `<strong>Key signals</strong>` → `<strong>关键信号</strong>`
- `<strong>Evidence refs</strong>` → `<strong>证据引用</strong>`
- 空态：`<div v-else class="analysis-state">No structured analysis yet. Generate one from the current evidence pack.</div>` → `…>暂无结构化分析，可基于当前证据生成。</div>`

- [ ] **Step 3: 其他英文字符串中文化**

- `runAnalysis` 函数内错误：`analysisError.value = "Missing valid cluster id.";` → `analysisError.value = "缺少有效的热点 ID。";`
- loading 区块：`<strong>正在加载热点详情</strong>` 保持中文；其下 `我们正在请求真实的 cluster detail 数据。` 保持。

- [ ] **Step 4: script 改用 shared datetime**

导入：
```ts
import { formatDateTime as formatDateTimeUtil, relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
```

删除本地定义的 `function relativeTime` 与 `function formatDateTime`（约原第 491-507 行）。

模板与 script 中所有 `relativeTime(...)` 替换为 `relativeTimeUtil(...)`，`formatDateTime(...)` 替换为 `formatDateTimeUtil(...)`。

- [ ] **Step 5: 清理 hot-cluster-detail.css**

删除该文件中：
- 第 1-45 行：`:root`、`*`、`body`、`svg`、`button/input`
- 第 47-251 行：所有布局规则（`.sidebar`、`.brand`、`.brand-mark`、`.brand-dot`、`.nav`、`.nav-item`、`.status-card`、`.status-title`、`.live`、`.source-list`、`.source-row`、`.source-icon`、`.hn/.arxiv/.github`、`.status-link`、`.account`、`.avatar`、`.pill`、`.topbar`、`.crumbs`、`.crumb-current`、`.top-actions`、`.search-shell`、`.kbd`、`.icon-button`、`.main`）

**保留**：`.panel`、`.analysis-*`、`.hero`、`.hero-*`、`.type-badge`、`.tag`、`.level`、`.tabs`、`.tab`、`.content-grid`、`.column`、`.card`、`.card-title`、`.bullets`、`.paragraph`、`.metric-*`、`.score-*`、`.timeline*`、`.evidence-*`、`.state`、`@media`。

新增页面根容器样式（放文件顶部）：

```css
.detail-page {
  display: grid;
  gap: 16px;
}
```

- [ ] **Step 6: 删除页面 scoped style**

原 `<style scoped>` 里的 `.status-link-button` 规则可删除（该按钮已不在模板中）。

- [ ] **Step 7: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过，无英文残留。

- [ ] **Step 8: Commit（维护者执行）**

```bash
git add frontend/src/pages/HotClusterDetailPage.vue frontend/src/styles/hot-cluster-detail.css
git commit -m "refactor: move detail page into AppLayout and localize analysis section"
```

---

## Task 8: HotClusterStatePage 纳入 AppShell

**Files:**
- Modify: `frontend/src/pages/HotClusterStatePage.vue`

**Scope:** 状态页本身没有自带 sidebar/topbar，纳入 AppShell 后只需保证最外层结构正常。此页的 `<style scoped>` 是自包含的（用 `radar-panel` 等 styles.css 全局 class + 自身 scoped 样式），无需改动 css。

- [ ] **Step 1: 检查模板根元素**

`HotClusterStatePage.vue` 最外层是 `<section class="states-page">`，它原本直接挂在 `<main>` 下（旧 App.vue 只有 RouterView）。现在 AppLayout 的 `.layout__main` 包裹它，`.states-page` 的 `display: grid; gap: 20px` 仍正常工作。

确认无需改动模板（除非需调整内边距）。如需对齐其他页面间距，给 `.states-page` 加上原本由 `.main` 提供的间距：因为 AppLayout 已提供 `.layout__main` 的 padding，此页不需额外处理。

**结论：本任务无需代码改动**。状态页已自动由 AppShell 承载。仅需构建验证。

- [ ] **Step 2: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过。访问 `/clusters/states` 应显示 AppShell + 状态页内容。

- [ ] **Step 3: Commit（维护者执行）**

本任务无代码改动，无需 commit。如维护者希望记录，可空提交或跳过。

---

## Task 9: AlertsPage 改造

**Files:**
- Modify: `frontend/src/pages/AlertsPage.vue`
- Modify: `frontend/src/styles/alerts-page.css`

**Scope:** 删 sidebar/topbar，status-card 移入 main，清理 css 布局规则与深色风格。本页文案已基本是中文，无需大改文案。

- [ ] **Step 1: 改造模板**

删除 `<aside class="sidebar">…</aside>` 与 `<header class="topbar">…</header>` 整块。最外层 `<div class="alerts-app">` 改为 `<section class="alerts-page">`。原 `<main class="main">` 包裹去掉。

把原 sidebar 里的 status-card 内容（告警状态/规则数/当前列表/最新匹配/手动运行匹配）作为面板移到内容顶部：

```vue
<section class="alerts-page">
  <section class="status-panel">
    <div class="status-title">告警状态 <span class="live">实时</span></div>
    <div>规则数：<span>{{ subscriptions.length }}</span></div>
    <div>当前列表：<span>{{ alerts.items.length }}</span></div>
    <div>最新匹配：<span>{{ lastRun ? relativeTimeUtil(lastRun.completedAt) : "尚未运行" }}</span></div>
    <button class="status-refresh" type="button" :disabled="matching" @click="triggerMatching">
      {{ matching ? "正在匹配..." : "手动运行匹配" }}
    </button>
  </section>

  <section class="hero">
    <!-- 原 hero 内容不变 -->
  </section>

  <!-- 原 content-grid 不变 -->
</section>
```

- [ ] **Step 2: script 改用 shared datetime**

删除页面内本地 `function relativeTime`（约原第 433-442 行）。

导入：
```ts
import { relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
```

模板中 `relativeTime(...)` 调用替换为 `relativeTimeUtil(...)`（出现在状态面板、告警卡片、state-banner 等处）。

- [ ] **Step 3: 清理 alerts-page.css**

该文件从第 1 行 `.alerts-app` 开始就是布局规则。删除：
- 第 1-9 行：`.alerts-app {…}`（含 grid 布局、彩色背景）
- 第 11-18 行：`.sidebar {…}`（深色背景）
- 第 20-35 行：共享 flex 工具样式块（`.brand,.nav,.account,.crumbs,.hero,.section-head,…{display:flex;align-items:center}`）
- 第 37-… 行：所有 `.brand`、`.brand-mark`、`.brand-dot`、`.nav`、`.nav-item`、`.sidebar *`、深色背景相关规则、`.topbar`、`.crumbs`、`.main`、`.account`、`.avatar`、`.pill` 等布局规则

**保留**：`.hero`（若非纯布局，保留其业务视觉）、`.panel`、`.form-panel`、`.form-grid`、`.rule-card`、`.rule-row`、`.rule-meta`、`.alert-card`、`.alert-title`、`.chip`、`.chips`、`.state-banner`、`.section-head`、`.column`、`.content-grid`、`.empty-state`、`.filter-row`、`.footer`、`.pager`、`.page`、`.subtle`、`.outline-button`、`.primary-button`、`.secondary-button` 等业务样式。

**判定规则**：若某规则只服务于已删的 sidebar/topbar/brand/nav/account/crumbs，删除；若同时服务于 main 内的业务元素，保留。对每个 selector，检查删除模板后是否还有元素引用它。

新增页面根与状态面板样式（放文件顶部）：

```css
.alerts-page {
  display: grid;
  gap: 16px;
}

.status-panel {
  padding: 16px 20px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(16, 24, 40, 0.04);
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.status-panel .status-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #18233f;
  font-size: 14px;
  font-weight: 900;
}

.status-panel .live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eaf8f0;
  color: #14a160;
  font-size: 11px;
  font-weight: 800;
}

.status-panel .live::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #27c678;
}

.status-refresh {
  margin-top: 12px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #1769f5;
  border-radius: 7px;
  background: linear-gradient(180deg, #267aff, #075be7);
  color: #fff;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.status-refresh:disabled {
  opacity: 0.7;
  cursor: wait;
}
```

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过。

- [ ] **Step 5: Commit（维护者执行）**

```bash
git add frontend/src/pages/AlertsPage.vue frontend/src/styles/alerts-page.css
git commit -m "refactor: move alerts page into AppLayout and drop dark sidebar styles"
```

---

## Task 10: DailyReportsPage 改造与中文化

**Files:**
- Modify: `frontend/src/pages/DailyReportsPage.vue`
- Modify: `frontend/src/styles/daily-reports-page.css`

**Scope:** 删 sidebar/topbar，英文文案转中文，本地 relativeTime/formatDateTime 改用 shared datetime，status-card 移入 main，清理 css 布局规则与深色风格。

- [ ] **Step 1: 改造模板 + 全面中文化**

删除 `<aside class="sidebar">…</aside>` 与 `<header class="topbar">…</header>`。最外层 `<div class="reports-app">` 改为 `<section class="reports-page">`，去掉 `<main class="main">` 包裹。

status-card 移入内容顶部，并中文化：

```vue
<section class="reports-page">
  <section class="status-panel">
    <div class="status-title">报告状态 <span class="live">手动</span></div>
    <div>所选日期：<span>{{ selectedDate }}</span></div>
    <div>最近生成：<span>{{ lastRun ? relativeTimeUtil(lastRun.generatedAt) : "尚未生成" }}</span></div>
    <div>历史数量：<span>{{ history.totalElements }}</span></div>
    <button class="status-refresh" type="button" :disabled="generating" @click="runReport">
      {{ generating ? "生成中..." : "生成报告" }}
    </button>
  </section>

  <section class="hero">
    <div>
      <h1 class="page-title">基于证据的日报</h1>
      <p class="subtitle">从已持久化的 cluster、score、evidence 和最新结构化分析生成每日快照。</p>
    </div>
    <div class="hero-actions">
      <label class="date-field">
        <span>日期</span>
        <input v-model="selectedDate" type="date" @change="loadReportOnly" />
      </label>
      <button class="primary-button" type="button" :disabled="generating" @click="runReport">
        {{ generating ? "生成中..." : "生成" }}
      </button>
    </div>
  </section>

  <section v-if="pageError" class="state-banner error">{{ pageError }}</section>
  <section v-else-if="lastRun" class="state-banner success">
    为 {{ lastRun.reportDate }} 生成了 {{ lastRun.clusterCount }} 个 cluster，{{ relativeTimeUtil(lastRun.generatedAt) }}。
  </section>

  <section class="content-grid">
    <div class="column">
      <section class="panel">
        <div class="section-head">
          <h2>报告内容</h2>
          <span class="subtle">{{ report ? report.status : "未生成" }}</span>
        </div>

        <div v-if="reportLoading" class="empty-state">正在加载报告快照...</div>
        <div v-else-if="report" class="report-card">
          <div class="report-meta">
            <span class="pill">{{ report.reportDate }}</span>
            <span>{{ report.clusterCount }} 个 cluster</span>
            <span>{{ formatDateTimeUtil(report.generatedAt) }}</span>
          </div>
          <h3 class="report-title">{{ report.title }}</h3>
          <p class="report-summary">{{ report.summary }}</p>

          <div v-if="report.clusters.length === 0" class="empty-state compact">
            当天没有捕获到活跃 cluster。
          </div>

          <div v-else class="cluster-list">
            <article v-for="cluster in report.clusters" :key="cluster.hotClusterId" class="cluster-card">
              <div class="cluster-row">
                <div>
                  <RouterLink class="cluster-link" :to="{ name: 'cluster-detail', params: { clusterId: cluster.hotClusterId } }">
                    {{ cluster.title }}
                  </RouterLink>
                  <div class="cluster-meta">
                    <span>{{ cluster.score ? Math.round(cluster.score.total) : 0 }} 热度分</span>
                    <span>{{ cluster.itemCount }} 条证据</span>
                    <span>{{ formatDateTimeUtil(cluster.lastSeenAt) }}</span>
                  </div>
                </div>
                <div class="chips">
                  <span v-for="source in cluster.sourceTypes" :key="source" class="chip">{{ sourceLabel(source) }}</span>
                </div>
              </div>

              <p class="cluster-summary">{{ cluster.summary || "该 cluster 暂无摘要。" }}</p>

              <div class="score-box" v-if="cluster.score">
                <div class="score-head">
                  <strong>评分明细</strong>
                  <span>{{ cluster.score.version }}</span>
                </div>
                <div class="score-components">
                  <div v-for="component in scoreComponents(cluster.score.components)" :key="component.label" class="score-component">
                    <span>{{ component.label }}</span>
                    <span>{{ component.value }}</span>
                  </div>
                </div>
              </div>

              <div class="analysis-box" v-if="cluster.latestAnalysis?.result">
                <div class="analysis-head">
                  <strong>最新分析</strong>
                  <span>{{ formatDateTimeUtil(cluster.latestAnalysis.createdAt) }}</span>
                </div>
                <div class="analysis-title">{{ cluster.latestAnalysis.result.headline }}</div>
                <p class="analysis-brief">{{ cluster.latestAnalysis.result.brief }}</p>
              </div>
            </article>
          </div>
        </div>
        <div v-else class="empty-state">
          {{ selectedDate }} 还没有报告，可基于当前持久化的 cluster 生成。
        </div>
      </section>
    </div>

    <div class="column">
      <section class="panel">
        <div class="section-head">
          <h2>历史报告</h2>
          <span class="subtle">共 {{ history.totalElements }} 条</span>
        </div>

        <div v-if="historyLoading" class="empty-state">正在加载历史...</div>
        <div v-else-if="history.items.length === 0" class="empty-state">还没有生成过日报。</div>
        <div v-else class="history-list">
          <button
            v-for="item in history.items"
            :key="item.id"
            class="history-card"
            :class="{ active: item.reportDate === selectedDate }"
            type="button"
            @click="selectHistory(item.reportDate)"
          >
            <div class="history-row">
              <strong>{{ item.reportDate }}</strong>
              <span>{{ item.clusterCount }} 个 cluster</span>
            </div>
            <div class="history-summary">{{ item.summary }}</div>
            <div class="history-time">{{ formatDateTimeUtil(item.generatedAt) }}</div>
          </button>
        </div>

        <footer class="footer" v-if="history.totalPages > 1">
          <div>共 {{ history.totalElements }} 条</div>
          <div class="pager">
            <button class="page" :disabled="historyPage <= 1" type="button" @click="changeHistoryPage(historyPage - 1)">上一页</button>
            <button class="page" :disabled="historyPage >= history.totalPages" type="button" @click="changeHistoryPage(historyPage + 1)">下一页</button>
          </div>
        </footer>
      </section>
    </div>
  </section>
</section>
```

- [ ] **Step 2: script 改用 shared datetime**

删除本地 `function formatDateTime` 与 `function relativeTime`（约原第 273-288 行）。

导入：
```ts
import { formatDateTime as formatDateTimeUtil, relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
```

`sourceLabel`、`scoreComponents`、`todayDate` 等其他本地函数保留不变。

- [ ] **Step 3: 清理 daily-reports-page.css**

删除该文件中：
- 第 1-9 行：`.reports-app {…}`（grid + 彩色光晕背景）
- 第 11-18 行：`.sidebar {…}`（深色背景）
- 第 20-35 行：共享 flex 工具块
- 第 37-… 行：`.brand`、`.brand-mark`（彩色渐变方块）、`.brand-dot`、`.nav`、`.nav-item`、`.topbar`、`.crumbs`、`.main`、`.account`、`.avatar`、`.account-copy`、`.account-name`、`.pill`、`.status-card`、`.status-title`、`.live`、`.status-link-button` 等布局规则

**保留**：`.hero`、`.hero-actions`、`.date-field`、`.primary-button`、`.state-banner`、`.content-grid`、`.column`、`.panel`、`.section-head`、`.subtle`、`.report-card`、`.report-meta`、`.report-title`、`.report-summary`、`.cluster-list`、`.cluster-card`、`.cluster-row`、`.cluster-link`、`.cluster-meta`、`.cluster-summary`、`.chips`、`.chip`、`.score-box`、`.score-head`、`.score-components`、`.score-component`、`.analysis-box`、`.analysis-head`、`.analysis-title`、`.analysis-brief`、`.history-list`、`.history-card`、`.history-row`、`.history-summary`、`.history-time`、`.footer`、`.pager`、`.page`、`.empty-state` 等业务样式。

**深色背景去除**：所有 `background: #101828`、`color: #f8fafc`、彩色光晕 `radial-gradient(...)`、彩色渐变 `linear-gradient(135deg, #ff7a59, #ffd166)` 等 deep-color 主题规则全部删除。

新增页面根与状态面板样式（与 Task 9 相同的 `.reports-page`、`.status-panel`、`.status-title`、`.live`、`.status-refresh` 规则）。

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过，无英文残留。

- [ ] **Step 5: Commit（维护者执行）**

```bash
git add frontend/src/pages/DailyReportsPage.vue frontend/src/styles/daily-reports-page.css
git commit -m "refactor: move daily reports page into AppLayout and localize to Chinese"
```

---

## Task 11: EvaluationPage 改造与中文化

**Files:**
- Modify: `frontend/src/pages/EvaluationPage.vue`
- Modify: `frontend/src/styles/evaluation-page.css`

**Scope:** 删 sidebar/topbar，英文文案转中文，本地 relativeTime/formatDateTime 改用 shared datetime，status-card 移入 main，清理 css 布局规则与深色风格。

- [ ] **Step 1: 改造模板 + 全面中文化**

删除 `<aside class="sidebar">…</aside>` 与 `<header class="topbar">…</header>`。最外层 `<div class="evaluation-app">` 改为 `<section class="evaluation-page">`，去掉 `<main class="main">` 包裹。

status-card 移入内容顶部并中文化：

```vue
<section class="evaluation-page">
  <section class="status-panel">
    <div class="status-title">评测状态 <span class="live">手动</span></div>
    <div>数据集：<span>{{ selectedDataset ? selectedDataset.name : "未选择" }}</span></div>
    <div>最近运行：<span>{{ latestRun ? relativeTimeUtil(latestRun.finishedAt ?? latestRun.startedAt) : "尚未运行" }}</span></div>
    <div>用例数：<span>{{ selectedDataset ? selectedDataset.caseCount : 0 }}</span></div>
    <button class="status-refresh" type="button" :disabled="!canRun || running" @click="runEvaluation">
      {{ running ? "运行中..." : "运行评测" }}
    </button>
  </section>

  <section class="hero">
    <div>
      <h1 class="page-title">手动评测闭环</h1>
      <p class="subtitle">
        基于已标注用例，对持久化的采集、聚类、评分、分析和告警数据进行评测，查看通过率、失败与错误。
      </p>
    </div>

    <div class="hero-actions">
      <label class="dataset-field">
        <span>数据集</span>
        <select :value="selectedDatasetId" @change="onDatasetChange">
          <option :value="0" disabled>选择数据集</option>
          <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">
            {{ dataset.name }}（{{ dataset.caseCount }} 条用例）
          </option>
        </select>
      </label>
      <button class="primary-button" type="button" :disabled="!canRun || running" @click="runEvaluation">
        {{ running ? "运行中..." : "立即运行" }}
      </button>
    </div>
  </section>

  <section v-if="pageError" class="state-banner error">{{ pageError }}</section>
  <section v-else-if="latestRun" class="state-banner success">
    最近一次运行 {{ latestRun.status }}，{{ latestRun.passedCases }}/{{ latestRun.totalCases }} 用例通过，
    {{ relativeTimeUtil(latestRun.finishedAt ?? latestRun.startedAt) }}。
  </section>

  <section v-if="metrics" class="metric-grid">
    <div class="metric-card">
      <div class="metric-label">通过率</div>
      <div class="metric-value">{{ formatPercent(metrics.passRate) }}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">用例总数</div>
      <div class="metric-value">{{ metrics.totalCases }}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">失败</div>
      <div class="metric-value fail">{{ metrics.failedCases }}</div>
    </div>
    <div class="metric-card">
      <div class="metric-label">错误</div>
      <div class="metric-value error">{{ metrics.errorCases }}</div>
    </div>
  </section>

  <div class="content-grid">
    <section class="panel">
      <div class="section-head">
        <h2>按用例类型</h2>
        <span class="subtle">{{ byCaseTypeEntries.length }} 种</span>
      </div>

      <div v-if="!selectedDataset" class="empty-state">选择数据集以查看评测指标。</div>
      <div v-else-if="!metrics" class="empty-state">还没有运行记录，触发一次评测查看指标。</div>
      <table v-else class="case-type-table">
        <thead>
          <tr>
            <th>用例类型</th>
            <th>总数</th>
            <th>通过</th>
            <th>失败</th>
            <th>错误</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in byCaseTypeEntries" :key="entry.type">
            <td>{{ entry.type }}</td>
            <td>{{ entry.total }}</td>
            <td>{{ entry.passed }}</td>
            <td>{{ entry.failed }}</td>
            <td>{{ entry.error }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <div class="section-head">
        <h2>失败与错误用例</h2>
        <span class="subtle">{{ failedOrErrorResults.length }} 条</span>
      </div>

      <div v-if="!selectedDataset" class="empty-state">选择数据集以查看评测结果。</div>
      <div v-else-if="!runDetail" class="empty-state">暂无运行详情。</div>
      <div v-else-if="failedOrErrorResults.length === 0" class="empty-state">
        最近一次运行全部用例通过。
      </div>
      <div v-else class="case-list">
        <article v-for="result in failedOrErrorResults" :key="result.id" class="case-card">
          <div class="case-head">
            <div>
              <div class="case-code">{{ result.caseCode }}</div>
              <div class="case-meta">{{ result.caseType }} · 评测于 {{ formatDateTimeUtil(result.evaluatedAt) }}</div>
            </div>
            <span class="status-badge" :class="result.status">{{ result.status }}</span>
          </div>
          <p v-if="result.failureReason" class="case-reason" :class="{ error: result.status === 'ERROR' }">
            {{ result.failureReason }}
          </p>
          <div class="payload-box">
            <div class="payload-block">
              <div class="payload-title">实际结果</div>
              <pre class="payload-json">{{ formatPayload(result.actualPayload) }}</pre>
            </div>
            <div class="payload-block">
              <div class="payload-title">期望结果</div>
              <pre class="payload-json">{{ formatPayload(expectedPayloadFor(result.caseId)) }}</pre>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</section>
```

- [ ] **Step 2: script 改用 shared datetime**

删除本地 `function formatDateTime` 与 `function relativeTime`（约原第 320-339 行）。

导入：
```ts
import { formatDateTime as formatDateTimeUtil, relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
```

`formatPercent`、`formatPayload`、`expectedPayloadFor`、`onSelectDataset` 等其他函数保留不变。

- [ ] **Step 3: 清理 evaluation-page.css**

删除该文件中：
- 第 1-9 行：`.evaluation-app {…}`（grid + 彩色光晕背景）
- 第 11-18 行：`.evaluation-app .sidebar {…}`（深色背景）
- 第 20-33 行：共享 flex 工具块（`.evaluation-app .brand,.nav,.account,.crumbs,…`）
- 第 35-… 行：所有 `.evaluation-app .brand`、`.brand-mark`（彩色渐变方块）、`.brand-dot`、`.nav`、`.nav-item`、`.topbar`、`.crumbs`、`.main`、`.account`、`.avatar`、`.account-copy`、`.account-name`、`.pill`、`.status-card`、`.status-title`、`.live`、`.status-link-button` 等布局规则

**保留**：`.hero`、`.hero-actions`、`.dataset-field`、`.primary-button`、`.state-banner`、`.metric-grid`、`.metric-card`、`.metric-label`、`.metric-value`、`.metric-value.fail`、`.metric-value.error`、`.content-grid`、`.panel`、`.section-head`、`.subtle`、`.case-type-table`、`.case-list`、`.case-card`、`.case-head`、`.case-code`、`.case-meta`、`.case-reason`、`.status-badge`、`.payload-box`、`.payload-block`、`.payload-title`、`.payload-json`、`.empty-state` 等业务样式。

**深色背景去除**：所有 `#101828`、`#f8fafc`、青蓝光晕 `radial-gradient(... rgba(125, 211, 252, ...))`、`linear-gradient(135deg, #38bdf8, #818cf8)` 等深色主题规则全部删除。

新增页面根与状态面板样式（与 Task 9/10 相同的 `.evaluation-page`、`.status-panel`、`.status-title`、`.live`、`.status-refresh` 规则）。

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建通过，无英文残留。

- [ ] **Step 5: Commit（维护者执行）**

```bash
git add frontend/src/pages/EvaluationPage.vue frontend/src/styles/evaluation-page.css
git commit -m "refactor: move evaluation page into AppLayout and localize to Chinese"
```

---

## Task 12: 最终验证与文档同步

**Files:**
- Modify: `docs/decision-log.md`

**Scope:** 全量验证 + decision-log 追加决策条目。

- [ ] **Step 1: 全量构建与测试**

Run: `cd frontend && npm run build && npm test`
Expected: 构建通过，所有测试通过。

- [ ] **Step 2: 人工核对清单（维护者本地 `npm run dev` 后逐项确认）**

- [ ] 6 个页面均有 sidebar（白色渐变）+ topbar（毛玻璃白底），无重复 sidebar
- [ ] 侧边栏菜单统一显示 4 项：热点榜单 / 订阅告警 / 日报 / 评测
- [ ] 面包屑第一级"热点榜单"可点击回 `/clusters`，第二级为当前页
- [ ] 切换页面时 sidebar/topbar 不重新渲染、不闪烁
- [ ] 列表页：status-card 在内容顶部，表格、分页正常
- [ ] 详情页：analysis 区块全中文（结构化分析 / 生成分析 / 置信度 / 关键信号 / 证据引用）
- [ ] 状态页：显示在 AppShell 内
- [ ] 告警页：status-card 在顶部，订阅/告警列表正常，无深色 sidebar
- [ ] 日报页：全中文（报告状态 / 生成报告 / 报告内容 / 评分明细 / 最新分析 / 历史报告），无彩色光晕背景
- [ ] 评测页：全中文（评测状态 / 运行评测 / 通过率 / 按用例类型 / 失败与错误用例 / 实际结果 / 期望结果），无青蓝光晕背景
- [ ] ant-design-vue 组件内置文案为中文（如 a-empty、a-pagination）

- [ ] **Step 3: 更新 decision-log**

在 `docs/decision-log.md` 的 "Accepted Decisions" 列表末尾追加（编号 18）：

```markdown
18. 前端统一使用 AppLayout 应用外壳组件承载 sidebar/topbar/breadcrumb，菜单与面包屑由路由 meta 驱动；界面语言统一为中文并配置 ant-design-vue zh_CN locale。Phase 6/7/8 各页面此前各自实现的独立布局与英文文案已收敛到该外壳。
```

- [ ] **Step 4: Commit（维护者执行）**

```bash
git add docs/decision-log.md docs/superpowers/specs/2026-07-10-frontend-unified-layout-design.md docs/superpowers/plans/2026-07-10-frontend-unified-layout.md
git commit -m "docs: record frontend unified AppLayout decision and design artifacts"
```

---

## Self-Review Notes

- 所有 spec 决策（AppShell / 中文 / 菜单 4 项 / 浅色风 / status-card 移出 / datetime 工具）均有对应任务覆盖。
- Task 1/3 有完整 TDD 循环；Task 2/4/5 有类型检查或构建验证；Task 6-11 为模板/样式/文案改动，以构建通过 + 人工核对清单验证。
- 命名一致性：`relativeTimeUtil` / `formatDateTimeUtil` 在所有页面任务中统一；`MenuItem` 接口在 Task 3 定义、Task 4 消费；`routes` 在 Task 2 导出、Task 4 消费。
- git commit 全部标注"维护者执行"，遵循 AGENTS.md。
