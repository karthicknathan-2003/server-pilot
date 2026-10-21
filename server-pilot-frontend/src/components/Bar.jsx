/* Horizontal usage bar - colour shifts at 60% and 85%. */
export default function Bar({ value, max = 100 }) {
  const raw = max ? (value / max) * 100 : value;
  const pct = Number.isFinite(raw) ? Math.max(0, Math.min(100, Math.round(raw))) : 0;
  const colour = pct >= 85 ? "bg-red-500" : pct >= 60 ? "bg-amber-400" : "bg-blue-500";
  return (
    <div className="h-1 w-full rounded-full bg-white/10">
      <div className={`h-1 rounded-full transition-all duration-500 ${colour}`} style={{ width: `${pct}%` }} />
    </div>
  );
}