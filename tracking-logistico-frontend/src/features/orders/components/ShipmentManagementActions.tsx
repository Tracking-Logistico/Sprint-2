import { useState } from "react";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, getApiError } from "@/lib/api";
import type { OrderStatus, ShipmentManagementResponse } from "@/types/api";

type Action = "PAUSAR" | "REANUDAR" | "CANCELAR";

const finalStates = new Set<OrderStatus>([
  "ENTREGADO",
  "DEVOLUCION_AL_REMITENTE",
  "ENTREGA_FALLIDA_CERRADA",
  "CANCELADO",
]);

export function ShipmentManagementActions({
  pedidoId,
  estado,
  token,
  onChanged,
}: {
  pedidoId: number;
  estado: OrderStatus;
  token: string | null;
  onChanged?: (response: ShipmentManagementResponse) => void;
}) {
  const [action, setAction] = useState<Action | null>(null);
  const [motivo, setMotivo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const canCancel = !finalStates.has(estado);
  const canPause = canCancel && estado !== "PAUSADO";
  const canResume = estado === "PAUSADO";
  if (!canPause && !canResume && !canCancel) return null;

  const actionLabel =
    action === "PAUSAR"
      ? "Pausar envío"
      : action === "REANUDAR"
        ? "Reanudar envío"
        : "Cancelar envío";

  function open(actionToOpen: Action) {
    setError("");
    setMotivo("");
    setAction(actionToOpen);
  }

  async function submit() {
    if (!token || !action || saving) return;
    if ((action === "PAUSAR" || action === "CANCELAR") && !motivo.trim()) {
      setError("Escribe un motivo para continuar.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const response =
        action === "PAUSAR"
          ? await api.pauseShipment(pedidoId, motivo.trim(), token)
          : action === "REANUDAR"
            ? await api.resumeShipment(pedidoId, token)
            : await api.cancelShipment(pedidoId, motivo.trim(), token);
      setAction(null);
      onChanged?.(response);
      toast.success(
        response.numeroTrackingDevolucion
          ? `Envío ${action === "CANCELAR" ? "cancelado" : "actualizado"}. Devolución: ${response.numeroTrackingDevolucion}.`
          : `Envío ${action === "PAUSAR" ? "pausado" : "reanudado"} correctamente.`,
      );
    } catch (err) {
      setError(getApiError(err, "No fue posible actualizar el envío."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {canPause && (
          <Button size="sm" variant="outline" onClick={() => open("PAUSAR")}>
            Pausar
          </Button>
        )}
        {canResume && (
          <Button size="sm" variant="outline" onClick={() => open("REANUDAR")}>
            Reanudar
          </Button>
        )}
        {canCancel && (
          <Button
            size="sm"
            variant="destructive"
            onClick={() => open("CANCELAR")}
          >
            Cancelar
          </Button>
        )}
      </div>
      <Dialog
        open={action !== null}
        onOpenChange={(openState) => !openState && !saving && setAction(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar: {actionLabel}</DialogTitle>
            <DialogDescription>
              Esta acción quedará registrada en el historial del envío.
            </DialogDescription>
          </DialogHeader>
          {(action === "PAUSAR" || action === "CANCELAR") && (
            <div className="grid gap-2">
              <Label htmlFor={`shipment-action-reason-${pedidoId}`}>
                Motivo
              </Label>
              <Input
                id={`shipment-action-reason-${pedidoId}`}
                value={motivo}
                onChange={(event) => setMotivo(event.target.value)}
                maxLength={500}
                required
                disabled={saving}
              />
            </div>
          )}
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setAction(null)}
              disabled={saving}
            >
              Volver
            </Button>
            <Button onClick={() => void submit()} disabled={saving}>
              {saving ? "Procesando..." : "Confirmar"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
