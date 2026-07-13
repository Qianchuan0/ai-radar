<template>
  <section class="sources-page">
    <section class="sources-hero">
      <div>
        <h1 class="page-title">来源管理</h1>
        <p class="subtitle">查看与控制每个数据来源的启用状态与抓取间隔，禁用的来源不会进入定时调度。</p>
      </div>
      <button class="source-toggle" type="button" :disabled="loading" @click="reload">
        {{ loading ? "刷新中..." : "刷新列表" }}
      </button>
    </section>

    <div class="sources-summary">
      <span class="pill">共 {{ sources.length }} 个来源</span>
      <span class="pill neutral">启用 {{ enabledCount }}</span>
      <span class="pill neutral">停用 {{ sources.length - enabledCount }}</span>
    </div>

    <section v-if="pageError" class="state-banner error">{{ pageError }}</section>
    <section v-else-if="lastAction" class="state-banner success">{{ lastAction }}</section>

    <section class="sources-panel">
      <div class="sources-head">
        <h2>来源列表</h2>
        <span class="subtle">{{ sources.length }} 条</span>
      </div>

      <div v-if="loading" class="sources-empty">正在加载来源配置。</div>
      <div v-else-if="sources.length === 0" class="sources-empty">还没有已配置的数据来源。</div>
      <table v-else class="sources-table">
        <thead>
          <tr>
            <th>展示名称</th>
            <th>来源类型</th>
            <th>来源编码</th>
            <th>状态</th>
            <th>抓取间隔</th>
            <th>版本</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="source in sources" :key="source.id">
            <td class="source-name">{{ source.displayName }}</td>
            <td><span class="type-chip">{{ sourceLabel(source.sourceType) }}</span></td>
            <td class="source-code">{{ source.sourceCode }}</td>
            <td>
              <span class="status-tag" :class="source.enabled ? 'enabled' : 'disabled'">
                {{ source.enabled ? "启用" : "停用" }}
              </span>
            </td>
            <td class="interval-cell">{{ formatInterval(source.crawlIntervalMinutes) }}</td>
            <td class="meta-cell">v{{ source.version }}</td>
            <td class="meta-cell" :title="formatDateTime(source.updatedAt)">
              {{ relativeTime(source.updatedAt) }}
            </td>
            <td>
              <button
                class="source-toggle"
                :class="{ enabled: !source.enabled }"
                type="button"
                :disabled="actionSourceId === source.id"
                @click="toggleSource(source)"
              >
                {{ actionLabel(source) }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { fetchSources, updateSourceStatus } from "../shared/api/sources";
import type { SourceConfig, SourceType } from "../shared/api/contracts";
import { getErrorMessage } from "../shared/api/errors";
import { formatDateTime, relativeTime } from "../shared/utils/datetime";
import "../styles/sources-page.css";

const sources = ref<SourceConfig[]>([]);
const loading = ref(false);
const pageError = ref("");
const lastAction = ref("");
const actionSourceId = ref<number | null>(null);

const enabledCount = computed(() => sources.value.filter((source) => source.enabled).length);

void initialize();

async function initialize(): Promise<void> {
  await reload();
}

async function reload(): Promise<void> {
  loading.value = true;
  pageError.value = "";
  lastAction.value = "";
  try {
    sources.value = await fetchSources();
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function toggleSource(source: SourceConfig): Promise<void> {
  actionSourceId.value = source.id;
  pageError.value = "";
  lastAction.value = "";
  try {
    const updated = await updateSourceStatus(source.id, !source.enabled);
    const index = sources.value.findIndex((item) => item.id === source.id);
    if (index >= 0) {
      sources.value[index] = updated;
    }
    lastAction.value = `已${updated.enabled ? "启用" : "停用"} ${updated.displayName}。`;
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    actionSourceId.value = null;
  }
}

function actionLabel(source: SourceConfig): string {
  if (actionSourceId.value !== source.id) {
    return source.enabled ? "停用" : "启用";
  }
  return source.enabled ? "停用中..." : "启用中...";
}

function formatInterval(minutes: number | null): string {
  if (minutes == null) return "手动";
  if (minutes === 1440) return "每天";
  return `${minutes} 分钟`;
}

function sourceLabel(sourceType: SourceType): string {
  if (sourceType === "ARXIV") return "arXiv";
  if (sourceType === "GITHUB") return "GitHub";
  if (sourceType === "HUGGING_FACE") return "Hugging Face";
  if (sourceType === "SOGOU_SEARCH") return "搜狗搜索";
  return "Hacker News";
}
</script>
