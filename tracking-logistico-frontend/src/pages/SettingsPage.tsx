import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, KeyRound } from "lucide-react";
import { toast } from "sonner";
import { useAuthStore } from "@/stores/authStore";
import { usePageMeta } from "@/hooks/usePageMeta";
import { api, getApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ChangePasswordDialog } from "@/features/settings/components/ChangePasswordDialog";
import { DeactivateAccountDialog } from "@/features/settings/components/DeactivateAccountDialog";
import { EditProfileDialog } from "@/features/settings/components/EditProfileDialog";
import { ProfileSummary } from "@/features/settings/components/ProfileSummary";
import { NotificationPreferences } from "@/features/settings/components/NotificationPreferences";
import { IdentityProfileCard } from "@/features/settings/components/IdentityProfileCard";
import type { IdentityProfile } from "@/types/api";

interface Perfil {
  nombre: string;
  telefono: string;
  direccion: string;
}

interface Password {
  actual: string;
  nueva: string;
  confirmacion: string;
}

export function SettingsPage() {
  usePageMeta("Configuración", "Seguridad y datos de tu cuenta en LogisTrack.");
  const { role, userId, accessToken, requiresPasswordChange, clearSession } =
    useAuthStore();
  const [password, setPassword] = useState<Password>({
    actual: "",
    nueva: "",
    confirmacion: "",
  });
  const [profile, setProfile] = useState<Perfil>({
    nombre: "",
    telefono: "",
    direccion: "",
  });
  const [loading, setLoading] = useState(false);
  const [profileDialogOpen, setProfileDialogOpen] = useState(false);
  const [identity, setIdentity] = useState<IdentityProfile | null>(null);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(
    requiresPasswordChange,
  );
  const navigate = useNavigate();

  useEffect(() => {
    if (role === "CLIENTE" && accessToken && !requiresPasswordChange) {
      api
        .myProfile(accessToken)
        .then((profileResponse) => {
          setProfile({
            nombre: profileResponse.nombre,
            telefono: profileResponse.telefono ?? "",
            direccion: profileResponse.direccion ?? "",
          });
          return api.getMyIdentity(accessToken);
        })
        .then(setIdentity)
        .catch((error: unknown) => {
          toast.error(
            getApiError(error, "No fue posible cargar tus datos personales."),
          );
        });
    }
  }, [role, accessToken, requiresPasswordChange]);

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!userId || !accessToken) {
      toast.error("La sesión no contiene la información necesaria.");
      return;
    }
    if (password.nueva !== password.confirmacion) {
      toast.error("Las contraseñas nuevas no coinciden.");
      return;
    }
    setLoading(true);
    try {
      await api.changePassword(
        userId,
        password.actual,
        password.nueva,
        password.confirmacion,
        accessToken,
      );
      toast.success(
        "Contraseña actualizada. Inicia sesión nuevamente con tu contraseña definitiva.",
      );
      setPasswordDialogOpen(false);
      setTimeout(() => {
        clearSession();
        navigate("/login", { replace: true });
      }, 800);
    } catch (error) {
      toast.error(
        getApiError(error, "No fue posible actualizar la contraseña."),
      );
    } finally {
      setLoading(false);
    }
  }

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!accessToken) return;
    setLoading(true);
    try {
      await api.updateMyProfile(profile, accessToken);
      setProfileDialogOpen(false);
      toast.success("Datos personales actualizados.");
    } catch (error) {
      toast.error(getApiError(error, "No fue posible actualizar el perfil."));
    } finally {
      setLoading(false);
    }
  }

  async function deactivate() {
    if (!accessToken) return;
    setLoading(true);
    try {
      await api.deactivateMyAccount(accessToken);
      clearSession();
      navigate("/", { replace: true });
    } catch (error) {
      toast.error(getApiError(error, "No fue posible desactivar la cuenta."));
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-10">
      {!requiresPasswordChange && (
        <Link
          to="/panel"
          className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-4" />
          Volver al resumen
        </Link>
      )}
      <h1 className="mt-8 text-3xl font-semibold tracking-tight">
        {requiresPasswordChange ? "Activa tu cuenta" : "Configuración"}
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">
        {requiresPasswordChange
          ? "Por seguridad debes cambiar la contraseña temporal antes de continuar."
          : `Sesión activa con rol ${role}.`}
      </p>

      {!requiresPasswordChange && (
        <>
          <div className="mt-8">
            <ProfileSummary
              profile={profile}
              role={role}
              onEdit={role === "CLIENTE" ? () => setProfileDialogOpen(true) : undefined}
            />
          </div>
          {role === "CLIENTE" && (
            <div className="mt-6">
              <IdentityProfileCard
                identity={identity}
                token={accessToken}
                onSaved={setIdentity}
              />
            </div>
          )}
          {role === "CLIENTE" && (
            <div className="mt-6">
              <NotificationPreferences
                token={accessToken}
                defaultPhone={profile.telefono}
              />
            </div>
          )}
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <KeyRound className="size-5 text-emerald-700" />
                  Seguridad
                </CardTitle>
              </CardHeader>
              <CardContent>
                <Button
                  variant="outline"
                  onClick={() => setPasswordDialogOpen(true)}
                >
                  Cambiar contraseña
                </Button>
              </CardContent>
            </Card>
            {role === "CLIENTE" && <Card>
              <CardHeader>
                <CardTitle>Cuenta</CardTitle>
              </CardHeader>
              <CardContent>
                <DeactivateAccountDialog
                  onConfirm={deactivate}
                  loading={loading}
                />
              </CardContent>
            </Card>}
          </div>
        </>
      )}

      {role === "CLIENTE" && (
        <EditProfileDialog
          open={profileDialogOpen}
          onOpenChange={setProfileDialogOpen}
          profile={profile}
          onChange={setProfile}
          onSubmit={saveProfile}
          loading={loading}
        />
      )}
      <ChangePasswordDialog
        open={passwordDialogOpen}
        onOpenChange={setPasswordDialogOpen}
        password={password}
        onChange={setPassword}
        onSubmit={changePassword}
        loading={loading}
      />
    </div>
  );
}
