import { useNewsStore } from "../../store/newsStore";

export function CorrelationPanel() {
  const clusters = useNewsStore((s) => s.clusters);

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-slate-800 px-3 py-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
        AI Correlation ({clusters.length})
      </div>
      <div className="flex-1 overflow-y-auto">
        {clusters.length === 0 && (
          <div className="p-3 text-sm text-slate-500">
            No story clusters yet — the local AI groups related articles every few minutes.
          </div>
        )}
        {clusters.map((c) => (
          <div key={c.id} className="border-b border-slate-900 px-3 py-2.5">
            <div className="text-sm font-medium text-slate-100">{c.title}</div>
            <div className="mt-1 text-xs leading-relaxed text-slate-400">{c.summary}</div>
            {c.rationale && (
              <div className="mt-1 text-[11px] italic text-sky-400/80">{c.rationale}</div>
            )}
            <div className="mt-1 text-[11px] text-slate-600">
              {c.article_ids.length} linked article{c.article_ids.length === 1 ? "" : "s"}
              {c.related_cluster_ids.length > 0 &&
                ` · related to ${c.related_cluster_ids.length} other stor${c.related_cluster_ids.length === 1 ? "y" : "ies"}`}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
