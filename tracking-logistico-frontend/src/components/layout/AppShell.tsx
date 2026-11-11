import { AssistantWidget } from "@/features/assistant/AssistantWidget";
import { Outlet, useNavigate } from "react-router-dom";
import { PackageCheck } from "lucide-react";
import { useAuthStore } from "@/stores/authStore";
import { SessionIdleGuard } from "./SessionIdleGuard";
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/AppSidebar";

export function AppShell() {
  const { role, signOut, accessToken, userId } = useAuthStore();
  const navigate = useNavigate();

  const handleSignOut = async () => {
    await signOut();
    navigate("/login", { replace: true });
  };

  return (
    <SidebarProvider
      open
      className="min-h-screen bg-background text-foreground"
    >
      <SessionIdleGuard />
      {role === "OPERADOR" && accessToken && <AssistantWidget key={userId} token={accessToken} />}
      <AppSidebar role={role} onSignOut={handleSignOut} />
      <SidebarInset className="min-h-screen bg-background">
        {/* Barra superior visible solo en celular con SidebarTrigger para abrir el menú */}
        <header className="flex h-14 items-center gap-3 border-b border-border/80 bg-card px-4 md:hidden">
          <SidebarTrigger className="text-foreground hover:bg-accent" />
          <div className="flex items-center gap-2 font-semibold text-foreground">
            <PackageCheck className="size-5 text-primary" />
            <span>LogisTrack</span>
          </div>
        </header>

        {/* Contenido principal de cada módulo */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
