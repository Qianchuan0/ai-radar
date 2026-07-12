import type { RouteRecordRaw } from "vue-router";

export interface MenuItem {
    name: string;
    menuText: string;
}

interface MenuMeta {
    menuText?: string;
    inMenu?: boolean;
}

export function selectMenuItems(routes: RouteRecordRaw[]): MenuItem[] {
    return routes
        .filter((route) => (route.meta as MenuMeta | undefined)?.inMenu === true)
        .map((route) => ({
            name: String(route.name),
            menuText: (route.meta as MenuMeta).menuText ?? String(route.name)
        }));
}

const CLUSTER_FAMILY_PREFIX = "cluster-";

export function resolveActiveMenuName(
    routeName: string | symbol | undefined,
    routes: RouteRecordRaw[]
): string | undefined {
    if (!routeName) return undefined;
    const name = String(routeName);

    const exact = routes.find((route) => String(route.name) === name);
    if (exact && (exact.meta as MenuMeta | undefined)?.inMenu === true) {
        return name;
    }

    if (name.startsWith(CLUSTER_FAMILY_PREFIX)) {
        const clusters = routes.find((route) => route.name === "clusters");
        if (clusters && (clusters.meta as MenuMeta | undefined)?.inMenu === true) {
            return "clusters";
        }
    }

    return undefined;
}
