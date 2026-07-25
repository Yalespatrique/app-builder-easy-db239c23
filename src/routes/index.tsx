import { createFileRoute, useRouter } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { useAster } from "../lib/aster-store";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Asterplay" },
      { name: "description", content: "Asterplay para Android TV — filmes, séries e canais ao vivo." },
      { property: "og:title", content: "Asterplay" },
      { property: "og:description", content: "Asterplay para Android TV — filmes, séries e canais ao vivo." },
    ],
  }),
  component: Intro,
});

function Intro() {
  const router = useRouter();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [showSplash, setShowSplash] = useState(true);
  const introSeen = useAster((s) => s.introSeen);
  const markIntroSeen = useAster((s) => s.markIntroSeen);
  const creds = useAster((s) => s.creds);

  useEffect(() => {
    // Fail-safe: never sit on intro longer than 9s (matches Roku forceIntroDone).
    const t = setTimeout(() => go(), 9000);

    // Skip intro if we already played it once this install.
    if (introSeen) {
      go();
    }

    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function go() {
    markIntroSeen();
    const authed =
      creds.host && creds.username && creds.password ? true : false;
    router.navigate({ to: authed ? "/login" : "/login" });
  }

  return (
    <div className="aster-app">
      {showSplash && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "#000 url(/tv/splash.jpg) center/cover no-repeat",
            zIndex: 3,
            transition: "opacity 500ms ease",
          }}
        />
      )}
      <video
        ref={videoRef}
        src="/tv/intro.mp4"
        autoPlay
        muted
        playsInline
        onPlaying={() => setShowSplash(false)}
        onEnded={go}
        onError={go}
        style={{
          position: "fixed",
          inset: 0,
          width: "100%",
          height: "100%",
          objectFit: "cover",
          background: "#000",
          zIndex: 2,
        }}
      />
    </div>
  );
}
