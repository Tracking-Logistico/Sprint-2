import { AlertCircle, Phone } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useDriverDeliveryDetail } from "@/features/driver/hooks/useDriverDeliveryDetail";
import type { AgeVerificationResult, DriverDelivery } from "@/types/api";

interface DriverDeliveryDetailProps {
  delivery: DriverDelivery | null;
  onRegister: (delivery: DriverDelivery) => void;
  onAgeVerification: (
    pedidoId: number,
    resultado: AgeVerificationResult,
  ) => Promise<void>;
  ageVerificationSaving: boolean;
}

export function DriverDeliveryDetail({
  delivery,
  onRegister,
  onAgeVerification,
  ageVerificationSaving,
}: DriverDeliveryDetailProps) {
  const { detail, loading, error } = useDriverDeliveryDetail(
    delivery?.pedidoId ?? null,
  );

  if (!delivery) {
    return (
      <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">
        Selecciona una entrega para consultar sus datos.
      </p>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Información del envío</CardTitle>
      </CardHeader>
      <CardContent>
        {loading && <Skeleton className="h-48 rounded-lg" />}
        {error && (
          <div
            role="alert"
            className="flex gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
          >
            <AlertCircle className="size-4 shrink-0" />
            {error}
          </div>
        )}
        {!loading && !error && detail && (
          <>
            <div>
              <p className="font-semibold">{detail.numeroPedido}</p>
              <p className="text-sm text-muted-foreground">
                {detail.numeroTracking}
              </p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Destino
              </p>
              <p>
                {detail.direccionDestino}, {detail.ciudadDestino}
              </p>
              {detail.codigoPostalDestino && (
                <p className="text-sm text-muted-foreground">
                  Código postal: {detail.codigoPostalDestino}
                </p>
              )}
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Destinatario
              </p>
              <p>{detail.destinatarioNombre}</p>
              <a
                className="flex items-center gap-2 text-primary underline"
                href={`tel:${detail.destinatarioTelefono}`}
              >
                <Phone className="size-4" /> {detail.destinatarioTelefono}
              </a>
            </div>
            {detail.indicacionesAcceso && (
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  Indicaciones de acceso
                </p>
                <p>{detail.indicacionesAcceso}</p>
              </div>
            )}
            {detail.requiereVerificacionEdad && (
              <div className="space-y-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-amber-950">
                <p className="font-semibold">
                  Requiere verificación de edad (18+)
                </p>
                <p className="text-sm">
                  Registra el resultado antes de cerrar la entrega.
                </p>
                {!detail.resultadoVerificacionEdad ? (
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Button
                      disabled={ageVerificationSaving}
                      onClick={() =>
                        void onAgeVerification(detail.pedidoId, "CUMPLE")
                      }
                    >
                      Mayor de edad verificado
                    </Button>
                    <Button
                      variant="destructive"
                      disabled={ageVerificationSaving}
                      onClick={() =>
                        void onAgeVerification(detail.pedidoId, "NO_CUMPLE")
                      }
                    >
                      Verificación no aprobada
                    </Button>
                  </div>
                ) : (
                  <p className="text-sm font-medium">
                    Resultado registrado:{" "}
                    {detail.resultadoVerificacionEdad === "CUMPLE"
                      ? "Mayor de edad verificado"
                      : "Verificación no aprobada"}
                  </p>
                )}
              </div>
            )}
            {detail.estadoParada === "PENDIENTE" && (
              <Button
                className="w-full sm:w-auto"
                disabled={Boolean(
                  detail.requiereVerificacionEdad &&
                  !detail.resultadoVerificacionEdad,
                )}
                onClick={() => onRegister(delivery)}
              >
                Registrar resultado
              </Button>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
