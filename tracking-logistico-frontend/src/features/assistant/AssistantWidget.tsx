import { useEffect, useRef, useState } from "react";
import {
  Bot, Building2, Check, Copy, LoaderCircle, MapPin, MessageCircle,
  Package, Send, Truck, UserRound, X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { queryAssistant, type AssistantCard } from "./assistantApi";

type Message = {
  id: string;
  role: "user" | "assistant";
  text: string;
  card?: AssistantCard;
};

function StatusBadge({ value }: { value: string }) {
  const tone = /fallid|rechazad|cancelad|bloquead|inactiv/i.test(value)
    ? "bg-destructive/10 text-destructive"
    : /pendiente|pausad|devoluci/i.test(value)
      ? "bg-amber-100 text-amber-900"
      : "bg-primary/10 text-primary";
  return (
    <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold first-letter:uppercase ${tone}`}>
      {value}
    </span>
  );
}

function AnswerCard({ card }: { card: AssistantCard }) {
  const [copyStatus, setCopyStatus] = useState("");
  if (card.tipo === "PAQUETE") {
    const LocationIcon = card.ubicacion.startsWith("Vehículo") ? Truck
      : card.ubicacion.startsWith("Oficina") ? Building2 : MapPin;

    async function copyTracking(tracking: string) {
      try {
        await navigator.clipboard.writeText(tracking);
        setCopyStatus("Código copiado");
      } catch {
        setCopyStatus("No se pudo copiar el código");
      }
    }

    return (
      <section aria-label="Ficha del paquete" className="rounded-xl border border-primary/20 bg-card p-3.5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
          <div className="flex min-w-0 items-center gap-2">
            <Package aria-hidden="true" className="size-5 shrink-0 text-primary" />
            <h3 className="wrap-break font-semibold tracking-tight">{card.tracking}</h3>
            <Button type="button" variant="ghost" size="icon" className="size-8 shrink-0"
              aria-label="Copiar código de seguimiento" onClick={() => void copyTracking(card.tracking)}>
              {copyStatus === "Código copiado" ? <Check className="size-4" /> : <Copy className="size-4" />}
            </Button>
          </div>
          <StatusBadge value={card.estado} />
        </div>
        <div className="mt-3 flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
            <LocationIcon aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">Última ubicación registrada</p>
            <p className="wrap-break font-semibold">{card.ubicacion}</p>
          </div>
        </div>
        {copyStatus && <p className="mt-2 text-xs text-muted-foreground">{copyStatus}</p>}
      </section>
    );
  }

  return (
    <section aria-label="Ficha del usuario" className="rounded-xl border border-primary/20 bg-card p-3.5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2 border-b pb-3">
        <div className="flex min-w-0 items-center gap-2">
          <UserRound aria-hidden="true" className="size-5 shrink-0 text-primary" />
          <div className="min-w-0">
            <h3 className="wrap-break font-semibold">{card.nombre}</h3>
            <p className="text-xs text-muted-foreground first-letter:uppercase">{card.rol}</p>
          </div>
        </div>
        <StatusBadge value={card.estadoCuenta} />
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-2">
        {[
          ["Correo", card.correo],
          ["Teléfono", card.telefono],
          ["Cédula enmascarada", card.cedulaEnmascarada],
        ].map(([label, value]) => (
          <div key={label} className={label === "Correo" ? "col-span-2 min-w-0" : "min-w-0"}>
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="wrap-break font-medium">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export function AssistantWidget({ token }: { token: string }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [session, setSession] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const active = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const conversation = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const panel = conversation.current;
    if (panel) panel.scrollTop = panel.scrollHeight;
  }, [messages, pending, error]);

  function prepareQuestion(prefix: string) {
    setText(prefix);
    input.current?.focus();
  }

  useEffect(
    () => () => {
      active.current?.abort();
      active.current = null;
    },
    [],
  );

  function close() {
    active.current?.abort();
    active.current = null;
    setOpen(false);
    setPending(false);
    setMessages([]);
    setText("");
    setSession(null);
    setError("");
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const question = text.trim();
    if (!question || question.length > 500 || pending || active.current) return;
    const controller = new AbortController();
    active.current = controller;
    const timer = window.setTimeout(() => controller.abort(), 10000);
    setPending(true);
    setError("");
    setText("");
    setMessages((old) => [
      ...old.slice(-18),
      { id: crypto.randomUUID(), role: "user", text: question },
    ]);
    try {
      const answer = await queryAssistant(
        {
          texto: question,
          sessionId: session,
          solicitudId: crypto.randomUUID(),
          origen: "WEB",
        },
        token,
        controller.signal,
      );
      if (active.current !== controller) return;
      setSession(answer.sessionId);
      setMessages((old) => [
        ...old.slice(-19),
        {
          id: crypto.randomUUID(),
          role: "assistant",
          text: answer.mensaje,
          card: answer.tarjeta,
        },
      ]);
    } catch (err) {
      if (active.current !== controller) return;
      setError(
        controller.signal.aborted
          ? "La consulta tardó más de lo esperado. Intenta nuevamente."
          : err instanceof Error
            ? err.message
            : "No pudimos completar la consulta.",
      );
    } finally {
      window.clearTimeout(timer);
      if (active.current === controller) {
        active.current = null;
        setPending(false);
      }
    }
  }

  if (!open)
    return (
      <Button
        className="fixed bottom-5 right-5 z-50 min-h-12 rounded-full shadow-lg"
        onClick={() => setOpen(true)}
      >
        <MessageCircle className="size-5" /> Asistente interno
      </Button>
    );

  return (
    <aside
      aria-label="Asistente interno"
      className="fixed bottom-4 right-4 z-50 flex h-[min(660px,calc(100dvh-2rem))] w-[min(460px,calc(100vw-2rem))] flex-col overflow-hidden rounded-2xl border bg-card text-card-foreground shadow-2xl"
    >
      <header className="flex shrink-0 items-center gap-3 border-b px-4 py-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary text-primary-foreground">
          <Bot aria-hidden="true" className="size-5" />
        </span>
        <h2 className="flex-1 font-semibold">Asistente interno</h2>
        <Button type="button" variant="ghost" size="icon" aria-label="Cerrar asistente" onClick={close}>
          <X className="size-4" />
        </Button>
      </header>
      <div className="grid shrink-0 grid-cols-2 gap-2 border-b px-4 py-3">
        <Button type="button" variant="outline" className="h-auto min-h-11 whitespace-normal px-2 text-xs sm:text-sm"
          disabled={pending} onClick={() => prepareQuestion("¿Dónde está el paquete ")}>
          <Truck aria-hidden="true" className="size-4 shrink-0" /> Consultar un envío
        </Button>
        <Button type="button" variant="outline" className="h-auto min-h-11 whitespace-normal px-2 text-xs sm:text-sm"
          disabled={pending} onClick={() => prepareQuestion("¿Qué rol tiene ")}>
          <UserRound aria-hidden="true" className="size-4 shrink-0" /> Consultar un perfil
        </Button>
      </div>
      <div ref={conversation} className="min-h-0 flex-1 space-y-4 overflow-y-auto bg-muted/20 px-4 py-4 text-sm"
        aria-live="polite" aria-label="Conversación">
        {messages.length === 0 && (
          <p className="rounded-xl border bg-card p-3 text-muted-foreground">
            Escribe el código de seguimiento de un envío o el nombre o correo de un usuario.
          </p>
        )}
        {messages.map((message) => message.role === "user" ? (
          <div key={message.id} className="ml-auto w-fit max-w-[90%] whitespace-pre-wrap wrap-break rounded-xl rounded-br-sm bg-primary/10 px-3 py-2.5">
            <span className="sr-only">Tú: </span>{message.text}
          </div>
        ) : (
          <div key={message.id} className="flex items-start gap-2.5">
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-primary text-primary-foreground">
              <Bot aria-hidden="true" className="size-4" />
            </span>
            <div className="min-w-0 flex-1 space-y-2">
              <span className="sr-only">Asistente: </span>
              {message.card ? (
                <>
                  <p className="pt-1.5">{message.card.tipo === "PAQUETE" ? "Encontré este envío." : "Encontré este perfil."}</p>
                  <AnswerCard card={message.card} />
                </>
              ) : (
                <p className="whitespace-pre-wrap wrap-break rounded-xl rounded-tl-sm bg-muted px-3 py-2.5">{message.text}</p>
              )}
            </div>
          </div>
        ))}
        {pending && (
          <p role="status" className="flex items-center gap-2 text-muted-foreground">
            <LoaderCircle aria-hidden="true" className="size-4 animate-spin" /> Generando respuesta…
          </p>
        )}
        {error && (
          <p role="alert" className="rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-destructive">
            {error}
          </p>
        )}
      </div>
      <form className="shrink-0 border-t p-3.5" onSubmit={(event) => void submit(event)}>
        <label className="sr-only" htmlFor="assistant-question">Tu consulta</label>
        <div className="flex items-center gap-2 rounded-xl border bg-background p-1.5">
          <Input ref={input} id="assistant-question" className="min-h-10 min-w-0 border-0 bg-transparent shadow-none"
            placeholder="Escribe tu consulta" value={text} onChange={(event) => setText(event.target.value)}
            maxLength={500} required disabled={pending} autoComplete="off" />
          <Button type="submit" size="icon" className="size-10 shrink-0 rounded-lg"
            aria-label="Consultar" disabled={pending || !text.trim()}>
            <Send aria-hidden="true" className="size-4" />
          </Button>
        </div>
      </form>
    </aside>
  );
}
