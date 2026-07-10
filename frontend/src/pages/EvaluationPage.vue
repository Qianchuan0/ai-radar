<template>
  <div class="evaluation-app">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><span class="brand-dot" /></div>
        <span>AI Radar</span>
      </div>

      <nav class="nav" aria-label="Primary">
        <RouterLink class="nav-item" :to="{ name: 'clusters' }">Hot Clusters</RouterLink>
        <div class="nav-item">Sources</div>
        <RouterLink class="nav-item" :to="{ name: 'alerts' }">Alerts</RouterLink>
        <RouterLink class="nav-item" :to="{ name: 'daily-reports' }">Daily Reports</RouterLink>
        <RouterLink class="nav-item active" :to="{ name: 'evaluation' }">Evaluation</RouterLink>
      </nav>

      <section class="status-card">
        <div class="status-title">Evaluation Status <span class="live">Manual</span></div>
        <div>Dataset: <span>{{ selectedDataset ? selectedDataset.name : "None" }}</span></div>
        <div>Latest run: <span>{{ latestRun ? relativeTime(latestRun.finishedAt ?? latestRun.startedAt) : "Not run yet" }}</span></div>
        <div>Cases: <span>{{ selectedDataset ? selectedDataset.caseCount : 0 }}</span></div>
        <button class="status-link-button" type="button" :disabled="!canRun || running" @click="runEvaluation">
          {{ running ? "Running..." : "Run evaluation" }}
        </button>
      </section>

      <section class="account">
        <div class="avatar">A</div>
        <div class="account-copy">
          <div class="account-name">AI Radar Team</div>
          <span class="pill">Phase 8</span>
        </div>
      </section>
    </aside>

    <header class="topbar">
      <div class="crumbs">
        <RouterLink :to="{ name: 'clusters' }">Hot Clusters</RouterLink>
        <span>/</span>
        <span class="crumb-current">Evaluation</span>
      </div>
    </header>

    <main class="main">
      <section class="hero">
        <div>
          <h1 class="page-title">Manual evaluation loop</h1>
          <p class="subtitle">
            Run labeled cases against persisted crawl, cluster, score, analysis, and alert data.
            Review pass rate, failures, and errors per case type.
          </p>
        </div>

        <div class="hero-actions">
          <label class="dataset-field">
            <span>Dataset</span>
            <select :value="selectedDatasetId" @change="onDatasetChange">
              <option :value="0" disabled>Select a dataset</option>
              <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">
                {{ dataset.name }} ({{ dataset.caseCount }} cases)
              </option>
            </select>
          </label>
          <button class="primary-button" type="button" :disabled="!canRun || running" @click="runEvaluation">
            {{ running ? "Running..." : "Run now" }}
          </button>
        </div>
      </section>

      <section v-if="pageError" class="state-banner error">{{ pageError }}</section>
      <section v-else-if="latestRun" class="state-banner success">
        Latest run {{ latestRun.status }} with {{ latestRun.passedCases }}/{{ latestRun.totalCases }} cases passing
        {{ relativeTime(latestRun.finishedAt ?? latestRun.startedAt) }}.
      </section>

      <section v-if="metrics" class="metric-grid">
        <div class="metric-card">
          <div class="metric-label">Pass rate</div>
          <div class="metric-value">{{ formatPercent(metrics.passRate) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total cases</div>
          <div class="metric-value">{{ metrics.totalCases }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Failed</div>
          <div class="metric-value fail">{{ metrics.failedCases }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Errors</div>
          <div class="metric-value error">{{ metrics.errorCases }}</div>
        </div>
      </section>

      <div class="content-grid">
        <section class="panel">
          <div class="section-head">
            <h2>By case type</h2>
            <span class="subtle">{{ byCaseTypeEntries.length }} types</span>
          </div>

          <div v-if="!selectedDataset" class="empty-state">Select a dataset to view its evaluation metrics.</div>
          <div v-else-if="!metrics" class="empty-state">No run yet. Trigger an evaluation to see metrics.</div>
          <table v-else class="case-type-table">
            <thead>
              <tr>
                <th>Case type</th>
                <th>Total</th>
                <th>Passed</th>
                <th>Failed</th>
                <th>Errors</th>
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
            <h2>Failed and error cases</h2>
            <span class="subtle">{{ failedOrErrorResults.length }} cases</span>
          </div>

          <div v-if="!selectedDataset" class="empty-state">Select a dataset to inspect evaluation outcomes.</div>
          <div v-else-if="!runDetail" class="empty-state">No run detail available yet.</div>
          <div v-else-if="failedOrErrorResults.length === 0" class="empty-state">
            All cases passed in the latest run.
          </div>
          <div v-else class="case-list">
            <article v-for="result in failedOrErrorResults" :key="result.id" class="case-card">
              <div class="case-head">
                <div>
                  <div class="case-code">{{ result.caseCode }}</div>
                  <div class="case-meta">{{ result.caseType }} · evaluated {{ formatDateTime(result.evaluatedAt) }}</div>
                </div>
                <span class="status-badge" :class="result.status">{{ result.status }}</span>
              </div>
              <p v-if="result.failureReason" class="case-reason" :class="{ error: result.status === 'ERROR' }">
                {{ result.failureReason }}
              </p>
              <div class="payload-box">
                <div class="payload-block">
                  <div class="payload-title">Actual payload</div>
                  <pre class="payload-json">{{ formatPayload(result.actualPayload) }}</pre>
                </div>
                <div class="payload-block">
                  <div class="payload-title">Expected payload</div>
                  <pre class="payload-json">{{ formatPayload(expectedPayloadFor(result.caseId)) }}</pre>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>
    </main>
  </div>
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

function formatDateTime(value: string): string {
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toLocaleString() : "--";
}

function relativeTime(value: string | null): string {
    if (!value) {
        return "--";
    }
    const timestamp = new Date(value).getTime();
    if (!Number.isFinite(timestamp)) {
        return "--";
    }
    const diffMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
    if (diffMinutes < 1) return "just now";
    if (diffMinutes < 60) return `${diffMinutes} min ago`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours} h ago`;
    return `${Math.floor(diffHours / 24)} d ago`;
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
