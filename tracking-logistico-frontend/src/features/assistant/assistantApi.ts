export type AssistantResult =
  | "RESUELTA"
  | "FUERA_DE_ALCANCE"
  | "SIN_DATOS"
  | "ERROR";
export type AssistantCard =
  | { tipo: "PAQUETE"; tracking: string; estado: string; ubicacion: string }
  | {
      tipo: "PERFIL";
      nombre: string;
      rol: string;
      estadoCuenta: string;
      correo: string;
      telefono: string;
      cedulaEnmascarada: string;
    };

function isAssistantCard(value: unknown): value is AssistantCard {
  if (!value || typeof value !== "object" || !("tipo" in value)) return false;
  const fields =
    value.tipo === "PAQUETE"
      ? ["tracking", "estado", "ubicacion"]
      : value.tipo === "PERFIL"
        ? ["nombre", "rol", "estadoCuenta", "correo", "telefono", "cedulaEnmascarada"]
        : null;
  return fields !== null && fields.every(
    (field) => typeof (value as Record<string, unknown>)[field] === "string",
  );
}

function legacyCard(message: string): AssistantCard | undefined {
  const fields = new Map<string, string>();
  for (const line of message.split("\n")) {
    const delimiter = line.indexOf(" — ");
    if (delimiter === -1) return undefined;
    const label = line.slice(0, delimiter);
    if (fields.has(label)) return undefined;
    fields.set(label, line.slice(delimiter + 3));
  }

  const tracking = fields.get("Paquete");
  const estado = fields.get("Estado actual");
  const ubicacion = fields.get("Última ubicación registrada");
  if (fields.size === 3 && tracking && estado && ubicacion) {
    return { tipo: "PAQUETE", tracking, estado, ubicacion };
  }

  const nombre = fields.get("Nombre");
  const rol = fields.get("Rol");
  const estadoCuenta = fields.get("Estado de cuenta");
  const correo = fields.get("Correo");
  const telefono = fields.get("Teléfono");
  const cedulaEnmascarada = fields.get("Cédula");
  if (fields.size === 6 && nombre && rol && estadoCuenta && correo && telefono &&
      cedulaEnmascarada && /^(?:\*+[0-9]{4}|No registrada)$/.test(cedulaEnmascarada)) {
    return { tipo: "PERFIL", nombre, rol, estadoCuenta, correo, telefono, cedulaEnmascarada };
  }
}

export interface AssistantAnswer {
  sessionId: string | null;
  resultado: AssistantResult;
  mensaje: string;
  tarjeta?: AssistantCard;
}
export interface AssistantQuery {
  texto: string;
  sessionId: string | null;
  solicitudId: string;
  origen: "WEB";
}

export async function queryAssistant(
  query: AssistantQuery,
  token: string,
  signal: AbortSignal,
): Promise<AssistantAnswer> {
  const origin = (
    import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1"
  ).replace(/\/$/, "");
  const response = await fetch(`${origin}/assistant/query`, {
    method: "POST",
    signal,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(query),
  });
  if (!response.ok) {
    if (response.status === 403)
      throw new Error(
        "Esta consulta no está disponible para tu cuenta o la sesión del chat venció. Abre una nueva consulta.",
      );
    if (response.status === 401)
      throw new Error("Tu sesión venció. Inicia sesión nuevamente.");
    if (response.status === 404)
      throw new Error("No se encontró la información consultada.");
    if (response.status === 409)
      throw new Error(
        "La consulta no pudo completarse porque la información cambió. Intenta nuevamente.",
      );
    if (response.status === 422)
      throw new Error("La consulta no tiene un formato válido.");
    if (response.status === 429)
      throw new Error(
        "Hay demasiadas consultas. Espera un momento e inténtalo de nuevo.",
      );
    throw new Error("No pudimos completar la consulta. Intenta nuevamente.");
  }
  const answer: unknown = await response.json();
  if (
    !answer ||
    typeof answer !== "object" ||
    !("mensaje" in answer) ||
    typeof answer.mensaje !== "string" ||
    answer.mensaje.length > 4000 ||
    !("sessionId" in answer) ||
    (answer.sessionId !== null && typeof answer.sessionId !== "string")
  )
    throw new Error("No pudimos completar la consulta. Intenta nuevamente.");
  const tarjeta = "tarjeta" in answer ? answer.tarjeta : null;
  return {
    ...(answer as AssistantAnswer),
    tarjeta:
      "resultado" in answer && answer.resultado === "RESUELTA"
        ? (isAssistantCard(tarjeta) ? tarjeta : legacyCard(answer.mensaje))
        : undefined,
  };
}
