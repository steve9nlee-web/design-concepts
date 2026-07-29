use crate::countries::guess_country;
use crate::db::Db;
use crate::types::Article;
use chrono::Utc;
use std::sync::Arc;
use uuid::Uuid;

/// Curated, verified-working public RSS/Atom feeds, deliberately spread across
/// regions so instability/correlation isn't skewed to Western outlets alone.
pub static DEFAULT_FEEDS: &[(&str, &str)] = &[
    ("BBC World", "http://feeds.bbci.co.uk/news/world/rss.xml"),
    ("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"),
    ("NPR World", "https://feeds.npr.org/1004/rss.xml"),
    ("DW World", "https://rss.dw.com/rdf/rss-en-world"),
    ("DW Asia", "https://rss.dw.com/rdf/rss-en-asia"),
    ("DW Africa", "https://rss.dw.com/rdf/rss-en-africa"),
    ("The Guardian World", "https://www.theguardian.com/world/rss"),
    ("Sky News World", "https://feeds.skynews.com/feeds/rss/world.xml"),
    ("CBC World", "https://www.cbc.ca/cmlink/rss-world"),
    ("France24", "https://www.france24.com/en/rss"),
    ("UN News", "https://news.un.org/feed/subscribe/en/news/all/rss.xml"),
    ("CNBC World", "https://www.cnbc.com/id/100727362/device/rss/rss.html"),
    ("Times of India World", "https://timesofindia.indiatimes.com/rssfeeds/296589292.cms"),
    ("SCMP", "https://www.scmp.com/rss/91/feed"),
    ("Fox News World", "https://moxie.foxnews.com/google-publisher/world.xml"),
];

pub async fn poll_all_feeds(client: &reqwest::Client, db: &Arc<Db>) {
    for (source, url) in DEFAULT_FEEDS {
        if let Err(e) = poll_feed(client, db, source, url).await {
            tracing::warn!("news feed '{source}' failed: {e:#}");
        }
    }
}

async fn poll_feed(client: &reqwest::Client, db: &Arc<Db>, source: &str, url: &str) -> anyhow::Result<()> {
    let bytes = client
        .get(url)
        .header("User-Agent", "Mozilla/5.0 (compatible; WorldMonitor/0.1)")
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await?
        .bytes()
        .await?;

    let feed = feed_rs::parser::parse(&bytes[..])?;

    for entry in feed.entries {
        let title = entry
            .title
            .map(|t| t.content)
            .unwrap_or_else(|| "(untitled)".to_string());
        let link = entry
            .links
            .first()
            .map(|l| l.href.clone())
            .unwrap_or_default();
        if link.is_empty() {
            continue;
        }
        let summary = entry
            .summary
            .map(|s| strip_html(&s.content))
            .unwrap_or_default();
        let published_at = entry
            .published
            .or(entry.updated)
            .unwrap_or_else(Utc::now)
            .to_rfc3339();

        let haystack = format!("{title} {summary}");
        let country = guess_country(&haystack);

        let article = Article {
            id: Uuid::new_v4().to_string(),
            source: source.to_string(),
            title,
            link,
            summary: truncate(&summary, 600),
            published_at,
            country_code: country.map(|c| c.iso2.to_string()),
            country_name: country.map(|c| c.name.to_string()),
            lat: country.map(|c| c.lat),
            lon: country.map(|c| c.lon),
            cluster_id: None,
            fetched_at: Utc::now().to_rfc3339(),
        };
        db.upsert_article(&article)?;
    }
    Ok(())
}

fn strip_html(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut in_tag = false;
    for c in s.chars() {
        match c {
            '<' => in_tag = true,
            '>' => in_tag = false,
            _ if !in_tag => out.push(c),
            _ => {}
        }
    }
    out.split_whitespace().collect::<Vec<_>>().join(" ")
}

fn truncate(s: &str, max: usize) -> String {
    if s.len() <= max {
        s.to_string()
    } else {
        let mut end = max;
        while !s.is_char_boundary(end) {
            end -= 1;
        }
        format!("{}…", &s[..end])
    }
}
