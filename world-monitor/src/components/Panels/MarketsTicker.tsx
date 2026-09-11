import { useMarketsStore } from "../../store/marketsStore";

export function MarketsTicker() {
  const quotes = useMarketsStore((s) => s.quotes);

  if (quotes.length === 0) {
    return (
      <div className="flex h-full items-center px-3 text-xs text-slate-500">
        Waiting for market data…
      </div>
    );
  }

  return (
    <div className="flex h-full items-center gap-6 overflow-x-auto px-3">
      {quotes.map((q) => (
        <div key={q.symbol} className="flex shrink-0 items-baseline gap-2">
          <span className="text-xs font-semibold text-slate-300">{q.name}</span>
          <span className="text-sm text-slate-100">
            {q.price.toLocaleString(undefined, { maximumFractionDigits: 2 })}
          </span>
          <span
            className={
              q.change_pct_24h >= 0
                ? "text-xs font-medium text-emerald-400"
                : "text-xs font-medium text-rose-400"
            }
          >
            {q.change_pct_24h >= 0 ? "▲" : "▼"} {Math.abs(q.change_pct_24h).toFixed(2)}%
          </span>
        </div>
      ))}
    </div>
  );
}
