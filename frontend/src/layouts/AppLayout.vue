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
