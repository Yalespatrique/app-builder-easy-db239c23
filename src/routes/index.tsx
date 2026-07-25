import { createFileRoute } from "@tanstack/react-router";
import { useMemo, useState } from "react";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Ative sua lista — Asterplay" },
      {
        name: "description",
        content: "Painel de teste para ativar sua lista M3U no reprodutor Asterplay Android TV.",
      },
      { property: "og:title", content: "Ative sua lista — Asterplay" },
      {
        property: "og:description",
        content: "Cadastre sua lista M3U informando MAC + Chave ou Código, usuário e senha.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Activate,
});

type Activation = {
  id: string;
  mode: "mac" | "code";
  mac?: string;
  key?: string;
  code?: string;
  user?: string;
  m3uUrl: string;
  createdAt: string;
};

const STORAGE_KEY = "asterplay.test.activations";

function loadAll(): Activation[] {
  if (typeof window === "undefined") return [];
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "[]");
  } catch {
    return [];
  }
}

function Activate() {
  const [mode, setMode] = useState<"mac" | "code">("mac");
  const [mac, setMac] = useState("");
  const [key, setKey] = useState("");
  const [code, setCode] = useState("");
  const [user, setUser] = useState("");
  const [pass, setPass] = useState("");
  const [m3u, setM3u] = useState("");
  const [list, setList] = useState<Activation[]>(() => loadAll());
  const [msg, setMsg] = useState<string | null>(null);

  const disclaimer = useMemo(
    () =>
      "Este painel é apenas para testar a ativação da sua lista pessoal. Não fornecemos conteúdos, canais nem listas de reprodução. Você é o único responsável pela URL M3U informada.",
    [],
  );

  function save(e: React.FormEvent) {
    e.preventDefault();
    if (!m3u.trim()) {
      setMsg("Informe a URL da sua lista M3U.");
      return;
    }
    if (mode === "mac" && (!mac.trim() || !key.trim())) {
      setMsg("Preencha MAC e Chave.");
      return;
    }
    if (mode === "code" && (!code.trim() || !user.trim() || !pass.trim())) {
      setMsg("Preencha código, usuário e senha.");
      return;
    }
    const item: Activation = {
      id: crypto.randomUUID(),
      mode,
      mac: mode === "mac" ? mac.trim().toUpperCase() : undefined,
      key: mode === "mac" ? key.trim().toUpperCase() : undefined,
      code: mode === "code" ? code.trim() : undefined,
      user: mode === "code" ? user.trim() : undefined,
      m3uUrl: m3u.trim(),
      createdAt: new Date().toISOString(),
    };
    const next = [item, ...list].slice(0, 50);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setList(next);
    setMsg("Lista ativada com sucesso. O reprodutor Asterplay poderá carregá-la.");
    setMac(""); setKey(""); setCode(""); setUser(""); setPass(""); setM3u("");
  }

  function remove(id: string) {
    const next = list.filter((x) => x.id !== id);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setList(next);
  }

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="mx-auto max-w-4xl px-6 py-10">
        <p className="text-xs font-bold uppercase tracking-widest text-primary">Asterplay</p>
        <h1 className="mt-2 text-4xl font-bold">Ative sua lista</h1>
        <p className="mt-3 text-sm text-muted-foreground">{disclaimer}</p>

        <div className="mt-8 flex gap-2">
          <button
            type="button"
            onClick={() => setMode("mac")}
            className={`rounded-md px-4 py-2 text-sm font-semibold ${mode === "mac" ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground border border-border"}`}
          >
            MAC + Chave
          </button>
          <button
            type="button"
            onClick={() => setMode("code")}
            className={`rounded-md px-4 py-2 text-sm font-semibold ${mode === "code" ? "bg-primary text-primary-foreground" : "bg-card text-card-foreground border border-border"}`}
          >
            Código / Usuário / Senha
          </button>
        </div>

        <form onSubmit={save} className="mt-6 space-y-4 rounded-lg border border-border bg-card p-6">
          {mode === "mac" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="text-xs font-bold uppercase text-primary">MAC</span>
                <input value={mac} onChange={(e) => setMac(e.target.value)} placeholder="AA:BB:CC:DD:EE:FF"
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" />
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase text-primary">Chave</span>
                <input value={key} onChange={(e) => setKey(e.target.value)} placeholder="XXXX-XXXX"
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm" />
              </label>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-3">
              <label className="block">
                <span className="text-xs font-bold uppercase text-primary">Código</span>
                <input value={code} onChange={(e) => setCode(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase text-primary">Usuário</span>
                <input value={user} onChange={(e) => setUser(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase text-primary">Senha</span>
                <input type="password" value={pass} onChange={(e) => setPass(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </label>
            </div>
          )}

          <label className="block">
            <span className="text-xs font-bold uppercase text-primary">URL da lista M3U</span>
            <input value={m3u} onChange={(e) => setM3u(e.target.value)}
              placeholder="http://seuprovedor.com/get.php?username=...&password=...&type=m3u_plus"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-xs" />
          </label>

          {msg && <p className="text-sm text-primary">{msg}</p>}

          <button type="submit" className="rounded-md bg-primary px-6 py-2.5 text-sm font-bold text-primary-foreground">
            Ativar lista
          </button>
        </form>

        <h2 className="mt-10 text-lg font-bold">Ativações de teste</h2>
        {list.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">Nenhuma ativação ainda.</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {list.map((it) => (
              <li key={it.id} className="flex items-center justify-between gap-4 rounded-md border border-border bg-card p-3 text-sm">
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">
                    {it.mode === "mac" ? `MAC ${it.mac} · Chave ${it.key}` : `Código ${it.code} · Usuário ${it.user}`}
                  </div>
                  <div className="truncate font-mono text-xs text-muted-foreground">{it.m3uUrl}</div>
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
