import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, getApiError } from "@/lib/api";
import type { LocationStage } from "@/types/api";

const stages: { value: LocationStage; label: string }[] = [
  { value: "EN_BODEGA_ORIGEN", label: "En bodega origen" },
  { value: "EN_TRANSITO_VEHICULO", label: "En tránsito en vehículo" },
  { value: "LLEGADO_OFICINA_DESTINO", label: "Llegado a oficina destino" },
  { value: "EN_PUNTO_ENTREGA", label: "En punto de entrega" },
];

export function LocationRegistrationForm({
  orderId,
  token,
}: {
  orderId: number;
  token: string;
}) {
  const [stage, setStage] = useState<LocationStage>("EN_BODEGA_ORIGEN");
  const [cityId, setCityId] = useState("");
  const [officeId, setOfficeId] = useState("");
  const [pointId, setPointId] = useState("");
  const [vehicleId, setVehicleId] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function submit() {
    const city = Number(cityId);
    if (!Number.isInteger(city) || city < 1) {
      setError("Ingresa el identificador de una ciudad válido.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      await api.registerLocation(
        orderId,
        {
          etapa: stage,
          origen: "SELECTOR",
          idEvento: crypto.randomUUID(),
          ciudadId: city,
          oficinaId: officeId ? Number(officeId) : undefined,
          puntoEntregaId: pointId ? Number(pointId) : undefined,
          vehiculoId: vehicleId || undefined,
          fechaEvento: new Date().toISOString(),
        },
        token,
      );
      setMessage("Evento de ubicación registrado.");
      setOfficeId("");
      setPointId("");
      setVehicleId("");
    } catch (err) {
      setError(getApiError(err, "El backend rechazó el evento de ubicación."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="mt-4 space-y-3 rounded-xl border bg-muted/30 p-4">
      <div>
        <h3 className="font-semibold">Registrar ubicación</h3>
        <p className="text-sm text-muted-foreground">
          Solo se muestran las cuatro etapas aceptadas por el backend. Los
          catálogos de ciudad, oficina y punto estarán disponibles cuando el
          orquestador exponga geografía.
        </p>
      </div>
      <select
        aria-label="Estado de ubicación"
        className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
        value={stage}
        onChange={(event) => setStage(event.target.value as LocationStage)}
      >
        {stages.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          aria-label="Identificador de ciudad"
          placeholder="ID ciudad"
          inputMode="numeric"
          value={cityId}
          onChange={(event) => setCityId(event.target.value)}
        />
        <Input
          aria-label="Identificador de oficina"
          placeholder="ID oficina (opcional)"
          inputMode="numeric"
          value={officeId}
          onChange={(event) => setOfficeId(event.target.value)}
        />
        <Input
          aria-label="Identificador de punto de entrega"
          placeholder="ID punto de entrega (opcional)"
          inputMode="numeric"
          value={pointId}
          onChange={(event) => setPointId(event.target.value)}
        />
        <Input
          aria-label="Identificador de vehículo"
          placeholder="Vehículo (opcional)"
          value={vehicleId}
          onChange={(event) => setVehicleId(event.target.value)}
        />
      </div>
      <Button onClick={() => void submit()} disabled={saving}>
        {saving ? "Registrando..." : "Registrar ubicación"}
      </Button>
      {message && (
        <p role="status" className="text-sm text-primary">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </section>
  );
}
