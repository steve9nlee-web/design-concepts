use crate::types::MarketQuote;
use chrono::Utc;
use serde::Deserialize;
use serde_json::Value;

pub struct WatchlistItem {
    pub symbol: &'static str,
    pub name: &'static str,
    pub asset_class: &'static str,
}

pub static EQUITY_WATCHLIST: &[WatchlistItem] = &[
    WatchlistItem { symbol: "^GSPC", name: "S&P 500", asset_class: "equity" },
    WatchlistItem { symbol: "^DJI", name: "Dow Jones", asset_class: "equity" },
    WatchlistItem { symbol: "^IXIC", name: "Nasdaq", asset_class: "equity" },
    WatchlistItem { symbol: "^FTSE", name: "FTSE 100", asset_class: "equity" },
    WatchlistItem { symbol: "^N225", name: "Nikkei 225", asset_class: "equity" },
    WatchlistItem { symbol: "GC=F", name: "Gold", asset_class: "commodity" },
    WatchlistItem { symbol: "SI=F", name: "Silver", asset_class: "commodity" },
    WatchlistItem { symbol: "CL=F", name: "Crude Oil (WTI)", asset_class: "commodity" },
    WatchlistItem { symbol: "BZ=F", name: "Brent Crude", asset_class: "commodity" },
    WatchlistItem { symbol: "NG=F", name: "Natural Gas", asset_class: "commodity" },
];

pub static CRYPTO_WATCHLIST: &[(&str, &str, &str)] = &[
    ("bitcoin", "BTC", "Bitcoin"),
    ("ethereum", "ETH", "Ethereum"),
    ("solana", "SOL", "Solana"),
    ("ripple", "XRP", "XRP"),
    ("dogecoin", "DOGE", "Dogecoin"),
];

/// Isolates the (unofficial, keyless) Yahoo Finance endpoint behind a small
/// adapter so it's a one-file swap to a keyed provider (Alpha Vantage, etc.)
/// if Yahoo's public chart endpoint ever breaks or gets locked down.
pub async fn fetch_equities(client: &reqwest::Client) -> anyhow::Result<Vec<MarketQuote>> {
    let mut out = Vec::new();
    for item in EQUITY_WATCHLIST {
        match fetch_yahoo_symbol(client, item).await {
            Ok(q) => out.push(q),
            Err(e) => tracing::warn!("yahoo quote for {} failed: {e:#}", item.symbol),
        }
    }
    Ok(out)
}

async fn fetch_yahoo_symbol(
    client: &reqwest::Client,
    item: &WatchlistItem,
) -> anyhow::Result<MarketQuote> {
    let url = format!(
        "https://query1.finance.yahoo.com/v8/finance/chart/{}",
        item.symbol
    );
    let body: Value = client
        .get(&url)
        .header("User-Agent", "Mozilla/5.0 (compatible; WorldMonitor/0.1)")
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await?
        .json()
        .await?;

    let meta = body
        .pointer("/chart/result/0/meta")
        .ok_or_else(|| anyhow::anyhow!("missing meta for {}", item.symbol))?;
    let price = meta
        .get("regularMarketPrice")
        .and_then(|v| v.as_f64())
        .ok_or_else(|| anyhow::anyhow!("missing price for {}", item.symbol))?;
    let prev_close = meta
        .get("chartPreviousClose")
        .or_else(|| meta.get("previousClose"))
        .and_then(|v| v.as_f64())
        .unwrap_or(price);
    let change_pct = if prev_close != 0.0 {
        (price - prev_close) / prev_close * 100.0
    } else {
        0.0
    };

    Ok(MarketQuote {
        symbol: item.symbol.to_string(),
        name: item.name.to_string(),
        asset_class: item.asset_class.to_string(),
        price,
        change_pct_24h: change_pct,
        updated_at: Utc::now().to_rfc3339(),
    })
}

pub async fn fetch_crypto(client: &reqwest::Client) -> anyhow::Result<Vec<MarketQuote>> {
    let ids: Vec<&str> = CRYPTO_WATCHLIST.iter().map(|(id, _, _)| *id).collect();
    let url = format!(
        "https://api.coingecko.com/api/v3/simple/price?ids={}&vs_currencies=usd&include_24hr_change=true",
        ids.join(",")
    );
    let body: std::collections::HashMap<String, CoinGeckoQuote> = client
        .get(&url)
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await?
        .json()
        .await?;

    let now = Utc::now().to_rfc3339();
    let out = CRYPTO_WATCHLIST
        .iter()
        .filter_map(|(id, symbol, name)| {
            let q = body.get(*id)?;
            Some(MarketQuote {
                symbol: symbol.to_string(),
                name: name.to_string(),
                asset_class: "crypto".to_string(),
                price: q.usd,
                change_pct_24h: q.usd_24h_change.unwrap_or(0.0),
                updated_at: now.clone(),
            })
        })
        .collect();
    Ok(out)
}

#[derive(Deserialize)]
struct CoinGeckoQuote {
    usd: f64,
    #[serde(rename = "usd_24h_change")]
    usd_24h_change: Option<f64>,
}
