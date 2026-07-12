import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";

export const routes: RouteRecordRaw[] = [
    {
        path: "/",
        redirect: "/clusters"
    },
    {
        path: "/clusters",
        name: "clusters",
        component: () => import("../pages/HotClusterListPage.vue"),
        meta: { menuText: "热点榜单", breadcrumb: ["热点榜单", "事件级热点"], inMenu: true }
    },
    {
        path: "/clusters/states",
        name: "cluster-states",
        component: () => import("../pages/HotClusterStatePage.vue"),
        meta: { breadcrumb: ["热点榜单", "状态页"], inMenu: false }
    },
    {
        path: "/clusters/:clusterId",
        name: "cluster-detail",
        component: () => import("../pages/HotClusterDetailPage.vue"),
        meta: { breadcrumb: ["热点榜单", "热点详情"], inMenu: false }
    },
    {
        path: "/alerts",
        name: "alerts",
        component: () => import("../pages/AlertsPage.vue"),
        meta: { menuText: "订阅告警", breadcrumb: ["热点榜单", "订阅告警"], inMenu: true }
    },
    {
        path: "/reports/daily",
        name: "daily-reports",
        component: () => import("../pages/DailyReportsPage.vue"),
        meta: { menuText: "日报", breadcrumb: ["热点榜单", "日报"], inMenu: true }
    },
    {
        path: "/evaluation",
        name: "evaluation",
        component: () => import("../pages/EvaluationPage.vue"),
        meta: { menuText: "评测", breadcrumb: ["热点榜单", "评测"], inMenu: true }
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

export default router;
