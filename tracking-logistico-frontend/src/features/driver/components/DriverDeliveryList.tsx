import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { DriverDelivery } from "@/types/api";

const statusLabels: Record<string, string> = {
  PENDIENTE: "Pendiente",
  ENTREGADO: "Entregado",
  FALLIDA: "Entrega fallida",
  DEVOLUCION_AL_REMITENTE: "Devolución al remitente",
  CANCELADA: "Cancelada",
};

function deliveryBadge(state: string) {
  if (state === "ENTREGADO")
    return "border-primary/30 bg-primary/10 text-primary";
  if (state === "FALLIDA" || state === "DEVOLUCION_AL_REMITENTE")
    return "border-destructive/30 bg-destructive/10 text-destructive";
  return "border-secondary bg-secondary text-secondary-foreground";
}

interface DriverDeliveryListProps {
  deliveries: DriverDelivery[];
  loading: boolean;
  tab: "pending" | "completed";
  onTabChange: (tab: "pending" | "completed") => void;
  onSelect: (delivery: DriverDelivery) => void;
}

export function DriverDeliveryList({
  deliveries,
  loading,
  tab,
  onTabChange,
  onSelect,
}: DriverDeliveryListProps) {
  const pending = deliveries.filter(
    (item) => item.estadoParada === "PENDIENTE",
  );
  const completed = deliveries.filter(
    (item) => item.estadoParada !== "PENDIENTE",
  );
  const visible = tab === "pending" ? pending : completed;

  return (
    <section>
      <div className="flex gap-2 border-b border-border">
        <button
          type="button"
          aria-selected={tab === "pending"}
          className={`px-4 py-3 text-sm font-semibold ${tab === "pending" ? "border-b-2 border-primary text-primary" : "text-muted-foreground"}`}
          onClick={() => onTabChange("pending")}
        >
          Pendientes ({pending.length})
        </button>
        <button
          type="button"
          aria-selected={tab === "completed"}
          className={`px-4 py-3 text-sm font-semibold ${tab === "completed" ? "border-b-2 border-primary text-primary" : "text-muted-foreground"}`}
          onClick={() => onTabChange("completed")}
        >
          Completadas ({completed.length})
        </button>
      </div>
      <div className="mt-4 grid gap-3">
        {loading &&
          [1, 2, 3].map((item) => (
            <Skeleton key={item} className="h-36 rounded-xl" />
          ))}
        {!loading &&
          visible.map((delivery) => (
            <Card key={delivery.pedidoId}>
              <CardContent className="pt-6">
                <button
                  className="w-full text-left"
                  onClick={() => onSelect(delivery)}
                  aria-label={`Ver información de ${delivery.numeroPedido}`}
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold">#{delivery.numeroPedido}</p>
                      <p className="text-xs text-muted-foreground">
                        {delivery.numeroTracking}
                      </p>
                    </div>
                    <Badge className={deliveryBadge(delivery.estadoParada)}>
                      {statusLabels[delivery.estadoParada] ??
                        delivery.estadoParada}
                    </Badge>
                  </div>
                  <p className="mt-2 text-sm">{delivery.destinatarioNombre}</p>
                  <p className="text-sm text-muted-foreground">
                    {delivery.direccionDestino}, {delivery.ciudadDestino}
                  </p>
                </button>
                {delivery.estadoParada === "PENDIENTE" && (
                  <Button
                    className="mt-2 w-full sm:w-auto"
                    onClick={() => onSelect(delivery)}
                  >
                    Ver detalle y registrar
                  </Button>
                )}
              </CardContent>
            </Card>
          ))}
        {!loading && !visible.length && (
          <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">
            No hay entregas en esta sección.
          </p>
        )}
      </div>
    </section>
  );
}
