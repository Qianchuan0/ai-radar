import { http } from "./http";
import type {
    ApiResponse,
    EvaluationCase,
    EvaluationDataset,
    EvaluationRun,
    EvaluationRunGeneration,
    EvaluationRunSummary,
    PageResponse
} from "./contracts";

export async function fetchEvaluationDatasets(): Promise<EvaluationDataset[]> {
    const response = await http.get<ApiResponse<EvaluationDataset[]>>("/api/v1/evaluation/datasets");
    return response.data.data;
}

export async function createEvaluationDataset(
    name: string,
    description: string,
    enabled: boolean
): Promise<EvaluationDataset> {
    const response = await http.post<ApiResponse<EvaluationDataset>>(
        "/api/v1/evaluation/datasets",
        { name, description, enabled }
    );
    return response.data.data;
}

export async function fetchEvaluationCases(datasetId: number): Promise<EvaluationCase[]> {
    const response = await http.get<ApiResponse<EvaluationCase[]>>(
        `/api/v1/evaluation/datasets/${datasetId}/cases`
    );
    return response.data.data;
}

export async function createEvaluationCase(
    datasetId: number,
    payload: {
        caseCode: string;
        caseType: string;
        targetPayload: Record<string, unknown>;
        expectedPayload: Record<string, unknown>;
        notes?: string;
        enabled: boolean;
    }
): Promise<EvaluationCase> {
    const response = await http.post<ApiResponse<EvaluationCase>>(
        `/api/v1/evaluation/datasets/${datasetId}/cases`,
        payload
    );
    return response.data.data;
}

export async function triggerEvaluationRun(datasetId: number): Promise<EvaluationRunGeneration> {
    const response = await http.post<ApiResponse<EvaluationRunGeneration>>(
        "/api/v1/evaluation/runs",
        null,
        { params: { datasetId } }
    );
    return response.data.data;
}

export async function fetchEvaluationRuns(
    page: number,
    size: number,
    datasetId?: number
): Promise<PageResponse<EvaluationRunSummary>> {
    const response = await http.get<ApiResponse<PageResponse<EvaluationRunSummary>>>(
        "/api/v1/evaluation/runs",
        { params: { page, size, datasetId } }
    );
    return response.data.data;
}

export async function fetchEvaluationRun(runId: number): Promise<EvaluationRun> {
    const response = await http.get<ApiResponse<EvaluationRun>>(`/api/v1/evaluation/runs/${runId}`);
    return response.data.data;
}
