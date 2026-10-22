// Cliente HTTP da API do LedgerLens. Em dev, "/api" é encaminhado pelo proxy do Vite.
const BASE_URL = import.meta.env.VITE_API_URL ?? "";
const WORKSPACE_STORAGE_KEY = "ledgerlens.workspaceId";

function workspaceId() {
  let id = window.localStorage.getItem(WORKSPACE_STORAGE_KEY);
  if (!id) {
    id = window.crypto.randomUUID();
    window.localStorage.setItem(WORKSPACE_STORAGE_KEY, id);
  }
  return id;
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers: {
        ...options.headers,
        "X-Workspace-Id": workspaceId(),
      },
    });
  } catch {
    throw new Error("Não foi possível conectar ao servidor. Verifique se o backend está rodando em localhost:8080.");
  }

  if (!response.ok) {
    let message = `Erro ${response.status} ao chamar a API.`;
    try {
      const problem = await response.json();
      message = problem.detail || problem.title || message;
    } catch {
      /* corpo sem JSON: mantém a mensagem padrão */
    }
    throw new Error(message);
  }

  return response.json();
}

/** sourceType: "ERP" | "BANK" */
export function uploadCsv(file, sourceType) {
  const body = new FormData();
  body.append("file", file);
  return request(`/api/v1/batches/upload?sourceType=${sourceType}`, { method: "POST", body });
}

export function executeReconciliation() {
  return request("/api/v1/reconciliations/execute", { method: "POST" });
}

export function fetchLatestReconciliation({ status, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  return request(`/api/v1/reconciliations/latest?${params}`);
}

export function latestReconciliationExportUrl() {
  return `${BASE_URL}/api/v1/reconciliations/latest/export`;
}
