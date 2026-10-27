import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("identificação do workspace", () => {
  it("creates and persists an UUID v4 for the visitor", async () => {
    vi.resetModules();
    const { getWorkspaceId } = await import("./client.js");
    const id = getWorkspaceId();

    expect(id).toMatch(/^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i);
    expect(localStorage.getItem("ledgerlens.workspaceId")).toBe(id);
    expect(getWorkspaceId()).toBe(id);
  });

  it("keeps requests scoped when localStorage is unavailable", async () => {
    vi.resetModules();
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("Storage indisponível", "SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("Storage indisponível", "SecurityError");
    });
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ items: [] }),
    });
    vi.stubGlobal("fetch", fetch);
    const { fetchLatestReconciliation } = await import("./client.js");

    await fetchLatestReconciliation();

    const workspaceHeader = fetch.mock.calls[0][1].headers["X-Workspace-Id"];
    expect(workspaceHeader).toMatch(/^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i);
  });

  it("automatically retries a waking server once after a transient 503", async () => {
    vi.resetModules();
    const fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 503,
        json: async () => ({ detail: "Servidor inicializando." }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ items: [] }),
      });
    vi.stubGlobal("fetch", fetch);
    const { fetchLatestReconciliation } = await import("./client.js");

    await expect(fetchLatestReconciliation()).resolves.toEqual({ items: [] });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("explains when the configured API origin has no matching route and does not retry", async () => {
    vi.resetModules();
    const fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: async () => {
        throw new SyntaxError("Resposta não é JSON");
      },
    });
    vi.stubGlobal("fetch", fetch);
    const { fetchLatestReconciliation } = await import("./client.js");

    await expect(fetchLatestReconciliation()).rejects.toMatchObject({
      message: "A API não foi encontrada no endereço configurado. Confira VITE_API_URL e se o backend está publicado.",
      retryable: false,
      status: 404,
    });
    expect(fetch).toHaveBeenCalledOnce();
    expect(fetch.mock.calls[0][0]).toMatch(/\/api\/v1\/reconciliations\/latest\?page=0&size=20$/);
  });

  it("includes the HTTP status when the server returns an unexpected error response", async () => {
    vi.resetModules();
    const fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    });
    vi.stubGlobal("fetch", fetch);
    const { fetchLatestReconciliation } = await import("./client.js");

    await expect(fetchLatestReconciliation()).rejects.toMatchObject({
      message: "Não foi possível concluir a solicitação (HTTP 500). Confira se o backend está ativo.",
      status: 500,
    });
    expect(fetch).toHaveBeenCalledOnce();
  });
});
