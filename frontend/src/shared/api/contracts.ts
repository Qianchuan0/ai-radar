export interface ApiResponse<T> {
    code: string;
    message: string;
    data: T;
    timestamp: string;
}

export interface PageResponse<T> {
    items: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export type SourceType = "ARXIV" | "HACKER_NEWS" | "GITHUB";
export type HotClusterSort = "SCORE_DESC" | "LATEST";
export type HotClusterStatus = "ACTIVE" | "MERGED" | "ARCHIVED";

export interface HotScore {
    total: number;
    version: string;
    calculatedAt: string;
    components: Record<string, number>;
}

export interface HotClusterSummary {
    id: number;
    title: string;
    summary: string | null;
    status: HotClusterStatus;
    firstSeenAt: string;
    lastSeenAt: string;
    itemCount: number;
    score: HotScore | null;
}

export interface HotItemEvidence {
    id: number;
    sourceType: SourceType;
    externalId: string;
    title: string;
    summary: string | null;
    sourceUrl: string;
    author: string | null;
    publishedAt: string | null;
    matchMethod: string;
    matchScore: number | null;
    matchReason: Record<string, unknown> | null;
    ruleVersion: string;
}

export interface HotClusterDetail extends HotClusterSummary {
    items: HotItemEvidence[];
}

export interface HotClusterListQuery {
    page: number;
    size: number;
    sort: HotClusterSort;
    sourceType?: SourceType;
    from?: string;
    to?: string;
}
