import { useSettingsStore } from "../../store/settingsStore";
import { useLayersStore } from "../../store/layersStore";
import type { LayerId } from "../../lib/types";

const LAYER_LABELS: Record<LayerId, string> = {
  news: "News events",
  flights: "Live flights",
  quakes: "Earthquakes",
  instability: "Instability index",
};

export function SettingsPanel({ onClose }: { onClose: () => void }) {
  const { settings, ollamaModels, ollamaAvailable, saveSettings } = useSettingsStore();
  const { enabledLayers, toggleLayer } = useLayersStore();

  return (
    <div className="absolute inset-y-0 right-0 z-20 w-80 border-l border-slate-800 bg-slate-950/95 p-4 backdrop-blur">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-200">Settings</h2>
        <button onClick={onClose} className="text-slate-500 hover:text-slate-300">
          ✕
        </button>
      </div>

      <section className="mt-4">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Local AI (Ollama)
        </h3>
        <div className="mt-2 flex items-center gap-2 text-xs">
          <span
            className={`h-2 w-2 rounded-full ${ollamaAvailable ? "bg-emerald-400" : "bg-rose-500"}`}
          />
          <span className="text-slate-400">
            {ollamaAvailable ? "Connected to localhost:11434" : "Ollama not reachable"}
          </span>
        </div>
        {settings && (
          <select
            className="mt-2 w-full rounded border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-slate-200"
            value={settings.ollama_model}
            onChange={(e) => saveSettings({ ...settings, ollama_model: e.target.value })}
          >
            {!ollamaModels.includes(settings.ollama_model) && (
              <option value={settings.ollama_model}>{settings.ollama_model}</option>
            )}
            {ollamaModels.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        )}
      </section>

      <section className="mt-6">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Globe layers
        </h3>
        <div className="mt-2 space-y-2">
          {(Object.keys(LAYER_LABELS) as LayerId[]).map((id) => (
            <label key={id} className="flex items-center justify-between text-sm text-slate-300">
              {LAYER_LABELS[id]}
              <input
                type="checkbox"
                checked={enabledLayers[id]}
                onChange={() => toggleLayer(id)}
                className="h-4 w-4 accent-sky-500"
              />
            </label>
          ))}
        </div>
      </section>
    </div>
  );
}
