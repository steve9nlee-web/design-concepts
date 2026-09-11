import { create } from "zustand";
import { api } from "../lib/api";
import type { CountryStat, Flight, LayerId, Quake } from "../lib/types";

interface LayersState {
  flights: Flight[];
  quakes: Quake[];
  countryStats: CountryStat[];
  enabledLayers: Record<LayerId, boolean>;
  toggleLayer: (id: LayerId) => void;
  fetchFlights: () => Promise<void>;
  fetchQuakes: () => Promise<void>;
  fetchCountryStats: () => Promise<void>;
}

export const useLayersStore = create<LayersState>((set, get) => ({
  flights: [],
  quakes: [],
  countryStats: [],
  enabledLayers: { news: true, flights: true, quakes: true, instability: true },
  toggleLayer: (id) =>
    set({ enabledLayers: { ...get().enabledLayers, [id]: !get().enabledLayers[id] } }),
  fetchFlights: async () => {
    try {
      set({ flights: await api.getFlights() });
    } catch {
      /* keep last known */
    }
  },
  fetchQuakes: async () => {
    try {
      set({ quakes: await api.getQuakes() });
    } catch {
      /* keep last known */
    }
  },
  fetchCountryStats: async () => {
    try {
      set({ countryStats: await api.getCountryStats() });
    } catch {
      /* keep last known */
    }
  },
}));
