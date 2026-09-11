import { invoke } from "@tauri-apps/api/core";
import type {
  Article,
  AppSettings,
  Cluster,
  CountryStat,
  Flight,
  MarketQuote,
  Quake,
} from "./types";

export const api = {
  getNews: (limit?: number) => invoke<Article[]>("get_news", { limit }),
  getClusters: () => invoke<Cluster[]>("get_clusters"),
  getCountryStats: () => invoke<CountryStat[]>("get_country_stats"),
  getFlights: () => invoke<Flight[]>("get_flights"),
  getQuakes: () => invoke<Quake[]>("get_quakes"),
  getMarkets: () => invoke<MarketQuote[]>("get_markets"),
  getSettings: () => invoke<AppSettings>("get_settings"),
  setSettings: (settings: AppSettings) => invoke<void>("set_settings", { settings }),
  listOllamaModels: () => invoke<string[]>("list_ollama_models"),
  ollamaStatus: () => invoke<boolean>("ollama_status"),
};
