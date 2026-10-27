import { useCallback, useEffect, useRef, useState } from "react";
import {
  clearWorkspace,
  downloadLatestReconciliation,
  executeReconciliation,
  fetchLatestReconciliation,
  uploadCsv,
} from "./api/client.js";
import { formatCurrency } from "./utils/format.js";
import { sampleUrl } from "./utils/samples.js";
import Dropzone from "./components/Dropzone.jsx";
import MetricCard from "./components/MetricCard.jsx";
import ResultsTable from "./components/ResultsTable.jsx";
import StatusBadge from "./components/StatusBadge.jsx";
import useServerWakeup from "./hooks/useServerWakeup.js";

const EMPTY_UPLOAD = { status: "idle" };
const STATUS_FILTERS = [
  ["", "Todos os status"],
  ["MATCHED", "Conciliados"],
  ["DIVERGENT", "Divergências"],
  ["MISSING", "Ausentes"],
];

function Step({ number, title, state, detail }) {
  return (
    <li aria-current={state === "current" ? "step" : undefined} className={`flow-step ${state}`}>
      <span aria-hidden="true" className="step-number">{state === "complete" ? "✓" : number}</span>
      <span className="step-content">
        <strong>{title}</strong>
        <span>{detail}</span>
      </span>
    </li>
  );
}

function retryAction(error, action) {
  return error.retryable === false ? undefined : action;
}

export default function App() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [executing, setExecuting] = useState(false);
  const [uploadingExamples, setUploadingExamples] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [uploads, setUploads] = useState({ ERP: EMPTY_UPLOAD, BANK: EMPTY_UPLOAD });
  const { serverWaking, showWakeupMessage, clearWakeupMessage } = useServerWakeup();
  const statusRef = useRef(status);

  const loadLatest = useCallback(async (requestedPage = 0, requestedStatus = statusRef.current) => {
    setLoading(true);
    setError(null);
    clearWakeupMessage();
    try {
      const latest = await fetchLatestReconciliation(
        { page: requestedPage, status: requestedStatus || undefined },
        { onSlow: showWakeupMessage },
      );
      clearWakeupMessage();
      setSummary(latest);
      setPage(latest.page);
    } catch (err) {
      clearWakeupMessage();
      setError({ message: err.message, action: retryAction(err, () => loadLatest(requestedPage, requestedStatus)) });
    } finally {
      setLoading(false);
    }
  }, [clearWakeupMessage, showWakeupMessage]);

  useEffect(() => {
    loadLatest(0, "");
  }, [loadLatest]);

  async function sendUpload(sourceType, file) {
    setUploads((current) => ({
      ...current,
      [sourceType]: { status: "uploading", fileName: file.name },
    }));
    setError(null);
    clearWakeupMessage();
    try {
      const receipt = await uploadCsv(file, sourceType, { onSlow: showWakeupMessage });
      clearWakeupMessage();
      setUploads((current) => ({
        ...current,
        [sourceType]: {
          status: "done",
          fileName: receipt.fileName || file.name,
          recordsImported: receipt.recordsImported,
        },
      }));
      return true;
    } catch (err) {
      clearWakeupMessage();
      setUploads((current) => ({
        ...current,
        [sourceType]: {
          status: "error",
          fileName: file.name,
          message: err.message,
          errors: err.errors ?? [],
        },
      }));
      setError({ message: err.message, action: retryAction(err, () => sendUpload(sourceType, file)) });
      return false;
    }
  }

  async function handleExecute() {
    setExecuting(true);
    setError(null);
    clearWakeupMessage();
    try {
      await executeReconciliation({ onSlow: showWakeupMessage });
      clearWakeupMessage();
      await loadLatest(0, status);
    } catch (err) {
      clearWakeupMessage();
      setError({ message: err.message, action: retryAction(err, handleExecute) });
    } finally {
      setExecuting(false);
    }
  }

  async function handleSampleData() {
    setUploadingExamples(true);
    setError(null);
    clearWakeupMessage();
    try {
      const sampleFiles = await Promise.all(
        ["ERP", "BANK"].map(async (sourceType) => {
          const response = await fetch(sampleUrl(sourceType));
          if (!response.ok) throw new Error("Não foi possível carregar os arquivos de exemplo.");
          const fileName = sourceType === "ERP" ? "erp.csv" : "banco.csv";
          return [sourceType, new File([await response.text()], fileName, { type: "text/csv" })];
        }),
      );
      for (const [sourceType, file] of sampleFiles) {
        const uploaded = await sendUpload(sourceType, file);
        if (!uploaded) return;
      }
      await handleExecute();
    } catch (err) {
      clearWakeupMessage();
      setError({ message: err.message, action: handleSampleData });
    } finally {
      setUploadingExamples(false);
    }
  }

  async function handleExport() {
    setExporting(true);
    setError(null);
    try {
      const blob = await downloadLatestReconciliation();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "conciliacao.csv";
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      setError({ message: err.message, action: retryAction(err, handleExport) });
    } finally {
      setExporting(false);
    }
  }

  async function handleClearWorkspace() {
    if (!window.confirm("Tem certeza de que deseja apagar todos os dados deste navegador? Esta ação não pode ser desfeita.")) {
      return;
    }
    setClearing(true);
    setError(null);
    try {
      await clearWorkspace({ onSlow: showWakeupMessage });
      clearWakeupMessage();
      setUploads({ ERP: EMPTY_UPLOAD, BANK: EMPTY_UPLOAD });
      setSummary(null);
      await loadLatest(0, status);
    } catch (err) {
      clearWakeupMessage();
      setError({ message: err.message, action: retryAction(err, handleClearWorkspace) });
    } finally {
      setClearing(false);
    }
  }

  const hasErp = uploads.ERP.status === "done";
  const hasBank = uploads.BANK.status === "done";
  const canReconcile = hasErp && hasBank && !executing && !uploadingExamples;
  const hasResults = Boolean(summary?.executedAt);
  const activeStep = hasResults ? 3 : canReconcile ? 2 : 1;
  const pageCount = summary?.totalPages ?? 0;

  return (
    <main className="app-shell">
      <header className="site-header">
        <a aria-label="LedgerLens, início" className="brand" href="/">
          <span aria-hidden="true" className="brand-mark">L</span>
          <span>
            <span className="brand-name">LedgerLens</span>
            <span className="brand-subtitle">Conciliação financeira, com clareza.</span>
          </span>
        </a>
        <button className="button button-quiet clear-button" disabled={clearing} onClick={handleClearWorkspace} type="button">
          {clearing ? "Limpando…" : "Limpar meus dados"}
        </button>
      </header>

      <section aria-label="Aviso de privacidade" className="privacy-note">
        <span aria-hidden="true">i</span>
        <p>Modo demonstração. Não envie dados reais; seus dados ficam isolados neste navegador e são apagados em até 24 horas.</p>
      </section>

      <section aria-labelledby="welcome-title" className="intro-section">
        <p className="eyebrow">MENOS CONFERÊNCIA. MAIS CONFIANÇA.</p>
        <h1 id="welcome-title">Seus números, em <em>harmonia.</em></h1>
        <p className="intro-copy">Compare lançamentos do ERP com o extrato bancário e encontre diferenças em poucos passos.</p>
      </section>

      <section aria-label="Etapas da conciliação" className="workflow">
        <ol className="flow-steps">
          <Step
            detail={hasErp && hasBank
              ? "Dois arquivos recebidos"
              : hasResults ? "Arquivos já conciliados" : "Envie o ERP e o extrato"}
            number="1"
            state={activeStep > 1 ? "complete" : "current"}
            title="Enviar arquivos"
          />
          <Step
            detail={hasResults ? "Conciliação concluída" : canReconcile ? "Pronto para comparar" : "Aguardando os arquivos"}
            number="2"
            state={activeStep > 2 ? "complete" : activeStep === 2 ? "current" : "pending"}
            title="Conciliar"
          />
          <Step
            detail={hasResults
              ? `${summary.totalItems} ${summary.totalItems === 1 ? "lançamento revisado" : "lançamentos revisados"}`
              : "Confira os resultados"}
            number="3"
            state={hasResults ? "current" : "pending"}
            title="Revisar resultados"
          />
        </ol>
      </section>

      <section aria-label="Importar arquivos" className="panel upload-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">PASSO 1</p>
            <h2>Comece pelos seus arquivos</h2>
          </div>
          <button
            className="button button-outline"
            disabled={uploadingExamples || executing}
            onClick={handleSampleData}
            type="button"
          >
            {uploadingExamples ? "Carregando exemplos…" : "Usar dados de exemplo"}
          </button>
        </div>
        <div className="upload-grid">
          <Dropzone
            description="CSV exportado do seu sistema de gestão"
            onFile={(file) => sendUpload("ERP", file)}
            state={uploads.ERP}
            title="Arquivo do ERP"
          />
          <Dropzone
            description="CSV exportado pelo banco"
            onFile={(file) => sendUpload("BANK", file)}
            state={uploads.BANK}
            title="Extrato bancário"
          />
        </div>
        <div className="sample-downloads">
          <span>Ou baixe os exemplos:</span>
          <a href={sampleUrl("ERP")} download="erp.csv">Arquivo ERP</a>
          <a href={sampleUrl("BANK")} download="banco.csv">Extrato bancário</a>
        </div>
      </section>

      <section aria-label="Métricas da conciliação" className="metrics-grid">
        <MetricCard hint="Lançamentos no ERP" label="Total importado ERP" loading={loading} value={formatCurrency(summary?.totalErpAmount)} />
        <MetricCard hint="Lançamentos no extrato" label="Total banco" loading={loading} value={formatCurrency(summary?.totalBankAmount)} />
        <MetricCard hint="Valor e data diferentes" label="Divergências" loading={loading} tone="warn" value={summary?.divergentCount ?? 0} />
        <MetricCard hint="Presentes em apenas um arquivo" label="Lançamentos ausentes" loading={loading} tone="bad" value={summary?.missingCount ?? 0} />
        <MetricCard hint="Encontrados nos dois arquivos" label="Conciliados" loading={loading} tone="good" value={summary?.matchedCount ?? 0} />
      </section>

      <section aria-label="Revisão dos resultados" className="panel results-panel">
        <div className="section-heading result-heading">
          <div>
            <p className="eyebrow">PASSO {hasResults ? "3" : "2"}</p>
            <h2>Visão geral da conciliação</h2>
          </div>
          <div className="result-actions">
            <button
              className="button button-outline"
              disabled={!hasResults || exporting}
              onClick={handleExport}
              type="button"
            >
              {exporting ? "Preparando…" : "Exportar CSV"}
            </button>
            <button
              className="button button-primary"
              disabled={!canReconcile}
              onClick={handleExecute}
              title={!canReconcile ? "Envie os arquivos do ERP e do banco para habilitar a conciliação." : undefined}
              type="button"
            >
              {executing ? "Conciliando…" : "Conciliar lançamentos"}
            </button>
          </div>
        </div>
        {!canReconcile && !hasResults && (
          <p className="inline-hint" id="reconcile-hint">
            {hasErp || hasBank
              ? "Envie também o arquivo que falta para iniciar a conciliação."
              : "O botão de conciliação será habilitado depois que os dois arquivos forem enviados."}
          </p>
        )}

        {serverWaking && (
          <p aria-live="polite" className="server-wakeup">
            Acordando o servidor, isso pode levar até um minuto…
          </p>
        )}
        {error && (
          <div aria-live="assertive" className="error-message" role="alert">
            <span>{error.message}</span>
            {error.action && (
              <button className="text-button" onClick={error.action} type="button">
                Tentar de novo
              </button>
            )}
          </div>
        )}

        <div className="results-toolbar">
          <label htmlFor="status-filter">Filtrar resultados</label>
          <select
            id="status-filter"
            onChange={(event) => {
              statusRef.current = event.target.value;
              setStatus(event.target.value);
              loadLatest(0, event.target.value);
            }}
            value={status}
          >
            {STATUS_FILTERS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <ResultsTable items={summary?.items} loading={loading} />
        <div className="pagination">
          <p aria-live="polite">
            {summary?.totalItems ?? 0} {summary?.totalItems === 1 ? "resultado" : "resultados"}
            {pageCount > 0 && ` · página ${page + 1} de ${pageCount}`}
          </p>
          <div className="pagination-buttons">
            <button className="button button-small" disabled={loading || page === 0} onClick={() => loadLatest(page - 1)} type="button">
              Anterior
            </button>
            <button className="button button-small" disabled={loading || page + 1 >= pageCount} onClick={() => loadLatest(page + 1)} type="button">
              Próxima
            </button>
          </div>
        </div>
      </section>

      <footer className="site-footer">
        <span>LedgerLens · Conciliação financeira simples e transparente.</span>
        <span className="footer-status"><StatusBadge status="MATCHED" /> seus dados permanecem neste espaço de trabalho</span>
      </footer>
    </main>
  );
}
