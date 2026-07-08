import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: "/",
            redirect: "/clusters"
        },
        {
            path: "/clusters",
            name: "clusters",
            component: () => import("../pages/HotClusterListPage.vue")
        },
        {
            path: "/clusters/states",
            name: "cluster-states",
            component: () => import("../pages/HotClusterStatePage.vue")
        },
        {
            path: "/clusters/:clusterId",
            name: "cluster-detail",
            component: () => import("../pages/HotClusterDetailPage.vue")
        }
    ]
});

export default router;
