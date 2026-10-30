import { createFileRoute } from "@tanstack/react-router";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, apikey, authorization",
};

const TRIAL_DAYS = 7;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

/** MAC normalizado: só hex maiúsculo, sem separadores. */
function normalizeMac(raw: string): string {
  return raw.replace(/[^0-9a-fA-F]/g, "").toUpperCase();
}

export const Route = createFileRoute("/api/public/trial-status")({
  server: {
    handlers: {
      OPTIONS: async () => new Response(null, { status: 204, headers: CORS }),
      GET: async ({ request }) => {
        const u = new URL(request.url);
        const mac = normalizeMac((u.searchParams.get("mac") ?? "").trim());
        const key = (u.searchParams.get("key") ?? "").trim().toUpperCase();
        if (mac.length !== 12) return json(400, { ok: false, message: "MAC inválido" });

        // Cliente de serviço (service_role), carregado só dentro do handler
        // do servidor, para a chave nunca ir para o bundle do cliente.
        // A tabela device_trials não tem policy pública, então só ele
        // consegue ler/criar o registro de teste.
        const { supabaseAdmin } = await import("@/integrations/supabase/client.server");

        const { data: existing, error: readError } = await supabaseAdmin
          .from("device_trials")
          .select("mac, trial_start, trial_days")
          .eq("mac", mac)
          .maybeSingle();
        if (readError) return json(500, { ok: false, message: readError.message });

        let trialStartIso: string;
        let trialDays = TRIAL_DAYS;
        if (existing) {
          trialStartIso = existing.trial_start as string;
          trialDays = (existing.trial_days as number) || TRIAL_DAYS;
        } else {
          // Primeiro teste deste MAC: grava agora e nunca mais muda.
          // Limpar dados do app ou reinstalar não cria um teste novo,
          // porque o MAC (derivado do ANDROID_ID) continua o mesmo.
          const { data: created, error: insertError } = await supabaseAdmin
            .from("device_trials")
            .insert({ mac, device_key: key || null, trial_start: new Date().toISOString(), trial_days: TRIAL_DAYS })
            .select("trial_start, trial_days")
            .single();
          if (insertError) return json(500, { ok: false, message: insertError.message });
          trialStartIso = created.trial_start as string;
          trialDays = (created.trial_days as number) || TRIAL_DAYS;
        }

        const startMs = new Date(trialStartIso).getTime();
        const endMs = startMs + trialDays * 24 * 60 * 60 * 1000;
        const diffMs = endMs - Date.now();
        const daysLeft = diffMs <= 0 ? 0 : Math.ceil(diffMs / (24 * 60 * 60 * 1000));

        return json(200, {
          ok: true,
          mac,
          trial_start: trialStartIso,
          trial_start_ms: startMs,
          trial_days: trialDays,
          trial_days_left: daysLeft,
          trial_expired: daysLeft <= 0,
        });
      },
    },
  },
});
