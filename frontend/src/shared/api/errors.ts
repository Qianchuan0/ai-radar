import axios from "axios";

export function getErrorMessage(error: unknown): string {
    if (axios.isAxiosError(error)) {
        const responseMessage = error.response?.data?.message;
        if (typeof responseMessage === "string" && responseMessage.trim().length > 0) {
            return responseMessage;
        }
        if (typeof error.message === "string" && error.message.trim().length > 0) {
            return error.message;
        }
    }

    if (error instanceof Error && error.message.trim().length > 0) {
        return error.message;
    }

    return "Request failed.";
}
