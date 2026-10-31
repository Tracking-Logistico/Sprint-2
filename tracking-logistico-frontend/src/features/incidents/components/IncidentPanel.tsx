import { useState, type FormEvent } from "react";
import { History, LoaderCircle, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiError } from "@/lib/api";
import type { IncidentType, OrderStatus } from "@/types/api";
import { useIncidents } from "../hooks/useIncidents";
import { formatIncidentDate } from "../utils/incidentDate";

const inputClass =
  "h-11 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10";

const statusLabels: Record<string, string> = {
  DIRECCION_POR_VERIFICAR: "Dirección por verificar",
  ENTREGA_FALLIDA: "Entrega fallida",
  DEVOLUCION_AL_REMITENTE: "Devolución al remitente",
};

function statusName(status: OrderStatus | null) {
  return status
    ? (statusLabels[status] ?? status.replaceAll("_", " "))
    : "Sin cambio de estado";
}

function incidentError(error: unknown) {
  if (typeof error === "object" && error !== null && "status" in error) {
    const status = (error as { status?: number }).status;
    const code =
      "details" in error &&
      typeof (error as { details?: { code?: unknown } }).details?.code ===
        "string"
        ? (error as { details: { code: string } }).details.code
        : "";
    if (status === 401) return "Tu sesión expiró. Inicia sesión nuevamente.";
    if (status === 403) return "No tienes permiso para registrar incidencias.";
    if (status === 404) return "No se encontró el envío solicitado.";
    if (status === 409 && code === "ENVIO_FINALIZADO")
      return "Este envío ya finalizó y no admite nuevas incidencias.";
    if (status === 409 && code === "INCIDENCIA_NO_PERMITIDA")
      return "Esta incidencia no puede registrarse para el estado actual del envío.";
    if (status === 409)
      return "Otro operador ya registró una novedad sobre este envío.";
  }
  return getApiError(error, "No fue posible consultar las incidencias.");
}

export function IncidentPanel({
  orderId,
  token,
  types,
  typesLoading,
  typesError,
  onChanged,
}: {
  orderId: number;
  token: string | null;
  types: IncidentType[];
  typesLoading: boolean;
  typesError: unknown;
  onChanged: () => Promise<void>;
}) {
  const {
    data,
    loading,
    error: incidentsError,
    reload,
    register,
  } = useIncidents(orderId, token);
  const [open, setOpen] = useState(false);
  const [type, setType] = useState("");
  const [comment, setComment] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const selectedType = types.find((item) => item.codigo === type);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedType || saving) return;
    if (selectedType.requiereComentario && !comment.trim()) {
      setError("Agrega un comentario para este tipo de incidencia.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await register({
        tipo: selectedType.codigo,
        comentario: comment.trim() || undefined,
      });
      setComment("");
      setType("");
      setOpen(false);
      toast.success("Incidencia registrada y estado actualizado.");
      await onChanged();
    } catch (err) {
      const message = incidentError(err);
      setError(message);
      if (
        typeof err === "object" &&
        err !== null &&
        "status" in err &&
        (err as { status?: number }).status === 409
      ) {
        await reload();
        await onChanged();
      }
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="mt-5 border-t border-border pt-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold text-foreground">
            Incidencias del envío
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Registra una novedad usando el catálogo oficial.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => void reload()}
            disabled={loading}
          >
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            Actualizar
          </Button>
          <Button
            size="sm"
            onClick={() => setOpen((value) => !value)}
            disabled={typesLoading || !!typesError || loading || !data}
          >
            <Plus className="size-4" /> Registrar incidencia
          </Button>
        </div>
      </div>

      {typesError !== null && (
        <Alert variant="destructive" className="mt-3">
          {incidentError(typesError)}
        </Alert>
      )}
      {incidentsError !== null && (
        <Alert variant="destructive" className="mt-3">
          {incidentError(incidentsError)}
        </Alert>
      )}
      {error !== "" && (
        <Alert variant="destructive" className="mt-3">
          {error}
        </Alert>
      )}

      {open && (
        <form
          onSubmit={(event) => void submit(event)}
          className="mt-4 grid gap-4 rounded-xl bg-muted p-4"
        >
          <div>
            <label
              htmlFor={`incident-type-${orderId}`}
              className="text-sm font-medium"
            >
              Tipo de incidencia
            </label>
            <select
              id={`incident-type-${orderId}`}
              className={`${inputClass} mt-2`}
              value={type}
              onChange={(event) => {
                setType(event.target.value);
                setError("");
              }}
              required
            >
              <option value="">Selecciona un tipo</option>
              {types.map((item) => (
                <option key={item.codigo} value={item.codigo}>
                  {item.descripcion}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label
              htmlFor={`incident-comment-${orderId}`}
              className="text-sm font-medium"
            >
              Comentario{" "}
              {selectedType?.requiereComentario
                ? "(obligatorio)"
                : "(opcional)"}
            </label>
            <textarea
              id={`incident-comment-${orderId}`}
              className="mt-2 min-h-24 w-full resize-y rounded-lg border border-input bg-background p-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10"
              maxLength={500}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder="Describe la situación"
            />
            <p className="mt-1 text-right text-xs text-muted-foreground">
              {comment.length}/500
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={saving || !selectedType}>
              {saving ? (
                <LoaderCircle className="size-4 animate-spin" />
              ) : (
                <Plus className="size-4" />
              )}
              {saving ? "Registrando..." : "Registrar"}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={saving}
            >
              Cancelar
            </Button>
          </div>
        </form>
      )}

      <div className="mt-4">
        <p className="flex items-center gap-2 text-sm font-medium text-foreground">
          <History className="size-4" /> Historial
        </p>
        {loading && (
          <div className="mt-3 space-y-2">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        )}
        {!loading && data?.incidencias.length === 0 && (
          <p className="mt-3 text-sm text-muted-foreground">
            No hay incidencias registradas.
          </p>
        )}
        {!loading && data && data.incidencias.length > 0 && (
          <div className="mt-3 space-y-3">
            {data.incidencias.map((incident) => (
              <div
                key={incident.id}
                className="rounded-lg border border-border bg-card p-3 text-sm"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-medium">{incident.descripcionTipo}</p>
                  <Badge>{formatIncidentDate(incident.fecha)}</Badge>
                </div>
                {incident.comentario && (
                  <p className="mt-2 text-muted-foreground">
                    {incident.comentario}
                  </p>
                )}
                <p className="mt-2 text-xs text-muted-foreground">
                  Estado: {statusName(incident.estadoResultante)} · Usuario{" "}
                  {incident.reportadoPorUsuarioId}
                  {incident.numeroIntento
                    ? ` · Intento ${incident.numeroIntento}`
                    : ""}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
