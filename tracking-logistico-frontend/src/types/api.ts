export type Role = "CLIENTE" | "OPERADOR" | "CONDUCTOR";
export type UserStatus =
  | "PENDIENTE_ACTIVACION"
  | "PENDIENTE_VERIFICACION"
  | "ACTIVO"
  | "INACTIVO"
  | "BLOQUEADO";

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  rol: Role;
  panel: string;
  usuarioId: number;
  requiereCambioPassword: boolean;
}

export interface RegisterPayload {
  nombre: string;
  email: string;
  password: string;
  confirmarPassword: string;
  telefono?: string;
  direccion?: string;
  ciudad?: string;
  aceptoTerminos: boolean;
  versionTerminos: string;
  aceptoPoliticaDatos: boolean;
  versionPoliticaDatos: string;
  numeroCedula: string;
  fechaNacimiento: string;
}

export interface IdentityProfile {
  registrada: boolean;
  numeroCedulaEnmascarado: string | null;
  fechaNacimiento: string | null;
  edad: number | null;
  editable: boolean;
}

export interface ClientResponse {
  idCliente: number;
  nombre: string;
  email: string;
  telefono?: string;
  direccion?: string;
  ciudad?: string;
  rol: Role;
  estado: UserStatus;
  fechaCreacion: string;
}

export type NotificationChannel = "EMAIL" | "SMS" | "AMBOS";

export interface NotificationPreference {
  canal: NotificationChannel;
  telefonoSms: string | null;
  fechaActualizacion: string | null;
}

export interface UpdateNotificationPreferencePayload {
  canal: NotificationChannel;
  telefonoSms: string | null;
}

export interface ApiErrorShape {
  message: string;
  status: number;
  details?: Record<string, string>;
}
export type ServiceType = "ESTANDAR" | "EXPRESS" | "PROGRAMADO";
export type Priority = "BAJA" | "MEDIA" | "ALTA";
export type OrderStatus =
  | "SOLICITADO"
  | "CORRECCION_SOLICITADA"
  | "CREADO"
  | "RECIBIDO_EN_ORIGEN"
  | "EN_TRANSITO"
  | "EN_REPARTO"
  | "ENTREGADO"
  | "RECHAZADO"
  | "ENTREGA_FALLIDA"
  | "ENTREGA_REPROGRAMADA"
  | "DIRECCION_POR_VERIFICAR"
  | "DEVOLUCION_AL_REMITENTE"
  | "ENTREGA_FALLIDA_CERRADA"
  | "PAUSADO"
  | "CANCELADO"
  | "RECIBIDO"
  | "EN_VALIDACION"
  | "VALIDADO";

export interface ReceiveOrderPayload {
  direccionOrigen: string;
  ciudadOrigen: string;
  codigoPostalOrigen?: string;
  direccionDestino: string;
  ciudadDestino: string;
  codigoPostalDestino?: string;
  remitenteTelefono: string;
  descripcionPaquete: string;
  pesoKg: number;
  largoCm: number;
  anchoCm: number;
  altoCm: number;
  tipoServicio: ServiceType;
  destinatarioNombre: string;
  destinatarioTelefono: string;
  requiereVerificacionEdad?: boolean;
}

export interface ValidateOrderPayload {
  aprobar: boolean;
  prioridadConfirmada?: Priority;
  observaciones?: string;
  justificacionPrioridad?: string;
  campoObservado?: string;
  solicitarCorreccion?: boolean;
}

export interface OrderResponse extends ReceiveOrderPayload {
  id: number;
  numeroPedido: string;
  clienteId: number;
  prioridadSugerida: Priority;
  prioridadConfirmada?: Priority;
  estado: OrderStatus;
  observacionesValidacion?: string;
  justificacionPrioridad?: string;
  operadorValidadorId?: number;
  fechaCreacion: string;
  fechaValidacion?: string;
  numeroTracking?: string;
  fechaActivacionTracking?: string;
  etiquetaImpresa: boolean;
  fechaImpresionEtiqueta?: string;
  remitenteNombre?: string;
  remitenteEmail?: string;
}

export interface MyOrderResponse {
  id: number;
  numeroPedido: string;
  numeroTracking?: string;
  remitenteNombre: string;
  destinatarioNombre: string;
  estado: OrderStatus;
  fechaCreacion: string;
  fechaEstimadaEntrega?: string;
  numeroTrackingOrigenDevolucion?: string;
  referenciaDevolucion?: string;
  requiereVerificacionEdad?: boolean;
}

export interface ShipmentTrackingMovement {
  estado?: OrderStatus;
  fecha: string;
  tipo: "ESTADO" | "PUNTO_CONTROL" | "NOVEDAD";
  descripcion: string;
}

export interface ShipmentLocation {
  id: number;
  etapa: string;
  estadoPedido: string;
  ciudad?: string;
  oficina?: string;
  puntoEntrega?: string;
  direccion?: string;
  vehiculoId?: string;
  origen: string;
  fechaEvento: string;
  fechaRegistro: string;
}

export type LocationStage =
  | "EN_BODEGA_ORIGEN"
  | "EN_TRANSITO_VEHICULO"
  | "LLEGADO_OFICINA_DESTINO"
  | "EN_PUNTO_ENTREGA";
export type CheckpointStage =
  | "RECIBIDO_EN_ORIGEN"
  | "EN_TRANSITO"
  | "EN_REPARTO"
  | "ENTREGADO";
export interface LocationEvent {
  id: number;
  pedidoId: number;
  numeroTracking: string;
  etapa: LocationStage;
  estadoPedido: OrderStatus;
  ciudadId: number;
  ciudad: string;
  oficinaId?: number;
  oficina?: string;
  puntoEntregaId?: number;
  puntoEntrega?: string;
  direccion?: string;
  vehiculoId?: string;
  usuarioId: number;
  origen: "WEB" | "APP_MOVIL" | "QR" | "SELECTOR";
  fechaEvento: string;
  fechaRegistro: string;
  corrigeEventoId?: number;
}
export interface TraceEvent {
  id: number;
  pedidoId: number;
  usuarioId?: number;
  fecha: string;
  tipoEvento: string;
  campoObservado?: string;
  detalle?: string;
  latitud?: number;
  longitud?: number;
  ubicacion?: string;
  etapa?: string;
  estadoRegistro?: string;
  eventoReferenciadoId?: number;
}
export interface TracePage {
  content: TraceEvent[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}
export interface TraceSummary {
  pedidoId: number;
  ultimoEvento?: TraceEvent;
  ultimoPuntoControl?: CheckpointResponse;
}
export interface CheckpointResponse {
  id: number;
  pedidoId: number;
  idEventoCliente: string;
  etapa: CheckpointStage;
  estadoAnterior?: OrderStatus;
  estadoResultante?: OrderStatus;
  latitud?: number;
  longitud?: number;
  precisionMetros?: number;
  ubicacionConfiable: boolean;
  origen: string;
  estadoRegistro: string;
  motivoRevision?: string;
  fechaEvento: string;
  fechaRegistro: string;
}

export interface ShipmentTrackingResponse {
  id: number;
  numeroPedido: string;
  numeroTracking: string;
  estado: OrderStatus;
  fechaEstimadaEntrega?: string;
  movimientos: ShipmentTrackingMovement[];
  descripcionEstado: string;
  novedad?: {
    titulo: string;
    mensaje: string;
    fecha: string;
  };
  ubicaciones?: ShipmentLocation[];
  numeroTrackingDevolucion?: string;
  referenciaDevolucion?: string;
  fechaEntregaReprogramada?: string;
}

export interface ShipmentManagementResponse {
  pedidoId: number;
  numeroTracking: string;
  estado: OrderStatus;
  numeroTrackingDevolucion?: string | null;
  fechaEntregaReprogramada?: string;
  fechaLimiteVerificacionDireccion?: string;
}

export interface ReschedulingRangeResponse {
  desde: string;
  hasta: string;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface OrderEvent {
  id: number;
  usuarioId?: number;
  tipoEvento: string;
  campoObservado?: string;
  detalle?: string;
  fecha: string;
}
export interface LabelResponse {
  numeroPedido: string;
  numeroTracking: string;
  contenido: string;
  fechaImpresion: string;
}
export interface IncidentType {
  codigo: string;
  descripcion: string;
  requiereComentario: boolean;
  estadoResultante: OrderStatus | null;
  cuentaComoIntento: boolean;
}
export interface Incident {
  id: number;
  pedidoId: number;
  tipo: string;
  descripcionTipo: string;
  comentario: string | null;
  latitud: number | null;
  longitud: number | null;
  estadoAnterior: OrderStatus;
  estadoResultante: OrderStatus | null;
  numeroIntento: number | null;
  reportadoPorUsuarioId: number;
  fecha: string;
}
export interface IncidentsResponse {
  pedidoId: number;
  estado: OrderStatus;
  versionPedido: number;
  intentosEntregaFallidos: number;
  incidencias: Incident[];
}
export interface RegisterIncidentPayload {
  tipo: string;
  comentario?: string;
  latitud?: number;
  longitud?: number;
  precisionMetros?: number;
  versionEsperada?: number;
}
export interface RegisterIncidentResponse {
  incidencia: Incident;
  estadoPedido: OrderStatus;
  versionPedido: number;
  intentosEntregaFallidos: number;
}
export interface RouteStop {
  id: number;
  pedidoId: number;
  orden: number;
  estado: "PENDIENTE" | "ENTREGADO" | "CANCELADA";
  fechaAsignacion: string;
  numeroPedido?: string;
  numeroTracking?: string;
  direccionDestino?: string;
  ciudadDestino?: string;
  destinatarioNombre?: string;
  destinatarioTelefono?: string;
  prioridad?: Priority;
  pesoKg?: number;
}
export interface RouteResponse {
  id: number;
  conductorId: number;
  fecha: string;
  paradas: RouteStop[];
}
export interface DriverResponse {
  usuarioId: number;
  nombre: string;
  estado: string;
  capacidadMaxKg: number;
  capacidadMaxVolumenCm3: number;
  maxEntregasDia: number;
  entregasAsignadas: number;
  pesoAsignadoKg: number;
  volumenAsignadoCm3: number;
}
export interface RouteNotification {
  id: number;
  pedidoId: number;
  mensaje: string;
  fecha: string;
  leida: boolean;
}
export interface AssignmentEvent {
  id: number;
  pedidoId: number;
  conductorAnteriorId?: number;
  conductorNuevoId: number;
  operadorId: number;
  accion: "ASIGNACION" | "REASIGNACION";
  motivo?: string;
  fecha: string;
}
export interface UserResponse {
  id: number;
  nombre: string;
  email: string;
  telefono?: string;
  direccion?: string;
  rol: Role;
  estado: UserStatus;
  fechaCreacion: string;
}

export type DeliveryResult =
  | "ENTREGADO"
  | "ENTREGA_FALLIDA"
  | "DEVOLUCION_AL_REMITENTE";
export type DeliveryState =
  | "PENDIENTE"
  | "ENTREGADO"
  | "FALLIDA"
  | "DEVOLUCION_AL_REMITENTE"
  | "CANCELADA";

export interface DriverProgress {
  fecha: string;
  totalEntregas: number;
  entregadas: number;
  pendientes: number;
  fallidas: number;
  canceladas: number;
}

export interface DriverDelivery {
  idParada: number;
  pedidoId: number;
  orden: number;
  numeroPedido: string;
  numeroTracking: string;
  direccionDestino: string;
  ciudadDestino: string;
  destinatarioNombre: string;
  destinatarioTelefono: string;
  prioridad: Priority;
  pesoKg: number;
  estadoParada: DeliveryState;
  estadoPedido: string;
}

export interface DriverDeliveryDetail extends DriverDelivery {
  codigoPostalDestino: string;
  indicacionesAcceso: string | null;
  descripcionPaquete: string | null;
  largoCm: number | null;
  anchoCm: number | null;
  altoCm: number | null;
  observacionesValidacion: string | null;
  fechaEntregaReprogramada: string | null;
  ultimosEventos: DriverHistoryEvent[];
  requiereVerificacionEdad?: boolean;
  resultadoVerificacionEdad?: "CUMPLE" | "NO_CUMPLE" | null;
}

export type AgeVerificationResult = "CUMPLE" | "NO_CUMPLE";
export interface AgeVerificationResponse {
  pedidoId: number;
  resultado: AgeVerificationResult;
  estadoPedido: string;
  fechaVerificacion: string;
  puedeEntregarse: boolean;
  duplicado: boolean;
}

export interface DriverHistoryEvent {
  tipoEvento: string;
  detalle: string | null;
  fecha: string;
}

export interface NextStop {
  idParada: number;
  pedidoId: number;
  orden: number;
  numeroTracking: string;
  direccionDestino: string;
  ciudadDestino: string;
  destinatarioNombre: string;
  destinatarioTelefono: string;
  indicacionesAcceso: string | null;
  pesoKg: number;
}

export interface DriverRouteStop {
  paradaId: number;
  pedidoId: number;
  orden: number;
  direccion: string;
  ciudad: string;
  estado: DeliveryState;
  sinUbicacion: boolean;
}

export interface DriverRoute {
  paradas: DriverRouteStop[];
  siguiente: DriverRouteStop | null;
}

export interface NoveltyOption {
  codigo: string;
  resultado: Exclude<DeliveryResult, "ENTREGADO">;
  descripcion: string;
}

export interface DeliveryEvent {
  resultado: DeliveryResult;
  codigoNovedad: string | null;
  motivo: string | null;
  latitud: number | null;
  longitud: number | null;
  fechaEvento: string;
  idEventoCliente: string;
}

export interface DeliveryResultResponse {
  idEventoCliente: string;
  pedidoId: number;
  resultado: DeliveryResult;
  estado: "APLICADO" | "DUPLICADO";
  fechaEvento: string;
  duplicado: boolean;
}

export interface OfflineDeliveryEvent {
  pedidoId: number;
  evento: DeliveryEvent;
  queuedAt: string;
}

export interface SyncResult {
  idEventoCliente: string;
  estado: "APLICADO" | "DUPLICADO" | "RECHAZADO";
  motivoRechazo: string | null;
}

export interface SyncResponse {
  resultados: SyncResult[];
}

// HU-18. El portal recibe exclusivamente mensajes y contacto de oficina.
export type AnomalyType =
  | "PERMANENCIA_EXCESIVA"
  | "INACTIVIDAD_REPARTO"
  | "REINTENTOS_ACUMULADOS"
  | "SIN_CONFIRMACION_RECEPCION";
export type AnomalyState = "ACTIVA" | "RESUELTA" | "DESCARTADA";
export interface ClientAnomaly {
  mensaje: string;
  oficina: string;
  telefono: string | null;
  enlaceLlamada: string | null;
}
export interface Anomaly {
  id: number;
  envioId: number;
  numeroTracking: string;
  tipo: AnomalyType;
  estado: AnomalyState;
  eventoOrigen: string;
  oficinaId: number | null;
  oficina: string | null;
  inicioUtc: string;
  umbralHoras: number | null;
  intentos: number | null;
  creadaEn: string;
  cerradaEn: string | null;
  cerradaPor: number | null;
  origenCierre: string | null;
}
export interface AnomalyPage {
  contenido: Anomaly[];
  pagina: number;
  tamano: number;
  total: number;
}
export interface AnomalyParameters {
  permanenciaHoras: number;
  inactividadHoras: number;
  recepcionHoras: number;
  version: number;
}
export interface PendingReceipt {
  envioId: number;
  eventoLlegadaId: number;
  numeroTracking: string;
  oficina: string | null;
  llegada: string;
}
