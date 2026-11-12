import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Eye, EyeOff, ShieldAlert } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Separator } from "@/components/ui/separator";
import { FormInputField } from "./FormInputField";
import { PasswordStrengthMeter } from "./PasswordStrengthMeter";
import { AcceptanceCheckbox } from "./AcceptanceCheckbox";

export interface RegisterFormValues {
  nombre: string;
  email: string;
  password: string;
  confirmarPassword: string;
  telefono: string;
  direccion: string;
  ciudad: string;
  numeroCedula: string;
  fechaNacimiento: string;
  aceptoTerminos: boolean;
  aceptoPoliticaDatos: boolean;
}

interface RegisterFormProps {
  form: RegisterFormValues;
  onChange: (key: keyof RegisterFormValues, value: string | boolean) => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => Promise<void>;
  loading: boolean;
  error: string;
  strength: { label: string; width: string };
}

export function RegisterForm({
  form,
  onChange,
  onSubmit,
  loading,
  error,
  strength,
}: RegisterFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  return (
    <Card className="w-full max-w-xl border-slate-200 bg-white shadow-sm">
      <CardHeader className="space-y-1">
        <p className="text-sm font-semibold uppercase tracking-[0.16em] text-emerald-700">
          Cuenta personal
        </p>
        <CardTitle className="text-3xl font-semibold tracking-tight text-slate-950">
          Crea tu cuenta de cliente
        </CardTitle>
        <CardDescription className="text-sm leading-6 text-slate-500">
          Regístrate para solicitar envíos y consultar su avance. La
          verificación del correo es opcional y no bloquea tu acceso.
        </CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit}>
        <CardContent className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormInputField
              label="Nombre completo"
              name="nombre"
              required
              autoComplete="name"
              value={form.nombre}
              onChange={(e) => onChange("nombre", e.target.value)}
            />
            <FormInputField
              label="Correo electrónico"
              name="email"
              type="email"
              required
              autoComplete="email"
              value={form.email}
              onChange={(e) => onChange("email", e.target.value)}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <FormInputField
              label="Teléfono"
              name="telefono"
              type="tel"
              required
              autoComplete="tel"
              hint="Formato internacional, ej: +573001234567"
              value={form.telefono}
              onChange={(e) => onChange("telefono", e.target.value)}
            />
            <FormInputField
              label="Ciudad"
              name="ciudad"
              required
              value={form.ciudad}
              onChange={(e) => onChange("ciudad", e.target.value)}
            />
          </div>

          <FormInputField
            label="Dirección"
            name="direccion"
            required
            autoComplete="street-address"
            value={form.direccion}
            onChange={(e) => onChange("direccion", e.target.value)}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <FormInputField
              label="Número de cédula"
              name="numeroCedula"
              required
              inputMode="numeric"
              pattern="[1-9][0-9 .]{5,19}"
              hint="Entre 6 y 10 dígitos; puedes usar puntos o espacios."
              value={form.numeroCedula}
              onChange={(e) => onChange("numeroCedula", e.target.value)}
            />
            <FormInputField
              label="Fecha de nacimiento"
              name="fechaNacimiento"
              type="date"
              required
              max={new Date().toISOString().slice(0, 10)}
              value={form.fechaNacimiento}
              onChange={(e) => onChange("fechaNacimiento", e.target.value)}
            />
          </div>

          <Separator className="my-1 bg-slate-100" />

          <div className="grid gap-4 sm:grid-cols-2">
            <FormInputField
              label="Contraseña"
              name="password"
              type={showPassword ? "text" : "password"}
              required
              autoComplete="new-password"
              hint="8+ caracteres: mayúscula, minúscula, número y símbolo."
              value={form.password}
              onChange={(e) => onChange("password", e.target.value)}
              endAdornment={
                <button
                  type="button"
                  tabIndex={-1}
                  className="text-slate-400 hover:text-slate-700"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={
                    showPassword ? "Ocultar contraseña" : "Mostrar contraseña"
                  }
                >
                  {showPassword ? (
                    <EyeOff className="size-4" aria-hidden="true" />
                  ) : (
                    <Eye className="size-4" aria-hidden="true" />
                  )}
                </button>
              }
            />
            <FormInputField
              label="Confirmar contraseña"
              name="confirmarPassword"
              type={showConfirmPassword ? "text" : "password"}
              required
              autoComplete="new-password"
              value={form.confirmarPassword}
              onChange={(e) => onChange("confirmarPassword", e.target.value)}
              endAdornment={
                <button
                  type="button"
                  tabIndex={-1}
                  className="text-slate-400 hover:text-slate-700"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  aria-label={
                    showConfirmPassword
                      ? "Ocultar contraseña"
                      : "Mostrar contraseña"
                  }
                >
                  {showConfirmPassword ? (
                    <EyeOff className="size-4" aria-hidden="true" />
                  ) : (
                    <Eye className="size-4" aria-hidden="true" />
                  )}
                </button>
              }
            />
          </div>

          <PasswordStrengthMeter
            label={strength.label}
            width={strength.width}
          />

          <Separator className="my-1 bg-slate-100" />

          <div className="space-y-3 pt-1">
            <AcceptanceCheckbox
              id="terminos"
              checked={form.aceptoTerminos}
              onCheckedChange={(val) => onChange("aceptoTerminos", val)}
            >
              Acepto los{" "}
              <Link
                to="/terminos"
                target="_blank"
                rel="noopener noreferrer"
                className="font-semibold text-emerald-700 underline hover:text-emerald-800"
              >
                Términos y Condiciones (2026-09)
              </Link>
              .
            </AcceptanceCheckbox>
            <AcceptanceCheckbox
              id="politicaDatos"
              checked={form.aceptoPoliticaDatos}
              onCheckedChange={(val) => onChange("aceptoPoliticaDatos", val)}
            >
              Acepto la{" "}
              <Link
                to="/politica-datos"
                target="_blank"
                rel="noopener noreferrer"
                className="font-semibold text-emerald-700 underline hover:text-emerald-800"
              >
                Política de Tratamiento de Datos Personales (2026-09)
              </Link>
              .
            </AcceptanceCheckbox>
          </div>

          {error && (
            <Alert
              variant="destructive"
              className="border-rose-200 bg-rose-50 text-rose-800"
            >
              <ShieldAlert className="size-4 text-rose-600" />
              <AlertDescription className="text-rose-700">
                {error}
              </AlertDescription>
            </Alert>
          )}
        </CardContent>

        <CardFooter className="flex flex-col-reverse gap-4 border-t border-slate-100 pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-slate-500">
            ¿Ya tienes cuenta?{" "}
            <Link
              to="/login"
              className="font-medium text-emerald-700 hover:underline"
            >
              Inicia sesión
            </Link>
          </p>
          <Button
            type="submit"
            className="h-11 w-full gap-2 sm:w-auto bg-slate-950 text-white hover:bg-slate-800"
            disabled={loading}
          >
            {loading ? "Creando cuenta..." : "Crear cuenta"}
            <ArrowRight className="size-4" />
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
