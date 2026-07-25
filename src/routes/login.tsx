import { createFileRoute, useRouter } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { FocusProvider } from "../components/tv/FocusProvider";
import { Focusable } from "../components/tv/Focusable";
import { resolveDeviceMac, deviceKeyFromMac } from "../lib/device";
import { fetchPanel } from "../lib/panel";
import { useAster } from "../lib/aster-store";

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: "Ativação — Asterplay" },
      { name: "description", content: "Ative seu dispositivo Asterplay." },
    ],
  }),
  component: LoginPage,
});

type Status = { text: string; tone: "info" | "error" | "success" };

function LoginPage() {
  const router = useRouter();
  const setCreds = useAster((s) => s.setCreds);
  const setDeviceStatus = useAster((s) => s.setDeviceStatus);

  const [mac, setMac] = useState("");
  const [deviceKey, setDeviceKey] = useState("------");
  const [status, setStatus] = useState<Status>({ text: "", tone: "info" });
  const [panelChecked, setPanelChecked] = useState(false);
  const [showActivation, setShowActivation] = useState(false);
  const [loading, setLoading] = useState(true);
  const [manual, setManual] = useState({ host: "", username: "", password: "" });

  useEffect(() => {
    const m = resolveDeviceMac();
    setMac(m);
    setDeviceKey(deviceKeyFromMac(m));

    const ac = new AbortController();
    (async () => {
      setLoading(true);
      const res = await fetchPanel(m, deviceKeyFromMac(m), ac.signal);
      setPanelChecked(true);
      setLoading(false);

      if (res.status?.status) {
        setDeviceStatus(
          String(res.status.status),
          res.status.days_left != null ? String(res.status.days_left) : "",
        );
      }

      if (res.ok && res.host && res.username && res.password) {
        setCreds({
          host: res.host,
          username: res.username,
          password: res.password,
        });
        setStatus({ text: "Ativado. Entrando…", tone: "success" });
        setTimeout(() => router.navigate({ to: "/home" }), 600);
      } else {
        setShowActivation(true);
        setStatus({
          text:
            res.message ??
            "Nenhuma lista vinculada a este MAC. Cadastre no site acima.",
          tone: "error",
        });
      }
    })();
    return () => ac.abort();
  }, [router, setCreds, setDeviceStatus]);

  const retry = useCallback(async () => {
    setStatus({ text: "Verificando…", tone: "info" });
    setLoading(true);
    const res = await fetchPanel(mac, deviceKey);
    setLoading(false);
    setPanelChecked(true);
    if (res.ok && res.host && res.username && res.password) {
      setCreds({
        host: res.host,
        username: res.username,
        password: res.password,
      });
      router.navigate({ to: "/home" });
    } else {
      setShowActivation(true);
      setStatus({
        text: res.message ?? "Ainda sem lista vinculada.",
        tone: "error",
      });
    }
  }, [mac, deviceKey, router, setCreds]);

  const submitManual = useCallback(() => {
    if (!manual.host || !manual.username || !manual.password) {
      setStatus({ text: "Preencha host, usuário e senha.", tone: "error" });
      return;
    }
    setCreds(manual);
    router.navigate({ to: "/home" });
  }, [manual, setCreds, router]);

  return (
    <FocusProvider>
      <div className="aster-app">
        <div className="aster-bg-image" />
        <div
          style={{
            position: "relative",
            zIndex: 1,
            height: "100%",
            display: "grid",
            gridTemplateColumns: "1.1fr 1fr",
            gap: "3rem",
            padding: "4rem 6rem",
          }}
        >
          {/* Left: brand + status */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              gap: "1.5rem",
            }}
          >
            <img
              src="/tv/icon.png"
              alt="Asterplay"
              style={{ width: 96, height: 96, borderRadius: 22 }}
            />
            <h1
              style={{
                fontSize: "3.4rem",
                fontWeight: 800,
                letterSpacing: "-0.02em",
                margin: 0,
              }}
            >
              Aster<span style={{ color: "var(--aster-accent)" }}>play</span>
            </h1>
            <p style={{ color: "var(--aster-text-dim)", fontSize: "1.1rem", margin: 0, maxWidth: 520 }}>
              Ative seu dispositivo cadastrando o código abaixo no site oficial.
            </p>

            {showActivation && (
              <div
                className="aster-card"
                style={{ padding: "1.75rem", maxWidth: 560 }}
              >
                <div style={{ fontSize: 14, color: "var(--aster-text-dim)", textTransform: "uppercase", letterSpacing: "0.12em" }}>
                  Site de ativação
                </div>
                <div style={{ fontSize: "1.6rem", fontWeight: 700, marginTop: 6 }}>
                  https://appasterplay.top
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.25rem", marginTop: "1.5rem" }}>
                  <div>
                    <div style={{ fontSize: 12, color: "var(--aster-text-dim)", textTransform: "uppercase", letterSpacing: "0.12em" }}>
                      MAC do dispositivo
                    </div>
                    <div style={{ fontFamily: "ui-monospace, monospace", fontSize: "1.15rem", marginTop: 4 }}>
                      {mac || "--:--:--:--:--:--"}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 12, color: "var(--aster-text-dim)", textTransform: "uppercase", letterSpacing: "0.12em" }}>
                      Código
                    </div>
                    <div style={{ fontFamily: "ui-monospace, monospace", fontSize: "1.5rem", color: "var(--aster-accent-2)", marginTop: 4 }}>
                      {deviceKey}
                    </div>
                  </div>
                </div>
              </div>
            )}

            <div
              style={{
                color:
                  status.tone === "error"
                    ? "var(--aster-danger)"
                    : status.tone === "success"
                      ? "var(--aster-success)"
                      : "var(--aster-text-dim)",
                fontSize: "1rem",
                minHeight: 24,
              }}
            >
              {loading ? "Verificando ativação…" : status.text}
            </div>

            {panelChecked && (
              <div style={{ display: "flex", gap: "1rem" }}>
                <Focusable
                  autoFocus
                  as="button"
                  onEnterPress={retry}
                >
                  {(focused) => (
                    <span
                      className="aster-btn aster-btn-primary"
                      style={{
                        display: "inline-flex",
                        background: focused
                          ? "linear-gradient(135deg, #ff477a, #ff2e5b)"
                          : undefined,
                      }}
                    >
                      ↻ Verificar novamente
                    </span>
                  )}
                </Focusable>
              </div>
            )}
          </div>

          {/* Right: QR + manual login */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              gap: "1.25rem",
            }}
          >
            <div
              className="aster-card"
              style={{
                padding: "2rem",
                display: "flex",
                gap: "1.5rem",
                alignItems: "center",
              }}
            >
              <img
                src="/tv/qr_activate.png"
                alt="QR ativação"
                style={{
                  width: 168,
                  height: 168,
                  background: "#fff",
                  padding: 12,
                  borderRadius: 16,
                }}
              />
              <div>
                <div style={{ fontSize: 14, color: "var(--aster-text-dim)", textTransform: "uppercase", letterSpacing: "0.12em" }}>
                  Escaneie para ativar
                </div>
                <div style={{ fontSize: "1.15rem", fontWeight: 600, marginTop: 6 }}>
                  Acesse pelo celular e cadastre este dispositivo.
                </div>
              </div>
            </div>

            <details className="aster-card" style={{ padding: "1.5rem" }}>
              <summary style={{ cursor: "pointer", fontWeight: 600, fontSize: "1.05rem" }}>
                Entrar com host / usuário / senha
              </summary>
              <div style={{ display: "grid", gap: "0.85rem", marginTop: "1rem" }}>
                <Focusable>
                  {() => (
                    <input
                      className="aster-input aster-focusable"
                      placeholder="http://servidor.tld:porta"
                      value={manual.host}
                      onChange={(e) =>
                        setManual((v) => ({ ...v, host: e.target.value }))
                      }
                    />
                  )}
                </Focusable>
                <Focusable>
                  {() => (
                    <input
                      className="aster-input aster-focusable"
                      placeholder="Usuário"
                      value={manual.username}
                      onChange={(e) =>
                        setManual((v) => ({ ...v, username: e.target.value }))
                      }
                    />
                  )}
                </Focusable>
                <Focusable>
                  {() => (
                    <input
                      className="aster-input aster-focusable"
                      placeholder="Senha"
                      type="password"
                      value={manual.password}
                      onChange={(e) =>
                        setManual((v) => ({ ...v, password: e.target.value }))
                      }
                    />
                  )}
                </Focusable>
                <Focusable as="button" onEnterPress={submitManual}>
                  {() => (
                    <span className="aster-btn aster-btn-primary" style={{ display: "inline-flex" }}>
                      Entrar
                    </span>
                  )}
                </Focusable>
              </div>
            </details>
          </div>
        </div>
      </div>
    </FocusProvider>
  );
}
