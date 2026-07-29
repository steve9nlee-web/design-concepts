use crate::ai::ollama::OllamaClient;
use crate::state::AppState;
use crate::types::{Article, AppSettings, Cluster, CountryStat, Flight, MarketQuote, Quake};
use tauri::State;

#[tauri::command]
pub fn get_news(state: State<AppState>, limit: Option<i64>) -> Result<Vec<Article>, String> {
    state.db.recent_articles(limit.unwrap_or(200)).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_clusters(state: State<AppState>) -> Result<Vec<Cluster>, String> {
    state.db.recent_clusters(50).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_country_stats(state: State<AppState>) -> Result<Vec<CountryStat>, String> {
    state.db.all_country_stats().map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_flights(state: State<AppState>) -> Result<Vec<Flight>, String> {
    Ok(state.flights.read().unwrap().clone())
}

#[tauri::command]
pub fn get_quakes(state: State<AppState>) -> Result<Vec<Quake>, String> {
    Ok(state.quakes.read().unwrap().clone())
}

#[tauri::command]
pub fn get_markets(state: State<AppState>) -> Result<Vec<MarketQuote>, String> {
    Ok(state.markets.read().unwrap().clone())
}

#[tauri::command]
pub fn get_settings(state: State<AppState>) -> Result<AppSettings, String> {
    Ok(state.settings.read().unwrap().clone())
}

#[tauri::command]
pub fn set_settings(state: State<AppState>, settings: AppSettings) -> Result<(), String> {
    *state.settings.write().unwrap() = settings;
    Ok(())
}

#[tauri::command]
pub async fn list_ollama_models(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let client = OllamaClient::new(state.http.clone());
    client.list_models().await.map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn ollama_status(state: State<'_, AppState>) -> Result<bool, String> {
    let client = OllamaClient::new(state.http.clone());
    Ok(client.is_available().await)
}
