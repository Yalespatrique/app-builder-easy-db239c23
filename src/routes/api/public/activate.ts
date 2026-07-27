import { createFileRoute } from "@tanstack/react-router";
import { createClient } from "@supabase/supabase-js";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, apikey, authorization",
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

function parseXtream(url: string): { host: string; username: string; password: string } | null {
  try {
    const u = new URL(url);
    const user = u.searchParams.get("username");
    const pass = u.searchParams.get("password");
    if (!user || !pass) return null;
    return { host: `${u.protocol}//${u.host}`, username: user, password: pass };
  } catch {
    return null;
  }
}

function sb() {
  const url = process.env.SUPABASE_URL!;
  const key = process.env.SUPABASE_PUBLISHABLE_KEY!;
  return createClient(url, key, {
    auth: { persistSession: false },
    global: {
      fetch: (input, init) => {
        const h = new Headers(init?.headers);
        if (key.startsWith("sb_") && h.get("Authorization") === `Bearer ${key}`) h.delete("Authorization");
        h.set("apikey", key);
        return fetch(input, { ...init, headers: h });
      },
    },
  });
}

export const Route = createFileRoute("/api/public/activate")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: CORS }),
      GET: async ({ request }) => {
        const u = new URL(request.url);
        const rawMac = (u.searchParams.get("mac") ?? "").trim();
        const rawKey = (u.searchParams.get("key") ?? "").trim();
        if (!rawMac || !rawKey) return json(400, { ok: false, message: "mac e key obrigatórios" });
        // Normaliza: só hex maiúsculo, sem separadores. Compara dos dois lados desse jeito.
        const macHex = rawMac.replace(/[^0-9a-fA-F]/g, "").toUpperCase();
        const keyNorm = rawKey.toUpperCase();
        if (macHex.length !== 12) return json(400, { ok: false, message: "MAC inválido" });
        // Busca pela chave (curta) e casa o MAC normalizado no servidor.
        const { data, error } = await sb()
          .from("activations")
          .select("playlist_url, active, mac, device_key")
          .eq("device_key", keyNorm);
        if (error) return json(500, { ok: false, message: error.message });
        const match = (data ?? []).find(
          (row) => (row.mac ?? "").replace(/[^0-9a-fA-F]/g, "").toUpperCase() === macHex,
        );
        if (!match) return json(404, { ok: false, message: "Ativação não encontrada" });
        if (!match.active) return json(403, { ok: false, message: "Ativação inativa" });
        return json(200, { ok: true, playlist_url: match.playlist_url, xtream: parseXtream(match.playlist_url) });
      },
    },
  },
});
