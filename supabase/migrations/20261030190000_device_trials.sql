-- Controle de teste grátis no servidor, por MAC do aparelho.
-- Motivo: o contador de 7 dias era só local (asterplay_trial no APK), então
-- limpar os dados do app ou desinstalar/reinstalar zerava o teste.
-- Com esta tabela, o servidor lembra a data do primeiro teste de cada MAC,
-- mesmo que o app seja apagado e instalado de novo no mesmo aparelho.
-- NÃO dar grant para anon/authenticated: só a service_role (usada pela API
-- do servidor) pode ler/escrever aqui, senão qualquer um com a chave
-- pública poderia apagar o próprio registro de teste. A service_role
-- ignora RLS, então basta habilitar RLS sem policy pública.
CREATE TABLE public.device_trials (
  mac text PRIMARY KEY,
  device_key text,
  trial_start timestamptz NOT NULL DEFAULT now(),
  trial_days integer NOT NULL DEFAULT 7,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.device_trials ENABLE ROW LEVEL SECURITY;
