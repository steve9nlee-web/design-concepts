use crate::types::Flight;
use serde::Deserialize;
use serde_json::Value;

const OPENSKY_URL: &str = "https://opensky-network.org/api/states/all";

#[derive(Deserialize)]
struct OpenSkyResponse {
    states: Option<Vec<Vec<Value>>>,
}

/// OpenSky anonymous access is rate-limited and returns raw JSON arrays per
/// aircraft (documented positional format, not named fields):
/// [0]=icao24 [1]=callsign [2]=origin_country [5]=lon [6]=lat [7]=baro_altitude
/// [8]=on_ground [9]=velocity [10]=true_track
pub async fn fetch_flights(client: &reqwest::Client) -> anyhow::Result<Vec<Flight>> {
    let resp: OpenSkyResponse = client
        .get(OPENSKY_URL)
        .timeout(std::time::Duration::from_secs(20))
        .send()
        .await?
        .json()
        .await?;

    let Some(states) = resp.states else {
        return Ok(Vec::new());
    };

    let flights = states
        .into_iter()
        .filter_map(|s| {
            let lon = s.get(5)?.as_f64()?;
            let lat = s.get(6)?.as_f64()?;
            Some(Flight {
                icao24: s.get(0)?.as_str().unwrap_or_default().to_string(),
                callsign: s
                    .get(1)
                    .and_then(|v| v.as_str())
                    .unwrap_or_default()
                    .trim()
                    .to_string(),
                origin_country: s
                    .get(2)
                    .and_then(|v| v.as_str())
                    .unwrap_or_default()
                    .to_string(),
                lat,
                lon,
                altitude_m: s.get(7).and_then(|v| v.as_f64()),
                velocity_ms: s.get(9).and_then(|v| v.as_f64()),
                heading: s.get(10).and_then(|v| v.as_f64()),
                on_ground: s.get(8).and_then(|v| v.as_bool()).unwrap_or(false),
            })
        })
        .filter(|f| !f.on_ground)
        .collect();
    Ok(flights)
}
