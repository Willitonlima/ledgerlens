const TONES = {
  neutral: "border-t-accent",
  warn: "border-t-warn",
  bad: "border-t-bad",
};

export default function MetricCard({ label, value, hint, tone = "neutral", loading }) {
  return (
    <div className={`rounded-md border border-rule border-t-4 bg-surface px-5 py-4 ${TONES[tone]}`}>
      <p className="text-sm text-muted">{label}</p>
      {loading ? (
        <div className="mt-2 h-8 w-32 animate-pulse rounded bg-rule" />
      ) : (
        <p className="tabular mt-1 font-serif text-3xl font-semibold">{value}</p>
      )}
      {hint && !loading && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}
