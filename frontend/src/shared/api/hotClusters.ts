import { http } from "./http";
import type {
    ApiResponse,
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
