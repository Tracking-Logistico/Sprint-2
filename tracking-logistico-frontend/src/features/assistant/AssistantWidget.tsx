import { useEffect, useRef, useState } from "react";
import { MessageCircle, X, LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { queryAssistant } from "./assistantApi";

type Message = { id: string; role: "user" | "assistant"; text: string };

export function AssistantWidget({ token }: { token: string }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [session, setSession] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const active = useRef<AbortController | null>(null);

  useEffect(() => () => { active.current?.abort(); active.current = null; }, []);

  function close() {
    active.current?.abort(); active.current = null;
    setOpen(false); setPending(false); setMessages([]); setText(""); setSession(null); setError("");
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const question = text.trim();
    if (!question || question.length > 500 || pending || active.current) return;
    const controller = new AbortController();
    active.current = controller;
    const timer = window.setTimeout(() => controller.abort(), 10000);
    setPending(true); setError(""); setText("");
    setMessages((old) => [...old.slice(-18), { id: crypto.randomUUID(), role: "user", text: question }]);
    try {
      const answer = await queryAssistant({ texto: question, sessionId: session, solicitudId: crypto.randomUUID(), origen: "WEB" }, token, controller.signal);
      if (active.current !== controller) return;
      setSession(answer.sessionId);
      setMessages((old) => [...old.slice(-19), { id: crypto.randomUUID(), role: "assistant", text: answer.mensaje }]);
    } catch (err) {
      if (active.current !== controller) return;
      setError(controller.signal.aborted ? "La consulta tardó más de lo esperado. Intenta nuevamente." : err instanceof Error ? err.message : "No pudimos completar la consulta.");
    } finally {
      window.clearTimeout(timer);
      if (active.current === controller) { active.current = null; setPending(false); }
    }
  }

  if (!open) return <Button className="fixed bottom-5 right-5 z-50 min-h-12 rounded-full shadow-lg" onClick={() => setOpen(true)}>
    <MessageCircle className="size-5" /> Asistente interno
  </Button>;

  return <aside aria-label="Asistente interno" className="fixed bottom-4 right-4 z-50 flex max-h-[85dvh] w-[min(390px,calc(100vw-2rem))] flex-col rounded-xl border bg-card text-card-foreground shadow-xl">
    <header className="flex items-center justify-between gap-2 border-b px-4 py-3">
      <h2 className="font-semibold">Asistente interno</h2>
      <Button variant="ghost" size="icon" aria-label="Cerrar asistente" onClick={close}><X className="size-4" /></Button>
    </header>
    <p className="border-b px-4 py-3 text-sm text-muted-foreground">Consulta perfiles de usuario y el estado o última ubicación de un paquete.</p>
    <div className="min-h-36 space-y-3 overflow-y-auto px-4 py-3" aria-live="polite" aria-label="Conversación">
      {messages.length === 0 && <p className="text-sm text-muted-foreground">Prueba con «¿Qué rol tiene Juan Pérez?» o «¿Dónde está el paquete CÓDIGO?».</p>}
      {messages.map((message) => <div className={`whitespace-pre-wrap break-words rounded-lg p-3 text-sm ${message.role === "user" ? "bg-primary/10" : "bg-muted"}`} key={message.id}>
        <p className="mb-1 text-xs font-semibold">{message.role === "user" ? "Tú" : "Asistente"}</p>
        <p>{message.text}</p>
      </div>)}
      {pending && <p role="status" className="flex items-center gap-2 text-sm"><LoaderCircle className="size-4 animate-spin" /> Generando respuesta…</p>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
    <form className="space-y-2 border-t p-4" onSubmit={(event) => void submit(event)}>
      <label className="text-sm" htmlFor="assistant-question">Tu consulta</label>
      <input id="assistant-question" className="block min-h-11 w-full rounded-md border bg-background px-3 py-2 text-sm" value={text} onChange={(event) => setText(event.target.value)} maxLength={500} required disabled={pending} autoComplete="off" />
      <div className="flex items-center justify-between gap-2"><span className="text-xs text-muted-foreground">{text.length}/500</span><Button type="submit" disabled={pending || !text.trim()}>Consultar</Button></div>
    </form>
  </aside>;
}
