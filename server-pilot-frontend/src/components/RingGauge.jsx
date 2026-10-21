/* SVG ring gauge - used in Dashboard and Monitoring. */
export default function RingGauge({ value, max = 100, size = 56, color = "#3b82f6", label }) {
  const raw = max ? (value / max) * 100 : value;
  const pct = Number.isFinite(raw) ? Math.max(0, Math.min(100, Math.round(raw))) : 0;
  const r = (size - 8) / 2;
  const circ = 2 * Math.PI * r;
  const dash = (pct / 100) * circ;
  const stroke = pct >= 85 ? "#ef4444" : pct >= 60 ? "#f59e0b" : color;

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90 absolute inset-0">
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth={5} />
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={stroke} strokeWidth={5}
            strokeLinecap="round" strokeDasharray={`${dash} ${circ}`}
            style={{ transition: "stroke-dasharray 0.5s ease" }} />
        </svg>
        {/* Centred percentage */}
        <div className="absolute inset-0 flex items-center justify-center text-xs font-semibold text-white">
          {pct}%
        </div>
      </div>
      {label && <span className="text-xs text-white/50">{label}</span>}
    </div>
  );
}