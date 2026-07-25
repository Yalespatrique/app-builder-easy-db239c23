CREATE TABLE public.dns_map (
  id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  code text NOT NULL UNIQUE,
  dns text NOT NULL,
  label text,
  created_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.dns_map TO anon, authenticated;
GRANT ALL ON public.dns_map TO service_role;
ALTER TABLE public.dns_map ENABLE ROW LEVEL SECURITY;
CREATE POLICY "public read dns_map" ON public.dns_map FOR SELECT USING (true);
CREATE POLICY "public write dns_map" ON public.dns_map FOR INSERT WITH CHECK (true);
CREATE POLICY "public update dns_map" ON public.dns_map FOR UPDATE USING (true);
CREATE POLICY "public delete dns_map" ON public.dns_map FOR DELETE USING (true);