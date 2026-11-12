import { useCallback, useEffect, useState } from "react";
import { getApiError } from "@/lib/api";
import type {
  ReschedulingRangeResponse,
  ShipmentTrackingResponse,
} from "@/types/api";
import {
  getReschedulingRange,
  getShipmentTracking,
  rescheduleShipment,
} from "../services/trackingService";

function errorMessage(error: unknown, fallback: string) {
  const status =
    typeof error === "object" && error !== null && "status" in error
      ? (error as { status?: number }).status
      : undefined;
  if (status === 400)
    return "La fecha seleccionada no es válida. Elige una fecha dentro del rango permitido.";
  if (status === 401) return "Tu sesión expiró. Inicia sesión nuevamente.";
  if (status === 403) return "No tienes permiso para reprogramar este envío.";
  if (status === 404) return "No se encontró el envío solicitado.";
  if (status === 409)
    return "El envío cambió o ya no permite reprogramar la entrega. Actualiza el seguimiento.";
  return getApiError(error, fallback);
}

export function useShipmentTracking(
  tracking: string | undefined,
  token: string | null,
) {
  const [shipment, setShipment] = useState<ShipmentTrackingResponse | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [showSkeleton, setShowSkeleton] = useState(false);
  const [error, setError] = useState("");
  const [reschedulingRange, setReschedulingRange] =
    useState<ReschedulingRangeResponse | null>(null);
  const [rescheduling, setRescheduling] = useState(false);
  const [reschedulingError, setReschedulingError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const skeletonTimer = window.setTimeout(() => {
      if (!cancelled) setShowSkeleton(true);
    }, 1000);

    void (async () => {
      await Promise.resolve();
      if (cancelled) return;

      setLoading(true);
      setShowSkeleton(false);
      setError("");
      setShipment(null);

      if (!tracking || !token) {
        setLoading(false);
        setError("No se encontró un número de seguimiento válido.");
        return;
      }

      try {
        const result = await getShipmentTracking(tracking, token);
        if (!cancelled) {
          setShipment(result);
          setReschedulingRange(null);
          setReschedulingError("");
          if (result.estado === "ENTREGA_FALLIDA") {
            try {
              setReschedulingRange(await getReschedulingRange(tracking, token));
            } catch (rangeError) {
              setReschedulingError(
                errorMessage(
                  rangeError,
                  "No pudimos consultar las fechas disponibles.",
                ),
              );
            }
          }
        }
      } catch (err: unknown) {
        if (cancelled) return;
        setError(
          errorMessage(
            err,
            "No pudimos cargar el seguimiento. Intenta nuevamente.",
          ),
        );
      } finally {
        if (!cancelled) {
          setLoading(false);
          setShowSkeleton(false);
        }
        window.clearTimeout(skeletonTimer);
      }
    })();

    return () => {
      cancelled = true;
      window.clearTimeout(skeletonTimer);
    };
  }, [reloadKey, token, tracking]);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  const submitRescheduling = useCallback(
    async (fecha: string) => {
      if (!tracking || !token || rescheduling) return;
      setRescheduling(true);
      setReschedulingError("");
      try {
        const result = await rescheduleShipment(tracking, fecha, token);
        setShipment(result);
      } catch (err: unknown) {
        setReschedulingError(
          errorMessage(err, "No pudimos reprogramar la entrega."),
        );
      } finally {
        setRescheduling(false);
      }
    },
    [rescheduling, token, tracking],
  );

  return {
    shipment,
    loading,
    showSkeleton,
    error,
    reschedulingRange,
    rescheduling,
    reschedulingError,
    submitRescheduling,
    reload,
  };
}
