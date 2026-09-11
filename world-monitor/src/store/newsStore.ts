import { create } from "zustand";
import { api } from "../lib/api";
import type { Article, Cluster } from "../lib/types";

interface NewsState {
  articles: Article[];
  clusters: Cluster[];
  loading: boolean;
  error: string | null;
  fetchNews: () => Promise<void>;
  fetchClusters: () => Promise<void>;
}

export const useNewsStore = create<NewsState>((set) => ({
  articles: [],
  clusters: [],
  loading: false,
  error: null,
  fetchNews: async () => {
    try {
      const articles = await api.getNews(300);
      set({ articles, error: null });
    } catch (e) {
      set({ error: String(e) });
    }
  },
  fetchClusters: async () => {
    try {
      const clusters = await api.getClusters();
      set({ clusters });
    } catch (e) {
      set({ error: String(e) });
    }
  },
}));
