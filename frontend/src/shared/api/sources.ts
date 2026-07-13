import { http } from "./http";
import type { ApiResponse, SourceConfig, SourceStatusUpdate } from "./contracts";

export async function fetchSources(): Promise<SourceConfig[]> {
    const response = await http.get<ApiResponse<SourceConfig[]>>("/api/v1/sources");
    return response.data.data;
}

export async function updateSourceStatus(sourceId: number, enabled: boolean): Promise<SourceConfig> {
    const payload: SourceStatusUpdate = { enabled };
    const response = await http.patch<ApiResponse<SourceConfig>>(`/api/v1/sources/${sourceId}/status`, payload);
    return response.data.data;
}
