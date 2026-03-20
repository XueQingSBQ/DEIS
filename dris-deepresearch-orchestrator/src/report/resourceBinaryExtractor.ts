import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

const execFileAsync = promisify(execFile);

export interface BinaryExtractResult {
  ok: boolean;
  kind: string;
  summary: string;
  excerpt: string;
  lines: string[];
  message?: string;
}

function scriptPath(): string {
  return path.resolve(process.cwd(), "scripts", "extract_resource_text.py");
}

export async function extractBinaryResourceText(
  url: string,
  contentType: string,
  timeoutMs: number,
  maxChars: number
): Promise<BinaryExtractResult> {
  const { stdout } = await execFileAsync(
    "python",
    ["-X", "utf8", scriptPath(), url, contentType || "", String(Math.max(1, Math.ceil(timeoutMs / 1000))), String(maxChars)],
    {
      timeout: timeoutMs + 2000,
      maxBuffer: 2 * 1024 * 1024,
      encoding: "utf8",
      env: {
        ...process.env,
        PYTHONIOENCODING: "utf-8",
        PYTHONUTF8: "1"
      }
    }
  );

  const payload = JSON.parse((stdout || "").trim()) as Partial<BinaryExtractResult>;
  return {
    ok: Boolean(payload.ok),
    kind: String(payload.kind || "binary"),
    summary: String(payload.summary || ""),
    excerpt: String(payload.excerpt || ""),
    lines: Array.isArray(payload.lines) ? payload.lines.map((item) => String(item)) : [],
    message: payload.message ? String(payload.message) : undefined
  };
}
