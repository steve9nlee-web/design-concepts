import { useEffect, useMemo, useRef } from "react";
import Globe, { type GlobeInstance } from "globe.gl";
import { useNewsStore } from "../../store/newsStore";
import { useLayersStore } from "../../store/layersStore";

interface GlobePoint {
  id: string;
  lat: number;
  lng: number;
  kind: "news" | "flight" | "quake" | "instability";
  color: string;
  radius: number;
  altitude: number;
  label: string;
}

interface GlobeArc {
  startLat: number;
  startLng: number;
  endLat: number;
  endLng: number;
}

export function GlobeView() {
  const containerRef = useRef<HTMLDivElement>(null);
  const globeRef = useRef<GlobeInstance | null>(null);

  const articles = useNewsStore((s) => s.articles);
  const clusters = useNewsStore((s) => s.clusters);
  const flights = useLayersStore((s) => s.flights);
  const quakes = useLayersStore((s) => s.quakes);
  const countryStats = useLayersStore((s) => s.countryStats);
  const enabledLayers = useLayersStore((s) => s.enabledLayers);

  useEffect(() => {
    if (!containerRef.current) return;
    const globe = new Globe(containerRef.current)
      .globeImageUrl("https://unpkg.com/three-globe/example/img/earth-night.jpg")
      .bumpImageUrl("https://unpkg.com/three-globe/example/img/earth-topology.png")
      .backgroundColor("rgba(0,0,0,0)")
      .pointAltitude((d: object) => (d as GlobePoint).altitude)
      .pointRadius((d: object) => (d as GlobePoint).radius)
      .pointColor((d: object) => (d as GlobePoint).color)
      .pointLabel((d: object) => (d as GlobePoint).label)
      .arcColor(() => "rgba(250,204,21,0.55)")
      .arcDashLength(0.4)
      .arcDashGap(0.3)
      .arcDashAnimateTime(4000)
      .arcStroke(0.3);

    globe.controls().autoRotate = true;
    globe.controls().autoRotateSpeed = 0.3;
    globeRef.current = globe;

    const handleResize = () => {
      if (!containerRef.current) return;
      globe.width(containerRef.current.clientWidth);
      globe.height(containerRef.current.clientHeight);
    };
    window.addEventListener("resize", handleResize);
    handleResize();

    return () => {
      window.removeEventListener("resize", handleResize);
      if (containerRef.current) containerRef.current.innerHTML = "";
      globeRef.current = null;
    };
  }, []);

  const points = useMemo<GlobePoint[]>(() => {
    const out: GlobePoint[] = [];

    if (enabledLayers.news) {
      for (const a of articles) {
        if (a.lat == null || a.lon == null) continue;
        out.push({
          id: `news-${a.id}`,
          lat: a.lat,
          lng: a.lon,
          kind: "news",
          color: a.cluster_id ? "#38bdf8" : "#64748b",
          radius: 0.32,
          altitude: 0.01,
          label: `<div style="max-width:220px"><b>${escapeHtml(a.title)}</b><br/><span style="opacity:.7">${escapeHtml(a.source)}</span></div>`,
        });
      }
    }

    if (enabledLayers.flights) {
      for (const f of flights) {
        out.push({
          id: `flight-${f.icao24}`,
          lat: f.lat,
          lng: f.lon,
          kind: "flight",
          color: "#a3e635",
          radius: 0.16,
          altitude: 0.02,
          label: `<b>${escapeHtml(f.callsign || f.icao24)}</b><br/>${escapeHtml(f.origin_country)}`,
        });
      }
    }

    if (enabledLayers.quakes) {
      for (const q of quakes) {
        out.push({
          id: `quake-${q.id}`,
          lat: q.lat,
          lng: q.lon,
          kind: "quake",
          color: "#f97316",
          radius: 0.22 + q.magnitude * 0.1,
          altitude: 0.015,
          label: `<b>M${q.magnitude.toFixed(1)}</b><br/>${escapeHtml(q.place)}`,
        });
      }
    }

    if (enabledLayers.instability) {
      for (const c of countryStats) {
        out.push({
          id: `instability-${c.country_code}`,
          lat: c.lat,
          lng: c.lon,
          kind: "instability",
          color: instabilityColor(c.instability_score),
          radius: 0.4 + c.instability_score / 55,
          altitude: 0.006 + c.instability_score / 3500,
          label: `<b>${escapeHtml(c.country_name)}</b><br/>Instability: ${c.instability_score.toFixed(0)}/100`,
        });
      }
    }

    return out;
  }, [articles, flights, quakes, countryStats, enabledLayers]);

  useEffect(() => {
    globeRef.current?.pointsData(points);
  }, [points]);

  const arcs = useMemo<GlobeArc[]>(() => {
    if (!enabledLayers.news) return [];
    const byId = new Map(articles.map((a) => [a.id, a]));
    const out: GlobeArc[] = [];
    for (const c of clusters) {
      const locs = c.article_ids
        .map((id) => byId.get(id))
        .filter((a): a is NonNullable<typeof a> => !!a && a.lat != null && a.lon != null);
      for (let i = 1; i < locs.length; i++) {
        out.push({
          startLat: locs[0].lat as number,
          startLng: locs[0].lon as number,
          endLat: locs[i].lat as number,
          endLng: locs[i].lon as number,
        });
      }
    }
    return out;
  }, [clusters, articles, enabledLayers.news]);

  useEffect(() => {
    globeRef.current?.arcsData(arcs);
  }, [arcs]);

  return <div ref={containerRef} className="h-full w-full" />;
}

function instabilityColor(score: number): string {
  if (score > 70) return "#ef4444";
  if (score > 45) return "#f59e0b";
  if (score > 25) return "#eab308";
  return "#22c55e";
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c] ?? c,
  );
}
