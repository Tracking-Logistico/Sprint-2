import type {
  ApiErrorShape,
  AnomalyPage, Anomaly, AnomalyParameters, ClientAnomaly, PendingReceipt, AnomalyState, AnomalyType,
  ClientResponse,
  DriverResponse,
  LabelResponse,
  LoginResponse,
  IdentityProfile,
  OrderResponse,
  OrderEvent,
  ReceiveOrderPayload,
  RegisterPayload,
  RouteResponse,
  RouteNotification,
  AssignmentEvent,
  UserResponse,
  ValidateOrderPayload,
  DriverProgress,
  DriverDelivery,
  DriverDeliveryDetail,
  NextStop,
  DriverRoute,
  NoveltyOption,
  DeliveryEvent,
  DeliveryResultResponse,
  OfflineDeliveryEvent,
  SyncResponse,
  MyOrderResponse,
  PageResponse,
  OrderStatus,
  ShipmentTrackingResponse,
  ReschedulingRangeResponse,
  IncidentType,
  IncidentsResponse,
  RegisterIncidentPayload,
  RegisterIncidentResponse,
  NotificationPreference,
  UpdateNotificationPreferencePayload,
  AgeVerificationResult,
  AgeVerificationResponse,
} from "@/types/api";

const API_URL = (
  (import.meta.env.VITE_API_ORIGIN
    ? `${import.meta.env.VITE_API_ORIGIN}/api/v1`
    : import.meta.env.VITE_API_URL) || "http://localhost:8080/api/v1"
).replace(/\/$/, "");

async function parseResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get("content-type") ?? "";
  const body: unknown =
    response.status === 204
      ? undefined
      : contentType.includes("application/json")
        ? await response.json()
        : await response.text();
  if (!response.ok) {
    const obj =
      typeof body === "object" && body !== null
        ? (body as Record<string, unknown>)
        : {};
    const details = Object.fromEntries(
      Object.entries(obj).filter(
        ([k, v]) => k !== "message" && k !== "error" && typeof v === "string",
      ),
    ) as Record<string, string>;
    const message =
      typeof obj.message === "string"
        ? obj.message
        : typeof obj.error === "string"
          ? obj.error
          : (Object.values(details)[0] ??
            (typeof body === "string"
              ? body
              : "No se pudo completar la operación."));
    throw { message, status: response.status, details } satisfies ApiErrorShape;
  }
  return body as T;
}

let refreshInFlight: Promise<string> | null = null;

async function renewAccessToken(rejectedToken: string): Promise<string> {
  const { useAuthStore } = await import("@/stores/authStore");
  const current = useAuthStore.getState();
  if (current.accessToken && current.accessToken !== rejectedToken)
    return current.accessToken;
  if (!current.refreshToken) throw new Error("La sesión ha expirado");
  if (!refreshInFlight) {
    const originalRefresh = current.refreshToken;
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${API_URL}/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: originalRefresh }),
        });
        const session = await parseResponse<LoginResponse>(response);

        if (useAuthStore.getState().refreshToken === originalRefresh) {
          useAuthStore.getState().applySession(session);
        }
        return useAuthStore.getState().accessToken ?? session.accessToken;
      } catch (error) {
        if (useAuthStore.getState().refreshToken === originalRefresh) {
          useAuthStore.getState().clearSession();
        }
        throw error;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  token?: string,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  Object.entries(extraHeaders ?? {}).forEach(([k, v]) => headers.set(k, v));
  let response = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (response.status === 401 && token && path !== "/auth/logout") {
    try {
      const replacement = await renewAccessToken(token);
      headers.set("Authorization", `Bearer ${replacement}`);
      response = await fetch(`${API_URL}${path}`, { ...init, headers });
    } catch {
      const { useAuthStore } = await import("@/stores/authStore");
      useAuthStore.getState().clearSession();
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }
  }
  return parseResponse<T>(response);
}

export const api = {
  login: (email: string, password: string) =>
    request<LoginResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  refresh: (refreshToken: string) =>
    request<LoginResponse>("/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    }),
  logout: (token: string) =>
    request<void>("/auth/logout", { method: "POST" }, token),
  forgotPassword: (email: string) =>
    request<string>("/auth/password/forgot", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),
  resetPassword: (
    token: string,
    nuevaPassword: string,
    confirmarPassword: string,
  ) =>
    request<void>("/auth/password/reset", {
      method: "POST",
      body: JSON.stringify({ token, nuevaPassword, confirmarPassword }),
    }),
  registerClient: (payload: RegisterPayload) =>
    request<ClientResponse>("/clientes/registro", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  resendVerification: (email: string) =>
    request<string>("/clientes/verificacion/reenviar", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),
  verifyClient: (token: string) =>
    request<string>(`/clientes/verificar?token=${encodeURIComponent(token)}`),
  changePassword: (
    userId: number,
    passwordActual: string,
    nuevaPassword: string,
    confirmarPassword: string,
    token: string,
  ) =>
    request<string>(
      `/usuarios/${userId}/password`,
      {
        method: "PUT",
        body: JSON.stringify({
          passwordActual,
          nuevaPassword,
          confirmarPassword,
        }),
      },
      token,
    ),
  getMyIdentity: (token: string) =>
    request<IdentityProfile>("/clientes/me/identidad", {}, token),
  completeMyIdentity: (
    payload: { numeroCedula: string; fechaNacimiento: string },
    token: string,
  ) =>
    request<IdentityProfile>(
      "/clientes/me/identidad",
      {
        method: "PUT",
        headers: { "X-Origen": "WEB" },
        body: JSON.stringify(payload),
      },
      token,
    ),
  myProfile: (token: string) =>
    request<ClientResponse>("/clientes/me", {}, token),
  updateMyProfile: (
    payload: { nombre: string; telefono?: string; direccion?: string },
    token: string,
  ) =>
    request<UserResponse>(
      "/clientes/me/perfil",
      { method: "PUT", body: JSON.stringify(payload) },
      token,
    ),
  deactivateMyAccount: (token: string) =>
    request<void>("/clientes/me", { method: "DELETE" }, token),
  getNotificationPreference: (token: string) =>
    request<NotificationPreference>("/notificaciones/preferencias", {}, token),
  updateNotificationPreference: (
    payload: UpdateNotificationPreferencePayload,
    token: string,
  ) =>
    request<NotificationPreference>(
      "/notificaciones/preferencias",
      { method: "PUT", body: JSON.stringify(payload) },
      token,
    ),

  createOrder: (payload: ReceiveOrderPayload, token: string) =>
    request<OrderResponse>(
      "/pedidos",
      { method: "POST", body: JSON.stringify(payload) },
      token,
    ),
  correctOrder: (id: number, payload: ReceiveOrderPayload, token: string) =>
    request<OrderResponse>(
      `/pedidos/${id}/corregir`,
      { method: "PUT", body: JSON.stringify(payload) },
      token,
    ),
  getOrder: (id: number, token: string) =>
    request<OrderResponse>(`/pedidos/${id}`, {}, token),
  orderHistory: (id: number, token: string) =>
    request<OrderEvent[]>(`/pedidos/${id}/historial`, {}, token),
  incidentTypes: (token: string) =>
    request<IncidentType[]>("/pedidos/incidencias/tipos", {}, token),
  orderIncidents: (id: number, token: string) =>
    request<IncidentsResponse>(`/pedidos/${id}/incidencias`, {}, token),
  registerIncident: (
    id: number,
    payload: RegisterIncidentPayload,
    token: string,
  ) =>
    request<RegisterIncidentResponse>(
      `/pedidos/${id}/incidencias`,
      { method: "POST", body: JSON.stringify(payload) },
      token,
    ),
  myOrders: (
    params: {
      page: number;
      size: number;
      sort: "fechaCreacion" | "estado";
      direction: "asc" | "desc";
      estado?: OrderStatus;
      fechaDesde?: string;
      fechaHasta?: string;
    },
    token: string,
  ) => {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size),
      sort: `${params.sort},${params.direction}`,
    });
    if (params.estado) query.set("estado", params.estado);
    if (params.fechaDesde) query.set("fechaDesde", params.fechaDesde);
    if (params.fechaHasta) query.set("fechaHasta", params.fechaHasta);
    return request<PageResponse<MyOrderResponse>>(
      `/pedidos/mios?${query.toString()}`,
      {},
      token,
    );
  },
  listPendingOrders: (token: string) =>
    request<PageResponse<OrderResponse>>("/pedidos/pendientes", {}, token),
  listActivableOrders: (token: string) =>
    request<PageResponse<OrderResponse>>("/pedidos/activables", {}, token),
  listDispatchOrders: (token: string) =>
    request<OrderResponse[]>("/pedidos/despachos", {}, token),
  validateOrder: (id: number, payload: ValidateOrderPayload, token: string) =>
    request<OrderResponse>(
      `/pedidos/${id}/validar`,
      { method: "PUT", body: JSON.stringify(payload) },
      token,
    ),
  activateTracking: (id: number, token: string) =>
    request<OrderResponse>(
      `/pedidos/${id}/activar-tracking`,
      { method: "PUT" },
      token,
    ),
  setLogisticsState: (
    id: number,
    estado: "RECIBIDO_EN_ORIGEN" | "EN_TRANSITO",
    token: string,
  ) =>
    request<OrderResponse>(
      `/pedidos/${id}/estado-logistico`,
      { method: "PUT", body: JSON.stringify({ estado }) },
      token,
    ),
  getOrderByTracking: (tracking: string, token: string) =>
    request<OrderResponse>(
      `/pedidos/tracking/${encodeURIComponent(tracking)}`,
      {},
      token,
    ),
  getMyShipmentTracking: (tracking: string, token: string) =>
    request<ShipmentTrackingResponse>(
      `/pedidos/mios/tracking/${encodeURIComponent(tracking)}`,
      {},
      token,
    ),
  getReschedulingRange: (tracking: string, token: string) =>
    request<ReschedulingRangeResponse>(
      `/pedidos/mios/tracking/${encodeURIComponent(tracking)}/reprogramacion/rango`,
      {},
      token,
    ),
  rescheduleShipment: (tracking: string, fecha: string, token: string) =>
    request<ShipmentTrackingResponse>(
      `/pedidos/mios/tracking/${encodeURIComponent(tracking)}/reprogramacion`,
      { method: "POST", body: JSON.stringify({ fecha }) },
      token,
    ),
  generateLabel: (id: number, token: string) =>
    request<LabelResponse>(
      `/pedidos/${id}/etiqueta`,
      { method: "POST" },
      token,
    ),

  listPendingRouteOrders: (token: string) =>
    request<PageResponse<OrderResponse>>("/rutas/envios-pendientes", {}, token),
  listDrivers: (token: string) =>
    request<DriverResponse[]>("/rutas/conductores-disponibles", {}, token),
  assignRouteOrder: (pedidoId: number, conductorId: number, token: string) =>
    request<RouteResponse>(
      "/rutas/asignaciones",
      { method: "POST", body: JSON.stringify({ pedidoId, conductorId }) },
      token,
    ),
  getDriverRoute: (conductorId: number, token: string) =>
    request<RouteResponse>(`/rutas/conductores/${conductorId}`, {}, token),
  myDriverRoute: (token: string) =>
    request<RouteResponse>("/rutas/mi-ruta", {}, token),
  assignRouteOrders: (
    pedidoIds: number[],
    conductorId: number,
    token: string,
  ) =>
    request<RouteResponse>(
      "/rutas/asignaciones/lote",
      { method: "POST", body: JSON.stringify({ pedidoIds, conductorId }) },
      token,
    ),
  routeNotifications: (token: string) =>
    request<RouteNotification[]>("/rutas/mis-notificaciones", {}, token),
  readRouteNotification: (id: number, token: string) =>
    request<void>(
      `/rutas/mis-notificaciones/${id}/leer`,
      { method: "PATCH" },
      token,
    ),
  routeAssignmentHistory: (pedidoId: number, token: string) =>
    request<AssignmentEvent[]>(
      `/rutas/asignaciones/${pedidoId}/historial`,
      {},
      token,
    ),
  reorderRoute: (routeId: number, pedidoIdsEnOrden: number[], token: string) =>
    request<RouteResponse>(
      `/rutas/${routeId}/orden`,
      { method: "PUT", body: JSON.stringify({ pedidoIdsEnOrden }) },
      token,
    ),
  reassignRouteOrder: (
    pedidoId: number,
    nuevoConductorId: number,
    motivo: string,
    token: string,
  ) =>
    request<RouteResponse>(
      "/rutas/asignaciones/reasignar",
      {
        method: "PUT",
        body: JSON.stringify({ pedidoId, nuevoConductorId, motivo }),
      },
      token,
    ),

  driverProgress: (token: string) =>
    request<DriverProgress>("/conductor/panel/progreso", {}, token),
  driverDeliveries: (token: string) =>
    request<DriverDelivery[]>("/conductor/panel/entregas", {}, token),
  driverDeliveryDetail: (pedidoId: number, token: string) =>
    request<DriverDeliveryDetail>(
      `/conductor/panel/entregas/${pedidoId}`,
      {},
      token,
    ),
  driverNextStop: (token: string) =>
    request<NextStop>("/conductor/panel/siguiente", {}, token),
  driverRoute: (token: string) =>
    request<DriverRoute>("/conductor/panel/ruta", {}, token),
  driverNoveltyCatalog: (token: string) =>
    request<NoveltyOption[]>("/conductor/panel/catalogo-novedades", {}, token),
  registerDriverDelivery: (
    pedidoId: number,
    evento: DeliveryEvent,
    token: string,
  ) =>
    request<DeliveryResultResponse>(
      `/conductor/panel/entregas/${pedidoId}/resultado`,
      { method: "POST", body: JSON.stringify(evento) },
      token,
    ),
  registerAgeVerification: (
    pedidoId: number,
    resultado: AgeVerificationResult,
    token: string,
  ) =>
    request<AgeVerificationResponse>(
      `/conductor/panel/entregas/${pedidoId}/verificacion-edad`,
      {
        method: "POST",
        body: JSON.stringify({ resultado, origen: "EN_LINEA" }),
      },
      token,
    ),
  syncDriverDeliveries: (events: OfflineDeliveryEvent[], token: string) =>
    request<SyncResponse>(
      "/conductor/panel/entregas/sincronizacion",
      {
        method: "POST",
        body: JSON.stringify({
          eventos: events.map(({ pedidoId, evento }) => ({ pedidoId, evento })),
        }),
      },
      token,
    ),

  adminUsers: (dbaKey: string) =>
    request<UserResponse[]>("/admin/usuarios", {}, undefined, {
      "X-DBA-Key": dbaKey,
    }),
  adminChangeRole: (
    id: number,
    body: {
      rol: "CLIENTE" | "OPERADOR" | "CONDUCTOR";
      licencia?: string;
      codigoEmpleado?: string;
    },
    dbaKey: string,
  ) =>
    request<UserResponse>(
      `/admin/usuarios/${id}/rol`,
      { method: "PATCH", body: JSON.stringify(body) },
      undefined,
      { "X-DBA-Key": dbaKey },
    ),
};

export function getApiError(
  error: unknown,
  fallback = "Ocurrió un error inesperado.",
) {
  if (
    typeof error === "object" &&
    error !== null &&
    "message" in error &&
    typeof (error as { message?: unknown }).message === "string"
  ) {
    const apiError = error as ApiErrorShape;
    if (apiError.status === 400)
      return "Revisa los datos ingresados e inténtalo nuevamente.";
    if (apiError.status === 401)
      return "Tu sesión expiró. Inicia sesión nuevamente.";
    if (apiError.status === 403)
      return "No tienes permiso para realizar esta operación.";
    if (apiError.status === 404)
      return "No se encontró la información solicitada.";
    if (apiError.status === 409)
      return "La operación no puede completarse porque la información cambió.";
    const fields = apiError.details
      ? Object.entries(apiError.details).map(
          ([field, detail]) => `${field}: ${detail}`,
        )
      : [];
    if (apiError.message === "Revisa los campos señalados" && fields.length)
      return fields.join(" · ");
    return apiError.message;
  }
  return fallback;
}

export const anomaliesApi = {
  list: (state: AnomalyState, type: AnomalyType | '', page: number, token: string) =>
    request<AnomalyPage>(`/pedidos/anomalias?estado=${state}&pagina=${page}&tamano=20${type ? `&tipo=${type}` : ''}`, {}, token),
  detail: (id: number, token: string) => request<Anomaly>(`/pedidos/anomalias/${id}`, {}, token),
  close: (id: number, estado: 'RESUELTA' | 'DESCARTADA', token: string) =>
    request<Anomaly>(`/pedidos/anomalias/${id}/cierre`, { method: 'POST', body: JSON.stringify({ estado, origen: 'WEB' }) }, token),
  alerts: (tracking: string, token: string) =>
    request<ClientAnomaly[]>(`/pedidos/mios/tracking/${encodeURIComponent(tracking)}/alertas`, {}, token),
  parameters: (key: string, token: string) =>
    request<AnomalyParameters>('/pedidos/anomalias/parametros', {}, token, { 'X-DBA-Key': key }),
  saveParameters: (values: AnomalyParameters, key: string, token: string) =>
    request<AnomalyParameters>('/pedidos/anomalias/parametros', { method: 'PUT', body: JSON.stringify({ ...values, origen: 'WEB' }) }, token, { 'X-DBA-Key': key }),
  pendingReceipts: (page: number, token: string) =>
    request<PendingReceipt[]>(`/pedidos/anomalias/recepciones-pendientes?pagina=${page}`, {}, token),
  confirmReceipt: (shipment: number, event: number, token: string) =>
    request<void>(`/pedidos/${shipment}/recepcion-oficina`, { method: 'POST', body: JSON.stringify({ eventoLlegadaId: event, origen: 'WEB' }) }, token),
};
