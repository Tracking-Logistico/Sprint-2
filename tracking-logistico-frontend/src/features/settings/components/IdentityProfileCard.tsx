import { useState } from "react";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, getApiError } from "@/lib/api";
import type { IdentityProfile } from "@/types/api";

export function IdentityProfileCard({
  identity,
  token,
  onSaved,
}: {
  identity: IdentityProfile | null;
  token: string | null;
  onSaved: (identity: IdentityProfile) => void;
}) {
  const [numeroCedula, setNumeroCedula] = useState("");
  const [fechaNacimiento, setFechaNacimiento] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  if (!identity) return null;

  async function save() {
    if (!token || saving) return;
    setError("");
    setSaving(true);
    try {
      const saved = await api.completeMyIdentity(
        { numeroCedula, fechaNacimiento },
        token,
      );
      onSaved(saved);
      setNumeroCedula("");
      setFechaNacimiento("");
      toast.success("Datos de identidad registrados.");
    } catch (err) {
      const message = getApiError(
        err,
        "No fue posible registrar los datos de identidad.",
      );
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Datos de identidad</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {identity.registrada ? (
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div>
              <p className="text-muted-foreground">Cédula</p>
              <p className="font-medium">{identity.numeroCedulaEnmascarado}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Fecha de nacimiento</p>
              <p className="font-medium">{identity.fechaNacimiento}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Edad</p>
              <p className="font-medium">{identity.edad} años</p>
            </div>
          </div>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">
              Completa estos datos una sola vez. La edad será calculada por el
              servidor.
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="grid gap-2">
                <Label htmlFor="identity-cedula">Número de cédula</Label>
                <Input
                  id="identity-cedula"
                  value={numeroCedula}
                  onChange={(event) => setNumeroCedula(event.target.value)}
                  inputMode="numeric"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="identity-birth">Fecha de nacimiento</Label>
                <Input
                  id="identity-birth"
                  type="date"
                  value={fechaNacimiento}
                  onChange={(event) => setFechaNacimiento(event.target.value)}
                  max={new Date().toISOString().slice(0, 10)}
                  required
                />
              </div>
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button onClick={() => void save()} disabled={saving}>
              {saving ? "Guardando..." : "Guardar datos de identidad"}
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}
