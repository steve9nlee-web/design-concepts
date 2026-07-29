import { create } from "zustand";
import { api } from "../lib/api";
import type { AppSettings } from "../lib/types";

interface SettingsState {
  settings: AppSettings | null;
  ollamaModels: string[];
  ollamaAvailable: boolean;
  fetchSettings: () => Promise<void>;
  fetchOllamaStatus: () => Promise<void>;
  saveSettings: (s: AppSettings) => Promise<void>;
}

export const useSettingsStore = create<SettingsState>((set, get) => ({
  settings: null,
  ollamaModels: [],
  ollamaAvailable: false,
  fetchSettings: async () => {
    try {
      set({ settings: await api.getSettings() });
    } catch {
      /* ignore */
    }
  },
  fetchOllamaStatus: async () => {
    try {
      const available = await api.ollamaStatus();
      const models = available ? await api.listOllamaModels() : [];
      set({ ollamaAvailable: available, ollamaModels: models });
    } catch {
      set({ ollamaAvailable: false });
    }
  },
  saveSettings: async (s) => {
    await api.setSettings(s);
    set({ settings: s });
    void get().fetchSettings();
  },
}));
