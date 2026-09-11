use crate::countries::by_fips;
use crate::db::Db;
use crate::types::CountryStat;
use chrono::Utc;
use std::collections::HashMap;
use std::io::Read;
use std::sync::Arc;

const LASTUPDATE_URL: &str = "http://data.gdeltproject.org/gdeltv2/lastupdate.txt";

// GDELT 2.0 event export is a 61-column, tab-separated, header-less CSV.
// Column positions verified against a live export (see codebook at
// http://data.gdeltproject.org/documentation/GDELT-Event_Codebook-V2.0.pdf):
const COL_GOLDSTEIN: usize = 30;
const COL_AVG_TONE: usize = 34;
const COL_ACTION_GEO_COUNTRY: usize = 53; // FIPS 10-4 code
const COL_ACTION_GEO_LAT: usize = 56;
const COL_ACTION_GEO_LON: usize = 57;
const MIN_COLS: usize = 61;

struct EventAgg {
    goldstein_sum: f64,
    tone_sum: f64,
    count: i64,
    lat_sum: f64,
    lon_sum: f64,
}

/// Downloads the latest GDELT 2.0 15-minute event export, aggregates
/// Goldstein Scale / Average Tone / event volume per country, computes a
/// heuristic 0-100 instability score, and upserts into `country_stats`.
///
/// This is a best-effort MVP heuristic (not an academic-grade index):
/// higher weight is given to conflictual events (very negative Goldstein),
/// negative sentiment (negative tone), and event-volume spikes vs. a mild
/// rolling baseline.
pub async fn refresh_instability(client: &reqwest::Client, db: &Arc<Db>) -> anyhow::Result<()> {
    let export_url = latest_export_url(client).await?;
    let csv_text = download_and_unzip(client, &export_url).await?;

    let mut agg: HashMap<String, EventAgg> = HashMap::new();
    for line in csv_text.lines() {
        let cols: Vec<&str> = line.split('\t').collect();
        if cols.len() < MIN_COLS {
            continue;
        }
        let fips = cols[COL_ACTION_GEO_COUNTRY].trim();
        if fips.is_empty() {
            continue;
        }
        let Some(goldstein) = cols[COL_GOLDSTEIN].parse::<f64>().ok() else {
            continue;
        };
        let Some(tone) = cols[COL_AVG_TONE].parse::<f64>().ok() else {
            continue;
        };
        let lat = cols[COL_ACTION_GEO_LAT].parse::<f64>().ok();
        let lon = cols[COL_ACTION_GEO_LON].parse::<f64>().ok();

        let entry = agg.entry(fips.to_string()).or_insert(EventAgg {
            goldstein_sum: 0.0,
            tone_sum: 0.0,
            count: 0,
            lat_sum: 0.0,
            lon_sum: 0.0,
        });
        entry.goldstein_sum += goldstein;
        entry.tone_sum += tone;
        entry.count += 1;
        if let (Some(la), Some(lo)) = (lat, lon) {
            entry.lat_sum += la;
            entry.lon_sum += lo;
        }
    }

    let now = Utc::now().to_rfc3339();
    for (fips, a) in agg {
        if a.count < 3 {
            continue; // skip noise from very low event volume
        }
        let Some(info) = by_fips(&fips) else { continue };
        let avg_goldstein = a.goldstein_sum / a.count as f64;
        let avg_tone = a.tone_sum / a.count as f64;
        let lat = if a.count > 0 { a.lat_sum / a.count as f64 } else { info.lat };
        let lon = if a.count > 0 { a.lon_sum / a.count as f64 } else { info.lon };

        let score = instability_score(avg_goldstein, avg_tone, a.count);

        db.upsert_country_stat(&CountryStat {
            country_code: info.iso2.to_string(),
            country_name: info.name.to_string(),
            lat,
            lon,
            avg_goldstein,
            avg_tone,
            event_count: a.count,
            instability_score: score,
            rationale: None, // preserved on conflict by db layer if already set (see note below)
            updated_at: now.clone(),
        })?;
    }
    Ok(())
}

/// Heuristic 0-100 instability score. Goldstein ranges roughly -10..+10
/// (very negative = conflictual), AvgTone roughly -10..+10 in practice
/// (negative = negative sentiment), event count used as a mild volume
/// signal capped to avoid a handful of very newsy-but-stable countries
/// dominating.
fn instability_score(avg_goldstein: f64, avg_tone: f64, count: i64) -> f64 {
    let goldstein_component = ((-avg_goldstein).clamp(-10.0, 10.0) + 10.0) * 3.0; // 0..60
    let tone_component = ((-avg_tone).clamp(-10.0, 10.0) + 10.0) * 1.5; // 0..30
    let volume_component = (count as f64).ln().clamp(0.0, 10.0); // 0..~10
    (goldstein_component + tone_component + volume_component).clamp(0.0, 100.0)
}

async fn latest_export_url(client: &reqwest::Client) -> anyhow::Result<String> {
    let text = client
        .get(LASTUPDATE_URL)
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await?
        .text()
        .await?;
    for line in text.lines() {
        if let Some(url) = line.split_whitespace().nth(2) {
            if url.ends_with(".export.CSV.zip") {
                return Ok(url.to_string());
            }
        }
    }
    anyhow::bail!("no export.CSV.zip entry in GDELT lastupdate.txt")
}

async fn download_and_unzip(client: &reqwest::Client, url: &str) -> anyhow::Result<String> {
    let bytes = client
        .get(url)
        .timeout(std::time::Duration::from_secs(60))
        .send()
        .await?
        .bytes()
        .await?;
    let cursor = std::io::Cursor::new(bytes);
    let mut zip = zip::ZipArchive::new(cursor)?;
    let mut file = zip.by_index(0)?;
    let mut out = String::new();
    file.read_to_string(&mut out)?;
    Ok(out)
}
