import { useCallback, useEffect, useRef, useState } from "react";
import { anomaliesApi, getApiError } from "@/lib/api";
import { useAuthStore } from "@/stores/authStore";
import type { Anomaly, AnomalyPage, AnomalyState, AnomalyType, PendingReceipt } from "@/types/api";
import { Button } from "@/components/ui/button";
import { AnomalyParametersForm } from "@/features/orders/components/AnomalyParametersForm";

const labels: Record<AnomalyType, string> = {
  PERMANENCIA_EXCESIVA: "Permanencia excesiva",
  INACTIVIDAD_REPARTO: "Inactividad en reparto",
  REINTENTOS_ACUMULADOS: "Reintentos acumulados",
  SIN_CONFIRMACION_RECEPCION: "Sin confirmación de recepción",
};

export function AnomaliesPage() {
  const token = useAuthStore((state) => state.accessToken);
  const [state, setState] = useState<AnomalyState>("ACTIVA");
  const [type, setType] = useState<AnomalyType | "">("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AnomalyPage | null>(null);
  const [detail, setDetail] = useState<Anomaly | null>(null);
  const [receipts, setReceipts] = useState<PendingReceipt[]>([]);
  const [receiptPage, setReceiptPage] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const requestNumber = useRef(0);

  const refresh = useCallback(async () => {
    if (!token) return;
    const currentRequest = ++requestNumber.current;
    setLoading(true);
    try {
      const [next, arrivals] = await Promise.all([
        anomaliesApi.list(state, type, page, token), anomaliesApi.pendingReceipts(receiptPage, token),
      ]);
      if (currentRequest !== requestNumber.current) return;
      setData(next); setReceipts(arrivals); setError("");
    } catch (err) { if (currentRequest === requestNumber.current) setError(getApiError(err, "No pudimos consultar las alertas.")); }
    finally { if (currentRequest === requestNumber.current) setLoading(false); }
  }, [token, state, type, page, receiptPage]);

  useEffect(() => {
    const requests = requestNumber;
    const initial = window.setTimeout(() => void refresh(), 0);
    const timer = window.setInterval(() => void refresh(), 60000);
    return () => { window.clearTimeout(initial); window.clearInterval(timer); requests.current++; };
  }, [refresh]);

  async function close(anomaly: Anomaly, next: "RESUELTA" | "DESCARTADA") {
    if (!token || busy || !window.confirm(`¿Marcar la alerta de ${anomaly.numeroTracking} como ${next.toLowerCase()}?`)) return;
    setBusy(true);
    try { await anomaliesApi.close(anomaly.id, next, token); setDetail(null); await refresh(); }
    catch (err) { setError(getApiError(err, "No pudimos actualizar la alerta.")); }
    finally { setBusy(false); }
  }

  async function confirm(arrival: PendingReceipt) {
    if (!token || busy || !window.confirm(`¿Confirmar que recibiste ${arrival.numeroTracking} en la oficina?`)) return;
    setBusy(true);
    try { await anomaliesApi.confirmReceipt(arrival.envioId, arrival.eventoLlegadaId, token); await refresh(); }
    catch (err) { setError(getApiError(err, "No pudimos confirmar la recepción.")); }
    finally { setBusy(false); }
  }

  async function openDetail(id: number) {
    if (!token) return;
    try { setDetail(await anomaliesApi.detail(id, token)); }
    catch (err) { setError(getApiError(err, "No pudimos consultar el detalle.")); }
  }

  return <div className="mx-auto max-w-6xl space-y-6">
    <header><h1 className="text-3xl font-semibold">Alertas operativas</h1><p className="mt-2 text-muted-foreground">Revisa envíos en riesgo y confirma su recepción en destino.</p></header>
    {error && <p role="alert" className="rounded-lg border border-destructive p-4">{error}</p>}
    <section className="rounded-xl border bg-card p-5" aria-label="Anomalías">
      <div className="mb-5 flex flex-wrap items-end gap-4">
        <label className="space-y-1">Estado<select aria-label="Estado" className="block rounded border bg-background p-2" value={state} onChange={(e) => { setState(e.target.value as AnomalyState); setPage(0); }}>
          <option value="ACTIVA">Activas</option><option value="RESUELTA">Resueltas</option><option value="DESCARTADA">Descartadas</option>
        </select></label>
        <label className="space-y-1">Tipo<select aria-label="Tipo" className="block max-w-full rounded border bg-background p-2" value={type} onChange={(e) => { setType(e.target.value as AnomalyType | ""); setPage(0); }}>
          <option value="">Todos</option>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <Button onClick={() => void refresh()} disabled={loading || busy}>Actualizar</Button>
      </div>
      {loading && <p role="status">Actualizando alertas…</p>}
      {!loading && data?.contenido.length === 0 && <p>No hay alertas con estos filtros.</p>}
      <div className="grid gap-3 md:grid-cols-2">{data?.contenido.map((anomaly) => <article className="space-y-2 rounded-lg border p-4" key={anomaly.id}>
        <h2 className="font-semibold">{labels[anomaly.tipo]}</h2><p>{anomaly.numeroTracking}</p>
        <p className="text-sm">{anomaly.oficina ?? "Atención general"}</p>
        <p className="text-sm">Desde {new Date(anomaly.inicioUtc).toLocaleString("es-CO")}</p>
        <div className="flex flex-wrap gap-2"><Button variant="outline" onClick={() => void openDetail(anomaly.id)}>Ver detalle</Button>
          {anomaly.estado === "ACTIVA" && <><Button disabled={busy} onClick={() => void close(anomaly, "RESUELTA")}>Resolver</Button>
            <Button variant="outline" disabled={busy} onClick={() => void close(anomaly, "DESCARTADA")}>Descartar</Button></>}
        </div>
      </article>)}</div>
      <div className="mt-4 flex items-center gap-3"><Button variant="outline" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>Anterior</Button>
        <span>Página {page + 1}</span><Button variant="outline" disabled={loading || !data || (page + 1) * 20 >= data.total} onClick={() => setPage(page + 1)}>Siguiente</Button></div>
      {detail && <aside aria-label="Detalle de alerta" className="mt-4 space-y-2 rounded-lg bg-muted p-4">
        <h2 className="font-semibold">Detalle de {detail.numeroTracking}</h2>
        <p>Origen operativo — {detail.eventoOrigen}</p>
        {detail.umbralHoras !== null && <p>Umbral al detectar — {detail.umbralHoras} horas</p>}
        {detail.intentos !== null && <p>Intentos fallidos — {detail.intentos}</p>}
        <p>Detección — {new Date(detail.creadaEn).toLocaleString("es-CO")}</p>
        {detail.cerradaEn && <p>Cierre — {new Date(detail.cerradaEn).toLocaleString("es-CO")} · {detail.cerradaPor === null ? "Sistema" : `Usuario ${detail.cerradaPor}`} · {detail.origenCierre}</p>}
        <Button variant="outline" onClick={() => setDetail(null)}>Cerrar detalle</Button>
      </aside>}
    </section>
    <section className="space-y-3 rounded-xl border bg-card p-5" aria-label="Recepciones pendientes">
      <h2 className="text-xl font-semibold">Recepciones pendientes en oficina</h2>
      {receipts.length === 0 && <p>No hay recepciones pendientes en esta página.</p>}
      {receipts.map((arrival) => <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3" key={arrival.eventoLlegadaId}>
        <p>{arrival.numeroTracking} · {arrival.oficina ?? "Oficina destino"}</p>
        <Button disabled={busy} onClick={() => void confirm(arrival)}>Confirmar recepción en oficina</Button>
      </div>)}
      <div className="flex gap-3"><Button variant="outline" disabled={receiptPage === 0 || loading} onClick={() => setReceiptPage(receiptPage - 1)}>Recepciones anteriores</Button>
        <Button variant="outline" disabled={receipts.length < 20 || loading} onClick={() => setReceiptPage(receiptPage + 1)}>Más recepciones</Button></div>
    </section>
    <AnomalyParametersForm />
  </div>;
}
