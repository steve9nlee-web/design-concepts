use crate::types::Quake;
use serde::Deserialize;

const USGS_URL: &str =
    "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_week.geojson";

#[derive(Deserialize)]
struct GeoJson {
    features: Vec<Feature>,
}

#[derive(Deserialize)]
struct Feature {
    properties: Properties,
    geometry: Geometry,
}

#[derive(Deserialize)]
struct Properties {
    mag: Option<f64>,
    place: Option<String>,
    time: i64,
}

#[derive(Deserialize)]
struct Geometry {
    coordinates: Vec<f64>, // [lon, lat, depth]
}

pub async fn fetch_quakes(client: &reqwest::Client) -> anyhow::Result<Vec<Quake>> {
    let body: GeoJson = client
        .get(USGS_URL)
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await?
        .json()
        .await?;

    let quakes = body
        .features
        .into_iter()
        .filter_map(|f| {
            let lon = *f.geometry.coordinates.first()?;
            let lat = *f.geometry.coordinates.get(1)?;
            Some(Quake {
                id: format!("{}-{}", f.properties.time, lat),
                magnitude: f.properties.mag.unwrap_or(0.0),
                place: f.properties.place.unwrap_or_else(|| "Unknown".to_string()),
                lat,
                lon,
                time_ms: f.properties.time,
            })
        })
        .collect();
    Ok(quakes)
}
