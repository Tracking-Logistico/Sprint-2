import { useEffect, useState } from "react";
import { anomaliesApi } from "@/lib/api";
import { useAuthStore } from "@/stores/authStore";
import type { ClientAnomaly } from "@/types/api";
import { Alert, AlertTitle, AlertDescription } from "@/components/ui/alert";

function safeTelephone(value: string | null): string | null {
  return value && /^\+?[1-9][0-9]{6,14}$/.test(value) ? `tel:${value}` : null;
}

export function AnomalyBanner({ tracking }: { tracking: string }) {
  const token = useAuthStore((state) => state.accessToken);
  const [snapshot, setSnapshot] = useState<{
    token: string;
    tracking: string;
    alerts: ClientAnomaly[];
    failed: boolean;
  } | null>(null);
  const matches =
    snapshot !== null &&
    snapshot.token === token &&
    snapshot.tracking === tracking;
  const alerts = matches ? snapshot.alerts : [];
  const failed = matches && snapshot.failed;

  useEffect(() => {
    let current = true;
    async function refresh() {
      if (!token) return;
      try {
        const result = await anomaliesApi.alerts(tracking, token);
        if (current)
          setSnapshot({ token, tracking, alerts: result, failed: false });
      } catch {
        if (current) setSnapshot({ token, tracking, alerts: [], failed: true });
      }
    }
    void refresh();
    const timer = window.setInterval(() => void refresh(), 30000);
    return () => {
      current = false;
      window.clearInterval(timer);
    };
  }, [token, tracking]);

  return (
    <div className="space-y-3" aria-live="polite">
      {failed && (
        <p role="status" className="text-sm text-muted-foreground">
          No pudimos actualizar las alertas. Volveremos a intentarlo.
        </p>
      )}
      {alerts.map((alert) => {
        const href = alert.enlaceLlamada ?? safeTelephone(alert.telefono);
        return (
          <Alert key={alert.mensaje}>
            <AlertTitle>Estamos revisando tu envío</AlertTitle>
            <AlertDescription>
              <p>{alert.mensaje}</p>
              <p className="mt-2">Oficina responsable — {alert.oficina}</p>
              {href ? (
                <a
                  className="mt-3 inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-primary-foreground"
                  href={href}
                >
                  Llamar a la oficina responsable
                </a>
              ) : (
                <p className="mt-2">
                  Consulta el canal de atención general de la empresa.
                </p>
              )}
            </AlertDescription>
          </Alert>
        );
      })}
    </div>
  );
}
