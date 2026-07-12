<template>
  <section class="alerts-page">
    <section class="status-panel" aria-label="告警状态">
      <div class="status-title">告警状态 <span class="live">实时</span></div>
      <div>规则数：<span>{{ subscriptions.length }}</span></div>
      <div>当前列表：<span>{{ alerts.items.length }}</span></div>
      <div>最新匹配：<span>{{ lastRun ? relativeTimeUtil(lastRun.completedAt) : "尚未运行" }}</span></div>
      <button class="status-refresh" type="button" :disabled="matching" @click="triggerMatching">
        {{ matching ? "正在匹配..." : "手动运行匹配" }}
      </button>
    </section>

      <section class="hero">
        <div>
          <h1 class="page-title">订阅规则与告警</h1>
          <p class="subtitle">围绕真实 hot_cluster 做规则匹配、抑制重复和人工确认，不引入调度或外部通知。</p>
        </div>
        <button class="primary-button" type="button" :disabled="matching" @click="triggerMatching">
          {{ matching ? "匹配中..." : "运行一次匹配" }}
        </button>
      </section>

      <section v-if="pageError" class="state-banner error">{{ pageError }}</section>
      <section v-else-if="lastRun" class="state-banner success">
        扫描 {{ lastRun.scannedClusterCount }} 个 cluster，命中 {{ lastRun.matchedRuleCount }} 条规则，
        新增 {{ lastRun.createdAlertCount }} 条告警，抑制重复 {{ lastRun.suppressedAlertCount }} 条。
      </section>

      <section class="content-grid">
        <div class="column">
          <section class="panel form-panel">
            <div class="section-head">
              <h2>新建订阅</h2>
              <span class="subtle">手动配置 Phase 6 规则</span>
            </div>

            <form class="form-grid" @submit.prevent="submitSubscription">
              <label>
                <span>规则名称</span>
                <input v-model.trim="draft.name" type="text" maxlength="100" placeholder="例如 OpenAI Agent Watch" />
              </label>

              <label>
                <span>关键词</span>
                <input v-model.trim="draft.keywordsText" type="text" placeholder="用逗号分隔，例如 openai, agent" />
              </label>

              <label>
                <span>最低热度分</span>
                <input v-model.number="draft.minScore" type="number" min="0" step="1" />
              </label>

              <label>
                <span>抑制窗口（小时）</span>
                <input v-model.number="draft.suppressWindowHours" type="number" min="1" max="720" step="1" />
              </label>

              <div>
                <span>来源约束</span>
                <div class="source-options">
                  <label v-for="source in sourceOptions" :key="source" class="check-option">
                    <input
                      :checked="draft.sourceTypes.includes(source)"
                      type="checkbox"
                      @change="toggleSource(source)"
                    />
                    <span>{{ sourceLabel(source) }}</span>
                  </label>
                </div>
              </div>

              <label class="inline-toggle">
                <input v-model="draft.enabled" type="checkbox" />
                <span>创建后立即启用</span>
              </label>

              <div class="form-actions">
                <button class="secondary-button" type="button" @click="resetDraft">重置</button>
                <button class="primary-button" type="submit" :disabled="creatingSubscription">
                  {{ creatingSubscription ? "创建中..." : "创建订阅" }}
                </button>
              </div>
            </form>
          </section>

          <section class="panel">
            <div class="section-head">
              <h2>订阅规则</h2>
              <span class="subtle">{{ subscriptions.length }} 条</span>
            </div>

            <div v-if="subscriptionsLoading" class="empty-state">正在加载订阅规则。</div>
            <div v-else-if="subscriptions.length === 0" class="empty-state">还没有订阅规则，先创建一条。</div>
            <div v-else class="rule-list">
              <article v-for="rule in subscriptions" :key="rule.id" class="rule-card">
                <div class="rule-row">
                  <div>
                    <h3>{{ rule.name }}</h3>
                    <div class="rule-meta">
                      <span class="pill">{{ rule.enabled ? "启用中" : "已停用" }}</span>
                      <span>窗口 {{ rule.suppressWindowHours }}h</span>
                      <span v-if="rule.minScore != null">最小分 {{ Math.round(rule.minScore) }}</span>
                    </div>
                  </div>
                  <button class="secondary-button" type="button" @click="toggleRuleStatus(rule)">
                    {{ rule.enabled ? "停用" : "启用" }}
                  </button>
                </div>
                <div class="chips">
                  <span v-for="keyword in rule.keywords" :key="keyword" class="chip">{{ keyword }}</span>
                  <span v-if="rule.sourceTypes.length === 0" class="chip neutral">全部来源</span>
                  <span v-for="source in rule.sourceTypes" :key="source" class="chip neutral">{{ sourceLabel(source) }}</span>
                </div>
              </article>
            </div>
          </section>
        </div>

        <div class="column">
          <section class="panel">
            <div class="section-head">
              <h2>告警列表</h2>
              <span class="subtle">真实 alert_record</span>
            </div>

            <div class="filter-row">
              <label>
                <span>状态</span>
                <select v-model="filters.status" @change="reloadAlerts">
                  <option value="">全部</option>
                  <option value="NEW">NEW</option>
                  <option value="ACKED">ACKED</option>
                  <option value="DISMISSED">DISMISSED</option>
                </select>
              </label>

              <label>
                <span>订阅</span>
                <select v-model="filters.subscriptionId" @change="reloadAlerts">
                  <option value="">全部</option>
                  <option v-for="rule in subscriptions" :key="rule.id" :value="String(rule.id)">
                    {{ rule.name }}
                  </option>
                </select>
              </label>
            </div>

            <div v-if="alertsLoading" class="empty-state">正在加载告警。</div>
            <div v-else-if="alerts.items.length === 0" class="empty-state">当前筛选下没有告警。</div>
            <div v-else class="alert-list">
              <article v-for="alert in alerts.items" :key="alert.id" class="alert-card">
                <div class="rule-row">
                  <div>
                    <div class="alert-title">
                      <RouterLink :to="{ name: 'cluster-detail', params: { clusterId: alert.hotClusterId } }">
                        {{ alert.hotClusterTitle }}
                      </RouterLink>
                    </div>
                    <div class="rule-meta">
                      <span class="pill">{{ alert.status }}</span>
                      <span>{{ alert.subscriptionName }}</span>
                      <span>{{ relativeTimeUtil(alert.matchedAt) }}</span>
                      <span v-if="alert.hotScore != null">分数 {{ Math.round(alert.hotScore) }}</span>
                    </div>
                  </div>
                  <div class="action-row" v-if="alert.status === 'NEW'">
                    <button class="secondary-button" type="button" @click="changeAlertStatus(alert.id, 'ACKED')">确认</button>
                    <button class="secondary-button" type="button" @click="changeAlertStatus(alert.id, 'DISMISSED')">忽略</button>
                  </div>
                </div>

                <div class="chips">
                  <span v-for="source in alert.sourceTypes" :key="source" class="chip neutral">{{ sourceLabel(source) }}</span>
                </div>

                <p class="reason-text">
                  命中关键词：
                  {{ matchedKeywordsText(alert.matchReason) }}
                </p>
              </article>
            </div>

            <footer class="footer" v-if="alerts.totalPages > 1">
              <div>共 {{ alerts.totalElements }} 条</div>
              <div class="pager">
                <button class="page" :disabled="filters.page <= 1" type="button" @click="changePage(filters.page - 1)">‹</button>
                <button
                  v-for="page in pages"
                  :key="page"
                  class="page"
                  :class="{ active: page === filters.page }"
                  type="button"
                  @click="changePage(page)"
                >
                  {{ page }}
                </button>
                <button class="page" :disabled="filters.page >= alerts.totalPages" type="button" @click="changePage(filters.page + 1)">›</button>
              </div>
            </footer>
          </section>
        </div>
      </section>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  createSubscription,
  fetchAlerts,
  fetchSubscriptions,
  runAlertMatching,
  updateAlertStatus,
  updateSubscriptionStatus
} from "../shared/api/alerts";
import type { AlertMatchingRun, AlertRecord, AlertStatus, PageResponse, SourceType, SubscriptionRule } from "../shared/api/contracts";
import { getErrorMessage } from "../shared/api/errors";
import { relativeTime as relativeTimeUtil } from "../shared/utils/datetime";
import "../styles/alerts-page.css";

const sourceOptions: SourceType[] = ["HACKER_NEWS", "ARXIV", "GITHUB", "HUGGING_FACE", "SOGOU_SEARCH"];

const subscriptions = ref<SubscriptionRule[]>([]);
const alerts = ref<PageResponse<AlertRecord>>({
  items: [],
  page: 1,
  size: 10,
  totalElements: 0,
  totalPages: 0
});
const lastRun = ref<AlertMatchingRun | null>(null);
const subscriptionsLoading = ref(false);
const alertsLoading = ref(false);
const matching = ref(false);
const creatingSubscription = ref(false);
const pageError = ref("");

const draft = reactive({
  name: "",
  keywordsText: "",
  sourceTypes: [] as SourceType[],
  minScore: 0,
  suppressWindowHours: 24,
  enabled: true
});

const filters = reactive({
  page: 1,
  size: 10,
  subscriptionId: "",
  status: ""
});

const pages = computed(() =>
  Array.from({ length: alerts.value.totalPages }, (_, index) => index + 1).slice(0, 7)
);

void initialize();

async function initialize(): Promise<void> {
  subscriptionsLoading.value = true;
  alertsLoading.value = true;
  pageError.value = "";
  try {
    const [subscriptionData, alertData] = await Promise.all([
      fetchSubscriptions(),
      fetchAlerts({ page: filters.page, size: filters.size })
    ]);
    subscriptions.value = subscriptionData;
    alerts.value = alertData;
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    subscriptionsLoading.value = false;
    alertsLoading.value = false;
  }
}

async function reloadSubscriptions(): Promise<void> {
  subscriptionsLoading.value = true;
  try {
    subscriptions.value = await fetchSubscriptions();
  } finally {
    subscriptionsLoading.value = false;
  }
}

async function reloadAlerts(): Promise<void> {
  alertsLoading.value = true;
  pageError.value = "";
  try {
    alerts.value = await fetchAlerts({
      page: filters.page,
      size: filters.size,
      subscriptionId: filters.subscriptionId ? Number(filters.subscriptionId) : undefined,
      status: filters.status ? (filters.status as AlertStatus) : undefined
    });
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    alertsLoading.value = false;
  }
}

async function submitSubscription(): Promise<void> {
  creatingSubscription.value = true;
  pageError.value = "";
  try {
    await createSubscription({
      name: draft.name,
      enabled: draft.enabled,
      keywords: parseKeywords(draft.keywordsText),
      sourceTypes: draft.sourceTypes,
      minScore: Number.isFinite(draft.minScore) ? draft.minScore : 0,
      suppressWindowHours: draft.suppressWindowHours
    });
    resetDraft();
    await reloadSubscriptions();
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    creatingSubscription.value = false;
  }
}

async function toggleRuleStatus(rule: SubscriptionRule): Promise<void> {
  pageError.value = "";
  try {
    await updateSubscriptionStatus(rule.id, !rule.enabled);
    await reloadSubscriptions();
  } catch (error) {
    pageError.value = getErrorMessage(error);
  }
}

async function triggerMatching(): Promise<void> {
  matching.value = true;
  pageError.value = "";
  try {
    lastRun.value = await runAlertMatching();
    filters.page = 1;
    await reloadAlerts();
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    matching.value = false;
  }
}

async function changeAlertStatus(alertId: number, status: AlertStatus): Promise<void> {
  pageError.value = "";
  try {
    await updateAlertStatus(alertId, status);
    await reloadAlerts();
  } catch (error) {
    pageError.value = getErrorMessage(error);
  }
}

function changePage(page: number): void {
  filters.page = page;
  void reloadAlerts();
}

function toggleSource(source: SourceType): void {
  if (draft.sourceTypes.includes(source)) {
    draft.sourceTypes = draft.sourceTypes.filter((item) => item !== source);
    return;
  }
  draft.sourceTypes = [...draft.sourceTypes, source];
}

function resetDraft(): void {
  draft.name = "";
  draft.keywordsText = "";
  draft.sourceTypes = [];
  draft.minScore = 0;
  draft.suppressWindowHours = 24;
  draft.enabled = true;
}

function parseKeywords(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function matchedKeywordsText(matchReason: Record<string, unknown>): string {
  const raw = matchReason.matchedKeywords;
  return Array.isArray(raw) && raw.length > 0 ? raw.join(", ") : "无";
}

function sourceLabel(source: SourceType): string {
  if (source === "ARXIV") return "arXiv";
  if (source === "GITHUB") return "GitHub";
  if (source === "HUGGING_FACE") return "Hugging Face";
  if (source === "SOGOU_SEARCH") return "搜狗搜索";
  return "Hacker News";
}


</script>
