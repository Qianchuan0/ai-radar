import axios from "axios";
import { http } from "./http";
import type {
    ApiResponse,
    ClusterAnalysis,
    HotClusterDetail,
    HotClusterListQuery,
    HotClusterSummary,
    PageResponse
} from "./contracts";

export async function fetchHotClusters(query: HotClusterListQuery): Promise<PageResponse<HotClusterSummary>> {
    const response = await http.get<ApiResponse<PageResponse<HotClusterSummary>>>("/api/v1/hot-clusters", {
        params: query
    });
    return response.data.data;
}

export async function fetchHotClusterDetail(clusterId: number): Promise<HotClusterDetail> {
    const response = await http.get<ApiResponse<HotClusterDetail>>(`/api/v1/hot-clusters/${clusterId}`);
    return response.data.data;
}

export async function fetchLatestHotClusterAnalysis(clusterId: number): Promise<ClusterAnalysis | null> {
    try {
        const response = await http.get<ApiResponse<ClusterAnalysis>>(`/api/v1/hot-clusters/${clusterId}/analysis`);
        return response.data.data;
    } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
            return null;
        }
        throw error;
    }
}

export async function triggerHotClusterAnalysis(clusterId: number): Promise<ClusterAnalysis> {
    const response = await http.post<ApiResponse<ClusterAnalysis>>(`/api/v1/hot-clusters/${clusterId}/analysis-runs`);
    return response.data.data;
}
