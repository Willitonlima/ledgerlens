const STATUS = {
  MATCHED: { label: "Conciliado", classes: "bg-ok-soft text-ok" },
  DIVERGENT: { label: "Divergente", classes: "bg-warn-soft text-warn" },
  MISSING: { label: "Ausente", classes: "bg-bad-soft text-bad" },
};

export default function StatusBadge({ status }) {
  const { label, classes } = STATUS[status] ?? { label: status, classes: "bg-rule text-muted" };
  return <span className={`status-badge ${classes}`}>{label}</span>;
}
