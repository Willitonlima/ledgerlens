const TONES = {
  neutral: "border-t-accent",
  good: "border-t-ok",
  warn: "border-t-warn",
  bad: "border-t-bad",
};

export default function MetricCard({ label, value, hint, tone = "neutral", loading }) {
  return (
    <article className={`metric-card ${TONES[tone]}`}>
      <p className="metric-label">{label}</p>
      {loading ? (
        <div aria-label={`Carregando: ${label}`} className="metric-skeleton" role="status" />
      ) : (
        <p className="metric-value tabular">{value}</p>
      )}
      {hint && !loading && <p className="metric-hint">{hint}</p>}
    </article>
  );
}
