export type AssistantResult =
  | "RESUELTA"
  | "FUERA_DE_ALCANCE"
  | "SIN_DATOS"
  | "ERROR";
export interface AssistantAnswer {
  sessionId: string | null;
  resultado: AssistantResult;
  mensaje: string;
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
  return answer as AssistantAnswer;
}
