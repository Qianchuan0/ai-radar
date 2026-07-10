import axios from "axios";
import { http } from "./http";
import type {
    ApiResponse,
    DailyReport,
    DailyReportGeneration,
    DailyReportSummary,
    PageResponse
} from "./contracts";

export async function fetchDailyReports(page: number, size: number): Promise<PageResponse<DailyReportSummary>> {
    const response = await http.get<ApiResponse<PageResponse<DailyReportSummary>>>("/api/v1/reports/daily", {
        params: { page, size }
    });
    return response.data.data;
}

export async function fetchDailyReport(reportDate: string): Promise<DailyReport | null> {
    try {
        const response = await http.get<ApiResponse<DailyReport>>(`/api/v1/reports/daily/${reportDate}`);
        return response.data.data;
    } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
            return null;
        }
        throw error;
    }
}

export async function generateDailyReport(reportDate: string): Promise<DailyReportGeneration> {
    const response = await http.post<ApiResponse<DailyReportGeneration>>("/api/v1/reports/daily-runs", null, {
        params: { date: reportDate }
    });
    return response.data.data;
}
