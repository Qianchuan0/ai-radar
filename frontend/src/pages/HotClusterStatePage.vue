<template>
  <section class="states-page">
    <header>
      <h1 class="page-title">热点榜单状态页</h1>
      <p class="page-subtitle">这个页面应该作为验收和设计对齐页存在，但它不应该再维护一套平行页面实现。</p>
    </header>

    <section class="state-notes radar-panel">
      <h2>正式收口建议</h2>
      <ol>
        <li>把状态页定位成“状态展台 / QA 页面”，不是第二套榜单页面。</li>
        <li>loading / empty / error 这三种状态，后续应提取为真实列表页复用的状态块。</li>
        <li>状态页只负责挂载这些真实状态块，传入示例 props 做视觉验收。</li>
        <li>这样既能保留你现在肉眼可见的设计检查入口，也能保持长期代码纯净。</li>
      </ol>
    </section>

    <div class="segmented">
      <button
        v-for="state in states"
        :key="state.key"
        :class="{ active: activeState === state.key }"
        type="button"
        @click="activeState = state.key"
      >
        {{ state.label }}
      </button>
    </div>

    <section class="state-grid">
      <article class="state-preview radar-panel" :class="{ active: activeState === 'loading' }">
        <h2><span>1.</span>加载中</h2>
        <div class="mini-filter-row">
          <span v-for="index in 4" :key="index" class="skeleton-chip" />
        </div>
        <div class="mini-metrics">
          <span v-for="index in 3" :key="index" />
        </div>
        <div class="table-skeleton">
          <div v-for="index in 5" :key="index" class="table-skeleton__row">
            <span />
            <span />
            <span />
            <span />
          </div>
        </div>
        <div class="loading-note"><span class="spinner" />正在请求真实热点数据...</div>
      </article>

      <article class="state-preview radar-panel" :class="{ active: activeState === 'empty' }">
        <h2><span>2.</span>空结果</h2>
        <div class="state-center">
          <div class="state-illustration state-illustration--empty">
            <span class="empty-orbit" />
            <span class="empty-plane" />
            <span class="empty-box" />
            <span class="state-ground" />
          </div>
          <h3>请求成功，但当前筛选没有命中结果</h3>
          <p>这是业务空态，不是接口异常。应该引导用户放宽筛选或重置条件。</p>
          <div class="state-actions">
            <button class="plain-button" type="button">重置筛选</button>
            <button class="plain-button" type="button">扩大范围</button>
          </div>
        </div>
      </article>

      <article class="state-preview radar-panel" :class="{ active: activeState === 'error' }">
        <h2><span>3.</span>请求失败</h2>
        <div class="state-center">
          <div class="state-illustration state-illustration--error">
            <span class="error-circle" />
            <span class="error-triangle" />
            <span class="error-mark">!</span>
            <span class="state-ground" />
          </div>
          <h3>hot cluster 请求失败</h3>
          <p>错误态要保留清晰提示、重试入口，以及能快速定位问题的接口线索。</p>
          <div class="state-actions">
            <button class="plain-button" type="button">重试</button>
            <RouterLink class="plain-button" :to="{ name: 'clusters' }">返回榜单</RouterLink>
          </div>
          <div class="api-chip">API: <strong>GET /api/v1/hot-clusters</strong></div>
        </div>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";

const states = [
  { key: "loading", label: "加载态" },
  { key: "empty", label: "空态" },
  { key: "error", label: "错误态" }
] as const;

const activeState = ref<(typeof states)[number]["key"]>("loading");
</script>

<style scoped>
.states-page {
  display: grid;
  gap: 20px;
}

.segmented {
  height: 38px;
  display: inline-flex;
  width: fit-content;
  border: 1px solid var(--border-strong);
  border-radius: 7px;
  overflow: hidden;
  background: #fff;
}

.segmented button {
  width: 102px;
  border: 0;
  border-right: 1px solid var(--border-strong);
  background: #fff;
  color: #344054;
  font-size: 14px;
  font-weight: 850;
  cursor: pointer;
}

.segmented button:last-child {
  border-right: 0;
}

.segmented button.active {
  color: var(--primary);
  background: #f4f8ff;
  box-shadow: inset 0 0 0 1px var(--primary);
}

.state-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.state-preview {
  height: 528px;
  padding: 22px;
  display: flex;
  flex-direction: column;
}

.state-preview.active {
  border-color: #bfdbff;
  box-shadow: var(--shadow);
}

.state-preview h2 {
  margin: 0 0 22px;
  color: #101828;
  font-size: 18px;
  font-weight: 900;
}

.state-preview h2 span {
  color: var(--primary);
  margin-right: 10px;
}

.mini-filter-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.skeleton-chip,
.mini-metrics span,
.table-skeleton__row span {
  background: linear-gradient(90deg, #eef2f7 0%, #e1e7ee 40%, #f3f6fa 80%);
  background-size: 220% 100%;
  animation: shimmer 1.4s infinite;
}

.skeleton-chip {
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 6px;
}

.mini-metrics {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.mini-metrics span {
  height: 62px;
  border-radius: 7px;
}

.table-skeleton {
  margin-top: 20px;
  border: 1px solid var(--border);
  border-radius: 7px;
  overflow: hidden;
}

.table-skeleton__row {
  height: 44px;
  padding: 0 10px;
  border-bottom: 1px solid var(--border);
  display: grid;
  grid-template-columns: 42px 1fr 70px 60px;
  gap: 14px;
  align-items: center;
}

.table-skeleton__row:last-child {
  border-bottom: 0;
}

.table-skeleton__row span {
  height: 10px;
  border-radius: 999px;
}

.loading-note {
  margin-top: auto;
  padding-top: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #52607a;
  font-size: 14px;
  font-weight: 750;
}

.spinner {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #dbe7fb;
  border-top-color: var(--primary);
  animation: spin 0.8s linear infinite;
}

.state-center {
  flex: 1;
  display: grid;
  place-items: center;
  text-align: center;
}

.state-center h3 {
  margin: 0;
  color: #101828;
  font-size: 20px;
  font-weight: 900;
}

.state-center p {
  max-width: 330px;
  margin: 12px auto 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
  font-weight: 650;
}

.state-illustration {
  width: 190px;
  height: 150px;
  margin: 0 auto 20px;
  position: relative;
}

.state-ground {
  position: absolute;
  left: 28px;
  right: 28px;
  bottom: 8px;
  height: 12px;
  border-radius: 50%;
  background: #e8eef8;
}

.empty-orbit {
  position: absolute;
  left: 54px;
  top: 22px;
  width: 62px;
  height: 38px;
  border: 2px dashed #c9d6ea;
  border-radius: 50%;
  transform: rotate(25deg);
}

.empty-plane {
  position: absolute;
  right: 20px;
  top: 18px;
  width: 48px;
  height: 48px;
  background: #7da6ff;
  clip-path: polygon(0 42%, 100% 0, 58% 100%, 43% 58%);
  transform: rotate(12deg);
}

.empty-box {
  position: absolute;
  left: 55px;
  bottom: 22px;
  width: 80px;
  height: 48px;
  background: linear-gradient(180deg, #ccd8ec, #9daeca);
  clip-path: polygon(12% 18%, 50% 0, 88% 18%, 88% 78%, 50% 100%, 12% 78%);
}

.error-circle {
  position: absolute;
  inset: 12px 28px 18px;
  border-radius: 50%;
  background: rgba(255, 77, 79, 0.1);
}

.error-triangle {
  position: absolute;
  left: 58px;
  top: 42px;
  width: 74px;
  height: 64px;
  border-radius: 12px;
  background: linear-gradient(180deg, #ff4254, #ec2436);
  clip-path: polygon(50% 0, 100% 100%, 0 100%);
}

.error-mark {
  position: absolute;
  left: 91px;
  top: 62px;
  color: #fff;
  font-size: 42px;
  line-height: 1;
  font-weight: 900;
}

.state-actions {
  margin-top: 28px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.plain-button {
  height: 32px;
  padding: 0 15px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  color: #1d293f;
  background: #fff;
  text-decoration: none;
  font-size: 14px;
  font-weight: 850;
  cursor: pointer;
}

.api-chip {
  margin: 18px auto 0;
  min-height: 32px;
  width: fit-content;
  padding: 0 14px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #52607a;
  background: #f0f4f9;
  font-size: 12px;
  font-weight: 850;
}

.state-notes {
  padding: 18px 20px;
}

.state-notes h2 {
  margin: 0 0 10px;
  color: #101828;
  font-size: 17px;
}

.state-notes ol {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.9;
}

@keyframes shimmer {
  to {
    background-position: -100% 0;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1440px) {
  .state-grid {
    grid-template-columns: 1fr;
  }
}
</style>
