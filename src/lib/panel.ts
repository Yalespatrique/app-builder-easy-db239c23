// Asterplay activation panel API client.
// Mirrors components/tasks/PanelTask.xml — tries the primary host, then the
// painel.* fallback, and returns the same response shape the Roku app expects.

const HOSTS = ["https://appasterplay.top", "https://painel.appasterplay.top"];

export type PanelStatus = {
  status?: string; // free | trial | active | expired
  days_left?: number | string;
};

export type PanelResponse = {
  ok: boolean;
  message?: string;
  status?: PanelStatus;
  host?: string;
  username?: string;
  password?: string;
  // raw payload from server, if any
  raw?: unknown;
};

export async function fetchPanel(
  mac: string,
  deviceKey: string,
  signal?: AbortSignal,
): Promise<PanelResponse> {
  const encMac = encodeURIComponent(mac);
  const token = String(Math.floor(Date.now() / 1000));
  let lastError: string | undefined;

  for (const host of HOSTS) {
    try {
      const url = `${host}/api/public/playlist?mac=${encMac}&key=${deviceKey}&_=${token}`;
      const res = await fetch(url, { signal, cache: "no-store" });
      if (!res.ok) {
        lastError = `HTTP ${res.status}`;
        continue;
      }
      const data = (await res.json()) as Record<string, unknown>;
      const ok = data?.ok === true || Boolean((data as any)?.host);
      return {
        ok,
        message: (data as any)?.message,
        status: (data as any)?.status,
        host: (data as any)?.host,
        username: (data as any)?.username,
        password: (data as any)?.password,
        raw: data,
      };
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }
  }

  return {
    ok: false,
    message: lastError
      ? `Não foi possível contactar o painel (${lastError}).`
      : "Nenhuma lista vinculada a este MAC. Cadastre no site acima.",
  };
}
