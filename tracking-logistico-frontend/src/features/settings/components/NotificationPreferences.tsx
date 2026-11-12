import { BellRing } from "lucide-react";
import { toast } from "sonner";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import type { NotificationChannel } from "@/types/api";
import { useNotificationPreferences } from "../hooks/useNotificationPreferences";

export function NotificationPreferences({
  token,
  defaultPhone,
}: {
  token: string | null;
  defaultPhone: string;
}) {
  const preferences = useNotificationPreferences(token, defaultPhone);
  const needsPhone =
    preferences.channel === "SMS" || preferences.channel === "AMBOS";

  async function submit() {
    if (await preferences.save())
      toast.success("Preferencias de notificación actualizadas.");
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <BellRing className="size-5 text-emerald-700" />
          Notificaciones
        </CardTitle>
        <CardDescription>
          Elige cómo recibir avisos sobre los cambios importantes de tus envíos.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {preferences.loading ? (
          <div
            className="space-y-3"
            aria-label="Cargando preferencias de notificación"
          >
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : (
          <>
            {preferences.error && (
              <Alert variant="destructive">
                <AlertTitle>No pudimos actualizar tus preferencias</AlertTitle>
                <AlertDescription>{preferences.error}</AlertDescription>
              </Alert>
            )}
            <RadioGroup
              aria-label="Canal de notificación"
              className="sm:grid-cols-3"
            >
              {(["EMAIL", "SMS", "AMBOS"] as const).map((value) => (
                <Label
                  key={value}
                  className="flex min-h-12 cursor-pointer items-center gap-3 rounded-lg border p-3 has-checked:border-emerald-600 has-checked:bg-emerald-50"
                >
                  <RadioGroupItem
                    name="notification-channel"
                    value={value}
                    checked={preferences.channel === value}
                    onChange={() =>
                      preferences.setChannel(value as NotificationChannel)
                    }
                  />
                  <span>
                    {value === "EMAIL"
                      ? "Email"
                      : value === "SMS"
                        ? "SMS"
                        : "Email y SMS"}
                  </span>
                </Label>
              ))}
            </RadioGroup>
            {needsPhone && (
              <div className="grid gap-2">
                <Label htmlFor="notification-sms-phone">
                  Teléfono para SMS
                </Label>
                <Input
                  id="notification-sms-phone"
                  type="tel"
                  inputMode="tel"
                  autoComplete="tel"
                  placeholder="+573001234567"
                  value={preferences.phone}
                  onChange={(event) => preferences.setPhone(event.target.value)}
                />
                <p className="text-xs text-muted-foreground">
                  Usa entre 8 y 15 dígitos, con prefijo + opcional.
                </p>
              </div>
            )}
            <Button
              type="button"
              className="w-full sm:w-auto"
              disabled={preferences.saving}
              onClick={() => void submit()}
            >
              {preferences.saving ? "Guardando..." : "Guardar preferencias"}
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}
