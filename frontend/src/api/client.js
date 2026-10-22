const BASE_URL = import.meta.env.VITE_API_URL ?? "";
const WORKSPACE_STORAGE_KEY = "ledgerlens.workspaceId";
let volatileWorkspaceId;

function createWorkspaceId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function getWorkspaceId() {
  if (volatileWorkspaceId) return volatileWorkspaceId;
  try {
    const stored = globalThis.localStorage.getItem(WORKSPACE_STORAGE_KEY);
    if (stored && /^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i.test(stored)) {
      volatileWorkspaceId = stored;
      return stored;
    }
  } catch {
    // Keep the workspace for this page session if browser storage is unavailable.
  }
  volatileWorkspaceId = createWorkspaceId();
  try {
    globalThis.localStorage.setItem(WORKSPACE_STORAGE_KEY, volatileWorkspaceId);
  } catch {
    // The in-memory identifier still scopes all requests made in this page session.
  }
  return volatileWorkspaceId;
}

export class ApiError extends Error {
  constructor(message, status, errors = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

async function request(path, options = {}, { onSlow, responseType } = {}) {
  let lastError;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 60_000);
    const slowNotice = setTimeout(() => onSlow?.(), 4_000);
    try {
      const response = await fetch(`${BASE_URL}${path}`, {
        ...options,
        signal: controller.signal,
        headers: {
          ...options.headers,
          "X-Workspace-Id": getWorkspaceId(),
        },
      });
      if (!response.ok) {
        let problem = {};
        try {
          problem = await response.json();
        } catch {
          // A non-ProblemDetail response gets a safe status-based message below.
        }
        const message = problem.detail || (
          response.status === 409 ? "Ainda falta enviar os dois arquivos para conciliar." :
            response.status === 422 ? "Confira os dados do arquivo e tente novamente." :
              response.status === 429 ? "Muitas tentativas em pouco tempo. Aguarde um instante e tente novamente." :
                "Não foi possível concluir a solicitação. Tente novamente."
        );
        throw new ApiError(message, response.status, problem.errors ?? []);
      }
      if (responseType === "blob") return response.blob();
      if (response.status === 204) return null;
      return response.json();
    } catch (error) {
      if (error instanceof ApiError) {
        if ((error.status === 502 || error.status === 503) && attempt === 0) {
          lastError = error;
          continue;
        }
        throw error;
      }
      lastError = error;
      if (attempt === 1) {
        throw new ApiError(
          error.name === "AbortError"
            ? "O servidor demorou mais de um minuto para responder. Tente novamente."
            : "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.",
          0,
        );
      }
    } finally {
      clearTimeout(timeout);
      clearTimeout(slowNotice);
    }
  }
  throw lastError;
}

export function uploadCsv(file, sourceType, options) {
  const body = new FormData();
  body.append("file", file);
  return request(`/api/v1/batches/upload?sourceType=${sourceType}`, { method: "POST", body }, options);
}

export function executeReconciliation(options) {
  return request("/api/v1/reconciliations/execute", { method: "POST" }, options);
}

export function fetchLatestReconciliation({ status, page = 0, size = 20 } = {}, options) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  return request(`/api/v1/reconciliations/latest?${params}`, {}, options);
}

export function downloadLatestReconciliation(options) {
  return request("/api/v1/reconciliations/latest/export", {}, {
    ...options,
    responseType: "blob",
  });
}

export function clearWorkspace(options) {
  return request("/api/v1/workspace", { method: "DELETE" }, options);
}
