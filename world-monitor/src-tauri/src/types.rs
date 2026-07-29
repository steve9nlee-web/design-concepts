use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Article {
    pub id: String,
    pub source: String,
    pub title: String,
    pub link: String,
    pub summary: String,
    pub published_at: String,
    pub country_code: Option<String>,
    pub country_name: Option<String>,
    pub lat: Option<f64>,
    pub lon: Option<f64>,
    pub cluster_id: Option<String>,
    pub fetched_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Cluster {
    pub id: String,
    pub title: String,
    pub summary: String,
    pub rationale: Option<String>,
    pub article_ids: Vec<String>,
    pub related_cluster_ids: Vec<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CountryStat {
    pub country_code: String,
    pub country_name: String,
    pub lat: f64,
    pub lon: f64,
    pub avg_goldstein: f64,
    pub avg_tone: f64,
    pub event_count: i64,
    pub instability_score: f64,
    pub rationale: Option<String>,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Flight {
    pub icao24: String,
    pub callsign: String,
    pub origin_country: String,
    pub lat: f64,
    pub lon: f64,
    pub altitude_m: Option<f64>,
    pub velocity_ms: Option<f64>,
    pub heading: Option<f64>,
    pub on_ground: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Quake {
    pub id: String,
    pub magnitude: f64,
    pub place: String,
    pub lat: f64,
    pub lon: f64,
    pub time_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MarketQuote {
    pub symbol: String,
    pub name: String,
    pub asset_class: String, // "equity" | "commodity" | "crypto"
    pub price: f64,
    pub change_pct_24h: f64,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    pub ollama_model: String,
    pub news_poll_secs: u64,
    pub markets_poll_secs: u64,
    pub flights_poll_secs: u64,
    pub quakes_poll_secs: u64,
    pub gdelt_poll_secs: u64,
    pub ai_pass_secs: u64,
    pub enabled_layers: Vec<String>,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            ollama_model: "llama3.2".to_string(),
            news_poll_secs: 180,
            markets_poll_secs: 30,
            flights_poll_secs: 60,
            quakes_poll_secs: 600,
            gdelt_poll_secs: 900,
            ai_pass_secs: 900,
            enabled_layers: vec![
                "news".into(),
                "flights".into(),
                "quakes".into(),
                "instability".into(),
            ],
        }
    }
}
