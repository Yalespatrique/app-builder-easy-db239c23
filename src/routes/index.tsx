import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Ative sua lista — Asterplay" },
      { name: "description", content: "Painel para ativar sua lista M3U no reprodutor Asterplay Android TV." },
      { property: "og:title", content: "Ative sua lista — Asterplay" },
      { property: "og:description", content: "Painel para ativar sua lista M3U no reprodutor Asterplay Android TV." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Activate,
});

type ActRow = { id: string; mac: string; device_key: string; playlist_url: string; active: boolean; created_at: string };
type CodeRow = { id: string; code: string; username: string; dns: string; playlist_url: string; active: boolean; created_at: string };

function Activate() {
  const [mode, setMode] = useState<"mac" | "code">("mac");
  const [mac, setMac] = useState(""); const [key, setKey] = useState("");
  const [code, setCode] = useState(""); const [user, setUser] = useState(""); const [pass, setPass] = useState("");
  const [dns, setDns] = useState(""); const [m3u, setM3u] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [acts, setActs] = useState<ActRow[]>([]);
  const [codes, setCodes] = useState<CodeRow[]>([]);

  async function reload() {
    const [{ data: a }, { data: c }] = await Promise.all([
      supabase.from("activations").select("*").order("created_at", { ascending: false }).limit(50),
      supabase.from("codes").select("id,code,username,dns,playlist_url,active,created_at").order("created_at", { ascending: false }).limit(50),
    ]);
    setActs((a as ActRow[]) ?? []); setCodes((c as CodeRow[]) ?? []);
  }
  useEffect(() => { void reload(); }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    if (!m3u.trim()) return setMsg("Informe a URL da lista M3U.");
    if (mode === "mac") {
      if (!mac.trim() || !key.trim()) return setMsg("Preencha MAC e Chave.");
      const { error } = await supabase.from("activations").upsert({
        mac: mac.trim().toUpperCase(), device_key: key.trim().toUpperCase(), playlist_url: m3u.trim(), active: true,
      }, { onConflict: "mac,device_key" });
      if (error) return setMsg(error.message);
    } else {
      if (!code.trim() || !user.trim() || !pass.trim() || !dns.trim()) return setMsg("Preencha DNS, código, usuário e senha.");
      const { error } = await supabase.from("codes").upsert({
        code: code.trim(), username: user.trim(), password: pass.trim(), dns: dns.trim(), playlist_url: m3u.trim(), active: true,
      }, { onConflict: "code,username,password" });
      if (error) return setMsg(error.message);
    }
    setMsg("Lista ativada. O app Asterplay já pode carregá-la.");
    setMac(""); setKey(""); setCode(""); setUser(""); setPass(""); setDns(""); setM3u("");
    void reload();
  }

  async function removeAct(id: string) { await supabase.from("activations").delete().eq("id", id); void reload(); }
  async function removeCode(id: string) { await supabase.from("codes").delete().eq("id", id); void reload(); }

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="mx-auto max-w-4xl px-6 py-10">
        <p className="text-xs font-bold uppercase tracking-widest text-primary">Asterplay</p>
        <h1 className="mt-2 text-4xl font-bold">Ative sua lista</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          Painel de teste. Não fornecemos conteúdos, canais nem listas de reprodução. Você é o único responsável pela URL M3U informada.
        </p>

        <div className="mt-8 flex gap-2">
          <button type="button" onClick={() => setMode("mac")}
            className={`rounded-md px-4 py-2 text-sm font-semibold ${mode === "mac" ? "bg-primary text-primary-foreground" : "bg-card border border-border"}`}>
            MAC + Chave
          </button>
          <button type="button" onClick={() => setMode("code")}
            className={`rounded-md px-4 py-2 text-sm font-semibold ${mode === "code" ? "bg-primary text-primary-foreground" : "bg-card border border-border"}`}>
            Código / Usuário / Senha
          </button>
        </div>

        <form onSubmit={save} className="mt-6 space-y-4 rounded-lg border border-border bg-card p-6">
          {mode === "mac" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block"><span className="text-xs font-bold uppercase text-primary">MAC</span>
                <input value={mac} onChange={(e) => setMac(e.target.value)} placeholder="AA:BB:CC:DD:EE:FF"
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" /></label>
              <label className="block"><span className="text-xs font-bold uppercase text-primary">Chave</span>
                <input value={key} onChange={(e) => setKey(e.target.value)} placeholder="XXXX-XXXX"
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" /></label>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block sm:col-span-2"><span className="text-xs font-bold uppercase text-primary">DNS</span>
                <input value={dns} onChange={(e) => setDns(e.target.value)} placeholder="http://seuprovedor.com:8080"
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" /></label>
              <label className="block"><span className="text-xs font-bold uppercase text-primary">Código</span>
                <input value={code} onChange={(e) => setCode(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" /></label>
              <label className="block"><span className="text-xs font-bold uppercase text-primary">Usuário</span>
                <input value={user} onChange={(e) => setUser(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" /></label>
              <label className="block sm:col-span-2"><span className="text-xs font-bold uppercase text-primary">Senha</span>
                <input value={pass} onChange={(e) => setPass(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" /></label>
            </div>
          )}

          <label className="block"><span className="text-xs font-bold uppercase text-primary">URL da lista M3U</span>
            <input value={m3u} onChange={(e) => setM3u(e.target.value)}
              placeholder="http://seuprovedor.com/get.php?username=...&password=...&type=m3u_plus"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-xs" /></label>

          {msg && <p className="text-sm text-primary">{msg}</p>}
          <button type="submit" className="rounded-md bg-primary px-6 py-2.5 text-sm font-bold text-primary-foreground">Ativar lista</button>
        </form>

        <h2 className="mt-10 text-lg font-bold">Ativações MAC + Chave</h2>
        {acts.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">Nenhuma.</p> : (
          <ul className="mt-3 space-y-2">
            {acts.map((it) => (
              <li key={it.id} className="flex items-center justify-between gap-4 rounded-md border border-border bg-card p-3 text-sm">
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">MAC {it.mac} · Chave {it.device_key}</div>
                  <div className="truncate font-mono text-xs text-muted-foreground">{it.playlist_url}</div>
                </div>
                <button onClick={() => removeAct(it.id)} className="text-xs text-destructive hover:underline">Remover</button>
              </li>
            ))}
          </ul>
        )}

        <h2 className="mt-8 text-lg font-bold">Ativações Código / Usuário / Senha</h2>
        {codes.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">Nenhuma.</p> : (
          <ul className="mt-3 space-y-2">
            {codes.map((it) => (
              <li key={it.id} className="flex items-center justify-between gap-4 rounded-md border border-border bg-card p-3 text-sm">
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">Código {it.code} · Usuário {it.username}</div>
                  <div className="truncate font-mono text-xs text-muted-foreground">DNS {it.dns} → {it.playlist_url}</div>
                </div>
                <button onClick={() => removeCode(it.id)} className="text-xs text-destructive hover:underline">Remover</button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
