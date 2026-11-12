import type { FormEvent } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { FormField } from "@/components/shared/FormField";
import type { ReceiveOrderPayload, ServiceType } from "@/types/api";
interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  form: ReceiveOrderPayload;
  update: <K extends keyof ReceiveOrderPayload>(
    key: K,
    value: ReceiveOrderPayload[K],
  ) => void;
  submit: (event: FormEvent<HTMLFormElement>) => void;
  saving: boolean;
  editing: boolean;
  onCancelEdit: () => void;
}
export function RequestShipmentDialog({
  open,
  onOpenChange,
  form,
  update,
  submit,
  saving,
  editing,
  onCancelEdit,
}: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[calc(100dvh-1rem)] max-w-3xl flex-col gap-0 overflow-hidden p-0 sm:max-h-[calc(100dvh-2rem)]">
        <DialogHeader className="shrink-0 border-b px-6 py-5">
          <DialogTitle>
            {editing ? "Corregir pedido" : "Solicitar envío"}
          </DialogTitle>
          <DialogDescription>
            La prioridad inicial se sugiere según el servicio; la confirma un
            operador.
          </DialogDescription>
        </DialogHeader>
        <ScrollArea className="flex-1 px-6 py-5">
          <form id="order-form" onSubmit={submit} className="space-y-5">
            <h3 className="text-sm font-semibold">Origen y remitente</h3>
            <FormField
              label="Dirección de recogida"
              name="direccionOrigen"
              required
              minLength={8}
              maxLength={250}
              placeholder="Calle y número o referencia rural"
              value={form.direccionOrigen}
              onChange={(e) => update("direccionOrigen", e.target.value)}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                label="Ciudad de origen"
                name="ciudadOrigen"
                required
                maxLength={100}
                value={form.ciudadOrigen}
                onChange={(e) => update("ciudadOrigen", e.target.value)}
              />
              <FormField
                label="Código postal (si aplica)"
                name="codigoPostalOrigen"
                maxLength={12}
                value={form.codigoPostalOrigen ?? ""}
                onChange={(e) => update("codigoPostalOrigen", e.target.value)}
              />
            </div>
            <FormField
              label="Tu teléfono de contacto"
              name="remitenteTelefono"
              type="tel"
              required
              pattern="\+?[1-9][0-9]{7,14}"
              placeholder="+573001234567"
              value={form.remitenteTelefono}
              onChange={(e) => update("remitenteTelefono", e.target.value)}
            />
            <Separator />
            <h3 className="text-sm font-semibold">Destino y destinatario</h3>
            <FormField
              label="Dirección de entrega"
              name="direccionDestino"
              required
              minLength={8}
              maxLength={250}
              placeholder="Calle y número o referencia rural"
              value={form.direccionDestino}
              onChange={(e) => update("direccionDestino", e.target.value)}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                label="Ciudad de destino"
                name="ciudadDestino"
                required
                maxLength={100}
                value={form.ciudadDestino}
                onChange={(e) => update("ciudadDestino", e.target.value)}
              />
              <FormField
                label="Código postal (si aplica)"
                name="codigoPostalDestino"
                maxLength={12}
                value={form.codigoPostalDestino ?? ""}
                onChange={(e) => update("codigoPostalDestino", e.target.value)}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                label="Nombre del destinatario"
                name="destinatarioNombre"
                required
                value={form.destinatarioNombre}
                onChange={(e) => update("destinatarioNombre", e.target.value)}
              />
              <FormField
                label="Teléfono destinatario"
                name="destinatarioTelefono"
                type="tel"
                pattern="\+?[1-9][0-9]{7,14}"
                required
                placeholder="+573001234567"
                value={form.destinatarioTelefono}
                onChange={(e) => update("destinatarioTelefono", e.target.value)}
              />
            </div>
            <Separator />
            <h3 className="text-sm font-semibold">Paquete y servicio</h3>
            <FormField
              label="Descripción del contenido"
              name="descripcionPaquete"
              maxLength={255}
              required
              value={form.descripcionPaquete}
              onChange={(e) => update("descripcionPaquete", e.target.value)}
            />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              {(
                [
                  ["pesoKg", "Peso (kg)"],
                  ["largoCm", "Largo (cm)"],
                  ["anchoCm", "Ancho (cm)"],
                  ["altoCm", "Alto (cm)"],
                ] as const
              ).map(([key, label]) => (
                <FormField
                  key={key}
                  label={label}
                  name={key}
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  value={form[key] || ""}
                  onChange={(e) => update(key, Number(e.target.value))}
                />
              ))}
            </div>
            <div>
              <label className="text-sm font-medium" htmlFor="service">
                Modalidad de servicio
              </label>
              <select
                id="service"
                className="mt-2 h-11 w-full rounded-lg border border-input bg-background px-3 text-sm"
                value={form.tipoServicio}
                onChange={(e) =>
                  update("tipoServicio", e.target.value as ServiceType)
                }
              >
                <option value="ESTANDAR">Estándar (prioridad media)</option>
                <option value="EXPRESS">Express (prioridad alta)</option>
                <option value="PROGRAMADO">Programado (prioridad baja)</option>
              </select>
            </div>
            <div className="flex items-start gap-3 rounded-lg border p-3">
              <Checkbox
                id="requiereVerificacionEdad"
                checked={Boolean(form.requiereVerificacionEdad)}
                onCheckedChange={(checked) =>
                  update("requiereVerificacionEdad", checked === true)
                }
              />
              <Label
                htmlFor="requiereVerificacionEdad"
                className="cursor-pointer text-sm leading-5"
              >
                Verificación de edad obligatoria (18+)
              </Label>
            </div>
          </form>
        </ScrollArea>
        <DialogFooter className="shrink-0 border-t bg-background px-6 py-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              onCancelEdit();
              onOpenChange(false);
            }}
          >
            Cancelar
          </Button>
          <Button type="submit" form="order-form" disabled={saving}>
            {saving
              ? "Guardando..."
              : editing
                ? "Enviar corrección"
                : "Solicitar envío"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
