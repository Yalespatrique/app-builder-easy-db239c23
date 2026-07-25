import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";

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
  label?: string;
  createdAt: string;
};

const KEY = "asterplay.test.dns";

function load(): DnsEntry[] {
  if (typeof window === "undefined") return [];
  try { return JSON.parse(localStorage.getItem(KEY) ?? "[]"); } catch { return []; }
}

function DnsPage() {
  const [items, setItems] = useState<DnsEntry[]>([]);
  const [code, setCode] = useState("");
  const [dns, setDns] = useState("");
  const [label, setLabel] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => { setItems(load()); }, []);

  function persist(next: DnsEntry[]) {
    localStorage.setItem(KEY, JSON.stringify(next));
    setItems(next);
  }

  function add(e: React.FormEvent) {
    e.preventDefault();
    if (!code.trim() || !dns.trim()) { setMsg("Preencha código e DNS."); return; }
    const exists = items.some((x) => x.code.toLowerCase() === code.trim().toLowerCase());
    if (exists) { setMsg("Já existe um DNS com esse código."); return; }
    const next = [
      { id: crypto.randomUUID(), code: code.trim(), dns: dns.trim(), label: label.trim() || undefined, createdAt: new Date().toISOString() },
      ...items,
    ];
    persist(next);
    setCode(""); setDns(""); setLabel("");
    setMsg("DNS cadastrado.");
  }

  function remove(id: string) {
    persist(items.filter((x) => x.id !== id));
  }

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="mx-auto max-w-4xl px-6 py-10">
        <p className="text-xs font-bold uppercase tracking-widest text-primary">Painel</p>
        <h1 className="mt-2 text-4xl font-bold">DNS e códigos</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          Cadastre aqui os DNS dos servidores e o código correspondente. No reprodutor, o usuário digita o código
          e o app usa o DNS salvo aqui para montar a URL da lista.
        </p>

        <form onSubmit={add} className="mt-6 space-y-4 rounded-lg border border-border bg-card p-6">
          <div className="grid gap-4 sm:grid-cols-3">
            <label className="block">
              <span className="text-xs font-bold uppercase text-primary">Código</span>
              <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="ex: 1234"
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" />
            </label>
            <label className="block sm:col-span-2">
              <span className="text-xs font-bold uppercase text-primary">DNS</span>
              <input value={dns} onChange={(e) => setDns(e.target.value)} placeholder="http://seudns.com:8080"
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" />
            </label>
          </div>
          <label className="block">
            <span className="text-xs font-bold uppercase text-primary">Nome (opcional)</span>
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Servidor principal"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
          </label>

          {msg && <p className="text-sm text-primary">{msg}</p>}

          <button type="submit" className="rounded-md bg-primary px-6 py-2.5 text-sm font-bold text-primary-foreground">
            Adicionar DNS
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
