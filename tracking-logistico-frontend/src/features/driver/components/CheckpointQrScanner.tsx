import { useEffect, useRef, useState } from "react";
import {
  BrowserMultiFormatReader,
  type IScannerControls,
} from "@zxing/browser";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, getApiError } from "@/lib/api";
import type { CheckpointStage } from "@/types/api";

const stages: { value: CheckpointStage; label: string }[] = [
  { value: "RECIBIDO_EN_ORIGEN", label: "Recibido en origen" },
  { value: "EN_TRANSITO", label: "En tránsito" },
  { value: "EN_REPARTO", label: "En reparto" },
  { value: "ENTREGADO", label: "Entregado" },
];

export function CheckpointQrScanner({
  pedidoId,
  token,
  onRegistered,
}: {
  pedidoId: number;
  token: string;
  onRegistered?: () => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const readerRef = useRef<BrowserMultiFormatReader | null>(null);
  const controlsRef = useRef<IScannerControls | null>(null);
  const [code, setCode] = useState("");
  const [stage, setStage] = useState<CheckpointStage>("EN_TRANSITO");
  const [scanning, setScanning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  function stopScanner() {
    controlsRef.current?.stop();
    controlsRef.current = null;
    readerRef.current = null;
    setScanning(false);
  }

  async function startScanner() {
    setError("");
    setMessage("");
    try {
      const reader = new BrowserMultiFormatReader();
      readerRef.current = reader;
      setScanning(true);
      controlsRef.current = await reader.decodeFromVideoDevice(
        undefined,
        videoRef.current!,
        (result) => {
          if (result) {
            setCode(result.getText());
            stopScanner();
            setMessage(
              "Código leído. Confirma la etapa y registra el checkpoint.",
            );
          }
        },
      );
    } catch {
      stopScanner();
      setError(
        "No se pudo acceder a la cámara. Revisa el permiso del navegador o escribe el código manualmente.",
      );
    }
  }

  useEffect(() => () => stopScanner(), []);

  async function submit() {
    if (!code.trim() || saving) {
      setError("Escribe o escanea un código QR.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      await api.registerCheckpoint(
        pedidoId,
        {
          codigoQr: code.trim(),
          etapa: stage,
          idEventoCliente: crypto.randomUUID(),
          fechaDispositivo: new Date().toISOString(),
        },
        token,
      );
      setMessage("Checkpoint registrado correctamente.");
      setCode("");
      onRegistered?.();
    } catch (err) {
      setError(
        getApiError(
          err,
          "El QR fue rechazado: puede ser inválido, pertenecer a otro envío o estar en estado final.",
        ),
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="space-y-3 rounded-xl border bg-card p-4">
      <div>
        <h3 className="font-semibold">Registrar checkpoint por QR</h3>
        <p className="text-sm text-muted-foreground">
          Escanea el código del envío o escríbelo manualmente.
        </p>
      </div>
      <div className="overflow-hidden rounded-lg bg-black">
        <video
          ref={videoRef}
          className="aspect-video w-full object-cover"
          muted
          playsInline
          aria-label="Vista de la cámara para escanear QR"
        />
      </div>
      <div className="flex flex-wrap gap-2">
        {!scanning ? (
          <Button
            type="button"
            variant="outline"
            onClick={() => void startScanner()}
          >
            Abrir escáner
          </Button>
        ) : (
          <Button type="button" variant="outline" onClick={stopScanner}>
            Cerrar escáner
          </Button>
        )}
        <Input
          aria-label="Código QR manual"
          placeholder="Código QR"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          maxLength={120}
        />
      </div>
      <select
        aria-label="Nuevo estado del checkpoint"
        className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
        value={stage}
        onChange={(event) => setStage(event.target.value as CheckpointStage)}
      >
        {stages.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <Button type="button" onClick={() => void submit()} disabled={saving}>
        {saving ? "Registrando..." : "Registrar checkpoint"}
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
