use crate::db::Db;
use crate::types::{AppSettings, Flight, MarketQuote, Quake};
use std::sync::{Arc, RwLock};

pub struct AppState {
    pub db: Arc<Db>,
    pub http: reqwest::Client,
    pub flights: RwLock<Vec<Flight>>,
    pub quakes: RwLock<Vec<Quake>>,
    pub markets: RwLock<Vec<MarketQuote>>,
    pub settings: RwLock<AppSettings>,
}

impl AppState {
    pub fn new(db: Db) -> Self {
        let http = reqwest::Client::builder()
            .user_agent("WorldMonitor/0.1")
            .build()
            .expect("failed to build http client");
        Self {
            db: Arc::new(db),
            http,
            flights: RwLock::new(Vec::new()),
            quakes: RwLock::new(Vec::new()),
            markets: RwLock::new(Vec::new()),
            settings: RwLock::new(AppSettings::default()),
        }
    }
}
