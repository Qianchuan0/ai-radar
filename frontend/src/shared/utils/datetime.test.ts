import { describe, expect, it, vi, afterEach } from "vitest";
import { formatDateTime, relativeTime } from "./datetime";

describe("datetime helpers", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("relativeTime returns placeholder for empty value", () => {
        expect(relativeTime(null)).toBe("--");
        expect(relativeTime(undefined)).toBe("--");
        expect(relativeTime("")).toBe("--");
        expect(relativeTime("not-a-date")).toBe("--");
    });

    it("relativeTime formats Chinese relative time buckets", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-07-10T12:00:00Z").getTime());

        const now = new Date("2026-07-10T12:00:00Z").toISOString();
        expect(relativeTime(now)).toBe("刚刚");

        const tenMinutesAgo = new Date("2026-07-10T11:50:00Z").toISOString();
        expect(relativeTime(tenMinutesAgo)).toBe("10 分钟前");

        const twoHoursAgo = new Date("2026-07-10T10:00:00Z").toISOString();
        expect(relativeTime(twoHoursAgo)).toBe("2 小时前");

        const threeDaysAgo = new Date("2026-07-07T12:00:00Z").toISOString();
        expect(relativeTime(threeDaysAgo)).toBe("3 天前");
    });

    it("formatDateTime returns placeholder for empty or invalid value", () => {
        expect(formatDateTime(null)).toBe("--");
        expect(formatDateTime(undefined)).toBe("--");
        expect(formatDateTime("not-a-date")).toBe("--");
    });

    it("formatDateTime formats as YYYY-MM-DD HH:mm", () => {
        const iso = new Date("2026-07-10T14:05:00").toISOString();
        const result = formatDateTime(iso);
        expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
    });
});
