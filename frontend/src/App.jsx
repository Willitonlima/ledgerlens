import { useCallback, useEffect, useState } from "react";
import {
  executeReconciliation,
  fetchLatestReconciliation,
  latestReconciliationExportUrl,
  uploadCsv,
} from "./api/client.js";
import { formatCurrency } from "./format.js";
import Dropzone from "./components/Dropzone.jsx";
import MetricCard from "./components/MetricCard.jsx";
import ResultsTable from "./components/ResultsTable.jsx";

const IDLE = { status: "idle" };

export default function App() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [executing, setExecuting] = useState(false);
  const [page, setPage] = useState(0);
  const [uploads, setUploads] = useState({ ERP: IDLE, BANK: IDLE });

  const loadLatest = useCallback(async (requestedPage = 0) => {
    setLoading(true);
    setError(null);
    try {
      const latest = await fetchLatestReconciliation({ page: requestedPage });
      setSummary(latest);
      setPage(latest.page);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadLatest();
  }, [loadLatest]);

  async function handleUpload(sourceType, file) {
    setUploads((prev) => ({ ...prev, [sourceType]: { status: "uploading", fileName: file.name } }));
    try {
      const receipt = await uploadCsv(file, sourceType);
      setUploads((prev) => ({
        ...prev,
        [sourceType]: {
          status: "done",
          fileName: file.name,
          message: receipt.message ?? `${receipt.recordsImported} lançamentos importados.`,
        },
      }));
    } catch (err) {
      setUploads((prev) => ({
        ...prev,
        [sourceType]: { status: "error", fileName: file.name, message: err.message },
      }));
    }
  }

  async function handleExecute() {
    setExecuting(true);
    setError(null);
    try {
      await executeReconciliation();
      await loadLatest(0);
    } catch (err) {
      setError(err.message);
    } finally {
      setExecuting(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl px-4 pb-16 pt-8 sm:px-6">
      <header className="mb-8">
        <h1 className="font-serif text-3xl font-semibold text-accent-strong">LedgerLens</h1>
        <p className="text-sm text-muted">Conciliação entre o ERP e o extrato bancário</p>
      </header>

      <section aria-label="Resumo" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <MetricCard label="Total importado ERP" value={formatCurrency(summary?.totalErpAmount)} loading={loading} />
        <MetricCard label="Total banco" value={formatCurrency(summary?.totalBankAmount)} loading={loading} />
        <MetricCard label="Conciliados" value={summary?.matchedCount ?? 0} loading={loading} />
        <MetricCard label="Divergências" value={summary?.divergentCount ?? 0} tone="warn" loading={loading} />
        <MetricCard label="Lançamentos ausentes" value={summary?.missingCount ?? 0} tone="bad" loading={loading} />
      </section>

      <section aria-label="Importação" className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2">
        <Dropzone
          title="Extrato bancário"
          description="CSV exportado pelo banco"
          state={uploads.BANK}
          onFile={(file) => handleUpload("BANK", file)}
        />
        <Dropzone
          title="ERP interno"
          description="CSV exportado do sistema de gestão"
          state={uploads.ERP}
          onFile={(file) => handleUpload("ERP", file)}
        />
      </section>

      <div className="mt-6 flex items-center justify-between gap-4">
        <h2 className="text-lg font-semibold">Resultado da conciliação</h2>
        <div className="flex items-center gap-3">
          <a
            href={latestReconciliationExportUrl()}
            className="text-sm font-semibold text-accent-strong underline"
          >
            Exportar CSV
          </a>
          <button
            type="button"
            onClick={handleExecute}
            disabled={executing}
            className="rounded-md bg-accent px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-accent-strong disabled:cursor-wait disabled:opacity-60"
          >
            {executing ? "Executando…" : "Executar conciliação"}
          </button>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-bad/30 bg-bad-soft px-4 py-3 text-sm text-bad"
        >
          <span>{error}</span>
          <button type="button" onClick={loadLatest} className="font-semibold underline">
            Tentar novamente
          </button>
        </div>
      )}

      <section aria-label="Resultados" className="mt-4 overflow-hidden rounded-md border border-rule bg-surface">
        <ResultsTable items={summary?.items} loading={loading} />
      </section>
      <div className="mt-3 flex items-center justify-between text-sm text-muted">
        <span>
          {summary?.totalItems ?? 0} resultados · página{" "}
          {summary?.totalPages ? (summary.page ?? 0) + 1 : 0} de {summary?.totalPages ?? 0}
        </span>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => loadLatest(page - 1)}
            disabled={loading || page === 0}
            className="rounded border border-rule px-3 py-1 disabled:opacity-50"
          >
            Anterior
          </button>
          <button
            type="button"
            onClick={() => loadLatest(page + 1)}
            disabled={loading || page + 1 >= (summary?.totalPages ?? 0)}
            className="rounded border border-rule px-3 py-1 disabled:opacity-50"
          >
            Próxima
          </button>
        </div>
      </div>
    </div>
  );
}
