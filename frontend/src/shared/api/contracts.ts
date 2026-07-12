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

export type SourceType = "ARXIV" | "HACKER_NEWS" | "GITHUB" | "HUGGING_FACE";
export type HotClusterSort = "SCORE_DESC" | "LATEST";
export type HotClusterStatus = "ACTIVE" | "MERGED" | "ARCHIVED";
export type AnalysisRunStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type AnalysisType = "CLUSTER_BRIEF";
export type AlertStatus = "NEW" | "ACKED" | "DISMISSED";
export type ReportStatus = "GENERATED";

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

export interface SubscriptionRule {
    id: number;
    name: string;
    enabled: boolean;
    keywords: string[];
    sourceTypes: SourceType[];
    minScore: number | null;
    suppressWindowHours: number;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface AlertRecord {
    id: number;
    subscriptionRuleId: number;
    subscriptionName: string;
    hotClusterId: number;
    hotClusterTitle: string;
    sourceTypes: SourceType[];
    hotScore: number | null;
    status: AlertStatus;
    matchReason: Record<string, unknown>;
    matchedAt: string;
    createdAt: string;
}

export interface AlertMatchingRun {
    scannedClusterCount: number;
    matchedRuleCount: number;
    createdAlertCount: number;
    suppressedAlertCount: number;
    completedAt: string;
}

export interface AlertListQuery {
    page: number;
    size: number;
    subscriptionId?: number;
    status?: AlertStatus;
}

export interface DailyReportCluster {
    hotClusterId: number;
    title: string;
    summary: string | null;
    sourceTypes: SourceType[];
    itemCount: number;
    score: HotScore | null;
    firstSeenAt: string;
    lastSeenAt: string;
    latestAnalysis: ClusterAnalysis | null;
}

export interface DailyReportSummary {
    id: number;
    reportDate: string;
    status: ReportStatus;
    title: string;
    summary: string;
    clusterCount: number;
    topClusterIds: number[];
    generatedAt: string;
}

export interface DailyReport {
    id: number;
    reportDate: string;
    status: ReportStatus;
    title: string;
    summary: string;
    clusterCount: number;
    topClusterIds: number[];
    clusters: DailyReportCluster[];
    generatedAt: string;
    createdAt: string;
}

export interface DailyReportGeneration {
    reportDate: string;
    clusterCount: number;
    generatedAt: string;
}

export type EvaluationCaseType =
    | "CRAWL_ITEM_PRESENT"
    | "CLUSTER_MEMBERSHIP"
    | "SCORE_THRESHOLD"
    | "ANALYSIS_REQUIRED_FIELDS"
    | "ALERT_EXPECTED_RECORD";
export type EvaluationCaseStatus = "PASSED" | "FAILED" | "ERROR";
export type EvaluationRunStatus = "RUNNING" | "COMPLETED" | "FAILED";

export interface EvaluationDataset {
    id: number;
    name: string;
    description: string | null;
    version: number;
    enabled: boolean;
    caseCount: number;
    createdAt: string;
    updatedAt: string;
}

export interface EvaluationCase {
    id: number;
    datasetId: number;
    caseCode: string;
    caseType: EvaluationCaseType;
    targetPayload: Record<string, unknown>;
    expectedPayload: Record<string, unknown>;
    notes: string | null;
    enabled: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface EvaluationRunSummary {
    id: number;
    datasetId: number;
    datasetName: string | null;
    status: EvaluationRunStatus;
    totalCases: number;
    passedCases: number;
    failedCases: number;
    errorCases: number;
    startedAt: string;
    finishedAt: string | null;
    createdAt: string;
}

export interface EvaluationCaseResult {
    id: number;
    caseId: number;
    caseCode: string;
    caseType: EvaluationCaseType;
    status: EvaluationCaseStatus;
    actualPayload: Record<string, unknown>;
    failureReason: string | null;
    evaluatedAt: string;
}

export interface EvaluationRun extends EvaluationRunSummary {
    metricsPayload: Record<string, unknown>;
    errorAnalysisPayload: Record<string, unknown>;
    caseResults: EvaluationCaseResult[];
}

export interface EvaluationRunGeneration {
    runId: number;
    datasetId: number;
    status: EvaluationRunStatus;
    totalCases: number;
    passedCases: number;
    failedCases: number;
    errorCases: number;
    startedAt: string;
    finishedAt: string;
}
