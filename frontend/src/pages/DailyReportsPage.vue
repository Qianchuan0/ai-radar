<template>
  <div class="reports-app">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><span class="brand-dot" /></div>
        <span>AI Radar</span>
      </div>

      <nav class="nav" aria-label="Primary">
        <RouterLink class="nav-item" :to="{ name: 'clusters' }">Hot Clusters</RouterLink>
        <div class="nav-item">Sources</div>
        <RouterLink class="nav-item" :to="{ name: 'alerts' }">Alerts</RouterLink>
        <RouterLink class="nav-item active" :to="{ name: 'daily-reports' }">Daily Reports</RouterLink>
        <RouterLink class="nav-item" :to="{ name: 'evaluation' }">Evaluation</RouterLink>
      </nav>

      <section class="status-card">
        <div class="status-title">Report Status <span class="live">Manual</span></div>
        <div>Selected date: <span>{{ selectedDate }}</span></div>
        <div>Latest run: <span>{{ lastRun ? relativeTime(lastRun.generatedAt) : "Not generated yet" }}</span></div>
        <div>History items: <span>{{ history.totalElements }}</span></div>
        <button class="status-link-button" type="button" :disabled="generating" @click="runReport">
          {{ generating ? "Generating..." : "Generate report" }}
        </button>
      </section>

      <section class="account">
        <div class="avatar">A</div>
        <div class="account-copy">
          <div class="account-name">AI Radar Team</div>
          <span class="pill">Phase 7</span>
        </div>
      </section>
    </aside>

    <header class="topbar">
      <div class="crumbs">
        <RouterLink :to="{ name: 'clusters' }">Hot Clusters</RouterLink>
        <span>/</span>
        <span class="crumb-current">Daily Reports</span>
      </div>
    </header>

    <main class="main">
      <section class="hero">
        <div>
          <h1 class="page-title">Evidence-backed daily report</h1>
          <p class="subtitle">
            Build a daily snapshot from persisted clusters, scores, evidence, and the latest stored structured analysis.
          </p>
        </div>

        <div class="hero-actions">
          <label class="date-field">
            <span>Date</span>
            <input v-model="selectedDate" type="date" @change="loadReportOnly" />
          </label>
          <button class="primary-button" type="button" :disabled="generating" @click="runReport">
            {{ generating ? "Generating..." : "Generate" }}
          </button>
        </div>
      </section>

      <section v-if="pageError" class="state-banner error">{{ pageError }}</section>
      <section v-else-if="lastRun" class="state-banner success">
        Generated {{ lastRun.clusterCount }} clusters for {{ lastRun.reportDate }} {{ relativeTime(lastRun.generatedAt) }}.
      </section>

      <section class="content-grid">
        <div class="column">
          <section class="panel">
            <div class="section-head">
              <h2>Report output</h2>
              <span class="subtle">{{ report ? report.status : "Missing" }}</span>
            </div>

            <div v-if="reportLoading" class="empty-state">Loading report snapshot...</div>
            <div v-else-if="report" class="report-card">
              <div class="report-meta">
                <span class="pill">{{ report.reportDate }}</span>
                <span>{{ report.clusterCount }} clusters</span>
                <span>{{ formatDateTime(report.generatedAt) }}</span>
              </div>
              <h3 class="report-title">{{ report.title }}</h3>
              <p class="report-summary">{{ report.summary }}</p>

              <div v-if="report.clusters.length === 0" class="empty-state compact">
                No active clusters were captured for this day.
              </div>

              <div v-else class="cluster-list">
                <article v-for="cluster in report.clusters" :key="cluster.hotClusterId" class="cluster-card">
                  <div class="cluster-row">
                    <div>
                      <RouterLink class="cluster-link" :to="{ name: 'cluster-detail', params: { clusterId: cluster.hotClusterId } }">
                        {{ cluster.title }}
                      </RouterLink>
                      <div class="cluster-meta">
                        <span>{{ cluster.score ? Math.round(cluster.score.total) : 0 }} score</span>
                        <span>{{ cluster.itemCount }} evidence items</span>
                        <span>{{ formatDateTime(cluster.lastSeenAt) }}</span>
                      </div>
                    </div>
                    <div class="chips">
                      <span v-for="source in cluster.sourceTypes" :key="source" class="chip">{{ sourceLabel(source) }}</span>
                    </div>
                  </div>

                  <p class="cluster-summary">{{ cluster.summary || "No summary stored for this cluster." }}</p>

                  <div class="score-box" v-if="cluster.score">
                    <div class="score-head">
                      <strong>Score breakdown</strong>
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
                      <strong>Latest analysis</strong>
                      <span>{{ formatDateTime(cluster.latestAnalysis.createdAt) }}</span>
                    </div>
                    <div class="analysis-title">{{ cluster.latestAnalysis.result.headline }}</div>
                    <p class="analysis-brief">{{ cluster.latestAnalysis.result.brief }}</p>
                  </div>
                </article>
              </div>
            </div>
            <div v-else class="empty-state">
              No report exists for {{ selectedDate }} yet. Generate one from the current persisted clusters.
            </div>
          </section>
        </div>

        <div class="column">
          <section class="panel">
            <div class="section-head">
              <h2>History</h2>
              <span class="subtle">{{ history.totalElements }} total</span>
            </div>

            <div v-if="historyLoading" class="empty-state">Loading history...</div>
            <div v-else-if="history.items.length === 0" class="empty-state">No daily reports have been generated yet.</div>
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
                  <span>{{ item.clusterCount }} clusters</span>
                </div>
                <div class="history-summary">{{ item.summary }}</div>
                <div class="history-time">{{ formatDateTime(item.generatedAt) }}</div>
              </button>
            </div>

            <footer class="footer" v-if="history.totalPages > 1">
              <div>Total {{ history.totalElements }}</div>
              <div class="pager">
                <button class="page" :disabled="historyPage <= 1" type="button" @click="changeHistoryPage(historyPage - 1)">Prev</button>
                <button class="page" :disabled="historyPage >= history.totalPages" type="button" @click="changeHistoryPage(historyPage + 1)">Next</button>
              </div>
            </footer>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink } from "vue-router";
import { getErrorMessage } from "../shared/api/errors";
import { fetchDailyReport, fetchDailyReports, generateDailyReport } from "../shared/api/reports";
import type {
  DailyReport,
  DailyReportGeneration,
  DailyReportSummary,
  PageResponse,
  SourceType
} from "../shared/api/contracts";
import "../styles/daily-reports-page.css";

const selectedDate = ref(todayDate());
const report = ref<DailyReport | null>(null);
const lastRun = ref<DailyReportGeneration | null>(null);
const history = ref<PageResponse<DailyReportSummary>>({
  items: [],
  page: 1,
  size: 8,
  totalElements: 0,
  totalPages: 0
});
const historyPage = ref(1);
const reportLoading = ref(false);
const historyLoading = ref(false);
const generating = ref(false);
const pageError = ref("");

const historyPageSize = computed(() => history.value.size || 8);

void initialize();

async function initialize(): Promise<void> {
  await Promise.all([loadReportOnly(), loadHistory()]);
}

async function loadReportOnly(): Promise<void> {
  reportLoading.value = true;
  pageError.value = "";
  try {
    report.value = await fetchDailyReport(selectedDate.value);
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    reportLoading.value = false;
  }
}

async function loadHistory(): Promise<void> {
  historyLoading.value = true;
  pageError.value = "";
  try {
    history.value = await fetchDailyReports(historyPage.value, historyPageSize.value);
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    historyLoading.value = false;
  }
}

async function runReport(): Promise<void> {
  generating.value = true;
  pageError.value = "";
  try {
    lastRun.value = await generateDailyReport(selectedDate.value);
    await Promise.all([loadReportOnly(), loadHistory()]);
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    generating.value = false;
  }
}

function selectHistory(reportDate: string): void {
  selectedDate.value = reportDate;
  void loadReportOnly();
}

function changeHistoryPage(page: number): void {
  historyPage.value = page;
  void loadHistory();
}

function todayDate(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return "--";
  return date.toLocaleString();
}

function relativeTime(value: string): string {
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return "--";
  const diffMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (diffMinutes < 1) return "just now";
  if (diffMinutes < 60) return `${diffMinutes} min ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} h ago`;
  return `${Math.floor(diffHours / 24)} d ago`;
}

function sourceLabel(source: SourceType): string {
  if (source === "ARXIV") return "arXiv";
  if (source === "GITHUB") return "GitHub";
  return "Hacker News";
}

function scoreComponents(components: Record<string, unknown>): Array<{ label: string; value: string }> {
  return Object.entries(components).map(([label, value]) => ({
    label,
    value: typeof value === "number" ? value.toFixed(2) : String(value)
  }));
}
</script>
