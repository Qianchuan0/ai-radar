const PLACEHOLDER = "--";

export function relativeTime(value: string | null | undefined): string {
    if (!value) return PLACEHOLDER;
    const timestamp = new Date(value).getTime();
    if (!Number.isFinite(timestamp)) return PLACEHOLDER;

    const diffMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
    if (diffMinutes < 1) return "刚刚";
    if (diffMinutes < 60) return `${diffMinutes} 分钟前`;

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours} 小时前`;

    return `${Math.floor(diffHours / 24)} 天前`;
}

export function formatDateTime(value: string | null | undefined): string {
    if (!value) return PLACEHOLDER;
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return PLACEHOLDER;

    const pad = (part: number) => String(part).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
