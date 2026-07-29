import { useLayersStore } from "../../store/layersStore";

function scoreColor(score: number): string {
  if (score > 70) return "text-rose-400";
  if (score > 45) return "text-amber-400";
  if (score > 25) return "text-yellow-400";
  return "text-emerald-400";
}

export function InstabilityLeaderboard() {
  const countryStats = useLayersStore((s) => s.countryStats);

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-slate-800 px-3 py-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
        Country Instability Index
      </div>
      <div className="flex-1 overflow-y-auto">
        {countryStats.length === 0 && (
          <div className="p-3 text-sm text-slate-500">
            Waiting for GDELT event data (refreshes every 15 min)…
          </div>
        )}
        {countryStats.map((c, i) => (
          <div key={c.country_code} className="border-b border-slate-900 px-3 py-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="w-4 text-right text-[11px] text-slate-600">{i + 1}</span>
                <span className="text-sm text-slate-200">{c.country_name}</span>
              </div>
              <span className={`text-sm font-semibold ${scoreColor(c.instability_score)}`}>
                {c.instability_score.toFixed(0)}
              </span>
            </div>
            {c.rationale && (
              <div className="mt-0.5 pl-6 text-[11px] leading-relaxed text-slate-500">
                {c.rationale}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
