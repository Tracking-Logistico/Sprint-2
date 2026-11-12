import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, getApiError } from "@/lib/api";
import type { TraceEvent, TracePage, TraceSummary } from "@/types/api";

function dateTime(value: string) {
  return new Date(value).toLocaleString("es-CO", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function download(file: { blob: Blob; filename: string }) {
  const url = URL.createObjectURL(file.blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = file.filename;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function TraceabilityPanel({
  orderId,
  token,
}: {
  orderId: number;
  token: string;
}) {
  const [type, setType] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [data, setData] = useState<TracePage | null>(null);
  const [summary, setSummary] = useState<TraceSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const filters = {
        tipo: type || undefined,
        desde: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
        hasta: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
      };
      const [next, latest] = await Promise.all([
        api.listTraceability(orderId, filters, token),
        api.traceSummary(orderId, token),
      ]);
      setData(next);
      setSummary(latest);
    } catch (err) {
      setError(getApiError(err, "No pudimos cargar la trazabilidad."));
    } finally {
      setLoading(false);
    }
  }, [from, orderId, token, to, type]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function exportFile(format: "pdf" | "csv") {
    setExporting(true);
    setError("");
    try {
      const file = await api.exportTraceability(
        orderId,
        format,
        {
          tipo: type || undefined,
          desde: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
          hasta: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
        },
        token,
      );
      download(file);
    } catch (err) {
      setError(getApiError(err, "No pudimos exportar la trazabilidad."));
    } finally {
      setExporting(false);
    }
  }

  return (
    <section className="mt-4 space-y-4 rounded-xl border bg-muted/30 p-4">
      <div>
        <h3 className="font-semibold">Historial cronológico</h3>
        <p className="text-sm text-muted-foreground">
          Consulta de solo lectura, ordenada desde el evento más reciente.
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        <Input
          aria-label="Tipo de evento"
          placeholder="Tipo de evento"
          value={type}
          onChange={(event) => setType(event.target.value)}
        />
        <Input
          aria-label="Desde"
          type="date"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
        />
        <Input
          aria-label="Hasta"
          type="date"
          value={to}
          onChange={(event) => setTo(event.target.value)}
        />
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={() => void load()} disabled={loading}>
          {loading ? "Consultando..." : "Aplicar filtros"}
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => void exportFile("pdf")}
          disabled={exporting}
        >
          Exportar PDF
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => void exportFile("csv")}
          disabled={exporting}
        >
          Exportar CSV
        </Button>
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      {summary?.ultimoPuntoControl && (
        <p className="text-sm">
          <strong>Último punto de control:</strong>{" "}
          {summary.ultimoPuntoControl.etapa} ·{" "}
          {dateTime(summary.ultimoPuntoControl.fechaEvento)}
        </p>
      )}
      {!loading && data?.content.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No hay eventos con los filtros seleccionados.
        </p>
      )}
      <ol className="space-y-3 border-l-2 border-primary/30 pl-4">
        {[...(data?.content ?? [])]
          .sort(
            (left, right) =>
              new Date(left.fecha).getTime() - new Date(right.fecha).getTime(),
          )
          .map((event: TraceEvent) => (
            <li key={event.id} className="relative">
              <span className="absolute left-[-1.4rem] top-1 size-2.5 rounded-full bg-primary" />
              <p className="font-medium">{event.tipoEvento}</p>
              <p className="text-sm">
                {event.detalle ?? "Sin detalle adicional"}
              </p>
              <p className="text-xs text-muted-foreground">
                {dateTime(event.fecha)} ·{" "}
                {event.ubicacion ?? "Ubicación no especificada"}
              </p>
            </li>
          ))}
      </ol>
    </section>
  );
}
