import { NavLink, useLocation } from "react-router-dom";
import {
  Boxes,
  Bell,
  ClipboardList,
  LogOut,
  Map,
  PackageCheck,
  Settings,
} from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import type { Role } from "@/types/api";

interface NavItemConfig {
  label: string;
  to: string;
  icon: typeof Boxes;
  roles: Role[];
}

const NAV_ITEMS: NavItemConfig[] = [
  { label: "Alertas", to: "/panel/alertas", icon: Bell, roles: ["OPERADOR"] },
  {
    label: "Resumen",
    to: "/panel",
    icon: Boxes,
    roles: ["CLIENTE", "OPERADOR", "CONDUCTOR"],
  },
  {
    label: "Pedidos",
    to: "/panel/pedidos",
    icon: ClipboardList,
    roles: ["CLIENTE", "OPERADOR"],
  },
  {
    label: "Envíos",
    to: "/panel/envios",
    icon: PackageCheck,
    roles: ["OPERADOR"],
  },
  {
    label: "Rutas",
    to: "/panel/rutas",
    icon: Map,
    roles: ["OPERADOR", "CONDUCTOR"],
  },
];

interface AppSidebarProps {
  role: Role | null;
  onSignOut: () => void | Promise<void>;
}

export function AppSidebar({ role, onSignOut }: AppSidebarProps) {
  const location = useLocation();
  const { isMobile, setOpenMobile } = useSidebar();

  const handleLinkClick = () => {
    if (isMobile) {
      setOpenMobile(false);
    }
  };

  const rolTexto =
    role === "CLIENTE"
      ? "Cliente"
      : role === "OPERADOR"
        ? "Operador"
        : role === "CONDUCTOR"
          ? "Conductor"
          : "Usuario";

  const itemsPermitidos = NAV_ITEMS.filter(
    (item) => role && item.roles.includes(role),
  );

  return (
    <Sidebar>
      <SidebarHeader className="border-b border-sidebar-border px-4 py-5">
        <div className="flex items-center gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
            <PackageCheck className="size-5" />
          </span>
          <div className="truncate">
            <p className="font-semibold tracking-tight text-sidebar-foreground">
              LogisTrack
            </p>
            <p className="text-xs text-sidebar-foreground/60">
              Centro operativo
            </p>
          </div>
        </div>
      </SidebarHeader>

      <SidebarContent className="px-3 py-4">
        <SidebarMenu className="space-y-1">
          {itemsPermitidos.map(({ label, to, icon: Icon }) => {
            const isActive =
              to === "/panel"
                ? location.pathname === "/panel" ||
                  location.pathname === "/panel/cliente" ||
                  location.pathname === "/panel/operador" ||
                  location.pathname === "/panel/conductor"
                : location.pathname.startsWith(to);

            return (
              <SidebarMenuItem key={to}>
                <SidebarMenuButton
                  render={
                    <NavLink
                      to={to}
                      end={to === "/panel"}
                      onClick={handleLinkClick}
                    />
                  }
                  isActive={isActive}
                  className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-sidebar-foreground/75 transition hover:bg-sidebar-accent hover:text-sidebar-accent-foreground data-[active=true]:bg-sidebar-primary data-[active=true]:font-semibold data-[active=true]:text-sidebar-primary-foreground"
                >
                  <Icon className="size-4 shrink-0" />
                  <span>
                    {role === "CLIENTE" && to === "/panel/pedidos"
                      ? "Mis envíos"
                      : label}
                  </span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            );
          })}

          <SidebarMenuItem>
            <SidebarMenuButton
              render={
                <NavLink to="/panel/configuracion" onClick={handleLinkClick} />
              }
              isActive={location.pathname.startsWith("/panel/configuracion")}
              className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-sidebar-foreground/75 transition hover:bg-sidebar-accent hover:text-sidebar-accent-foreground data-[active=true]:bg-sidebar-primary data-[active=true]:font-semibold data-[active=true]:text-sidebar-primary-foreground"
            >
              <Settings className="size-4 shrink-0" />
              <span>Configuración</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarContent>

      <SidebarFooter className="border-t border-sidebar-border p-4">
        <p className="px-3 text-xs uppercase tracking-[0.16em] text-sidebar-foreground/50">
          Sesión activa
        </p>
        <p className="mt-1 px-3 text-sm font-medium text-sidebar-foreground">
          {rolTexto}
        </p>
        <button
          type="button"
          onClick={() => {
            handleLinkClick();
            onSignOut();
          }}
          className="mt-3 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-sidebar-foreground/75 transition hover:bg-destructive/20 hover:text-sidebar-foreground"
        >
          <LogOut className="size-4 shrink-0" />
          <span>Cerrar sesión</span>
        </button>
      </SidebarFooter>
    </Sidebar>
  );
}
