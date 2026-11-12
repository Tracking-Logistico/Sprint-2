import { useCallback, useEffect, useState } from "react";
import {
  Download,
  LoaderCircle,
  PackageCheck,
  RefreshCw,
  Truck,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { api, getApiError } from "@/lib/api";
import { useAuthStore } from "@/stores/authStore";
import { usePageMeta } from "@/hooks/usePageMeta";
import type { LabelResponse, OrderResponse } from "@/types/api";
import { IncidentPanel } from "@/features/incidents/components/IncidentPanel";
import { useIncidentTypes } from "@/features/incidents/hooks/useIncidents";
import { ShipmentManagementActions } from "@/features/orders/components/ShipmentManagementActions";

const statusNames: Record<string, string> = {
  SOLICITADO: "Validado · listo para activar",
  CREADO: "Tracking activo",
  RECIBIDO_EN_ORIGEN: "Recibido en origen",
  EN_TRANSITO: "En tránsito",
};

function downloadPdf(label: LabelResponse) {
  const bytes = Uint8Array.from(atob(label.contenido), (char) =>
    char.charCodeAt(0),
  );
  const url = URL.createObjectURL(
    new Blob([bytes], { type: "application/pdf" }),
  );
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `etiqueta-${label.numeroTracking}.pdf`;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function ShipmentsPage() {
  usePageMeta(
    "Despachos",
    "Control de tracking, etiquetas y tránsito de envíos.",
  );
  const token = useAuthStore((state) => state.accessToken);
  const incidentTypes = useIncidentTypes(token);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [workingId, setWorkingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      setOrders(await api.listDispatchOrders(token));
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function perform(id: number, action: () => Promise<string>) {
    if (workingId !== null) return;
    setWorkingId(id);
    setError("");
    setMessage("");
    try {
      const result = await action();
      await load();
      setMessage(result);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setWorkingId(null);
    }
  }

  const waiting = orders.filter((order) => !order.numeroTracking).length;
  const inTransit = orders.filter(
    (order) => order.estado === "EN_TRANSITO",
  ).length;

  return (
    <div className="mx-auto max-w-6xl px-5 py-9 sm:px-8 lg:px-10">
      <header className="flex flex-wrap items-end justify-between gap-5 border-b border-border pb-7">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.18em] text-primary">
            Operación logística
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">
            Centro de despachos
          </h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
            Activa el seguimiento, imprime etiquetas y registra cada avance
            hasta que el envío esté listo para reparto.
          </p>
        </div>
        <Button
          variant="outline"
          disabled={loading || workingId !== null}
          onClick={() => void load()}
        >
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />{" "}
          Actualizar
        </Button>
      </header>

      <div className="mt-6 grid gap-3 sm:grid-cols-3">
        <Summary label="En preparación o tránsito" value={orders.length} />
        <Summary label="Sin tracking" value={waiting} />
        <Summary label="En tránsito" value={inTransit} />
      </div>

      {error && (
        <p
          role="alert"
          className="mt-5 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive"
        >
          {error}
        </p>
      )}
      {message && (
        <p
          role="status"
          className="mt-5 rounded-xl border border-primary/30 bg-primary/10 p-4 text-sm text-primary"
        >
          {message}
        </p>
      )}

      <div className="mt-7 space-y-3">
        {loading && (
          <div role="status" aria-label="Cargando despachos" className="space-y-3 py-4">
            <Skeleton className="h-28 w-full rounded-2xl" />
            <Skeleton className="h-28 w-full rounded-2xl" />
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <LoaderCircle className="size-4 animate-spin" /> Consultando
              despachos...
            </p>
          </div>
        )}
        {!loading && orders.length === 0 && (
          <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center text-sm text-muted-foreground">
            No hay envíos pendientes de despacho.
          </div>
        )}
        {orders.map((order) => (
          <article
            key={order.id}
            className="rounded-2xl border border-border bg-card p-6 shadow-sm"
          >
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="font-mono text-sm font-semibold text-foreground">
                  {order.numeroPedido}
                </p>
                <p className="mt-2 text-sm font-medium text-foreground">
                  {order.destinatarioNombre || "Destinatario"}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {order.direccionDestino} · {order.ciudadDestino}
                </p>
                <p className="mt-2 font-mono text-xs text-muted-foreground">
                  {order.numeroTracking || "Guía pendiente de generación"}
                </p>
              </div>
              <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                {statusNames[order.estado] ?? order.estado.replaceAll("_", " ")}
              </span>
            </div>
            <div className="mt-5 flex flex-wrap gap-2 border-t border-border pt-5">
              {!order.numeroTracking && (
                <Button
                  size="sm"
                  disabled={workingId !== null}
                  onClick={() =>
                    void perform(order.id, async () => {
                      const response = await api.activateTracking(
                        order.id,
                        token!,
                      );
                      return `Tracking ${response.numeroTracking} activado.`;
                    })
                  }
                >
                  <PackageCheck className="size-4" /> Activar tracking
                </Button>
              )}
              {order.numeroTracking && (
                <Button
                  size="sm"
                  variant="outline"
                  disabled={workingId !== null}
                  onClick={() =>
                    void perform(order.id, async () => {
                      downloadPdf(await api.generateLabel(order.id, token!));
                      return "Etiqueta PDF generada.";
                    })
                  }
                >
                  <Download className="size-4" /> Descargar etiqueta
                </Button>
              )}
              {order.estado === "CREADO" && (
                <Button
                  size="sm"
                  variant="outline"
                  disabled={workingId !== null}
                  onClick={() =>
                    void perform(order.id, async () => {
                      await api.setLogisticsState(
                        order.id,
                        "RECIBIDO_EN_ORIGEN",
                        token!,
                      );
                      return "Recepción en origen registrada.";
                    })
                  }
                >
                  <Truck className="size-4" /> Recibido en origen
                </Button>
              )}
              {order.estado === "RECIBIDO_EN_ORIGEN" && (
                <Button
                  size="sm"
                  variant="outline"
                  disabled={workingId !== null}
                  onClick={() =>
                    void perform(order.id, async () => {
                      await api.setLogisticsState(
                        order.id,
                        "EN_TRANSITO",
                        token!,
                      );
                      return "Tránsito registrado. El envío está disponible para asignación.";
                    })
                  }
                >
                  <Truck className="size-4" /> Pasar a tránsito
                </Button>
              )}
            </div>
            <div className="mt-4 border-t border-border pt-4">
              <ShipmentManagementActions
                pedidoId={order.id}
                estado={order.estado}
                token={token}
                onChanged={load}
              />
            </div>
            <IncidentPanel
              orderId={order.id}
              token={token}
              types={incidentTypes.types}
              typesLoading={incidentTypes.loading}
              typesError={incidentTypes.error}
              onChanged={load}
            />
          </article>
        ))}
      </div>
    </div>
  );
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-border bg-card px-5 py-4 shadow-sm">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-semibold tabular-nums text-foreground">
        {value}
      </p>
    </div>
  );
}
