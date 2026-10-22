import { formatCurrency, formatDate } from "../utils/format.js";
import StatusBadge from "./StatusBadge.jsx";

const amountOrDash = (value) => value == null ? "—" : formatCurrency(value);
const originLabels = { ERP: "ERP", BANK: "Banco", BOTH: "ERP e banco" };
const formatOrigin = (origin) => originLabels[origin] ?? origin ?? "—";

export default function ResultsTable({ items, loading }) {
  if (loading) {
    return (
      <div aria-label="Carregando resultados" aria-live="polite" className="result-skeletons">
        {Array.from({ length: 5 }, (_, index) => <div className="skeleton-row" key={index} />)}
      </div>
    );
  }

  if (!items?.length) {
    return (
      <div className="empty-state">
        <span aria-hidden="true" className="empty-icon">↗</span>
        <h3>Nenhum resultado nesta página</h3>
        <p>Envie os arquivos do ERP e do banco. Depois, concilie para revisar os lançamentos.</p>
      </div>
    );
  }

  return (
    <div className="results-view">
      <div className="desktop-results">
        <table className="results-table">
          <thead>
            <tr>
              {["Data", "Descrição", "Valor", "Origem", "Status", "Diferença", "Observação"].map((label) => (
                <th key={label} scope="col">{label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td className="tabular">{formatDate(item.date) || "—"}</td>
                <td className="result-description">{item.description || "—"}</td>
                <td className="tabular">{amountOrDash(item.amount)}</td>
                <td>{formatOrigin(item.origin)}</td>
                <td><StatusBadge status={item.status} /></td>
                <td className="tabular">{amountOrDash(item.differenceAmount)}</td>
                <td className="result-notes">{item.notes || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul aria-label="Resultados da conciliação" className="mobile-results">
        {items.map((item) => (
          <li className="result-card" key={item.id}>
            <div className="result-card-heading">
              <span className="tabular">{formatDate(item.date) || "—"}</span>
              <StatusBadge status={item.status} />
            </div>
            <h3>{item.description || "—"}</h3>
            <dl>
              <div><dt>Valor</dt><dd className="tabular">{amountOrDash(item.amount)}</dd></div>
              <div><dt>Origem</dt><dd>{formatOrigin(item.origin)}</dd></div>
              <div><dt>Diferença</dt><dd className="tabular">{amountOrDash(item.differenceAmount)}</dd></div>
              <div><dt>Observação</dt><dd>{item.notes || "—"}</dd></div>
            </dl>
          </li>
        ))}
      </ul>
    </div>
  );
}
