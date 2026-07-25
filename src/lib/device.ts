// Device MAC + activation key derivation.
// Mirrors Roku LoginScene.brs -> ResolveStreamCodesMac + DeviceKeyFromMac.
// The "MAC" is a stable per-install 12-hex identifier persisted in localStorage,
// analogous to Roku RegRead/RegWrite("streamcodes_mac", "device").

const MAC_KEY = "asterplay.streamcodes_mac";
const SEED_KEY = "asterplay.streamcodes_seed";

function fnvHash(str: string, mod: number): number {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (h * 31 + str.charCodeAt(i)) % 16777215;
  }
  return h % mod;
}

function buildMacFromSeed(seed: string): string {
  let hash = fnvHash(seed || String(Date.now()), 16777215);
  if (hash < 4096) hash += 4096;
  let hex = hash.toString(16).toUpperCase().padStart(6, "0").slice(-6);
  // 12-hex MAC (48 bits). Prefix with a stable locally-administered vendor.
  const raw = "AE10" + hex + hex.slice(0, 2);
  const norm = raw.slice(0, 12).toUpperCase();
  return norm.match(/.{1,2}/g)!.join(":");
}

export function resolveDeviceMac(): string {
  if (typeof window === "undefined") return "";
  const saved = window.localStorage.getItem(MAC_KEY);
  if (saved && /^[0-9A-F:]{17}$/.test(saved)) return saved;
  let seed = window.localStorage.getItem(SEED_KEY) ?? "";
  if (!seed) {
    seed =
      "asterplay-web-" +
      Date.now().toString(36) +
      "-" +
      Math.random().toString(36).slice(2, 10);
    window.localStorage.setItem(SEED_KEY, seed);
  }
  const mac = buildMacFromSeed(seed);
  window.localStorage.setItem(MAC_KEY, mac);
  return mac;
}

export function deviceKeyFromMac(mac: string): string {
  if (!mac) return "------";
  const clean = mac.toUpperCase().replace(/[^0-9A-F]/g, "");
  if (clean.length < 6) return "------";
  let hash = 0;
  for (let i = 0; i < clean.length; i++) {
    const v = "0123456789ABCDEF".indexOf(clean[i]);
    hash = (hash * 17 + v) % 1000000;
  }
  return String(hash).padStart(6, "0");
}
