import { useState } from "react";
import { GlobeView } from "../Globe/GlobeView";
import { NewsFeedPanel } from "../Panels/NewsFeedPanel";
import { CorrelationPanel } from "../Panels/CorrelationPanel";
import { MarketsTicker } from "../Panels/MarketsTicker";
import { InstabilityLeaderboard } from "../Panels/InstabilityLeaderboard";
import { SettingsPanel } from "../Panels/SettingsPanel";
import { useSettingsStore } from "../../store/settingsStore";

export function CommandCenterLayout() {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const ollamaAvailable = useSettingsStore((s) => s.ollamaAvailable);

  return (
    <div className="relative flex h-screen w-screen flex-col overflow-hidden bg-[#05070d]">
      <header className="flex h-11 shrink-0 items-center justify-between border-b border-slate-800 px-4">
        <div className="flex items-center gap-2">
          <span className="text-sm font-bold tracking-wide text-slate-100">WORLD MONITOR</span>
          <span className="text-[10px] text-slate-600">local · offline-first</span>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
            <span
              className={`h-1.5 w-1.5 rounded-full ${ollamaAvailable ? "bg-emerald-400" : "bg-rose-500"}`}
            />
            {ollamaAvailable ? "AI online" : "AI offline"}
          </div>
          <button
            onClick={() => setSettingsOpen((v) => !v)}
            className="rounded border border-slate-700 px-2 py-1 text-xs text-slate-300 hover:bg-slate-800"
          >
            Settings
          </button>
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className="w-80 shrink-0 border-r border-slate-800 bg-slate-950/40">
          <NewsFeedPanel />
        </aside>

        <main className="relative min-w-0 flex-1">
          <GlobeView />
        </main>

        <aside className="flex w-80 shrink-0 flex-col border-l border-slate-800 bg-slate-950/40">
          <div className="h-1/2 min-h-0 border-b border-slate-800">
            <CorrelationPanel />
          </div>
          <div className="h-1/2 min-h-0">
            <InstabilityLeaderboard />
          </div>
        </aside>
      </div>

      <footer className="h-10 shrink-0 border-t border-slate-800 bg-slate-950/60">
        <MarketsTicker />
      </footer>

      {settingsOpen && <SettingsPanel onClose={() => setSettingsOpen(false)} />}
    </div>
  );
}
