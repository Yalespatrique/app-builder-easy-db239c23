import { create } from "zustand";
import { persist } from "zustand/middleware";

export type Creds = {
  host: string;
  username: string;
  password: string;
};

type AsterState = {
  creds: Creds;
  deviceStatus: string; // free | trial | active | expired | ""
  daysLeft: string;
  introSeen: boolean;
  setCreds: (c: Creds) => void;
  clearCreds: () => void;
  setDeviceStatus: (s: string, d?: string) => void;
  markIntroSeen: () => void;
};

export const useAster = create<AsterState>()(
  persist(
    (set) => ({
      creds: { host: "", username: "", password: "" },
      deviceStatus: "",
      daysLeft: "",
      introSeen: false,
      setCreds: (creds) => set({ creds }),
      clearCreds: () =>
        set({ creds: { host: "", username: "", password: "" } }),
      setDeviceStatus: (deviceStatus, daysLeft = "") =>
        set({ deviceStatus, daysLeft }),
      markIntroSeen: () => set({ introSeen: true }),
    }),
    { name: "asterplay.state" },
  ),
);
