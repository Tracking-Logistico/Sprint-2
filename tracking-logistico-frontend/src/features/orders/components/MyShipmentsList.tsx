import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Link } from "react-router-dom";
import type { MyOrderResponse } from "@/types/api";

const statusLabels: Record<string, string> = {
  SOLICITADO: "Pendiente de validación",
  CORRECCION_SOLICITADA: "Corrección solicitada",
  CREADO: "Despacho aprobado",
  RECIBIDO_EN_ORIGEN: "Recibido en origen",
  EN_TRANSITO: "En tránsito",
  EN_REPARTO: "En reparto",
  ENTREGADO: "Entregado",
  RECHAZADO: "Rechazado",
  PAUSADO: "Pausado",
  CANCELADO: "Cancelado",
};

function formatDate(value?: string) {
  return value
    ? new Date(value).toLocaleDateString("es-CO", { dateStyle: "medium" })
    : "Pendiente de definir";
}

function StatusBadge({ value }: { value: string }) {
  return (
    <Badge
      className={
        value === "ENTREGADO"
          ? "border-primary/30 bg-primary/10 text-primary"
          : value === "RECHAZADO"
            ? "border-destructive/30 bg-destructive/10 text-destructive"
            : "border-border bg-muted text-muted-foreground"
      }
    >
      {statusLabels[value] ?? value.replaceAll("_", " ")}
    </Badge>
  );
}

export function MyShipmentsList({
  shipments,
  loading,
  onCorrect,
}: {
  shipments: MyOrderResponse[];
  loading: boolean;
  onCorrect: (id: number) => void;
}) {
  if (loading)
    return (
      <div className="space-y-3" role="status" aria-label="Cargando envíos">
        {[1, 2, 3].map((item) => (
          <Skeleton key={item} className="h-32 w-full rounded-2xl" />
        ))}
      </div>
    );
  return (
    <div className="space-y-3">
      {shipments.map((shipment) => (
        <Card key={shipment.id}>
          <CardContent className="grid gap-4 p-5 sm:grid-cols-[1fr_auto] sm:items-center">
            <div>
              <p className="font-mono text-sm font-semibold">
                {shipment.numeroPedido}
              </p>
              <p className="mt-2 text-sm text-foreground">
                <span className="font-medium">Remitente:</span>{" "}
                {shipment.remitenteNombre}
              </p>
              <p className="mt-1 text-sm text-foreground">
                <span className="font-medium">Destinatario:</span>{" "}
                {shipment.destinatarioNombre}
              </p>
              {shipment.numeroTracking && (
                <Link
                  to={`/panel/pedidos/seguimiento/${encodeURIComponent(shipment.numeroTracking)}`}
                  className="mt-2 inline-flex min-h-11 items-center rounded-md px-2 py-1 font-mono text-sm font-semibold text-primary underline decoration-primary/50 underline-offset-4 transition hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  Ver seguimiento: {shipment.numeroTracking}
                </Link>
              )}
              {shipment.numeroTrackingOrigenDevolucion && (
                <p className="mt-2 text-sm text-muted-foreground">
                  Devolución del envío{" "}
                  <Link
                    to={`/panel/pedidos/seguimiento/${encodeURIComponent(shipment.numeroTrackingOrigenDevolucion)}`}
                    className="font-mono font-semibold text-primary underline underline-offset-4"
                  >
                    {shipment.numeroTrackingOrigenDevolucion}
                  </Link>
                </p>
              )}
            </div>
            <div className="sm:text-right">
              <StatusBadge value={shipment.estado} />
              <p className="mt-2 text-sm text-muted-foreground">
                Entrega estimada:{" "}
                <span className="font-medium text-foreground">
                  {formatDate(shipment.fechaEstimadaEntrega)}
                </span>
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Creado: {formatDate(shipment.fechaCreacion)}
              </p>
              {shipment.estado === "CORRECCION_SOLICITADA" && (
                <Button
                  className="mt-3"
                  size="sm"
                  variant="outline"
                  onClick={() => onCorrect(shipment.id)}
                >
                  Corregir información
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
