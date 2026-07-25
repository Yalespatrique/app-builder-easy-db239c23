import { createRootRoute, Link, Outlet, useLocation } from "@tanstack/react-router";

export const Route = createRootRoute({
  component: RootLayout,
});

function RootLayout() {
  return (
    <>
      <NavBar />
      <Outlet />
    </>
  );
}

function NavBar() {
  const { pathname } = useLocation();
  const link = (to: "/" | "/dns", label: string) => (
    <Link
      to={to}
      className={`px-3 py-1.5 rounded-md text-sm font-semibold ${
        pathname === to
          ? "bg-primary text-primary-foreground"
          : "text-foreground hover:bg-card"
      }`}
    >
      {label}
    </Link>
  );
  return (
    <header className="border-b border-border bg-background">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-3">
        <div className="text-sm font-bold tracking-widest text-primary">ASTERPLAY</div>
        <nav className="flex gap-2">
          {link("/", "Ativar lista")}
          {link("/dns", "DNS")}
        </nav>
      </div>
    </header>
  );
}
