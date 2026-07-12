import type { HotClusterListQuery, HotClusterSort, SourceType } from "../api/contracts";

export interface HotClusterFilters {
    page: number;
    size: number;
    sort: HotClusterSort;
    sourceType?: SourceType;
    from?: string;
    to?: string;
    q: string;
    minScore: number;
}

const DEFAULT_FILTERS: HotClusterFilters = {
    page: 1,
    size: 12,
    sort: "SCORE_DESC",
    sourceType: undefined,
    from: undefined,
    to: undefined,
    q: "",
    minScore: 0
};

export function parseHotClusterFilters(query: Record<string, unknown>): HotClusterFilters {
    return {
        page: parsePositiveInteger(query.page, DEFAULT_FILTERS.page),
        size: parsePositiveInteger(query.size, DEFAULT_FILTERS.size),
        sort: parseSort(query.sort),
        sourceType: parseSourceType(query.sourceType),
        from: parseOptionalString(query.from),
        to: parseOptionalString(query.to),
        q: parseOptionalString(query.q) ?? DEFAULT_FILTERS.q,
        minScore: parseNonNegativeInteger(query.minScore, DEFAULT_FILTERS.minScore)
    };
}

export function buildHotClusterQuery(filters: HotClusterFilters): Record<string, string> {
    const query: Record<string, string> = {
        page: String(filters.page),
        size: String(filters.size),
        sort: filters.sort
    };

    if (filters.sourceType) query.sourceType = filters.sourceType;
    if (filters.from) query.from = filters.from;
    if (filters.to) query.to = filters.to;
    if (filters.q) query.q = filters.q;
    if (filters.minScore > 0) query.minScore = String(filters.minScore);

    return query;
}

export function toHotClusterListQuery(filters: HotClusterFilters): HotClusterListQuery {
    return {
        page: filters.page,
        size: filters.size,
        sort: filters.sort,
        sourceType: filters.sourceType,
        from: toIsoString(filters.from),
        to: toIsoString(filters.to)
    };
}

export function resetHotClusterFilters(): HotClusterFilters {
    return { ...DEFAULT_FILTERS };
}

function parsePositiveInteger(value: unknown, fallback: number): number {
    const parsed = Number(single(value));
    return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : fallback;
}

function parseNonNegativeInteger(value: unknown, fallback: number): number {
    const parsed = Number(single(value));
    return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : fallback;
}

function parseSort(value: unknown): HotClusterSort {
    const parsed = single(value);
    return parsed === "LATEST" ? "LATEST" : "SCORE_DESC";
}

function parseSourceType(value: unknown): SourceType | undefined {
    const parsed = single(value);
    if (parsed === "ARXIV" || parsed === "HACKER_NEWS" || parsed === "GITHUB" || parsed === "HUGGING_FACE") {
        return parsed;
    }
    return undefined;
}

function parseOptionalString(value: unknown): string | undefined {
    const parsed = single(value)?.trim();
    return parsed ? parsed : undefined;
}

function toIsoString(value?: string): string | undefined {
    if (!value) return undefined;
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toISOString() : undefined;
}

function single(value: unknown): string | undefined {
    if (Array.isArray(value)) {
        return value[0] == null ? undefined : String(value[0]);
    }
    return value == null ? undefined : String(value);
}
