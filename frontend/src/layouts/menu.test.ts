import { describe, expect, it } from "vitest";
import type { RouteRecordRaw } from "vue-router";
import { resolveActiveMenuName, selectMenuItems } from "./menu";

const routes: RouteRecordRaw[] = [
    { path: "/a", name: "clusters", component: {}, meta: { menuText: "热点榜单", inMenu: true } },
    { path: "/b", name: "alerts", component: {}, meta: { menuText: "订阅告警", inMenu: true } },
    { path: "/c", name: "cluster-detail", component: {}, meta: { inMenu: false } },
    { path: "/c2", name: "cluster-states", component: {}, meta: { inMenu: false } },
    { path: "/d", name: "hidden", component: {}, meta: { inMenu: false } }
];

describe("menu derivation", () => {
    it("selects only routes with inMenu true", () => {
        const items = selectMenuItems(routes);
        expect(items).toEqual([
            { name: "clusters", menuText: "热点榜单" },
            { name: "alerts", menuText: "订阅告警" }
        ]);
    });

    it("resolves exact match first", () => {
        expect(resolveActiveMenuName("alerts", routes)).toBe("alerts");
    });

    it("merges cluster-detail and cluster-states into clusters", () => {
        expect(resolveActiveMenuName("cluster-detail", routes)).toBe("clusters");
        expect(resolveActiveMenuName("cluster-states", routes)).toBe("clusters");
    });

    it("returns undefined when nothing matches", () => {
        expect(resolveActiveMenuName("unknown", routes)).toBeUndefined();
        expect(resolveActiveMenuName(undefined, routes)).toBeUndefined();
    });
});
