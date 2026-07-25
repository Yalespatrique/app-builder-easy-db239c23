import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Asterplay APK Android TV" },
      {
        name: "description",
        content: "Status do projeto Asterplay: APK nativo Android TV com splash, vídeo de intro e assets do Roku.",
      },
      { property: "og:title", content: "Asterplay APK Android TV" },
      {
        property: "og:description",
        content: "Projeto Android TV nativo do Asterplay pronto para abrir no Android Studio.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

function Index() {
  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="mx-auto flex min-h-screen max-w-5xl flex-col justify-center px-6 py-12">
        <p className="text-sm font-semibold uppercase text-primary">Android TV APK</p>
        <h1 className="mt-4 text-5xl font-bold leading-tight text-foreground">Asterplay</h1>
        <p className="mt-5 max-w-2xl text-lg text-muted-foreground">
          Projeto nativo em Kotlin com os assets originais do Roku: vídeo de intro, splash, logo, ícones e tela de pareamento.
        </p>
        <div className="mt-10 grid gap-4 sm:grid-cols-3">
          {[
            "Abra a pasta android-tv no Android Studio",
            "Faça Sync / Make Project",
            "Desinstale o app antigo e rode novamente",
          ].map((item) => (
            <div key={item} className="border border-border bg-card p-5 text-card-foreground">
              {item}
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
