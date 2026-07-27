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

export const Route = createFileRoute("/api/public/code-login")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: CORS }),
      GET: async ({ request }) => {
        const u = new URL(request.url);
        const code = (u.searchParams.get("code") ?? "").trim();
        const username = (u.searchParams.get("user") ?? "").trim();
        const password = (u.searchParams.get("pass") ?? "").trim();
        if (!code || !username || !password)
          return json(400, { ok: false, message: "code, user e pass obrigatórios" });
        const client = sb();
        const codeRow = await client
          .from("codes")
          .select("playlist_url, active")
          .eq("code", code)
          .eq("username", username)
          .eq("password", password)
          .maybeSingle();
        if (codeRow.data && codeRow.data.active) {
          return json(200, {
            ok: true,
            playlist_url: codeRow.data.playlist_url,
            xtream: parseXtream(codeRow.data.playlist_url),
          });
        }
        const dnsRow = await client
          .from("dns_map")
          .select("dns")
          .eq("code", code)
          .maybeSingle();
        if (dnsRow.error) return json(500, { ok: false, message: dnsRow.error.message });
        if (!dnsRow.data) return json(404, { ok: false, message: "Código não encontrado" });
        let base = dnsRow.data.dns.trim().replace(/\/+$/, "");
        if (!/^https?:\/\//i.test(base)) base = `http://${base}`;
        const playlist_url = `${base}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=mpegts`;
        let host = base;
        try {
          const bu = new URL(base);
          host = `${bu.protocol}//${bu.host}`;
        } catch {}
        return json(200, {
          ok: true,
          playlist_url,
          xtream: { host, username, password },
        });
      },
    },
  },
});
