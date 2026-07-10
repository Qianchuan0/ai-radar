<template>
  <div>
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><span class="brand-dot" /></div>
        <span>AI Radar</span>
      </div>

      <nav class="nav" aria-label="主导航">
        <RouterLink class="nav-item active" :to="{ name: 'clusters', query: listQuery }">热点榜单</RouterLink>
        <div class="nav-item">数据源</div>
        <RouterLink class="nav-item" :to="{ name: 'alerts' }">订阅告警</RouterLink>
        <RouterLink class="nav-item" :to="{ name: 'daily-reports' }">日报</RouterLink>
        <div class="nav-item">评测</div>
      </nav>

      <section class="status-card">
        <div class="status-title">数据更新 <span class="live">实时</span></div>
        <div>最后刷新：<span>{{ generatedAt ? relativeTime(generatedAt) : "等待加载" }}</span></div>
        <div style="margin-top:12px;font-weight:900;">证据来源：</div>
        <div class="source-list">
          <div v-for="source in sourceStatus" :key="source.name" class="source-row">
            <span class="source-icon" :class="sourceClass(source.name)">{{ sourceLetter(source.name) }}</span>
            {{ source.name }}
          </div>
        </div>
        <button class="status-link status-link-button" type="button" @click="reload">刷新详情</button>
      </section>

      <section class="account">
        <div class="avatar">A</div>
        <div style="flex:1;min-width:0;">
          <div style="font-size:14px;font-weight:900;color:#1b2540;">AI Radar 团队</div>
          <span class="pill">MVP</span>
        </div>
      </section>
    </aside>

    <header class="topbar">
      <div class="crumbs">
        <RouterLink :to="{ name: 'clusters', query: listQuery }">热点榜单</RouterLink>
        <span>/</span>
        <span class="crumb-current">热点详情</span>
      </div>

      <div class="top-actions">
        <form class="search-shell" @submit.prevent="goBackToListWithSearch">
          <input v-model.trim="searchDraft" type="search" placeholder="返回列表并带着关键词搜索" />
          <kbd class="kbd">⌘ K</kbd>
        </form>
        <div class="avatar" style="width:34px;height:34px;font-size:16px;">A</div>
      </div>
    </header>

    <main class="main">
      <section v-if="loading" class="state panel">
        <div>
          <strong>正在加载热点详情</strong>
          我们正在请求真实的 cluster detail 数据。
        </div>
      </section>

      <section v-else-if="errorMessage" class="state panel">
        <div>
          <strong>详情加载失败</strong>
          {{ errorMessage }}
        </div>
      </section>

      <template v-else-if="view">
        <section class="hero panel">
          <div class="hero-icon">🔥</div>
          <div>
            <div class="hero-title-row">
              <h1 class="hero-title">{{ view.title }}</h1>
              <span class="type-badge">事件</span>
            </div>

            <div class="hero-metadata">
              <div class="hero-meta">
                <span>影响等级</span>
                <strong><span class="level" :class="view.impact">{{ impactText(view.impact) }}</span></strong>
              </div>
              <div class="hero-meta">
                <span>热度分</span>
                <strong>{{ view.score }}</strong>
              </div>
              <div class="hero-meta">
                <span>证据数量</span>
                <strong>{{ view.items.length }}</strong>
              </div>
              <div class="hero-meta">
                <span>首次出现</span>
                <strong>{{ formatDateTime(view.firstSeenAt) }}</strong>
              </div>
              <div class="hero-meta">
                <span>最近更新</span>
                <strong>{{ formatDateTime(view.lastSeenAt) }}</strong>
              </div>
            </div>

            <div class="hero-tags">
              <span>主题标签</span>
              <span v-for="tag in view.tags" :key="tag" class="tag">{{ tag }}</span>
            </div>
          </div>

          <div class="hero-actions">
            <button class="outline-button" type="button" @click="copyLink">{{ copied ? "已复制" : "复制链接" }}</button>
            <button class="outline-button" :class="{ active: favorite }" type="button" @click="favorite = !favorite">
              {{ favorite ? "已收藏" : "收藏" }}
            </button>
          </div>
        </section>

        <nav class="tabs" aria-label="详情标签页">
          <button v-for="tab in tabs" :key="tab.key" class="tab" :class="{ active: activeTab === tab.key }" type="button" @click="activeTab = tab.key">
            {{ tab.label }}
          </button>
        </nav>

        <section v-if="activeTab === 'overview'" class="content-grid">
          <div class="column">
            <section class="card panel">
              <h2 class="card-title">为什么重要</h2>
              <ul class="bullets">
                <li v-for="reason in view.reasons" :key="reason">{{ reason }}</li>
              </ul>
            </section>

            <section class="card panel">
              <h2 class="card-title">热点摘要</h2>
              <p class="paragraph">{{ view.summary }}</p>
            </section>
          </div>

          <div class="column">
            <section class="card panel">
              <h2 class="card-title">关键指标</h2>
              <div class="metric-grid">
                <div class="metric-mini">
                  <div>
                    <div class="metric-label">证据条数</div>
                    <div class="metric-value">{{ view.items.length }}</div>
                  </div>
                </div>
                <div class="metric-mini">
                  <div>
                    <div class="metric-label">来源数</div>
                    <div class="metric-value">{{ sourceStatus.length }}</div>
                  </div>
                </div>
                <div class="metric-mini">
                  <div>
                    <div class="metric-label">状态</div>
                    <div class="metric-value">{{ view.status }}</div>
                  </div>
                </div>
                <div class="metric-mini">
                  <div>
                    <div class="metric-label">最近更新</div>
                    <div class="metric-value">{{ relativeTime(view.lastSeenAt) }}</div>
                  </div>
                </div>
              </div>
            </section>

            <section class="card panel analysis-card">
              <div class="analysis-header">
                <h2 class="card-title">Structured analysis</h2>
                <button class="outline-button" type="button" :disabled="analysisLoading || analysisTriggering" @click="runAnalysis">
                  {{ analysisTriggering ? "Generating..." : "Generate analysis" }}
                </button>
              </div>
              <div v-if="analysisLoading" class="analysis-state">Loading the latest structured analysis.</div>
              <div v-else-if="analysisError" class="analysis-state analysis-error">{{ analysisError }}</div>
              <div v-else-if="analysis?.status === 'FAILED'" class="analysis-state analysis-error">
                {{ analysis.failureMessage || "Structured analysis failed." }}
              </div>
              <div v-else-if="analysis?.result" class="analysis-body">
                <div class="analysis-meta">
                  <span class="pill">Confidence {{ analysis.result.confidence }}</span>
                  <span class="analysis-subtle">{{ formatDateTime(analysis.createdAt) }}</span>
                </div>
                <h3 class="analysis-headline">{{ analysis.result.headline }}</h3>
                <p class="paragraph">{{ analysis.result.brief }}</p>
                <p class="paragraph analysis-why">{{ analysis.result.whyItMatters }}</p>
                <div class="analysis-block">
                  <strong>Key signals</strong>
                  <ul class="bullets">
                    <li v-for="signal in analysis.result.keySignals" :key="signal">{{ signal }}</li>
                  </ul>
                </div>
                <div class="analysis-block">
                  <strong>Evidence refs</strong>
                  <div class="analysis-links">
                    <a
                      v-for="refItem in analysis.result.evidenceRefs"
                      :key="`${refItem.hotItemId}-${refItem.sourceUrl}`"
                      class="analysis-link"
                      :href="safeUrl(refItem.sourceUrl)"
                      target="_blank"
                      rel="noreferrer"
                    >
                      {{ sourceTypeLabel(refItem.sourceType) }} - {{ refItem.title }}
                    </a>
                  </div>
                </div>
              </div>
              <div v-else class="analysis-state">No structured analysis yet. Generate one from the current evidence pack.</div>
            </section>
          </div>
        </section>

        <section v-else-if="activeTab === 'score'" class="content-grid">
          <div class="column">
            <section class="card panel">
              <h2 class="card-title">评分明细</h2>
              <div class="score-list">
                <div v-for="part in scoreBreakdown" :key="part.label" class="score-row">
                  <span>{{ part.label }}</span>
                  <div class="score-track"><div class="score-fill" :style="{ width: `${part.percent}%` }" /></div>
                  <span class="score-value">{{ part.value }}</span>
                </div>
              </div>
              <div class="score-total">
                <span>总分</span>
                <span><strong>{{ view.score }}</strong> / 100</span>
              </div>
            </section>
          </div>
          <div class="column">
            <section class="card panel">
              <h2 class="card-title">评分说明</h2>
              <p class="paragraph">当前仍是规则可解释评分，不依赖 LLM 直接拍板。这里展示的只是详情页解释视图。</p>
            </section>
          </div>
        </section>

        <section v-else-if="activeTab === 'evidence'" class="column" style="margin-top:16px;">
          <section class="card panel">
            <h2 class="card-title">来源证据</h2>
            <div class="evidence-list">
              <article v-for="item in view.items" :key="item.id" class="evidence-item">
                <div>
                  <span class="source-icon" :class="sourceClass(sourceTypeLabel(item.sourceType))">{{ sourceLetter(sourceTypeLabel(item.sourceType)) }}</span>
                  <strong>{{ sourceTypeLabel(item.sourceType) }}</strong>
                </div>
                <div>
                  <a class="evidence-title" :href="safeUrl(item.sourceUrl)" target="_blank" rel="noreferrer">{{ item.title }}</a>
                  <div>{{ item.summary || matchReasonText(item.matchReason) || "暂无摘要" }}</div>
                </div>
                <div style="text-align:right;color:#667085;font-weight:800;">{{ formatDateTime(item.publishedAt || view.lastSeenAt) }}</div>
              </article>
            </div>
          </section>
        </section>

        <section v-else class="column" style="margin-top:16px;">
          <section class="card panel">
            <h2 class="card-title">时间线</h2>
            <div class="timeline">
              <div v-for="entry in timeline" :key="entry.time + entry.text" class="timeline-item">
                <div class="timeline-time">{{ formatDateTime(entry.time) }}</div>
                <div class="timeline-type" :class="entry.tone">{{ entry.type }}</div>
                <div>{{ entry.text }}</div>
                <a class="timeline-link" :href="safeUrl(entry.url)" target="_blank" rel="noreferrer">↗</a>
              </div>
            </div>
          </section>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import type { ClusterAnalysis, HotClusterDetail, HotItemEvidence, SourceType } from "../shared/api/contracts";
import { getErrorMessage } from "../shared/api/errors";
import { fetchHotClusterDetail, fetchLatestHotClusterAnalysis, triggerHotClusterAnalysis } from "../shared/api/hotClusters";
import "../styles/hot-cluster-detail.css";

type TabKey = "overview" | "score" | "evidence" | "timeline";

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: "overview", label: "概览" },
  { key: "score", label: "评分明细" },
  { key: "evidence", label: "来源证据" },
  { key: "timeline", label: "事件追踪" }
];

const route = useRoute();
const router = useRouter();

const activeTab = ref<TabKey>("overview");
const loading = ref(false);
const errorMessage = ref("");
const detail = ref<HotClusterDetail | null>(null);
const analysis = ref<ClusterAnalysis | null>(null);
const analysisLoading = ref(false);
const analysisTriggering = ref(false);
const analysisError = ref("");
const generatedAt = ref("");
const copied = ref(false);
const favorite = ref(false);
const searchDraft = ref(typeof route.query.q === "string" ? route.query.q : "");

const listQuery = computed(() => {
  const query: Record<string, string> = {};
  if (typeof route.query.q === "string" && route.query.q) query.q = route.query.q;
  if (typeof route.query.page === "string" && route.query.page) query.page = route.query.page;
  if (typeof route.query.size === "string" && route.query.size) query.size = route.query.size;
  if (typeof route.query.sort === "string" && route.query.sort) query.sort = route.query.sort;
  return query;
});

const view = computed(() => {
  if (!detail.value) return null;
  const score = Number(detail.value.score?.total ?? 0);
  return {
    ...detail.value,
    score: Math.round(score),
    impact: impactClass(score),
    tags: buildTags(detail.value),
    reasons: buildReasons(detail.value),
    summary: detail.value.summary || "当前热点已形成可追溯的真实证据聚合，但暂未生成更长摘要。"
  };
});

const sourceStatus = computed(() => {
  const names = new Set((detail.value?.items ?? []).map((item) => sourceTypeLabel(item.sourceType)));
  return Array.from(names.size ? names : new Set(["Hacker News"])).map((name) => ({ name }));
});

const scoreBreakdown = computed(() => {
  const total = Number(detail.value?.score?.total ?? 0);
  return [
    { label: "来源覆盖", value: Math.min(20, Math.round(sourceStatus.value.length * 6)), percent: Math.min(100, sourceStatus.value.length * 30) },
    { label: "新鲜度", value: Math.min(20, Math.max(6, 20 - Math.floor(hoursSince(detail.value?.lastSeenAt) / 6))), percent: Math.min(100, 100 - Math.floor(hoursSince(detail.value?.lastSeenAt) * 3)) },
    { label: "证据密度", value: Math.min(20, (detail.value?.items.length ?? 0) * 2), percent: Math.min(100, (detail.value?.items.length ?? 0) * 10) },
    { label: "关键词权重", value: Math.min(20, buildTags(detail.value).length * 5), percent: Math.min(100, buildTags(detail.value).length * 25) },
    { label: "综合热度", value: Math.min(20, Math.round(total / 5)), percent: Math.min(100, Math.round(total)) }
  ];
});

const timeline = computed(() => {
  const items = [...(detail.value?.items ?? [])].sort((a, b) => new Date(a.publishedAt ?? 0).getTime() - new Date(b.publishedAt ?? 0).getTime());
  return (items.length ? items : fallbackTimeline()).slice(0, 5).map((item, index) => ({
    time: "publishedAt" in item ? item.publishedAt || detail.value?.lastSeenAt || new Date().toISOString() : item.time,
    type: ["首次发现", "持续传播", "新增证据", "讨论升温", "最近更新"][index] || "信号更新",
    tone: (["blue", "purple", "green", "orange", "blue"] as const)[index] || "blue",
    text: "title" in item ? `${sourceTypeLabel(item.sourceType)} 捕获到证据：${item.title}` : item.text,
    url: "sourceUrl" in item ? item.sourceUrl : item.url
  }));
});

watch(
  () => route.params.clusterId,
  async () => {
    await reload();
  },
  { immediate: true }
);

async function reload(): Promise<void> {
  const clusterId = Number(route.params.clusterId);
  if (!Number.isFinite(clusterId) || clusterId <= 0) {
    errorMessage.value = "缺少有效的热点 ID。";
    detail.value = null;
    return;
  }

  loading.value = true;
  errorMessage.value = "";
  try {
    detail.value = await fetchHotClusterDetail(clusterId);
    await loadLatestAnalysis(clusterId);
    generatedAt.value = new Date().toISOString();
  } catch (error) {
    detail.value = null;
    analysis.value = null;
    errorMessage.value = getErrorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function loadLatestAnalysis(clusterId: number): Promise<void> {
  analysisLoading.value = true;
  analysisError.value = "";
  try {
    analysis.value = await fetchLatestHotClusterAnalysis(clusterId);
  } catch (error) {
    analysis.value = null;
    analysisError.value = getErrorMessage(error);
  } finally {
    analysisLoading.value = false;
  }
}

async function runAnalysis(): Promise<void> {
  const clusterId = Number(route.params.clusterId);
  if (!Number.isFinite(clusterId) || clusterId <= 0) {
    analysisError.value = "Missing valid cluster id.";
    return;
  }
  analysisTriggering.value = true;
  analysisError.value = "";
  try {
    analysis.value = await triggerHotClusterAnalysis(clusterId);
  } catch (error) {
    analysisError.value = getErrorMessage(error);
  } finally {
    analysisTriggering.value = false;
  }
}

async function copyLink(): Promise<void> {
  try {
    await navigator.clipboard.writeText(window.location.href);
    copied.value = true;
    window.setTimeout(() => {
      copied.value = false;
    }, 1200);
  } catch {
    copied.value = false;
  }
}

function goBackToListWithSearch(): void {
  router.push({ name: "clusters", query: searchDraft.value ? { ...listQuery.value, q: searchDraft.value } : listQuery.value });
}

function buildTags(item: HotClusterDetail | null): string[] {
  const text = `${item?.title ?? ""} ${item?.summary ?? ""}`.toLowerCase();
  const tags: string[] = [];
  if (text.includes("agent")) tags.push("Agent");
  if (text.includes("llm") || text.includes("gpt") || text.includes("claude")) tags.push("LLM");
  if (text.includes("paper") || text.includes("research") || text.includes("arxiv")) tags.push("Research");
  if (text.includes("github") || text.includes("open source")) tags.push("Open Source");
  return (tags.length ? tags : ["AI Radar"]).slice(0, 4);
}

function buildReasons(item: HotClusterDetail): string[] {
  return [
    `这个事件当前聚合了 ${item.itemCount} 条证据，说明不是单点噪音。`,
    `它最近一次更新发生在 ${relativeTime(item.lastSeenAt)}，仍然处在可追踪窗口。`,
    `详情页保留 raw evidence，便于后续回溯、重跑和评测。`
  ];
}

function sourceTypeLabel(sourceType: SourceType): string {
  if (sourceType === "ARXIV") return "arXiv";
  if (sourceType === "GITHUB") return "GitHub";
  return "Hacker News";
}

function sourceClass(name: string): string {
  if (name === "arXiv") return "arxiv";
  if (name === "GitHub") return "github";
  return "hn";
}

function sourceLetter(name: string): string {
  if (name === "arXiv") return "X";
  if (name === "GitHub") return "G";
  return "Y";
}

function impactClass(score: number): "high" | "mid" | "low" {
  if (score >= 80) return "high";
  if (score >= 50) return "mid";
  return "low";
}

function impactText(level: "high" | "mid" | "low"): string {
  if (level === "high") return "高";
  if (level === "mid") return "中";
  return "低";
}

function hoursSince(value?: string): number {
  if (!value) return 24;
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return 24;
  return Math.max(0, (Date.now() - timestamp) / 3600000);
}

function relativeTime(value: string): string {
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return "--";
  const diffMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (diffMinutes < 1) return "刚刚";
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} 小时前`;
  return `${Math.floor(diffHours / 24)} 天前`;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return "--";
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function safeUrl(value: string): string {
  return /^https?:\/\//i.test(value) ? value : "#";
}

function matchReasonText(value: unknown): string {
  if (!value) return "";
  try {
    return typeof value === "string" ? value : JSON.stringify(value);
  } catch {
    return "";
  }
}

function fallbackTimeline() {
  return [
    {
      time: detail.value?.lastSeenAt || new Date().toISOString(),
      text: "系统已完成该 cluster 的聚合展示。",
      url: "#"
    }
  ];
}
</script>

<style scoped>
.status-link-button {
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
}
</style>
