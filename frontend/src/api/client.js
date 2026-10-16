// Cliente HTTP da API do LedgerLens. Em dev, "/api" é encaminhado pelo proxy do Vite.
const BASE_URL = import.meta.env.VITE_API_URL ?? "";

async function request(path, options) {
  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, options);
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

export function fetchLatestReconciliation() {
  return request("/api/v1/reconciliations/latest");
}
