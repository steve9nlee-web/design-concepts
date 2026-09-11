export interface Article {
  id: string;
  source: string;
  title: string;
  link: string;
  summary: string;
  published_at: string;
  country_code: string | null;
  country_name: string | null;
  lat: number | null;
  lon: number | null;
  cluster_id: string | null;
  fetched_at: string;
}

export interface Cluster {
  id: string;
  title: string;
  summary: string;
  rationale: string | null;
  article_ids: string[];
  related_cluster_ids: string[];
  created_at: string;
}

export interface CountryStat {
  country_code: string;
  country_name: string;
  lat: number;
  lon: number;
  avg_goldstein: number;
  avg_tone: number;
  event_count: number;
  instability_score: number;
  rationale: string | null;
  updated_at: string;
}

export interface Flight {
  icao24: string;
  callsign: string;
  origin_country: string;
  lat: number;
  lon: number;
  altitude_m: number | null;
  velocity_ms: number | null;
  heading: number | null;
  on_ground: boolean;
}

export interface Quake {
  id: string;
  magnitude: number;
  place: string;
  lat: number;
  lon: number;
  time_ms: number;
}

export interface MarketQuote {
  symbol: string;
  name: string;
  asset_class: "equity" | "commodity" | "crypto";
  price: number;
  change_pct_24h: number;
  updated_at: string;
}

export interface AppSettings {
  ollama_model: string;
  news_poll_secs: number;
  markets_poll_secs: number;
  flights_poll_secs: number;
  quakes_poll_secs: number;
  gdelt_poll_secs: number;
  ai_pass_secs: number;
  enabled_layers: string[];
}

export type LayerId = "news" | "flights" | "quakes" | "instability";
