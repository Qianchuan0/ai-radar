import { http } from "./http";
import type {
    AlertListQuery,
    AlertMatchingRun,
    AlertRecord,
    AlertStatus,
    ApiResponse,
    PageResponse,
    SourceType,
    SubscriptionRule
} from "./contracts";

export interface CreateSubscriptionPayload {
    name: string;
    enabled: boolean;
    keywords: string[];
    sourceTypes: SourceType[];
    minScore: number | null;
    suppressWindowHours: number;
}

export async function fetchSubscriptions(): Promise<SubscriptionRule[]> {
    const response = await http.get<ApiResponse<SubscriptionRule[]>>("/api/v1/subscriptions");
    return response.data.data;
}

export async function createSubscription(payload: CreateSubscriptionPayload): Promise<SubscriptionRule> {
    const response = await http.post<ApiResponse<SubscriptionRule>>("/api/v1/subscriptions", payload);
    return response.data.data;
}

export async function updateSubscriptionStatus(subscriptionId: number, enabled: boolean): Promise<SubscriptionRule> {
    const response = await http.patch<ApiResponse<SubscriptionRule>>(`/api/v1/subscriptions/${subscriptionId}/status`, {
        enabled
    });
    return response.data.data;
}

export async function runAlertMatching(): Promise<AlertMatchingRun> {
    const response = await http.post<ApiResponse<AlertMatchingRun>>("/api/v1/alerts/matching-runs");
    return response.data.data;
}

export async function fetchAlerts(query: AlertListQuery): Promise<PageResponse<AlertRecord>> {
    const response = await http.get<ApiResponse<PageResponse<AlertRecord>>>("/api/v1/alerts", {
        params: query
    });
    return response.data.data;
}

export async function updateAlertStatus(alertId: number, status: AlertStatus): Promise<AlertRecord> {
    const response = await http.patch<ApiResponse<AlertRecord>>(`/api/v1/alerts/${alertId}/status`, {
        status
    });
    return response.data.data;
}
