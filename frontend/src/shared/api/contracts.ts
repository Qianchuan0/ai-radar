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
export type AnalysisRunStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type AnalysisType = "CLUSTER_BRIEF";

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
    sourceTypes: SourceType[];
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

export interface AnalysisEvidenceRef {
    hotItemId: number;
    sourceType: SourceType;
    title: string;
    sourceUrl: string;
}

export interface StructuredAnalysisResult {
    headline: string;
    brief: string;
    whyItMatters: string;
    keySignals: string[];
    evidenceRefs: AnalysisEvidenceRef[];
    risks: string[];
    followUps: string[];
    confidence: string;
}

export interface ClusterAnalysis {
    id: number;
    hotClusterId: number;
    analysisType: AnalysisType;
    status: AnalysisRunStatus;
    schemaVersion: string;
    promptVersion: string;
    modelProvider: string;
    modelName: string;
    inputHash: string;
    result: StructuredAnalysisResult | null;
    failureCode: string | null;
    failureMessage: string | null;
    startedAt: string;
    finishedAt: string | null;
    createdAt: string;
}

export interface HotClusterListQuery {
    page: number;
    size: number;
    sort: HotClusterSort;
    sourceType?: SourceType;
    from?: string;
    to?: string;
}
