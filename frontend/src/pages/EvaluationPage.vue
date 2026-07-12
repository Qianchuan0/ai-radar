<template>
  <section class="evaluation-page">
    <section class="status-panel" aria-label="评测状态">
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
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink } from "vue-router";
import { getErrorMessage } from "../shared/api/errors";
import {
    fetchEvaluationCases,
    fetchEvaluationDatasets,
    fetchEvaluationRun,
    fetchEvaluationRuns,
    triggerEvaluationRun
} from "../shared/api/evaluation";
import type {
    EvaluationCase,
    EvaluationDataset,
    EvaluationRun,
    EvaluationRunSummary
} from "../shared/api/contracts";
import { formatDateTime as formatDateTimeUtil, relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
import "../styles/evaluation-page.css";

interface CaseTypeMetric {
    total: number;
    passed: number;
    failed: number;
    error: number;
}

const datasets = ref<EvaluationDataset[]>([]);
const selectedDatasetId = ref(0);
const selectedDataset = ref<EvaluationDataset | null>(null);
const cases = ref<EvaluationCase[]>([]);
const latestRun = ref<EvaluationRunSummary | null>(null);
const runDetail = ref<EvaluationRun | null>(null);
const running = ref(false);
const loading = ref(false);
const pageError = ref("");

const canRun = computed(() => selectedDataset.value !== null);
const metrics = computed(() => {
    const payload = runDetail.value?.metricsPayload as Record<string, unknown> | undefined;
    return payload ? (payload as unknown as {
        totalCases: number;
        passedCases: number;
        failedCases: number;
        errorCases: number;
        passRate: number;
    }) : null;
});

const byCaseTypeEntries = computed<Array<{ type: string } & CaseTypeMetric>>(() => {
    const raw = metrics.value ? (runDetail.value?.metricsPayload as Record<string, unknown>)?.byCaseType : null;
    const map = raw as Record<string, CaseTypeMetric> | null;
    if (!map) {
        return [];
    }
    return Object.entries(map).map(([type, value]) => ({ type, ...value }));
});

const failedOrErrorResults = computed(() =>
    (runDetail.value?.caseResults ?? []).filter((result) => result.status !== "PASSED")
);

void initialize();

async function initialize(): Promise<void> {
    await loadDatasets();
}

async function loadDatasets(): Promise<void> {
    loading.value = true;
    pageError.value = "";
    try {
        datasets.value = await fetchEvaluationDatasets();
        if (datasets.value.length > 0 && selectedDatasetId.value === 0) {
            selectedDatasetId.value = datasets.value[0].id;
            await onSelectDataset(datasets.value[0].id);
        }
    } catch (error) {
        pageError.value = getErrorMessage(error);
    } finally {
        loading.value = false;
    }
}

async function onDatasetChange(event: Event): Promise<void> {
    const value = Number((event.target as HTMLSelectElement).value);
    selectedDatasetId.value = value;
    await onSelectDataset(value);
}

async function onSelectDataset(datasetId: number): Promise<void> {
    selectedDataset.value = datasets.value.find((dataset) => dataset.id === datasetId) ?? null;
    cases.value = [];
    latestRun.value = null;
    runDetail.value = null;
    pageError.value = "";
    if (!selectedDataset.value) {
        return;
    }
    try {
        cases.value = await fetchEvaluationCases(datasetId);
        const runsPage = await fetchEvaluationRuns(1, 1, datasetId);
        if (runsPage.items.length > 0) {
            latestRun.value = runsPage.items[0];
            runDetail.value = await fetchEvaluationRun(latestRun.value.id);
        }
    } catch (error) {
        pageError.value = getErrorMessage(error);
    }
}

async function runEvaluation(): Promise<void> {
    if (!selectedDataset.value) {
        return;
    }
    running.value = true;
    pageError.value = "";
    try {
        const generation = await triggerEvaluationRun(selectedDataset.value.id);
        const detail = await fetchEvaluationRun(generation.runId);
        latestRun.value = {
            id: detail.id,
            datasetId: detail.datasetId,
            datasetName: detail.datasetName,
            status: detail.status,
            totalCases: detail.totalCases,
            passedCases: detail.passedCases,
            failedCases: detail.failedCases,
            errorCases: detail.errorCases,
            startedAt: detail.startedAt,
            finishedAt: detail.finishedAt,
            createdAt: detail.createdAt
        };
        runDetail.value = detail;
        await loadDatasets();
    } catch (error) {
        pageError.value = getErrorMessage(error);
    } finally {
        running.value = false;
    }
}

function expectedPayloadFor(caseId: number): Record<string, unknown> | null {
    const found = cases.value.find((item) => item.id === caseId);
    return found ? found.expectedPayload : null;
}

function formatPercent(value: number | undefined): string {
    if (value === undefined || value === null) {
        return "--";
    }
    return `${(value * 100).toFixed(1)}%`;
}

function formatPayload(payload: Record<string, unknown> | null): string {
    if (!payload) {
        return "--";
    }
    try {
        return JSON.stringify(payload, null, 2);
    } catch {
        return String(payload);
    }
}
</script>
