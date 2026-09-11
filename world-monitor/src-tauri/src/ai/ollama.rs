use crate::db::Db;
use crate::types::{Article, Cluster, CountryStat};
use chrono::Utc;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::Arc;
use uuid::Uuid;

const OLLAMA_BASE: &str = "http://localhost:11434";

pub struct OllamaClient {
    client: reqwest::Client,
    base_url: String,
}

impl OllamaClient {
    pub fn new(client: reqwest::Client) -> Self {
        Self {
            client,
            base_url: OLLAMA_BASE.to_string(),
        }
    }

    pub async fn is_available(&self) -> bool {
        self.client
            .get(format!("{}/api/tags", self.base_url))
            .timeout(std::time::Duration::from_secs(3))
            .send()
            .await
            .map(|r| r.status().is_success())
            .unwrap_or(false)
    }

    pub async fn list_models(&self) -> anyhow::Result<Vec<String>> {
        #[derive(Deserialize)]
        struct Tags {
            models: Vec<ModelEntry>,
        }
        #[derive(Deserialize)]
        struct ModelEntry {
            name: String,
        }
        let tags: Tags = self
            .client
            .get(format!("{}/api/tags", self.base_url))
            .timeout(std::time::Duration::from_secs(5))
            .send()
            .await?
            .json()
            .await?;
        Ok(tags.models.into_iter().map(|m| m.name).collect())
    }

    async fn generate(&self, model: &str, prompt: &str) -> anyhow::Result<String> {
        #[derive(Serialize)]
        struct Req<'a> {
            model: &'a str,
            prompt: &'a str,
            stream: bool,
        }
        #[derive(Deserialize)]
        struct Resp {
            response: String,
        }
        let resp: Resp = self
            .client
            .post(format!("{}/api/generate", self.base_url))
            .json(&Req { model, prompt, stream: false })
            // On CPU-only hardware (no discrete GPU — common, and what this
            // was developed against) a few hundred output tokens can
            // genuinely take several minutes. The AI pass is a sequential
            // interval loop (never overlapping calls), so a long ceiling
            // here just delays the next tick rather than piling up work.
            .timeout(std::time::Duration::from_secs(600))
            .send()
            .await?
            .json()
            .await?;
        Ok(resp.response)
    }
}

/// One pass of the AI pipeline: cluster recent unclustered articles into
/// "stories" with an AI summary, cross-reference new clusters against
/// recent existing ones for correlation, and backfill instability
/// rationales for the highest-scoring countries that don't have one yet.
/// All calls are throttled to a single pass on the caller's interval —
/// never hammer the local model continuously.
pub async fn run_ai_pass(ollama: &OllamaClient, model: &str, db: &Arc<Db>) {
    if !ollama.is_available().await {
        tracing::warn!("ollama not reachable at {OLLAMA_BASE}; skipping AI pass");
        return;
    }

    if let Err(e) = cluster_recent_articles(ollama, model, db).await {
        tracing::warn!("article clustering failed: {e:#}");
    }
    if let Err(e) = backfill_instability_rationales(ollama, model, db).await {
        tracing::warn!("instability rationale backfill failed: {e:#}");
    }
}

async fn cluster_recent_articles(ollama: &OllamaClient, model: &str, db: &Arc<Db>) -> anyhow::Result<()> {
    // Kept small: on CPU-only inference (measured ~2 tokens/sec on
    // integrated graphics), even this batch size takes well over a minute.
    let articles = db.unclustered_articles(6, 8)?;
    if articles.len() < 3 {
        return Ok(());
    }

    let existing = db.recent_clusters(15)?;
    let plan = request_clustering(ollama, model, &articles, &existing).await?;

    for group in plan {
        if group.article_indices.is_empty() {
            continue;
        }
        let cluster_id = Uuid::new_v4().to_string();
        let cluster = Cluster {
            id: cluster_id.clone(),
            title: group.title,
            summary: group.summary,
            rationale: group.rationale,
            article_ids: vec![],
            related_cluster_ids: group.related_to,
            created_at: Utc::now().to_rfc3339(),
        };
        db.insert_cluster(&cluster)?;
        for idx in group.article_indices {
            if let Some(a) = articles.get(idx) {
                db.set_article_cluster(&a.id, &cluster_id)?;
            }
        }
    }
    Ok(())
}

struct ClusterPlanItem {
    title: String,
    summary: String,
    rationale: Option<String>,
    article_indices: Vec<usize>,
    related_to: Vec<String>,
}

async fn request_clustering(
    ollama: &OllamaClient,
    model: &str,
    articles: &[Article],
    existing: &[Cluster],
) -> anyhow::Result<Vec<ClusterPlanItem>> {
    let mut listing = String::new();
    for (i, a) in articles.iter().enumerate() {
        listing.push_str(&format!(
            "[{i}] ({}) {} — {}\n",
            a.country_name.as_deref().unwrap_or("Unknown"),
            a.title,
            truncate(&a.summary, 100)
        ));
    }
    let mut existing_listing = String::new();
    for c in existing.iter().take(5) {
        existing_listing.push_str(&format!("- {}: {}\n", c.id, c.title));
    }

    let prompt = format!(
        r#"You are a geopolitical news analyst. Below is a numbered list of recent news article headlines with country tags.

Group articles that describe the SAME underlying news story/event into clusters (ignore articles that are one-off / unrelated to anything else — leave them out entirely rather than forcing a group). For each cluster, write a short neutral title, a 1-2 sentence summary, and a one-sentence rationale for why these are correlated.

If a cluster clearly continues or relates to one of these EXISTING stories, reference its id in "related_to":
{existing_listing}

Respond with ONLY valid JSON, no prose, no markdown fences, in this exact shape:
{{"clusters": [{{"title": "...", "summary": "...", "rationale": "...", "article_indices": [0,1], "related_to": []}}]}}

Articles:
{listing}"#
    );

    let raw = ollama.generate(model, &prompt).await?;
    let json_str = extract_json(&raw);
    let parsed: Value = match serde_json::from_str(&json_str) {
        Ok(v) => v,
        Err(e) => {
            tracing::warn!("clustering response was not valid JSON ({e}); skipping this pass");
            return Ok(vec![]);
        }
    };

    let mut out = Vec::new();
    if let Some(clusters) = parsed.get("clusters").and_then(|c| c.as_array()) {
        for c in clusters {
            let title = c.get("title").and_then(|v| v.as_str()).unwrap_or("Untitled story").to_string();
            let summary = c.get("summary").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let rationale = c.get("rationale").and_then(|v| v.as_str()).map(|s| s.to_string());
            let article_indices: Vec<usize> = c
                .get("article_indices")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|i| i.as_u64().map(|n| n as usize)).collect())
                .unwrap_or_default();
            let related_to: Vec<String> = c
                .get("related_to")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|i| i.as_str().map(|s| s.to_string())).collect())
                .unwrap_or_default();
            if !article_indices.is_empty() {
                out.push(ClusterPlanItem { title, summary, rationale, article_indices, related_to });
            }
        }
    }
    Ok(out)
}

async fn backfill_instability_rationales(ollama: &OllamaClient, model: &str, db: &Arc<Db>) -> anyhow::Result<()> {
    let needing = db.stats_needing_rationale(5)?;
    for stat in needing {
        match rationale_for_country(ollama, model, &stat).await {
            Ok(text) => db.set_country_rationale(&stat.country_code, &text)?,
            Err(e) => tracing::warn!("rationale for {} failed: {e:#}", stat.country_name),
        }
    }
    Ok(())
}

async fn rationale_for_country(ollama: &OllamaClient, model: &str, stat: &CountryStat) -> anyhow::Result<String> {
    let prompt = format!(
        r#"You are a geopolitical risk analyst. A country has the following aggregated GDELT event statistics over a recent rolling window:

Country: {}
Average Goldstein Scale: {:.2} (range -10 very conflictual to +10 very cooperative)
Average Tone: {:.2} (range roughly -10 very negative to +10 very positive sentiment)
Event count: {}
Computed instability score (0-100, higher = more unstable): {:.1}

In ONE plain-English sentence (no preamble, no markdown), explain what this suggests about the country's current situation. Be measured and avoid speculation beyond what the numbers support."#,
        stat.country_name, stat.avg_goldstein, stat.avg_tone, stat.event_count, stat.instability_score
    );
    let raw = ollama.generate(model, &prompt).await?;
    Ok(raw.trim().to_string())
}

fn extract_json(raw: &str) -> String {
    let trimmed = raw.trim();
    if let (Some(start), Some(end)) = (trimmed.find('{'), trimmed.rfind('}')) {
        if end > start {
            return trimmed[start..=end].to_string();
        }
    }
    trimmed.to_string()
}

fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        s.chars().take(max).collect::<String>() + "…"
    }
}
