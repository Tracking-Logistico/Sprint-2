import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { ShipmentTrackingResponse } from "@/types/api";
import { AnomalyBanner } from "./AnomalyBanner";
import { RescheduleDeliveryForm } from "./RescheduleDeliveryForm";

const statusLabels: Record<string, string> = {
  SOLICITADO: "Pendiente de validación",
  CORRECCION_SOLICITADA: "Corrección solicitada",
  CREADO: "Despacho aprobado",
  RECIBIDO_EN_ORIGEN: "Recibido en origen",
  EN_TRANSITO: "En tránsito",
  EN_REPARTO: "En reparto",
  ENTREGADO: "Entregado",
  RECHAZADO: "Rechazado",
  ENTREGA_FALLIDA: "Entrega fallida",
  ENTREGA_REPROGRAMADA: "Entrega reprogramada",
  DIRECCION_POR_VERIFICAR: "Dirección por verificar",
  DEVOLUCION_AL_REMITENTE: "Devolución al remitente",
  ENTREGA_FALLIDA_CERRADA: "Entrega fallida cerrada",
};

const locationLabels: Record<string, string> = {
  EN_BODEGA_ORIGEN: "En Bodega Origen",
  EN_TRANSITO_VEHICULO: "En Tránsito",
  LLEGADO_OFICINA_DESTINO: "Llegado a Oficina Destino",
  EN_PUNTO_ENTREGA: "En Punto de Entrega",
};

function formatDate(value?: string) {
  return value
    ? new Date(value).toLocaleString("es-CO", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "Pendiente de definir";
}

export function ShipmentTrackingView({
  shipment,
  loading,
  showSkeleton,
  error,
  reschedulingRange,
  rescheduling,
  reschedulingError,
  submitRescheduling,
}: {
  shipment: ShipmentTrackingResponse | null;
  loading: boolean;
  showSkeleton: boolean;
  error: string;
  reschedulingRange?: { desde: string; hasta: string } | null;
  rescheduling: boolean;
  reschedulingError: string;
  submitRescheduling: (fecha: string) => Promise<void>;
}) {
  if (loading || showSkeleton) {
    return (
      <div
        className="space-y-4"
        role="status"
        aria-label="Cargando seguimiento"
      >
        <Skeleton className="h-36 w-full rounded-2xl" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>No pudimos cargar el seguimiento</AlertTitle>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  if (!shipment) return null;

  return (
    <div className="space-y-4">
      <AnomalyBanner tracking={shipment.numeroTracking} />
      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center justify-between gap-3">
            <span>Pedido {shipment.numeroPedido}</span>
            <Badge>
              {statusLabels[shipment.estado] ??
                shipment.estado.replaceAll("_", " ")}
            </Badge>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">Número de seguimiento</p>
          <p className="mt-1 break-all font-mono text-base font-semibold">
            {shipment.numeroTracking}
          </p>
          <p className="mt-4 text-base font-medium text-foreground">
            {shipment.descripcionEstado}
          </p>
          {shipment.estado === "ENTREGA_REPROGRAMADA" &&
            shipment.fechaEntregaReprogramada && (
              <p className="mt-2 text-sm font-medium text-primary">
                Nueva fecha de entrega:{" "}
                {new Date(
                  `${shipment.fechaEntregaReprogramada}T00:00:00`,
                ).toLocaleDateString("es-CO", { dateStyle: "long" })}
              </p>
            )}
          <p className="mt-2 text-sm text-muted-foreground">
            Entrega estimada: {formatDate(shipment.fechaEstimadaEntrega)}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Historial de movimientos</CardTitle>
        </CardHeader>
        <CardContent>
          {shipment.movimientos.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Aún no hay movimientos registrados.
            </p>
          ) : (
            <div className="space-y-5 border-l-2 border-primary/30 pl-4">
              {shipment.movimientos.map((movement, index) => (
                <div
                  key={`${movement.fecha}-${movement.tipo}-${index}`}
                  className="relative"
                >
                  <span className="absolute -left-[1.65rem] top-1 size-3 rounded-full bg-primary ring-4 ring-card" />
                  <p className="font-medium">{movement.descripcion}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {formatDate(movement.fecha)}
                  </p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Trazabilidad de ubicación</CardTitle>
        </CardHeader>
        <CardContent>
          {!shipment.ubicaciones?.length ? (
            <p className="text-sm text-muted-foreground">
              Aún no hay eventos de ubicación registrados.
            </p>
          ) : (
            <ol className="space-y-5 border-l-2 border-primary/30 pl-4">
              {[...shipment.ubicaciones]
                .sort(
                  (left, right) =>
                    new Date(left.fechaEvento).getTime() -
                    new Date(right.fechaEvento).getTime(),
                )
                .map((location) => (
                  <li
                    key={location.id}
                    className="relative space-y-1"
                  >
                    <span className="absolute -left-[1.65rem] top-1 size-3 rounded-full bg-primary ring-4 ring-card" />
                    <p className="font-medium">
                      {locationLabels[location.etapa] ??
                        location.etapa.replaceAll("_", " ")}
                    </p>
                    <p className="text-sm">
                      {[
                        location.oficina,
                        location.puntoEntrega,
                        location.ciudad,
                        location.vehiculoId &&
                          `Vehículo ${location.vehiculoId}`,
                      ]
                        .filter(Boolean)
                        .join(" · ") || "Ubicación no especificada"}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {formatDate(location.fechaEvento)} · Origen:{" "}
                      {location.origen}
                    </p>
                  </li>
                ))}
            </ol>
          )}
        </CardContent>
      </Card>

      {shipment.novedad && (
        <Alert>
          <AlertTitle>{shipment.novedad.titulo}</AlertTitle>
          <AlertDescription>{shipment.novedad.mensaje}</AlertDescription>
        </Alert>
      )}
      {shipment.estado === "ENTREGA_FALLIDA" && reschedulingRange && (
        <RescheduleDeliveryForm
          desde={reschedulingRange.desde}
          hasta={reschedulingRange.hasta}
          loading={rescheduling}
          error={reschedulingError}
          onSubmit={submitRescheduling}
        />
      )}
      {shipment.estado === "ENTREGA_FALLIDA" &&
        !reschedulingRange &&
        reschedulingError && (
          <Alert variant="destructive">
            <AlertTitle>No pudimos consultar las fechas disponibles</AlertTitle>
            <AlertDescription>{reschedulingError}</AlertDescription>
          </Alert>
        )}
    </div>
  );
}
