import { describe, expect, it } from "vitest";
import {
    buildHotClusterQuery,
    parseHotClusterFilters,
    resetHotClusterFilters,
    toHotClusterListQuery
} from "./query";

describe("hot cluster query helpers", () => {
    it("parses defaults when the query is empty", () => {
        expect(parseHotClusterFilters({})).toEqual({
            page: 1,
            size: 12,
            sort: "SCORE_DESC",
            sourceType: undefined,
            from: undefined,
            to: undefined,
            q: "",
            minScore: 0
        });
    });

    it("builds and converts a query payload", () => {
        const filters = {
            page: 2,
            size: 24,
            sort: "LATEST" as const,
            sourceType: "HACKER_NEWS" as const,
            from: "2026-07-03T10:00",
            to: "2026-07-03T18:00",
            q: "agent",
            minScore: 42
        };

        expect(buildHotClusterQuery(filters)).toEqual({
            page: "2",
            size: "24",
            sort: "LATEST",
            sourceType: "HACKER_NEWS",
            from: "2026-07-03T10:00",
            to: "2026-07-03T18:00",
            q: "agent",
            minScore: "42"
        });
        expect(toHotClusterListQuery(filters)).toEqual({
            page: 2,
            size: 24,
            sort: "LATEST",
            sourceType: "HACKER_NEWS",
            from: new Date("2026-07-03T10:00").toISOString(),
            to: new Date("2026-07-03T18:00").toISOString()
        });
    });

    it("parses SOGOU_SEARCH sourceType from query", () => {
        const filters = parseHotClusterFilters({ sourceType: "SOGOU_SEARCH" });
        expect(filters.sourceType).toBe("SOGOU_SEARCH");
    });

    it("parses WEIBO_HOT_SEARCH sourceType from query", () => {
        const filters = parseHotClusterFilters({ sourceType: "WEIBO_HOT_SEARCH" });
        expect(filters.sourceType).toBe("WEIBO_HOT_SEARCH");
    });

    it("parses HACKER_NEWS_SEARCH sourceType from query", () => {
        const filters = parseHotClusterFilters({ sourceType: "HACKER_NEWS_SEARCH" });
        expect(filters.sourceType).toBe("HACKER_NEWS_SEARCH");
    });

    it("parses TWITTER sourceType from query", () => {
        const filters = parseHotClusterFilters({ sourceType: "TWITTER" });
        expect(filters.sourceType).toBe("TWITTER");
    });

    it("creates an isolated reset payload", () => {
        const first = resetHotClusterFilters();
        const second = resetHotClusterFilters();
        first.page = 9;

        expect(second.page).toBe(1);
    });
});
