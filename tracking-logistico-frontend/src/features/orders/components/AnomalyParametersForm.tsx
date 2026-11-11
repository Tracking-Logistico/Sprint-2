import { useState } from "react";
import { anomaliesApi, getApiError } from "@/lib/api";
import type { AnomalyParameters } from "@/types/api";
import { useAuthStore } from "@/stores/authStore";
import { Button } from "@/components/ui/button";

export function AnomalyParametersForm() {
  const token = useAuthStore((state) => state.accessToken);
  const [key, setKey] = useState("");
  const [values, setValues] = useState<AnomalyParameters | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  async function load() {
    if (!token || !key || busy) return;
    setBusy(true); setMessage("");
    try { setValues(await anomaliesApi.parameters(key, token)); }
    catch (err) { setValues(null); setMessage(getApiError(err, "No fue posible acceder a los parámetros.")); }
    finally { setBusy(false); }
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!values || !token || !key || busy) return;
    if (![values.permanenciaHoras, values.inactividadHoras, values.recepcionHoras].every((v) => Number.isInteger(v) && v >= 1 && v <= 168)) {
      setMessage("Los umbrales deben ser horas enteras entre 1 y 168."); return;
    }
    if (!window.confirm("¿Guardar los umbrales? Se usarán en la siguiente evaluación.")) return;
    setBusy(true);
    try {
      await anomaliesApi.saveParameters(values, key, token);
      setValues(null); setKey(""); setMessage("Parámetros guardados. Se aplicarán en la siguiente evaluación.");
    } catch (err) { setMessage(getApiError(err, "No fue posible guardar. Consulta nuevamente los parámetros.")); }
    finally { setBusy(false); }
  }

  return <section className="space-y-4 rounded-xl border bg-card p-5" aria-label="Administración de umbrales">
    <h2 className="text-xl font-semibold">Administración de umbrales</h2>
    <p className="text-sm text-muted-foreground">Requiere autorización DBA y tu sesión de operador. El cambio quedará registrado con tu usuario.</p>
    <label className="block">Clave DBA<input aria-label="Clave DBA" type="password" disabled={busy} autoComplete="off" maxLength={512} value={key}
      onChange={(e) => { setKey(e.target.value); setValues(null); }} className="mt-1 block w-full max-w-sm rounded border bg-background p-2" /></label>
    <Button disabled={!key || busy} onClick={() => void load()}>Consultar parámetros</Button>
    {values && <form className="space-y-4" onSubmit={(event) => void save(event)}>
      <div className="grid gap-4 sm:grid-cols-3">{([
        ["permanenciaHoras", "Permanencia X"], ["inactividadHoras", "Inactividad Y"], ["recepcionHoras", "Recepción Z"],
      ] as const).map(([field, label]) => <label key={field}>{label} (horas)<input aria-label={label} type="number" min={1} max={168} step={1} required
        className="mt-1 block w-full rounded border bg-background p-2" value={values[field]}
        onChange={(e) => setValues({ ...values, [field]: Number(e.target.value) })} /></label>)}</div>
      <Button type="submit" disabled={busy}>Guardar umbrales</Button>
    </form>}
    {message && <p role="status">{message}</p>}
  </section>;
}
