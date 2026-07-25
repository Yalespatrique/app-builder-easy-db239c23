import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/dns")({
  head: () => ({
    meta: [
      { title: "DNS — Asterplay" },
      { name: "description", content: "Cadastro de DNS e códigos para ativação de listas." },
      { property: "og:title", content: "DNS — Asterplay" },
      { property: "og:description", content: "Gerencie DNS e códigos usados pelo reprodutor Asterplay." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: DnsPage,
});

type DnsEntry = {
  id: string;
  code: string;
  dns: string;
  label: string | null;
  created_at: string;
};

function DnsPage() {
  const [items, setItems] = useState<DnsEntry[]>([]);
  const [code, setCode] = useState("");
  const [dns, setDns] = useState("");
  const [label, setLabel] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function refresh() {
    const { data, error } = await supabase
      .from("dns_map")
      .select("id, code, dns, label, created_at")
      .order("created_at", { ascending: false });
    if (!error && data) setItems(data as DnsEntry[]);
  }

  useEffect(() => { refresh(); }, []);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    if (!code.trim() || !dns.trim()) { setMsg("Preencha código e DNS."); return; }
    setLoading(true);
    const { error } = await supabase.from("dns_map").insert({
      code: code.trim(),
      dns: dns.trim(),
      label: label.trim() || null,
    });
    setLoading(false);
    if (error) { setMsg(error.message); return; }
    setCode(""); setDns(""); setLabel("");
    setMsg("DNS cadastrado.");
    refresh();
  }

  async function remove(id: string) {
    await supabase.from("dns_map").delete().eq("id", id);
    refresh();
  }

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="mx-auto max-w-4xl px-6 py-10">
        <p className="text-xs font-bold uppercase tracking-widest text-primary">Painel</p>
        <h1 className="mt-2 text-4xl font-bold">DNS e códigos</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          Cadastre o DNS do servidor Xtream e o código. No app, o usuário digita código + usuário + senha
          e o backend monta <code className="font-mono text-xs">{"{dns}/get.php?username=…&password=…"}</code>.
        </p>

        <form onSubmit={add} className="mt-6 space-y-4 rounded-lg border border-border bg-card p-6">
          <div className="grid gap-4 sm:grid-cols-3">
            <label className="block">
              <span className="text-xs font-bold uppercase text-primary">Código</span>
              <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="ex: 1020"
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" />
            </label>
            <label className="block sm:col-span-2">
              <span className="text-xs font-bold uppercase text-primary">DNS</span>
              <input value={dns} onChange={(e) => setDns(e.target.value)} placeholder="http://parcerias.plim.cc"
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" />
            </label>
          </div>
          <label className="block">
            <span className="text-xs font-bold uppercase text-primary">Nome (opcional)</span>
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Servidor principal"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </label>

          {msg && <p className="text-sm text-primary">{msg}</p>}

          <button type="submit" disabled={loading}
            className="rounded-md bg-primary px-6 py-2.5 text-sm font-bold text-primary-foreground disabled:opacity-50">
            {loading ? "Salvando…" : "Adicionar DNS"}
          </button>
        </form>

        <h2 className="mt-10 text-lg font-bold">DNS cadastrados</h2>
        {items.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">Nenhum DNS cadastrado ainda.</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {items.map((it) => (
              <li key={it.id} className="flex items-center justify-between gap-4 rounded-md border border-border bg-card p-3 text-sm">
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">
                    Código <span className="font-mono text-primary">{it.code}</span>
                    {it.label && <span className="ml-2 text-muted-foreground">· {it.label}</span>}
                  </div>
                  <div className="truncate font-mono text-xs text-muted-foreground">{it.dns}</div>
                </div>
                <button onClick={() => remove(it.id)} className="text-xs text-destructive hover:underline">
                  Remover
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
