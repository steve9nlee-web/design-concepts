import { create } from "zustand";
import { api } from "../lib/api";
import type { MarketQuote } from "../lib/types";

interface MarketsState {
  quotes: MarketQuote[];
  fetchMarkets: () => Promise<void>;
}

export const useMarketsStore = create<MarketsState>((set) => ({
  quotes: [],
  fetchMarkets: async () => {
    try {
      const quotes = await api.getMarkets();
      set({ quotes });
    } catch {
      // transient fetch failures are fine; keep showing last known quotes
    }
  },
}));
