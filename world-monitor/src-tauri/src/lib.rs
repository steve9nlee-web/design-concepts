mod ai;
mod commands;
mod countries;
mod db;
mod feeds;
mod state;
mod types;

use ai::ollama::{run_ai_pass, OllamaClient};
use db::Db;
use state::AppState;
use std::time::Duration;
use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tracing_subscriber::fmt::init();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let app_dir = app.path().app_data_dir()?;
            std::fs::create_dir_all(&app_dir)?;
            let db_path = app_dir.join("world-monitor.sqlite3");
            let db = Db::open(&db_path)?;
            let state = AppState::new(db);
            app.manage(state);

            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                spawn_background_tasks(handle).await;
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_news,
            commands::get_clusters,
            commands::get_country_stats,
            commands::get_flights,
            commands::get_quakes,
            commands::get_markets,
            commands::get_settings,
            commands::set_settings,
            commands::list_ollama_models,
            commands::ollama_status,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

async fn spawn_background_tasks(app: tauri::AppHandle) {
    // tokio::time::interval fires its first tick immediately, so each of
    // these also performs an initial fetch on startup before settling into
    // its own period.
    spawn_interval(app.clone(), Duration::from_secs(180), move |app| {
        let http = app.state::<AppState>().http.clone();
        let db = app.state::<AppState>().db.clone();
        async move {
            feeds::news::poll_all_feeds(&http, &db).await;
        }
    });

    spawn_interval(app.clone(), Duration::from_secs(900), move |app| {
        let http = app.state::<AppState>().http.clone();
        let db = app.state::<AppState>().db.clone();
        async move {
            if let Err(e) = feeds::gdelt::refresh_instability(&http, &db).await {
                tracing::warn!("gdelt refresh failed: {e:#}");
            }
        }
    });

    spawn_interval(app.clone(), Duration::from_secs(60), move |app| {
        let state = app.state::<AppState>();
        let http = state.http.clone();
        async move {
            match feeds::flights::fetch_flights(&http).await {
                Ok(flights) => *app.state::<AppState>().flights.write().unwrap() = flights,
                Err(e) => tracing::warn!("flights refresh failed: {e:#}"),
            }
        }
    });

    spawn_interval(app.clone(), Duration::from_secs(600), move |app| {
        let state = app.state::<AppState>();
        let http = state.http.clone();
        async move {
            match feeds::earthquakes::fetch_quakes(&http).await {
                Ok(quakes) => *app.state::<AppState>().quakes.write().unwrap() = quakes,
                Err(e) => tracing::warn!("quakes refresh failed: {e:#}"),
            }
        }
    });

    spawn_interval(app.clone(), Duration::from_secs(30), move |app| {
        let state = app.state::<AppState>();
        let http = state.http.clone();
        async move {
            let mut quotes = Vec::new();
            match feeds::markets::fetch_equities(&http).await {
                Ok(mut q) => quotes.append(&mut q),
                Err(e) => tracing::warn!("equities refresh failed: {e:#}"),
            }
            match feeds::markets::fetch_crypto(&http).await {
                Ok(mut q) => quotes.append(&mut q),
                Err(e) => tracing::warn!("crypto refresh failed: {e:#}"),
            }
            if !quotes.is_empty() {
                *app.state::<AppState>().markets.write().unwrap() = quotes;
            }
        }
    });

    // CPU-only local inference (no discrete GPU) can take several minutes
    // per pass, so this runs on a longer interval than the data feeds.
    spawn_interval(app.clone(), Duration::from_secs(900), move |app| {
        let state = app.state::<AppState>();
        let http = state.http.clone();
        let db = state.db.clone();
        let model = state.settings.read().unwrap().ollama_model.clone();
        async move {
            let ollama = OllamaClient::new(http);
            run_ai_pass(&ollama, &model, &db).await;
        }
    });
}

/// Runs `task` once immediately, then repeatedly on `period`. `task` is a
/// factory (not a future) so each tick can pull fresh clones of shared state.
fn spawn_interval<F, Fut>(app: tauri::AppHandle, period: Duration, task: F)
where
    F: Fn(tauri::AppHandle) -> Fut + Send + 'static,
    Fut: std::future::Future<Output = ()> + Send + 'static,
{
    tauri::async_runtime::spawn(async move {
        let mut ticker = tokio::time::interval(period);
        ticker.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        loop {
            ticker.tick().await;
            task(app.clone()).await;
        }
    });
}
