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
        const mac = (u.searchParams.get("mac") ?? "").trim().toUpperCase();
        const key = (u.searchParams.get("key") ?? "").trim().toUpperCase();
        if (!mac || !key) return json(400, { ok: false, message: "mac e key obrigatórios" });
        const { data, error } = await sb()
          .from("activations")
          .select("playlist_url, active")
          .eq("mac", mac)
          .eq("device_key", key)
          .maybeSingle();
        if (error) return json(500, { ok: false, message: error.message });
        if (!data || !data.active) return json(404, { ok: false, message: "Ativação não encontrada" });
        return json(200, { ok: true, playlist_url: data.playlist_url });
      },
    },
  },
});
