<template>
  <section class="reports-page">
    <section class="status-panel" aria-label="报告状态">
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
import { formatDateTime as formatDateTimeUtil, relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
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

function sourceLabel(source: SourceType): string {
  if (source === "ARXIV") return "arXiv";
  if (source === "GITHUB") return "GitHub";
  if (source === "HUGGING_FACE") return "Hugging Face";
  if (source === "SOGOU_SEARCH") return "搜狗搜索";
  return "Hacker News";
}

function scoreComponents(components: Record<string, unknown>): Array<{ label: string; value: string }> {
  return Object.entries(components).map(([label, value]) => ({
    label,
    value: typeof value === "number" ? value.toFixed(2) : String(value)
  }));
}
</script>
