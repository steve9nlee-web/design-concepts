import { useNewsStore } from "../../store/newsStore";

export function NewsFeedPanel() {
  const articles = useNewsStore((s) => s.articles);

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-slate-800 px-3 py-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
        Live News ({articles.length})
      </div>
      <div className="flex-1 overflow-y-auto">
        {articles.length === 0 && (
          <div className="p-3 text-sm text-slate-500">Waiting for news feeds…</div>
        )}
        {articles.map((a) => (
          <a
            key={a.id}
            href={a.link}
            target="_blank"
            rel="noreferrer"
            className="block border-b border-slate-900 px-3 py-2 hover:bg-slate-900/60"
          >
            <div className="flex items-center gap-2 text-[11px] text-slate-500">
              <span>{a.source}</span>
              {a.country_name && (
                <span className="rounded bg-slate-800 px-1.5 py-0.5 text-slate-300">
                  {a.country_name}
                </span>
              )}
              {a.cluster_id && (
                <span className="rounded bg-sky-900/60 px-1.5 py-0.5 text-sky-300">
                  clustered
                </span>
              )}
            </div>
            <div className="mt-0.5 text-sm leading-snug text-slate-200">{a.title}</div>
          </a>
        ))}
      </div>
    </div>
  );
}
