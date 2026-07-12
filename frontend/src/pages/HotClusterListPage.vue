<template>
  <section class="list-page">
    <section class="status-panel" aria-label="数据更新">
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
      <div class="subtitle">展示真实 hot_cluster 数据，并在前端做轻量筛选与排序。</div>

      <section class="filter-panel">
        <form class="filters" @submit.prevent="applyFilters">
          <label>
            <div class="field-label">来源</div>
            <div class="control">
              <select v-model="draft.sourceType">
                <option value="">全部</option>
                <option value="HACKER_NEWS">Hacker News</option>
                <option value="ARXIV">arXiv</option>
                <option value="GITHUB">GitHub</option>
                <option value="HUGGING_FACE">Hugging Face</option>
                <option value="SOGOU_SEARCH">搜狗搜索</option>
              </select>
            </div>
          </label>

          <label>
            <div class="field-label">排序</div>
            <div class="control">
              <select v-model="draft.sort">
                <option value="SCORE_DESC">按热度分</option>
                <option value="LATEST">按最新更新时间</option>
              </select>
            </div>
          </label>

          <label>
            <div class="field-label">最小分数</div>
            <div class="control">
              <input v-model.number="draft.minScore" type="number" min="0" step="100" />
            </div>
          </label>

          <label>
            <div class="field-label">开始时间</div>
            <div class="control">
              <input v-model="draft.from" type="datetime-local" />
            </div>
          </label>

          <label>
            <div class="field-label">结束时间</div>
            <div class="control">
              <input v-model="draft.to" type="datetime-local" />
            </div>
          </label>

          <label>
            <div class="field-label">关键词</div>
            <div class="control">
              <input v-model.trim="draft.q" type="search" placeholder="如 agent / model / github" />
            </div>
          </label>

          <button class="button" type="button" :disabled="loading" @click="resetFilters">重置</button>
          <button class="button primary" type="submit" :disabled="loading">查询</button>
        </form>
      </section>

      <section class="metrics" aria-label="统计概览">
        <article class="metric-card">
          <div>
            <div class="metric-label">热点总数</div>
            <div class="metric-value">{{ filteredRows.length }}</div>
            <div class="metric-note">当前列表结果</div>
          </div>
        </article>
        <article class="metric-card">
          <div>
            <div class="metric-label">平均热度分</div>
            <div class="metric-value">{{ averageScore }}</div>
            <div class="metric-note">基于当前筛选</div>
          </div>
        </article>
        <article class="metric-card">
          <div>
            <div class="metric-label">最高热度分</div>
            <div class="metric-value">{{ topScore }}</div>
            <div class="metric-note">当前列表峰值</div>
          </div>
        </article>
        <article class="metric-card">
          <div>
            <div class="metric-label">24h 活跃事件</div>
            <div class="metric-value">{{ freshCount }}</div>
            <div class="metric-note">最近 24 小时更新</div>
          </div>
        </article>
        <article class="metric-card">
          <div>
            <div class="metric-label">来源覆盖</div>
            <div class="metric-value">{{ sourceStatusRows.length }}</div>
            <div class="metric-note">{{ sourceStatusRows.map((item) => item.name).join(" / ") }}</div>
          </div>
        </article>
      </section>

      <section class="table-panel">
        <div class="table-toolbar">
          <div class="source-statuses">
            <span v-for="source in sourceStatusRows" :key="source.name" class="source-status">
              {{ source.name }}
            </span>
          </div>
          <div class="live-note">
            <RouterLink class="state-link" :to="{ name: 'cluster-states' }">状态页</RouterLink>
            <span>接口：GET /api/v1/hot-clusters</span>
          </div>
        </div>

        <div v-if="loading" class="ranking-state">
          <div>
            <div class="spinner" style="margin:0 auto 12px;" />
            <div class="ranking-state__title">正在加载热点列表</div>
            <p class="ranking-state__copy">前端正在请求真实后端数据。</p>
          </div>
        </div>

        <div v-else-if="errorMessage" class="ranking-state">
          <div>
            <h3 class="ranking-state__title">列表加载失败</h3>
            <p class="ranking-state__copy">{{ errorMessage }}</p>
            <div class="ranking-state__actions">
              <button class="ranking-state__button primary" type="button" @click="reload">重试</button>
              <button class="ranking-state__button" type="button" @click="resetFilters">重置筛选</button>
            </div>
          </div>
        </div>

        <div v-else-if="pagedRows.length === 0" class="ranking-state">
          <div>
            <h3 class="ranking-state__title">当前筛选下没有热点</h3>
            <p class="ranking-state__copy">这说明请求成功了，但前端筛选把结果过滤掉了。</p>
            <div class="ranking-state__actions">
              <button class="ranking-state__button" type="button" @click="resetFilters">恢复默认筛选</button>
            </div>
          </div>
        </div>

        <template v-else>
          <table>
            <colgroup>
              <col class="rank-col" />
              <col class="title-col" />
              <col class="level-col" />
              <col class="score-col" />
              <col class="source-col" />
              <col class="tag-col" />
              <col class="count-col" />
              <col class="time-col" />
              <col class="more-col" />
            </colgroup>
            <thead>
              <tr>
                <th>排名</th>
                <th>标题</th>
                <th>影响</th>
                <th>热度分</th>
                <th>来源</th>
                <th>标签</th>
                <th>条目数</th>
                <th>最近更新</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in pagedRows" :key="row.id" class="data-row">
                <td>
                  <div class="rank">
                    <span class="medal" :class="medalClass((currentPage - 1) * filters.size + index + 1)">
                      {{ (currentPage - 1) * filters.size + index + 1 }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="hot-title">
                    <RouterLink class="hot-title-link" :to="{ name: 'cluster-detail', params: { clusterId: row.id }, query: buildHotClusterQuery(filters) }">
                      {{ row.title }}
                    </RouterLink>
                    <span v-if="isFresh(row.lastSeenAt)" class="new-badge">新</span>
                  </div>
                  <div class="summary">{{ row.summary || "暂无结构化摘要。" }}</div>
                </td>
                <td><span class="level" :class="impactClass(row.score?.total)">{{ impactText(row.score?.total) }}</span></td>
                <td><div class="score">{{ displayScore(row.score?.total) }}</div></td>
                <td>
                  <div class="sources">
                    <div class="source-chip">
                      <span class="source-icon" :class="sourceClass(primarySourceName(row))">{{ sourceLetter(primarySourceName(row)) }}</span>
                      {{ primarySourceName(row) }}
                    </div>
                  </div>
                </td>
                <td>
                  <div class="tags">
                    <span v-for="tag in buildTags(row)" :key="tag" class="tag">{{ tag }}</span>
                  </div>
                </td>
                <td><span class="count">{{ row.itemCount }}</span></td>
                <td><span class="time">{{ relativeTimeUtil(row.lastSeenAt) }}</span></td>
                <td>
                  <RouterLink class="detail-jump" :to="{ name: 'cluster-detail', params: { clusterId: row.id }, query: buildHotClusterQuery(filters) }">→</RouterLink>
                </td>
              </tr>
            </tbody>
          </table>

          <footer class="footer">
            <div>共 {{ filteredRows.length }} 条</div>
            <div class="pager">
              <button class="page" :disabled="currentPage <= 1" type="button" @click="changePage(currentPage - 1)">‹</button>
              <button
                v-for="page in pages"
                :key="page"
                class="page"
                :class="{ active: currentPage === page }"
                type="button"
                @click="changePage(page)"
              >
                {{ page }}
              </button>
              <button class="page" :disabled="currentPage >= totalPages" type="button" @click="changePage(currentPage + 1)">›</button>
            </div>
            <label class="page-size">
              <select v-model.number="draft.size" @change="applyFilters">
                <option :value="10">每页 10</option>
                <option :value="12">每页 12</option>
                <option :value="20">每页 20</option>
              </select>
            </label>
          </footer>
        </template>
      </section>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import type { HotClusterSummary } from "../shared/api/contracts";
import { getErrorMessage } from "../shared/api/errors";
import { fetchHotClusters } from "../shared/api/hotClusters";
import { buildHotClusterQuery, parseHotClusterFilters, resetHotClusterFilters, toHotClusterListQuery } from "../shared/utils/query";
import { relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
import "../styles/hot-cluster-list.css";

const route = useRoute();
const router = useRouter();

const filters = reactive(parseHotClusterFilters(route.query));
const draft = reactive({ ...filters });
const loading = ref(false);
const errorMessage = ref("");
const items = ref<HotClusterSummary[]>([]);
const generatedAt = ref("");

const filteredRows = computed(() => {
  const query = filters.q.trim().toLowerCase();
  return items.value.filter((item) => {
    const score = Number(item.score?.total ?? 0);
    const sourceMatch = !filters.sourceType || item.sourceTypes.includes(filters.sourceType);
    const scoreMatch = score >= filters.minScore;
    const queryMatch = !query || `${item.title} ${item.summary ?? ""}`.toLowerCase().includes(query);
    return sourceMatch && scoreMatch && queryMatch;
  });
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / filters.size)));
const currentPage = computed(() => Math.min(filters.page, totalPages.value));
const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * filters.size;
  return filteredRows.value.slice(start, start + filters.size);
});
const pages = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(0, 7));
const averageScore = computed(() => {
  if (!filteredRows.value.length) return 0;
  return Math.round(filteredRows.value.reduce((sum, item) => sum + Number(item.score?.total ?? 0), 0) / filteredRows.value.length);
});
const topScore = computed(() => Math.max(0, ...filteredRows.value.map((item) => Number(item.score?.total ?? 0))));
const freshCount = computed(() => filteredRows.value.filter((item) => isFresh(item.lastSeenAt)).length);
const generatedAtLabel = computed(() => generatedAt.value ? relativeTimeUtil(generatedAt.value) : "等待加载");
const sourceStatusRows = computed(() => {
  const values = new Set(filteredRows.value.flatMap((item) => item.sourceTypes.map(sourceTypeLabel)));
  return Array.from(values.size ? values : new Set(["Hacker News"])).map((name) => ({ name }));
});

watch(
  () => route.query,
  async () => {
    Object.assign(filters, parseHotClusterFilters(route.query));
    Object.assign(draft, filters);
    await reload();
  },
  { immediate: true }
);

async function reload(): Promise<void> {
  loading.value = true;
  errorMessage.value = "";
  try {
    const response = await fetchHotClusters(toHotClusterListQuery(filters));
    items.value = response.items;
    generatedAt.value = new Date().toISOString();
  } catch (error) {
    items.value = [];
    errorMessage.value = getErrorMessage(error);
  } finally {
    loading.value = false;
  }
}

function applyFilters(): void {
  router.push({ name: "clusters", query: buildHotClusterQuery({ ...draft, page: 1 }) });
}

function resetFilters(): void {
  const defaults = resetHotClusterFilters();
  Object.assign(draft, defaults);
  router.push({ name: "clusters", query: buildHotClusterQuery(defaults) });
}

function changePage(page: number): void {
  router.push({ name: "clusters", query: buildHotClusterQuery({ ...filters, page }) });
}

function primarySourceName(item: HotClusterSummary): string {
  return sourceTypeLabel(item.sourceTypes[0] ?? "HACKER_NEWS");
}

function sourceTypeLabel(sourceType: "ARXIV" | "HACKER_NEWS" | "GITHUB" | "HUGGING_FACE" | "SOGOU_SEARCH"): string {
  if (sourceType === "ARXIV") return "arXiv";
  if (sourceType === "GITHUB") return "GitHub";
  if (sourceType === "HUGGING_FACE") return "Hugging Face";
  if (sourceType === "SOGOU_SEARCH") return "搜狗搜索";
  return "Hacker News";
}

function buildTags(item: HotClusterSummary): string[] {
  const text = `${item.title} ${item.summary ?? ""}`.toLowerCase();
  const tags: string[] = [];
  if (text.includes("agent")) tags.push("Agent");
  if (text.includes("llm") || text.includes("gpt") || text.includes("claude")) tags.push("LLM");
  if (text.includes("code") || text.includes("coding")) tags.push("AI Coding");
  if (text.includes("paper") || text.includes("research") || text.includes("arxiv")) tags.push("Research");
  if (text.includes("github") || text.includes("open source")) tags.push("Open Source");
  return (tags.length ? tags : ["AI Radar"]).slice(0, 3);
}

function displayScore(score?: number | null): number {
  return Math.round(Number(score ?? 0));
}

function impactClass(score?: number | null): "high" | "mid" | "low" {
  const value = Number(score ?? 0);
  if (value >= 80) return "high";
  if (value >= 50) return "mid";
  return "low";
}

function impactText(score?: number | null): string {
  const level = impactClass(score);
  if (level === "high") return "高";
  if (level === "mid") return "中";
  return "低";
}

function isFresh(value: string): boolean {
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) && Date.now() - timestamp <= 24 * 60 * 60 * 1000;
}

function medalClass(rank: number): string {
  if (rank === 1) return "gold";
  if (rank === 2) return "silver";
  if (rank === 3) return "bronze";
  return "";
}

function sourceClass(name: string): string {
  if (name === "arXiv") return "arxiv";
  if (name === "GitHub") return "github";
  if (name === "Hugging Face") return "github";
  return "hn";
}

function sourceLetter(name: string): string {
  if (name === "arXiv") return "X";
  if (name === "GitHub") return "G";
  if (name === "Hugging Face") return "H";
  return "Y";
}
</script>

