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

/** host:port em minúsculo, sem protocolo, sem barra final, sem "www.". */
function normalizeHost(raw: string): string {
  let v = raw.trim().toLowerCase();
  v = v.replace(/^https?:\/\//, "");
  v = v.replace(/\/.*$/, "");
  v = v.replace(/^www\./, "");
  return v;
}

export const Route = createFileRoute("/api/public/dns-check")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: CORS }),
      GET: async ({ request }) => {
        const u = new URL(request.url);
        const raw = (u.searchParams.get("host") ?? u.searchParams.get("dns") ?? "").trim();
        if (!raw) return json(400, { ok: false, message: "host obrigatório" });
        const host = normalizeHost(raw);
        if (!host) return json(400, { ok: false, message: "host inválido" });

        const { data, error } = await sb().from("dns_map").select("dns");
        if (error) return json(500, { ok: false, message: error.message });

        const registered = (data ?? []).some((row) => {
          const d = normalizeHost(row.dns ?? "");
          if (!d) return false;
          // casa com ou sem porta
          return d === host || d.split(":")[0] === host.split(":")[0];
        });

        return json(200, { ok: true, registered, host });
      },
    },
  },
});
