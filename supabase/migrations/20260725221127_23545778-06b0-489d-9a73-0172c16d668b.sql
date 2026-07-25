
CREATE TABLE public.activations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  mac text NOT NULL,
  device_key text NOT NULL,
  playlist_url text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (mac, device_key)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.activations TO anon, authenticated;
GRANT ALL ON public.activations TO service_role;
ALTER TABLE public.activations ENABLE ROW LEVEL SECURITY;
CREATE POLICY "public read activations" ON public.activations FOR SELECT USING (true);
CREATE POLICY "public write activations" ON public.activations FOR INSERT WITH CHECK (true);
CREATE POLICY "public update activations" ON public.activations FOR UPDATE USING (true);
CREATE POLICY "public delete activations" ON public.activations FOR DELETE USING (true);

CREATE TABLE public.codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL,
  username text NOT NULL,
  password text NOT NULL,
  dns text NOT NULL,
  playlist_url text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (code, username, password)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.codes TO anon, authenticated;
GRANT ALL ON public.codes TO service_role;
ALTER TABLE public.codes ENABLE ROW LEVEL SECURITY;
CREATE POLICY "public read codes" ON public.codes FOR SELECT USING (true);
CREATE POLICY "public write codes" ON public.codes FOR INSERT WITH CHECK (true);
CREATE POLICY "public update codes" ON public.codes FOR UPDATE USING (true);
CREATE POLICY "public delete codes" ON public.codes FOR DELETE USING (true);
