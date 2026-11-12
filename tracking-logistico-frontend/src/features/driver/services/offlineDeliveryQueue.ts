import type {
  DeliveryEvent,
  OfflineDeliveryEvent,
  SyncResult,
} from "@/types/api";

const STORAGE_KEY = "logistrack-driver-delivery-queue";
export const OFFLINE_QUEUE_LIMIT = 100;
export const OFFLINE_EVENT_MAX_AGE_MS = 24 * 60 * 60 * 1000;

function readQueue(): OfflineDeliveryEvent[] {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (!value) return [];
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? (parsed as OfflineDeliveryEvent[]) : [];
  } catch {
    return [];
  }
}

function writeQueue(queue: OfflineDeliveryEvent[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(queue));
  window.dispatchEvent(new CustomEvent("logistrack-queue-change"));
}

export function getOfflineQueue() {
  return readQueue();
}

export function enqueueDelivery(pedidoId: number, evento: DeliveryEvent) {
  const queue = readQueue();
  if (queue.length >= OFFLINE_QUEUE_LIMIT) {
    throw new Error(
      "La cola sin conexión está llena. Conéctate a internet para sincronizar antes de registrar otra entrega.",
    );
  }
  const item = { pedidoId, evento, queuedAt: new Date().toISOString() };
  writeQueue([...queue, item]);
  return item;
}

export function removeOfflineEvents(ids: Set<string>) {
  writeQueue(
    readQueue().filter((item) => !ids.has(item.evento.idEventoCliente)),
  );
}

export function getOfflineQueueAgeMs() {
  const first = readQueue()[0];
  return first ? Date.now() - new Date(first.queuedAt).getTime() : 0;
}

export function isNetworkError(error: unknown) {
  return (
    error instanceof TypeError ||
    (typeof error === "object" && error !== null && !("status" in error))
  );
}

export function acceptedSyncResults(results: SyncResult[]) {
  return new Set(
    results
      .filter(
        (result) =>
          result.estado === "APLICADO" || result.estado === "DUPLICADO",
      )
      .map((result) => result.idEventoCliente),
  );
}
