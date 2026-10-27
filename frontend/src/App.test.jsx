import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.jsx";
import { ApiError } from "./api/client.js";

vi.mock("./api/client.js", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    clearWorkspace: vi.fn(),
    downloadLatestReconciliation: vi.fn(),
    executeReconciliation: vi.fn(),
    fetchLatestReconciliation: vi.fn(),
    uploadCsv: vi.fn(),
  };
});

import {
  clearWorkspace,
  downloadLatestReconciliation,
  executeReconciliation,
  fetchLatestReconciliation,
  uploadCsv,
} from "./api/client.js";

const emptySummary = {
  executedAt: null,
  totalErpAmount: 0,
  totalBankAmount: 0,
  divergentCount: 0,
  missingCount: 0,
  matchedCount: 0,
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

describe("conciliação da tela principal", () => {
  beforeEach(() => {
    fetchLatestReconciliation.mockResolvedValue(emptySummary);
    clearWorkspace.mockReset();
    downloadLatestReconciliation.mockReset();
    executeReconciliation.mockReset();
    fetchLatestReconciliation.mockClear();
    uploadCsv.mockReset();
  });

  it("shows Portuguese API validation errors and line details", async () => {
    uploadCsv.mockRejectedValue(new ApiError(
      "O arquivo contém lançamentos inválidos.",
      422,
      [{ line: 4, field: "valor", message: "O valor é obrigatório." }],
    ));
    render(<App />);
    await screen.findByText("LedgerLens");

    const file = new File(["data,descricao,valor"], "erp.csv", { type: "text/csv" });
    fireEvent.change(screen.getByLabelText("Arquivo do ERP"), {
      target: { files: [file] },
    });

    expect(await screen.findByRole("alert")).toHaveTextContent("O arquivo contém lançamentos inválidos.");
    expect(await screen.findByText("Linha 4, valor: O valor é obrigatório.")).toBeInTheDocument();
  });

  it("keeps the reconciliation action disabled until both files are uploaded", async () => {
    render(<App />);
    await screen.findByText("LedgerLens");

    const button = screen.getByRole("button", { name: "Conciliar lançamentos" });
    expect(button).toBeDisabled();
    expect(screen.getByText(/Envie os arquivos do ERP e do banco/)).toBeInTheDocument();
  });

  it("allows retrying a failed result request", async () => {
    fetchLatestReconciliation
      .mockRejectedValueOnce(new ApiError("O serviço está temporariamente indisponível.", 500))
      .mockResolvedValueOnce(emptySummary);
    render(<App />);
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fireEvent.click(retry);

    await waitFor(() => expect(fetchLatestReconciliation).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/Envie os arquivos do ERP e do banco/)).toBeInTheDocument();
  });

  it("does not offer a futile retry when the API origin returns 404", async () => {
    fetchLatestReconciliation.mockRejectedValueOnce(new ApiError(
      "A API não foi encontrada no endereço configurado. Confira VITE_API_URL e se o backend está publicado.",
      404,
      [],
      false,
    ));
    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A API não foi encontrada no endereço configurado. Confira VITE_API_URL e se o backend está publicado.",
    );
    expect(screen.queryByRole("button", { name: "Tentar de novo" })).not.toBeInTheDocument();
  });

  it("uploads both example CSVs and runs them through reconciliation", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      text: async () => "data,descricao,valor\n2025-01-02,Exemplo,10.00\n",
    }));
    uploadCsv.mockResolvedValue({ fileName: "sample.csv", recordsImported: 1 });
    executeReconciliation.mockResolvedValue({});
    fetchLatestReconciliation
      .mockResolvedValueOnce(emptySummary)
      .mockResolvedValueOnce({ ...emptySummary, executedAt: "2025-01-02T00:00:00Z" });
    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: "Usar dados de exemplo" }));

    await screen.findByText("Conciliação concluída");
    expect(uploadCsv).toHaveBeenCalledTimes(2);
    expect(uploadCsv.mock.calls.map(([, sourceType]) => sourceType)).toEqual(["ERP", "BANK"]);
    expect(executeReconciliation).toHaveBeenCalledOnce();
  });
});
