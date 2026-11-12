import { ArrowLeft } from "lucide-react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useShipmentTracking } from "@/features/orders/hooks/useShipmentTracking";
import { ShipmentTrackingView } from "@/features/orders/components/ShipmentTrackingView";
import { useAuthStore } from "@/stores/authStore";
import { usePageMeta } from "@/hooks/usePageMeta";
import { toast } from "sonner";

export function ShipmentTrackingPage() {
  usePageMeta(
    "Seguimiento",
    "Consulta el estado y los movimientos de tu envío.",
  );
  const { numeroTracking } = useParams<{ numeroTracking: string }>();
  const [searchParams] = useSearchParams();
  const token = useAuthStore((state) => state.accessToken);
  const trackingCode = numeroTracking ?? searchParams.get("code") ?? undefined;
  const tracking = useShipmentTracking(trackingCode, token);

  return (
    <main className="mx-auto max-w-3xl px-5 py-8 sm:px-8">
      <Button
        variant="ghost"
        render={<Link to="/panel/pedidos" />}
        className="mb-5 -ml-3"
      >
        <ArrowLeft className="size-4" /> Volver a Mis envíos
      </Button>
      <header className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
          Mis envíos
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">
          Seguimiento del envío
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Consulta el estado actual y el historial de movimientos.
        </p>
      </header>
      {tracking.error && (
        <div className="space-y-4">
          <Alert variant="destructive">
            <AlertTitle>No se pudo encontrar el envío</AlertTitle>
            <AlertDescription>{tracking.error}</AlertDescription>
          </Alert>
          <Button render={<Link to="/panel/pedidos" />}>
            Volver a Mis envíos
          </Button>
        </div>
      )}
      {!tracking.error && (
        <ShipmentTrackingView
          {...tracking}
          token={token}
          reload={tracking.reload}
          submitRescheduling={async (fecha) => {
            await tracking.submitRescheduling(fecha);
            toast.success("Entrega reprogramada correctamente.");
          }}
        />
      )}
    </main>
  );
}
