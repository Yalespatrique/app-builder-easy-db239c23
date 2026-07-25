import { createFileRoute, useRouter } from "@tanstack/react-router";
import { FocusProvider } from "../components/tv/FocusProvider";
import { Focusable } from "../components/tv/Focusable";
import { useAster } from "../lib/aster-store";

export const Route = createFileRoute("/home")({
  head: () => ({
    meta: [
      { title: "Início — Asterplay" },
      { name: "description", content: "Filmes, séries, TV ao vivo e mais no Asterplay." },
    ],
  }),
  component: HomePage,
});

type Tile = { label: string; icon: string; hint: string };

const TILES: Tile[] = [
  { label: "Filmes", icon: "🎬", hint: "movies" },
  { label: "Séries", icon: "📺", hint: "series" },
  { label: "TV ao vivo", icon: "📡", hint: "live" },
  { label: "Playlist", icon: "🧾", hint: "playlist" },
  { label: "Pedidos", icon: "⭐", hint: "requests" },
  { label: "Ajustes", icon: "⚙️", hint: "settings" },
];

function HomePage() {
  const router = useRouter();
  const creds = useAster((s) => s.creds);
  const clearCreds = useAster((s) => s.clearCreds);
  const deviceStatus = useAster((s) => s.deviceStatus);

  return (
    <FocusProvider>
      <div className="aster-app">
        <div className="aster-bg-image" />
        <div
          style={{
            position: "relative",
            zIndex: 1,
            padding: "3.5rem 5rem",
            height: "100%",
            display: "flex",
            flexDirection: "column",
            gap: "2.5rem",
          }}
        >
          <header
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
              <img src="/tv/icon.png" alt="" style={{ width: 56, height: 56, borderRadius: 14 }} />
              <div>
                <div style={{ fontSize: "1.8rem", fontWeight: 800 }}>
                  Aster<span style={{ color: "var(--aster-accent)" }}>play</span>
                </div>
                <div style={{ fontSize: 13, color: "var(--aster-text-dim)" }}>
                  {creds.host || "Sem servidor"} · {deviceStatus || "status desconhecido"}
                </div>
              </div>
            </div>
            <Focusable
              as="button"
              onEnterPress={() => {
                clearCreds();
                router.navigate({ to: "/login" });
              }}
            >
              {() => <span className="aster-btn">Sair</span>}
            </Focusable>
          </header>

          <h1 style={{ fontSize: "2rem", fontWeight: 700, margin: 0 }}>
            Olá 👋 — o que vai assistir hoje?
          </h1>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
              gap: "1.5rem",
              maxWidth: 1100,
            }}
          >
            {TILES.map((t, i) => (
              <Focusable
                key={t.hint}
                autoFocus={i === 0}
                onEnterPress={() => {
                  // Phase 2+ will wire real routes; keep no-op for now.
                }}
              >
                {() => (
                  <div
                    className="aster-card"
                    style={{
                      padding: "2rem 1.5rem",
                      minHeight: 180,
                      display: "flex",
                      flexDirection: "column",
                      justifyContent: "space-between",
                    }}
                  >
                    <div style={{ fontSize: "2.6rem" }}>{t.icon}</div>
                    <div style={{ fontSize: "1.4rem", fontWeight: 700 }}>
                      {t.label}
                    </div>
                  </div>
                )}
              </Focusable>
            ))}
          </div>

          <div style={{ marginTop: "auto", color: "var(--aster-text-dim)", fontSize: 13 }}>
            Fase 1 — Login e navegação D-pad prontos. Home / Player / Categorias
            serão adicionados nas próximas fases.
          </div>
        </div>
      </div>
    </FocusProvider>
  );
}
