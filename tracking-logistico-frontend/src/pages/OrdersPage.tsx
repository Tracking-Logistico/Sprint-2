import { useCallback, useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { Check, LoaderCircle, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/shared/FormField";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { RequestShipmentDialog } from "@/features/orders/components/RequestShipmentDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { OrderHistory } from "@/features/orders/components/OrderHistory";
import { MyShipmentsFilters } from "@/features/orders/components/MyShipmentsFilters";
import { MyShipmentsList } from "@/features/orders/components/MyShipmentsList";
import { useMyShipments } from "@/features/orders/hooks/useMyShipments";
import { api, getApiError } from "@/lib/api";
import { useAuthStore } from "@/stores/authStore";
import { usePageMeta } from "@/hooks/usePageMeta";
import type { OrderResponse, Priority, ReceiveOrderPayload } from "@/types/api";

const inputClass =
  "h-11 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10";
const emptyForm: ReceiveOrderPayload = {
  direccionOrigen: "",
  ciudadOrigen: "",
  codigoPostalOrigen: "",
  direccionDestino: "",
  ciudadDestino: "",
  codigoPostalDestino: "",
  remitenteTelefono: "",
  descripcionPaquete: "",
  pesoKg: 0,
  largoCm: 0,
  anchoCm: 0,
  altoCm: 0,
  tipoServicio: "ESTANDAR",
  destinatarioNombre: "",
  destinatarioTelefono: "",
  requiereVerificacionEdad: false,
};
const statusLabels: Record<string, string> = {
  SOLICITADO: "Pendiente de validación",
  CORRECCION_SOLICITADA: "Se requieren correcciones",
  CREADO: "Despacho aprobado",
  RECIBIDO_EN_ORIGEN: "Recibido en origen",
  EN_TRANSITO: "En tránsito",
  EN_REPARTO: "En reparto",
  ENTREGADO: "Entregado",
  RECHAZADO: "Rechazado",
};
const priorityLabels: Record<Priority, string> = {
  ALTA: "Alta",
  MEDIA: "Media",
  BAJA: "Baja",
};

export function OrdersPage() {
  const role = useAuthStore((s) => s.role);
  return role === "CLIENTE" ? <ClientOrders /> : <OperatorInbox />;
}

function ClientOrders() {
  usePageMeta("Mis envíos", "Consulta tus pedidos y el estado de cada envío.");
  const token = useAuthStore((s) => s.accessToken);
  const shipments = useMyShipments(token);
  const [form, setForm] = useState<ReceiveOrderPayload>({ ...emptyForm });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const update = <K extends keyof ReceiveOrderPayload>(
    key: K,
    value: ReceiveOrderPayload[K],
  ) => setForm((current) => ({ ...current, [key]: value }));
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || saving) return;
    setError("");
    setMessage("");
    setSaving(true);
    try {
      const result =
        editingId === null
          ? await api.createOrder(form, token)
          : await api.correctOrder(editingId, form, token);
      const successMessage =
        editingId === null
          ? `Pedido ${result.numeroPedido} enviado a validación.`
          : `Pedido ${result.numeroPedido} corregido y enviado a nueva validación.`;
      setMessage(successMessage);
      toast.success(successMessage);
      setForm({ ...emptyForm });
      setEditingId(null);
      setDialogOpen(false);
      await shipments.reload();
    } catch (err) {
      const errorMessage = getApiError(err, "Revisa los datos del envío.");
      setError(errorMessage);
      toast.error(errorMessage);
    } finally {
      setSaving(false);
    }
  }
  async function edit(id: number) {
    if (!token) return;
    setError("");
    try {
      const order = await api.getOrder(id, token);
      setEditingId(id);
      setForm({
        direccionOrigen: order.direccionOrigen,
        ciudadOrigen: order.ciudadOrigen ?? "",
        codigoPostalOrigen: order.codigoPostalOrigen ?? "",
        direccionDestino: order.direccionDestino,
        ciudadDestino: order.ciudadDestino ?? "",
        codigoPostalDestino: order.codigoPostalDestino ?? "",
        remitenteTelefono: order.remitenteTelefono ?? "",
        descripcionPaquete: order.descripcionPaquete,
        pesoKg: order.pesoKg,
        largoCm: order.largoCm,
        anchoCm: order.anchoCm,
        altoCm: order.altoCm,
        tipoServicio: order.tipoServicio,
        destinatarioNombre: order.destinatarioNombre,
        destinatarioTelefono: order.destinatarioTelefono,
        requiereVerificacionEdad: order.requiereVerificacionEdad ?? false,
      });
      setDialogOpen(true);
    } catch (err) {
      setError(getApiError(err));
    }
  }
  return (
    <Page>
      <Header
        eyebrow="Clientes"
        title="Mis envíos"
        description="Consulta los pedidos asociados a tu cuenta y su fecha estimada de entrega."
        onRefresh={shipments.reload}
        onCreate={() => {
          setEditingId(null);
          setForm({ ...emptyForm });
          setDialogOpen(true);
        }}
      />
      <Feedback error={error || shipments.error} message={message} />
      <section className="mt-8">
        <h2 className="text-xl font-semibold">Tus envíos</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Filtra y ordena la información directamente desde el servidor.
        </p>
        <div className="mt-4 space-y-4">
          <MyShipmentsFilters
            filters={shipments.filters}
            onChange={shipments.updateFilters}
          />
          <MyShipmentsList
            shipments={shipments.content}
            loading={shipments.loading}
            onCorrect={(id) => void edit(id)}
          />
          {!shipments.loading && shipments.content.length === 0 && (
            <EmptyState onCreate={() => setDialogOpen(true)} />
          )}
          {!shipments.loading && shipments.totalPages > 1 && (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 text-sm">
              <span>
                Página {shipments.page + 1} de {shipments.totalPages}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  disabled={shipments.first}
                  onClick={() => shipments.setPage(shipments.page - 1)}
                >
                  Anterior
                </Button>
                <Button
                  variant="outline"
                  disabled={shipments.last}
                  onClick={() => shipments.setPage(shipments.page + 1)}
                >
                  Siguiente
                </Button>
              </div>
            </div>
          )}
        </div>
      </section>
      <RequestShipmentDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        form={form}
        update={update}
        submit={submit}
        saving={saving}
        editing={editingId !== null}
        onCancelEdit={() => {
          setEditingId(null);
          setForm({ ...emptyForm });
        }}
      />
    </Page>
  );
}

function Status({ value }: { value: string }) {
  const styles: Record<string, string> = {
    SOLICITADO: "bg-secondary text-secondary-foreground border-border",
    CORRECCION_SOLICITADA: "bg-secondary text-secondary-foreground border-border",
    CREADO: "bg-accent text-accent-foreground border-border",
    ENTREGADO: "bg-primary/10 text-primary border-primary/30",
    RECHAZADO: "bg-destructive/10 text-destructive border-destructive/30",
  };
  return (
    <Badge className={styles[value] ?? "bg-muted text-muted-foreground"}>
      {statusLabels[value] ?? value.replaceAll("_", " ")}
    </Badge>
  );
}
function OperatorInbox() {
  usePageMeta(
    "Bandeja de pedidos",
    "Verifica datos de despacho y prioridades.",
  );
  const token = useAuthStore((s) => s.accessToken);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [working, setWorking] = useState<number | null>(null);
  const [priority, setPriority] = useState<Record<number, Priority>>({});
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [field, setField] = useState<Record<number, string>>({});
  const [justification, setJustification] = useState<Record<number, string>>(
    {},
  );
  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const pendingOrders = await api.listPendingOrders(token);
      setOrders(pendingOrders.content);
      setError("");
    } catch (e) {
      setError(getApiError(e));
    } finally {
      setLoading(false);
    }
  }, [token]);
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  async function validate(
    o: OrderResponse,
    action: "approve" | "reject" | "correct",
  ) {
    if (!token || working !== null) return;
    setError("");
    setMessage("");
    const p = priority[o.id] ?? o.prioridadSugerida;
    const changed = p !== o.prioridadSugerida;
    if (action === "correct" && (!notes[o.id]?.trim() || !field[o.id])) {
      setError(
        "Selecciona el campo observado y describe la corrección requerida.",
      );
      return;
    }
    if (action === "reject" && !notes[o.id]?.trim()) {
      setError("Indica el motivo del rechazo.");
      return;
    }
    if (action === "approve" && changed && !justification[o.id]?.trim()) {
      setError("Justifica la modificación de prioridad.");
      return;
    }
    setWorking(o.id);
    try {
      await api.validateOrder(
        o.id,
        {
          aprobar: action === "approve",
          prioridadConfirmada: action === "approve" ? p : undefined,
          observaciones: notes[o.id],
          justificacionPrioridad:
            action === "approve" && changed ? justification[o.id] : undefined,
          campoObservado: action === "correct" ? field[o.id] : undefined,
          solicitarCorreccion: action === "correct",
        },
        token,
      );
      setMessage(
        action === "correct"
          ? "Corrección solicitada al cliente."
          : action === "approve"
            ? "Pedido aprobado para despacho."
            : "Pedido rechazado.",
      );
      await load();
    } catch (e) {
      setError(getApiError(e));
    } finally {
      setWorking(null);
    }
  }
  return (
    <Page>
      <Header
        eyebrow="Operación"
        title="Bandeja de pedidos"
        description="Confirma remitente, destinatario, contenido, medidas y prioridad antes de activar el seguimiento."
        onRefresh={load}
      />
      <Feedback error={error} message={message} />
      <div className="mt-8 space-y-4">
        {loading && <Loading />}
        {!loading && orders.length === 0 && (
          <OperatorEmpty text="No hay solicitudes pendientes de revisión." />
        )}
        {orders.map((o) => (
          <article
            key={o.id}
            className="rounded-2xl border border-border bg-card p-6 shadow-sm"
          >
            <div className="flex flex-wrap justify-between gap-4">
              <div>
                <p className="font-mono text-sm font-semibold">
                  {o.numeroPedido}
                </p>
                <h2 className="mt-2 text-lg font-semibold">
                  {o.destinatarioNombre}
                </h2>
                <p className="text-sm text-muted-foreground">
                  {o.destinatarioTelefono} · {o.ciudadDestino} ·{" "}
                  {o.direccionDestino}
                </p>
              </div>
              <Status value={o.estado} />
            </div>
            <div className="mt-5 grid gap-3 rounded-xl bg-muted p-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
              <span>
                <b>Remitente:</b> {o.remitenteNombre || "Sin dato histórico"}
              </span>
              <span>
                <b>Teléfono origen:</b>{" "}
                {o.remitenteTelefono || "Sin dato histórico"}
              </span>
              <span>
                <b>Origen:</b> {o.ciudadOrigen} · {o.direccionOrigen}
              </span>
              <span>
                <b>Paquete:</b> {o.descripcionPaquete}
              </span>
              <span>
                <b>Medidas:</b> {o.pesoKg} kg · {o.largoCm} × {o.anchoCm} ×{" "}
                {o.altoCm} cm
              </span>
              <span>
                <b>Servicio:</b> {o.tipoServicio}
              </span>
            </div>
            {o.observacionesValidacion && (
              <p className="mt-4 rounded-lg bg-secondary px-3 py-2 text-sm text-secondary-foreground">
                {o.observacionesValidacion}
              </p>
            )}
            {o.estado === "SOLICITADO" && (
              <>
                <div className="mt-5 grid gap-4 lg:grid-cols-2">
                  <div>
                    <label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      Prioridad sugerida: {priorityLabels[o.prioridadSugerida]}
                    </label>
                    <select
                      id={`priority-${o.id}`}
                      aria-label={`Prioridad para el pedido ${o.numeroPedido}`}
                      className={`${inputClass} mt-2`}
                      value={priority[o.id] ?? o.prioridadSugerida}
                      onChange={(e) =>
                        setPriority((v) => ({
                          ...v,
                          [o.id]: e.target.value as Priority,
                        }))
                      }
                    >
                      <option value="ALTA">Alta</option>
                      <option value="MEDIA">Media</option>
                      <option value="BAJA">Baja</option>
                    </select>
                  </div>
                  <FormField
                    label="Justificación de cambio de prioridad"
                    name={`just-${o.id}`}
                    maxLength={500}
                    value={justification[o.id] ?? ""}
                    onChange={(e) =>
                      setJustification((v) => ({
                        ...v,
                        [o.id]: e.target.value,
                      }))
                    }
                  />
                </div>
                <div className="mt-4 grid gap-4 lg:grid-cols-2">
                  <div>
                    <label
                      className="text-sm font-medium"
                      htmlFor={`field-${o.id}`}
                    >
                      Campo a corregir
                    </label>
                    <select
                      id={`field-${o.id}`}
                      className={`${inputClass} mt-2`}
                      value={field[o.id] ?? ""}
                      onChange={(e) =>
                        setField((v) => ({ ...v, [o.id]: e.target.value }))
                      }
                    >
                      <option value="">Selecciona un campo</option>
                      {[
                        "direccionOrigen",
                        "ciudadOrigen",
                        "direccionDestino",
                        "ciudadDestino",
                        "remitenteTelefono",
                        "destinatarioNombre",
                        "destinatarioTelefono",
                        "descripcionPaquete",
                        "pesoKg",
                        "largoCm",
                        "anchoCm",
                        "altoCm",
                        "tipoServicio",
                      ].map((name) => (
                        <option key={name} value={name}>
                          {name.replace(/([A-Z])/g, " $1")}
                        </option>
                      ))}
                    </select>
                  </div>
                  <FormField
                    label="Observación / motivo de rechazo"
                    name={`notes-${o.id}`}
                    maxLength={500}
                    value={notes[o.id] ?? ""}
                    onChange={(e) =>
                      setNotes((v) => ({ ...v, [o.id]: e.target.value }))
                    }
                  />
                </div>
                <div className="mt-5 flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    disabled={working !== null}
                    onClick={() => void validate(o, "approve")}
                  >
                    <Check className="size-4" />
                    Confirmar prioridad y aprobar
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={working !== null}
                    onClick={() => void validate(o, "correct")}
                  >
                    Solicitar corrección
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={working !== null}
                    onClick={() => void validate(o, "reject")}
                  >
                    Rechazar
                  </Button>
                </div>
              </>
            )}
            <div className="mt-4">
              <OrderHistory id={o.id} token={token} />
            </div>
          </article>
        ))}
      </div>
    </Page>
  );
}

function Page({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-10">
      {children}
    </div>
  );
}
function Header({
  eyebrow,
  title,
  description,
  onRefresh,
  onCreate,
}: {
  eyebrow: string;
  title: string;
  description: string;
  onRefresh: () => void | Promise<void>;
  onCreate?: () => void;
}) {
  return (
    <header className="flex flex-col gap-5 border-b border-border pb-8 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-sm font-semibold uppercase tracking-[.16em] text-primary">
          {eyebrow}
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">
          {description}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" onClick={() => void onRefresh()}>
          <RefreshCw className="size-4" />
          Actualizar
        </Button>
        {onCreate && (
          <Button onClick={onCreate}>
            <Plus className="size-4" />
            Solicitar envío
          </Button>
        )}
      </div>
    </header>
  );
}
function Feedback({ error, message }: { error: string; message: string }) {
  return error ? (
    <Alert variant="destructive" className="mt-6">
      {error}
    </Alert>
  ) : message ? (
    <Alert className="mt-6 border-primary/30 bg-primary/10 text-primary">
      {message}
    </Alert>
  ) : null;
}
function Loading() {
  return (
    <div role="status" aria-label="Cargando pedidos" className="space-y-3 py-4">
      <Skeleton className="h-28 w-full rounded-2xl" />
      <Skeleton className="h-28 w-full rounded-2xl" />
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <LoaderCircle className="size-4 animate-spin" />
        Cargando...
      </div>
    </div>
  );
}
function OperatorEmpty({ text }: { text: string }) {
  return (
    <div className="rounded-xl border border-dashed border-border bg-card p-8 text-center text-sm text-muted-foreground">
      {text}
    </div>
  );
}
