export function prettyJson(value: unknown): string {
  if (value == null) {
    return "";
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function formatTime(value?: string | null): string {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString("zh-CN", { hour12: false });
}

export function formatNumber(value?: number | null, digits = 2): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  return value.toFixed(digits);
}

export function truncate(value?: string | null, length = 48): string {
  if (!value) {
    return "-";
  }
  return value.length > length ? `${value.slice(0, length)}...` : value;
}
