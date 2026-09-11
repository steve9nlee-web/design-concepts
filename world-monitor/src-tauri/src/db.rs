use crate::types::{Article, Cluster, CountryStat};
use chrono::Utc;
use rusqlite::{params, Connection, OptionalExtension};
use std::path::Path;
use std::sync::Mutex;

pub struct Db(pub Mutex<Connection>);

impl Db {
    pub fn open(path: &Path) -> anyhow::Result<Self> {
        let conn = Connection::open(path)?;
        conn.execute_batch(
            r#"
            CREATE TABLE IF NOT EXISTS articles (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                title TEXT NOT NULL,
                link TEXT NOT NULL UNIQUE,
                summary TEXT NOT NULL DEFAULT '',
                published_at TEXT NOT NULL,
                country_code TEXT,
                country_name TEXT,
                lat REAL,
                lon REAL,
                cluster_id TEXT,
                fetched_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_articles_fetched ON articles(fetched_at);
            CREATE INDEX IF NOT EXISTS idx_articles_cluster ON articles(cluster_id);

            CREATE TABLE IF NOT EXISTS clusters (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                rationale TEXT,
                related_cluster_ids TEXT NOT NULL DEFAULT '[]',
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS country_stats (
                country_code TEXT PRIMARY KEY,
                country_name TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                avg_goldstein REAL NOT NULL,
                avg_tone REAL NOT NULL,
                event_count INTEGER NOT NULL,
                instability_score REAL NOT NULL,
                rationale TEXT,
                updated_at TEXT NOT NULL
            );
            "#,
        )?;
        Ok(Self(Mutex::new(conn)))
    }

    pub fn upsert_article(&self, a: &Article) -> anyhow::Result<bool> {
        let conn = self.0.lock().unwrap();
        let changed = conn.execute(
            r#"INSERT OR IGNORE INTO articles
                (id, source, title, link, summary, published_at, country_code, country_name, lat, lon, cluster_id, fetched_at)
                VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12)"#,
            params![
                a.id, a.source, a.title, a.link, a.summary, a.published_at,
                a.country_code, a.country_name, a.lat, a.lon, a.cluster_id, a.fetched_at
            ],
        )?;
        Ok(changed > 0)
    }

    pub fn recent_articles(&self, limit: i64) -> anyhow::Result<Vec<Article>> {
        let conn = self.0.lock().unwrap();
        let mut stmt = conn.prepare(
            r#"SELECT id, source, title, link, summary, published_at, country_code, country_name, lat, lon, cluster_id, fetched_at
               FROM articles ORDER BY fetched_at DESC LIMIT ?1"#,
        )?;
        let rows = stmt.query_map(params![limit], row_to_article)?;
        Ok(rows.filter_map(|r| r.ok()).collect())
    }

    pub fn unclustered_articles(&self, max_age_hours: i64, limit: i64) -> anyhow::Result<Vec<Article>> {
        let conn = self.0.lock().unwrap();
        let cutoff = (Utc::now() - chrono::Duration::hours(max_age_hours)).to_rfc3339();
        let mut stmt = conn.prepare(
            r#"SELECT id, source, title, link, summary, published_at, country_code, country_name, lat, lon, cluster_id, fetched_at
               FROM articles WHERE cluster_id IS NULL AND fetched_at >= ?1 ORDER BY fetched_at DESC LIMIT ?2"#,
        )?;
        let rows = stmt.query_map(params![cutoff, limit], row_to_article)?;
        Ok(rows.filter_map(|r| r.ok()).collect())
    }

    pub fn set_article_cluster(&self, article_id: &str, cluster_id: &str) -> anyhow::Result<()> {
        let conn = self.0.lock().unwrap();
        conn.execute(
            "UPDATE articles SET cluster_id = ?1 WHERE id = ?2",
            params![cluster_id, article_id],
        )?;
        Ok(())
    }

    pub fn insert_cluster(&self, c: &Cluster) -> anyhow::Result<()> {
        let conn = self.0.lock().unwrap();
        conn.execute(
            r#"INSERT INTO clusters (id, title, summary, rationale, related_cluster_ids, created_at)
               VALUES (?1,?2,?3,?4,?5,?6)"#,
            params![
                c.id, c.title, c.summary, c.rationale,
                serde_json::to_string(&c.related_cluster_ids).unwrap_or_else(|_| "[]".into()),
                c.created_at
            ],
        )?;
        Ok(())
    }

    pub fn recent_clusters(&self, limit: i64) -> anyhow::Result<Vec<Cluster>> {
        let conn = self.0.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, title, summary, rationale, related_cluster_ids, created_at FROM clusters ORDER BY created_at DESC LIMIT ?1",
        )?;
        let cluster_rows: Vec<(String, String, String, Option<String>, String, String)> = stmt
            .query_map(params![limit], |row| {
                Ok((
                    row.get(0)?,
                    row.get(1)?,
                    row.get(2)?,
                    row.get(3)?,
                    row.get(4)?,
                    row.get(5)?,
                ))
            })?
            .filter_map(|r| r.ok())
            .collect();
        drop(stmt);

        let mut clusters = Vec::new();
        for (id, title, summary, rationale, related_json, created_at) in cluster_rows {
            let mut stmt2 = conn.prepare("SELECT id FROM articles WHERE cluster_id = ?1")?;
            let article_ids: Vec<String> = stmt2
                .query_map(params![id], |row| row.get::<_, String>(0))?
                .filter_map(|r| r.ok())
                .collect();
            let related_cluster_ids: Vec<String> =
                serde_json::from_str(&related_json).unwrap_or_default();
            clusters.push(Cluster {
                id,
                title,
                summary,
                rationale,
                article_ids,
                related_cluster_ids,
                created_at,
            });
        }
        Ok(clusters)
    }

    pub fn upsert_country_stat(&self, s: &CountryStat) -> anyhow::Result<()> {
        let conn = self.0.lock().unwrap();
        conn.execute(
            r#"INSERT INTO country_stats
                (country_code, country_name, lat, lon, avg_goldstein, avg_tone, event_count, instability_score, rationale, updated_at)
               VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10)
               ON CONFLICT(country_code) DO UPDATE SET
                 country_name=excluded.country_name, lat=excluded.lat, lon=excluded.lon,
                 avg_goldstein=excluded.avg_goldstein, avg_tone=excluded.avg_tone,
                 event_count=excluded.event_count, instability_score=excluded.instability_score,
                 updated_at=excluded.updated_at"#,
            params![
                s.country_code, s.country_name, s.lat, s.lon, s.avg_goldstein, s.avg_tone,
                s.event_count, s.instability_score, s.rationale, s.updated_at
            ],
        )?;
        Ok(())
    }

    pub fn set_country_rationale(&self, country_code: &str, rationale: &str) -> anyhow::Result<()> {
        let conn = self.0.lock().unwrap();
        conn.execute(
            "UPDATE country_stats SET rationale = ?1 WHERE country_code = ?2",
            params![rationale, country_code],
        )?;
        Ok(())
    }

    pub fn all_country_stats(&self) -> anyhow::Result<Vec<CountryStat>> {
        let conn = self.0.lock().unwrap();
        let mut stmt = conn.prepare(
            r#"SELECT country_code, country_name, lat, lon, avg_goldstein, avg_tone, event_count, instability_score, rationale, updated_at
               FROM country_stats ORDER BY instability_score DESC"#,
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(CountryStat {
                country_code: row.get(0)?,
                country_name: row.get(1)?,
                lat: row.get(2)?,
                lon: row.get(3)?,
                avg_goldstein: row.get(4)?,
                avg_tone: row.get(5)?,
                event_count: row.get(6)?,
                instability_score: row.get(7)?,
                rationale: row.get(8)?,
                updated_at: row.get(9)?,
            })
        })?;
        Ok(rows.filter_map(|r| r.ok()).collect())
    }

    pub fn stats_needing_rationale(&self, limit: i64) -> anyhow::Result<Vec<CountryStat>> {
        let conn = self.0.lock().unwrap();
        let mut stmt = conn.prepare(
            r#"SELECT country_code, country_name, lat, lon, avg_goldstein, avg_tone, event_count, instability_score, rationale, updated_at
               FROM country_stats WHERE rationale IS NULL ORDER BY instability_score DESC LIMIT ?1"#,
        )?;
        let rows = stmt.query_map(params![limit], |row| {
            Ok(CountryStat {
                country_code: row.get(0)?,
                country_name: row.get(1)?,
                lat: row.get(2)?,
                lon: row.get(3)?,
                avg_goldstein: row.get(4)?,
                avg_tone: row.get(5)?,
                event_count: row.get(6)?,
                instability_score: row.get(7)?,
                rationale: row.get(8)?,
                updated_at: row.get(9)?,
            })
        })?;
        Ok(rows.filter_map(|r| r.ok()).collect())
    }

    #[allow(dead_code)]
    pub fn article_by_id(&self, id: &str) -> anyhow::Result<Option<Article>> {
        let conn = self.0.lock().unwrap();
        let mut stmt = conn.prepare(
            r#"SELECT id, source, title, link, summary, published_at, country_code, country_name, lat, lon, cluster_id, fetched_at
               FROM articles WHERE id = ?1"#,
        )?;
        Ok(stmt.query_row(params![id], row_to_article).optional()?)
    }
}

fn row_to_article(row: &rusqlite::Row) -> rusqlite::Result<Article> {
    Ok(Article {
        id: row.get(0)?,
        source: row.get(1)?,
        title: row.get(2)?,
        link: row.get(3)?,
        summary: row.get(4)?,
        published_at: row.get(5)?,
        country_code: row.get(6)?,
        country_name: row.get(7)?,
        lat: row.get(8)?,
        lon: row.get(9)?,
        cluster_id: row.get(10)?,
        fetched_at: row.get(11)?,
    })
}
