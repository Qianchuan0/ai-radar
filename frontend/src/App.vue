<template>
  <a-config-provider :locale="zhCN">
    <AppLayout>
      <RouterView />
    </AppLayout>
  </a-config-provider>
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import AppLayout from "./layouts/AppLayout.vue";
</script>
