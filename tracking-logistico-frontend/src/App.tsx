import { Routes, Route, Outlet, Navigate } from "react-router-dom";
import { useAuthStore } from "@/stores/authStore";
import { Navbar } from "./components/layout/Navbar";
import { AppShell } from "./components/layout/AppShell";
import { ProtectedRoute } from "./components/layout/ProtectedRoute";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { LegalPage } from "./pages/LegalPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { VerifyPage } from "./pages/VerifyPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { AnomaliesPage } from "./pages/AnomaliesPage";
import { DashboardPage } from "./pages/DashboardPage";
import { SettingsPage } from "./pages/SettingsPage";
import { OrdersPage } from "./pages/OrdersPage";
import { ShipmentsPage } from "./pages/ShipmentsPage";
import { RoutesPage } from "./pages/RoutesPage";
import { ShipmentTrackingPage } from "./pages/ShipmentTrackingPage";
import { DriverRoutes } from "./features/driver/components/DriverRoutes";

import { Toaster } from "@/components/ui/sonner";

export default function App() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <Toaster richColors position="top-right" />
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/registro" element={<RegisterPage />} />
          <Route path="/terminos" element={<LegalPage kind="terms" />} />
          <Route
            path="/politica-datos"
            element={<LegalPage kind="privacy" />}
          />
          <Route path="/recuperar-password" element={<ForgotPasswordPage />} />
          <Route path="/verificar" element={<VerifyPage />} />
          <Route
            path="/restablecer-password/:token"
            element={<ResetPasswordPage />}
          />
        </Route>
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/panel" element={<RoleHomeRedirect />} />
            <Route element={<ProtectedRoute roles={["CLIENTE"]} />}>
              <Route path="/panel/cliente" element={<DashboardPage />} />
            </Route>
            <Route element={<ProtectedRoute roles={["OPERADOR"]} />}>
              <Route path="/panel/operador" element={<DashboardPage />} />
            </Route>
            <Route element={<ProtectedRoute roles={["CONDUCTOR"]} />}>
              <Route path="/panel/conductor" element={<DashboardPage />} />
            </Route>
            <Route path="/panel/configuracion" element={<SettingsPage />} />
            <Route element={<ProtectedRoute roles={["CLIENTE", "OPERADOR"]} />}>
              <Route path="/panel/pedidos" element={<OrdersPage />} />
            </Route>
            <Route element={<ProtectedRoute roles={["CLIENTE"]} />}>
              <Route
                path="/panel/pedidos/seguimiento/:numeroTracking"
                element={<ShipmentTrackingPage />}
              />
              <Route path="/tracking" element={<ShipmentTrackingPage />} />
            </Route>
            <Route element={<ProtectedRoute roles={["OPERADOR"]} />}>
              <Route path="/panel/envios" element={<ShipmentsPage />} />
              <Route path="/panel/alertas" element={<AnomaliesPage />} />
            </Route>
            <Route
              element={<ProtectedRoute roles={["OPERADOR", "CONDUCTOR"]} />}
            >
              <Route path="/panel/rutas" element={<RoleRoutesPage />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </div>
  );
}

function RoleRoutesPage() {
  const role = useAuthStore((state) => state.role);
  return role === "CONDUCTOR" ? <DriverRoutes /> : <RoutesPage />;
}

function PublicLayout() {
  return (
    <>
      <Navbar />
      <Outlet />
    </>
  );
}

function RoleHomeRedirect() {
  const role = useAuthStore((state) => state.role);
  const home =
    role === "CLIENTE"
      ? "/panel/cliente"
      : role === "OPERADOR"
        ? "/panel/operador"
        : role === "CONDUCTOR"
          ? "/panel/conductor"
          : "/login";
  return <Navigate to={home} replace />;
}
