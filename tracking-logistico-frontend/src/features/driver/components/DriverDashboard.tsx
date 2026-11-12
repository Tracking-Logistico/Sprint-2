import { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw, Wifi, WifiOff } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { api, getApiError } from "@/lib/api";
import { useAuthStore } from "@/stores/authStore";
import { useOfflineDeliveryQueue } from "@/features/driver/hooks/useOfflineDeliveryQueue";
import { DriverDeliveryDetail } from "@/features/driver/components/DriverDeliveryDetail";
import { DriverDeliveryList } from "@/features/driver/components/DriverDeliveryList";
import { CheckpointQrScanner } from "@/features/driver/components/CheckpointQrScanner";
import { LocationRegistrationForm } from "@/features/orders/components/LocationRegistrationForm";
import type {
  DeliveryEvent,
  DeliveryResult,
  AgeVerificationResult,
  DriverDelivery,
  DriverProgress,
  NoveltyOption,
} from "@/types/api";

const resultLabels: Record<DeliveryResult, string> = {
  ENTREGADO: "Entregado",
  ENTREGA_FALLIDA: "Entrega fallida",
  DEVOLUCION_AL_REMITENTE: "Devolución al remitente",
};

export function DriverDashboard() {
  const token = useAuthStore((state) => state.accessToken);
  const [deliveries, setDeliveries] = useState<DriverDelivery[]>([]);
  const [progress, setProgress] = useState<DriverProgress | null>(null);
  const [catalog, setCatalog] = useState<NoveltyOption[]>([]);
  const [selected, setSelected] = useState<DriverDelivery | null>(null);
  const [result, setResult] = useState<DeliveryResult>("ENTREGADO");
  const [novelty, setNovelty] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"pending" | "completed">("pending");
  const catalogLoaded = useRef(false);
  const catalogRef = useRef<NoveltyOption[]>([]);
  const offline = useOfflineDeliveryQueue();

  const refresh = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const [nextDeliveries, nextProgress] = await Promise.all([
        api.driverDeliveries(token),
        api.driverProgress(token),
      ]);
      setDeliveries(nextDeliveries);
      setProgress(nextProgress);
      if (!catalogLoaded.current) {
        try {
          const nextCatalog = await api.driverNoveltyCatalog(token);
          setCatalog(nextCatalog);
          catalogRef.current = nextCatalog;
          catalogLoaded.current = true;
        } catch {
          toast.warning(
            "No se cargó el catálogo de novedades; intenta actualizar el panel.",
          );
        }
      }
    } catch (e) {
      setError(getApiError(e, "No fue posible cargar tu jornada."));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void Promise.resolve().then(refresh);
  }, [refresh]);
  useEffect(() => {
    const listener = () => void refresh();
    window.addEventListener("logistrack-driver-refresh", listener);
    return () =>
      window.removeEventListener("logistrack-driver-refresh", listener);
  }, [refresh]);

  const availableNovelties = catalog.filter(
    (item) => item.resultado === result,
  );
  const percentage =
    progress && progress.totalEntregas
      ? Math.round((progress.entregadas / progress.totalEntregas) * 100)
      : 0;

  function openResult(delivery: DriverDelivery) {
    setSelected(delivery);
    setResult("ENTREGADO");
    setNovelty("");
    setReason("");
  }

  async function getLocation() {
    if (!navigator.geolocation) return { latitud: null, longitud: null };
    return new Promise<{ latitud: number | null; longitud: number | null }>(
      (resolve) => {
        navigator.geolocation.getCurrentPosition(
          (position) =>
            resolve({
              latitud: position.coords.latitude,
              longitud: position.coords.longitude,
            }),
          () => {
            toast.warning(
              "No se pudo obtener tu ubicación. El registro continuará sin coordenadas.",
            );
            resolve({ latitud: null, longitud: null });
          },
          { enableHighAccuracy: true, timeout: 8000 },
        );
      },
    );
  }

  async function submitResult() {
    if (!selected || !token || submitting) return;
    if (result !== "ENTREGADO" && (!novelty || !reason.trim())) {
      toast.error(
        "Selecciona un motivo y escribe la descripción de la novedad.",
      );
      return;
    }

    if (result === "ENTREGADO") {
      setNovelty("");
      setReason("");
    }
    if (!offline.isOnline && offline.limitReached) {
      toast.error(
        "La cola sin conexión está llena. Conéctate a internet para continuar.",
      );
      return;
    }
    setSubmitting(true);
    const location = await getLocation();
    const event: DeliveryEvent = {
      resultado: result,
      codigoNovedad: result === "ENTREGADO" ? null : novelty,
      motivo: result === "ENTREGADO" ? null : reason.trim(),
      ...location,
      fechaEvento: new Date().toISOString(),
      idEventoCliente: crypto.randomUUID(),
    };
    try {
      if (!offline.isOnline) {
        offline.queueEvent(selected.pedidoId, event);
        toast.success("Sin conexión: evento guardado para sincronización.");
      } else {
        await api.registerDriverDelivery(selected.pedidoId, event, token);
        toast.success("Resultado de entrega registrado.");
      }
      setSelected(null);
      await refresh();
    } catch (e) {
      if (
        offline.isOnline &&
        (e instanceof TypeError ||
          (typeof e === "object" && e !== null && !("status" in e)))
      ) {
        try {
          offline.queueEvent(selected.pedidoId, event);
          toast.warning("La red falló. Evento guardado para sincronización.");
          setSelected(null);
        } catch (queueError) {
          toast.error(
            queueError instanceof Error
              ? queueError.message
              : "No se pudo guardar el evento.",
          );
        }
      } else {
        toast.error(getApiError(e, "No fue posible registrar el resultado."));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function registerAgeVerification(
    pedidoId: number,
    resultado: AgeVerificationResult,
  ) {
    if (!token || submitting) return;
    setSubmitting(true);
    try {
      await api.registerAgeVerification(pedidoId, resultado, token);
      toast.success(
        resultado === "CUMPLE"
          ? "Verificación de mayoría de edad registrada."
          : "Verificación no aprobada. Se inició el retorno a bodega.",
      );
      await refresh();
    } catch (error) {
      toast.error(
        getApiError(error, "No fue posible registrar la verificación de edad."),
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-7xl px-5 py-8 sm:px-8 lg:px-10">
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-border pb-7">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.19em] text-primary">
            Jornada del conductor
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">
            Mis entregas de hoy
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Registra cada resultado y mantén actualizada tu ruta.
          </p>
        </div>
        <Button
          variant="outline"
          disabled={loading}
          onClick={() => void refresh()}
        >
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />{" "}
          Actualizar
        </Button>
      </header>

      <div
        role="status"
        aria-live="polite"
        className={`mt-5 flex flex-wrap items-center gap-3 rounded-xl border p-3 text-sm ${offline.isOnline ? "border-primary/30 bg-primary/10 text-primary" : "border-secondary bg-secondary text-secondary-foreground"}`}
      >
        {offline.isOnline ? (
          <Wifi className="size-4" />
        ) : (
          <WifiOff className="size-4" />
        )}
        <span>{offline.isOnline ? "En línea" : "Sin conexión"}</span>
        <span className="font-medium">
          {offline.pendingEvents} eventos pendientes
        </span>
        {offline.limitReached && (
          <span className="font-semibold">Límite offline alcanzado</span>
        )}
        {offline.ageWarning && (
          <span>Hay eventos próximos a cumplir 24 horas.</span>
        )}
      </div>
      {error && (
        <div
          role="alert"
          className="mt-5 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive"
        >
          {error}
        </div>
      )}

      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>Avance de la jornada</span>
            <span>{percentage}%</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div
            role="progressbar"
            aria-label="Avance de la jornada"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percentage}
            className="h-3 overflow-hidden rounded-full bg-muted"
          >
            <div
              className="h-full rounded-full bg-primary transition-all"
              style={{ width: `${percentage}%` }}
            />
          </div>
          <div className="mt-3 grid grid-cols-2 gap-3 text-sm text-muted-foreground sm:grid-cols-5">
            <span>Total: {progress?.totalEntregas ?? 0}</span>
            <span>Entregadas: {progress?.entregadas ?? 0}</span>
            <span>Pendientes: {progress?.pendientes ?? 0}</span>
            <span>Fallidas: {progress?.fallidas ?? 0}</span>
            <span>Canceladas: {progress?.canceladas ?? 0}</span>
          </div>
        </CardContent>
      </Card>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.3fr_1fr]">
        <DriverDeliveryList
          deliveries={deliveries}
          loading={loading}
          tab={tab}
          onTabChange={setTab}
          onSelect={setSelected}
        />

        <section>
          <div className="mt-6">
            <DriverDeliveryDetail
              delivery={selected}
              onRegister={openResult}
              onAgeVerification={registerAgeVerification}
              ageVerificationSaving={submitting}
            />
          </div>
          {selected && token && (
            <div className="mt-4">
              <LocationRegistrationForm
                orderId={selected.pedidoId}
                token={token}
              />
              <CheckpointQrScanner
                pedidoId={selected.pedidoId}
                token={token}
                onRegistered={refresh}
              />
            </div>
          )}
        </section>
      </div>

      <Dialog
        open={Boolean(selected)}
        onOpenChange={(open) => !open && setSelected(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Registrar resultado</DialogTitle>
            <DialogDescription>
              {selected
                ? `Pedido ${selected.numeroPedido}. Esta acción actualiza la trazabilidad.`
                : ""}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <fieldset className="grid gap-2">
              <legend className="text-sm font-medium">Resultado</legend>
              {(Object.keys(resultLabels) as DeliveryResult[]).map((option) => (
                <label
                  key={option}
                  className="flex min-h-11 items-center gap-3 rounded-lg border p-3"
                >
                  <input
                    type="radio"
                    name="result"
                    aria-label={resultLabels[option]}
                    value={option}
                    checked={result === option}
                    onChange={() => {
                      setResult(option);
                      setNovelty("");
                    }}
                  />
                  {resultLabels[option]}
                </label>
              ))}
            </fieldset>
            {result !== "ENTREGADO" && (
              <>
                <label
                  className="grid gap-2 text-sm font-medium"
                  htmlFor="delivery-novelty"
                >
                  Motivo
                  <select
                    id="delivery-novelty"
                    aria-label="Motivo del resultado"
                    className="h-11 rounded-md border bg-background px-3 font-normal"
                    value={novelty}
                    onChange={(event) => setNovelty(event.target.value)}
                  >
                    <option value="">Selecciona un motivo</option>
                    {availableNovelties.map((option) => (
                      <option key={option.codigo} value={option.codigo}>
                        {option.descripcion}
                      </option>
                    ))}
                  </select>
                </label>
                <label
                  className="grid gap-2 text-sm font-medium"
                  htmlFor="delivery-reason"
                >
                  Descripción
                  <textarea
                    id="delivery-reason"
                    aria-label="Descripción de la novedad"
                    className="min-h-24 rounded-md border bg-background p-3 font-normal"
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder="Describe la novedad"
                  />
                </label>
              </>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSelected(null)}>
              Cancelar
            </Button>
            <Button disabled={submitting} onClick={() => void submitResult()}>
              {submitting ? "Guardando..." : "Confirmar registro"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
