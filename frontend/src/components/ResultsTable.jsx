import { formatCurrency, formatDate } from "../format.js";
import StatusBadge from "./StatusBadge.jsx";

export default function ResultsTable({ items, loading }) {
  if (loading) {
    return (
      <div className="space-y-px">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-12 animate-pulse bg-rule/60" />
        ))}
      </div>
    );
  }

  if (!items?.length) {
    return (
      <p className="px-4 py-10 text-center text-sm text-muted">
        Nenhum resultado ainda. Envie os dois arquivos CSV e execute a conciliação.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[48rem] text-left text-sm">
        <thead className="border-b border-rule text-muted">
          <tr>
            <th className="px-4 py-3 font-medium">Data</th>
            <th className="px-4 py-3 font-medium">Descrição</th>
            <th className="px-4 py-3 text-right font-medium">Valor</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Origem</th>
            <th className="px-4 py-3 font-medium">Observação</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id} className="border-b border-rule/70 last:border-0">
              <td className="tabular whitespace-nowrap px-4 py-3">{formatDate(item.date)}</td>
              <td className="px-4 py-3">{item.description}</td>
              <td className={`tabular whitespace-nowrap px-4 py-3 text-right ${item.amount < 0 ? "text-bad" : ""}`}>
                {formatCurrency(item.amount)}
              </td>
              <td className="px-4 py-3">
                <StatusBadge status={item.status} />
              </td>
              <td className="px-4 py-3">{item.origin}</td>
              <td className="px-4 py-3 text-muted">{item.notes}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
