import { CommandCenterLayout } from "./components/Layout/CommandCenterLayout";
import { usePolling } from "./lib/usePolling";
import { useNewsStore } from "./store/newsStore";
import { useMarketsStore } from "./store/marketsStore";
import { useLayersStore } from "./store/layersStore";
import { useSettingsStore } from "./store/settingsStore";

function App() {
  const fetchNews = useNewsStore((s) => s.fetchNews);
  const fetchClusters = useNewsStore((s) => s.fetchClusters);
  const fetchMarkets = useMarketsStore((s) => s.fetchMarkets);
  const fetchFlights = useLayersStore((s) => s.fetchFlights);
  const fetchQuakes = useLayersStore((s) => s.fetchQuakes);
  const fetchCountryStats = useLayersStore((s) => s.fetchCountryStats);
  const fetchSettings = useSettingsStore((s) => s.fetchSettings);
  const fetchOllamaStatus = useSettingsStore((s) => s.fetchOllamaStatus);

  // Poll the local Tauri commands (which themselves read from the app's own
  // SQLite cache / in-memory state) — these are cheap local reads, so the
  // frontend can refresh more often than the backend re-fetches externally.
  usePolling(fetchNews, 15_000);
  usePolling(fetchClusters, 20_000);
  usePolling(fetchMarkets, 10_000);
  usePolling(fetchFlights, 15_000);
  usePolling(fetchQuakes, 60_000);
  usePolling(fetchCountryStats, 30_000);
  usePolling(fetchSettings, 60_000);
  usePolling(fetchOllamaStatus, 20_000);

  return <CommandCenterLayout />;
}

export default App;
