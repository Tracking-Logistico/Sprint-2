import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { PackageCheck, ShieldCheck } from "lucide-react";
import { api, getApiError } from "@/lib/api";
import { usePageMeta } from "@/hooks/usePageMeta";
import {
  RegisterForm,
  type RegisterFormValues,
} from "@/features/auth/components/RegisterForm";
import { RegistrationSuccess } from "@/features/auth/components/RegistrationSuccess";
import { toast } from "sonner";

const passwordPattern =
  /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,}$/;
const TERMS_VERSION = "2026-09";
const PRIVACY_VERSION = "2026-09";

function passwordStrength(password: string) {
  const checks = [
    password.length >= 8,
    password.length >= 12,
    /[A-Z]/.test(password),
    /[a-z]/.test(password),
    /\d/.test(password),
    /[^A-Za-z0-9]/.test(password),
  ];
  const score = checks.filter(Boolean).length;
  if (!password) return { label: "Sin definir", width: "0%" };
  if (score <= 3) return { label: "Débil", width: "33%" };
  if (score <= 5) return { label: "Media", width: "66%" };
  return { label: "Fuerte", width: "100%" };
}

export function RegisterPage() {
  usePageMeta(
    "Registro de cliente",
    "Crea una cuenta de cliente para gestionar tus envíos.",
  );
  const [form, setForm] = useState<RegisterFormValues>({
    nombre: "",
    email: "",
    password: "",
    confirmarPassword: "",
    telefono: "",
    direccion: "",
    ciudad: "",
    numeroCedula: "",
    fechaNacimiento: "",
    aceptoTerminos: false,
    aceptoPoliticaDatos: false,
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const strength = useMemo(
    () => passwordStrength(form.password),
    [form.password],
  );
  const update = (key: keyof RegisterFormValues, value: string | boolean) =>
    setForm((current) => ({ ...current, [key]: value }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!passwordPattern.test(form.password)) {
      const msg =
        "La contraseña debe tener mínimo 8 caracteres, mayúscula, minúscula, número y carácter especial.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (
      form.password.toLowerCase() === form.email.toLowerCase() ||
      form.password.toLowerCase() === form.nombre.toLowerCase()
    ) {
      const msg = "La contraseña no puede ser igual a tu correo o nombre.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (form.password !== form.confirmarPassword) {
      const msg = "Las contraseñas no coinciden.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (!form.aceptoTerminos || !form.aceptoPoliticaDatos) {
      const msg =
        "Debes aceptar los Términos y la Política de Tratamiento de Datos.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (!/^[1-9][0-9 .]{5,19}$/.test(form.numeroCedula) ||
        form.numeroCedula.replace(/[ .]/g, "").length < 6 ||
        form.numeroCedula.replace(/[ .]/g, "").length > 10) {
      const msg = "El número de cédula debe tener entre 6 y 10 dígitos y no puede empezar por cero.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (!form.fechaNacimiento) {
      const msg = "La fecha de nacimiento es obligatoria.";
      setError(msg);
      toast.error(msg);
      return;
    }
    const birthDate = new Date(`${form.fechaNacimiento}T00:00:00`);
    if (Number.isNaN(birthDate.getTime()) || birthDate.toISOString().slice(0, 10) !== form.fechaNacimiento) {
      const msg = "La fecha de nacimiento no es válida.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (birthDate > new Date()) {
      const msg = "La fecha de nacimiento no puede ser futura.";
      setError(msg);
      toast.error(msg);
      return;
    }

    setLoading(true);
    try {
      await api.registerClient({
        ...form,
        versionTerminos: TERMS_VERSION,
        versionPoliticaDatos: PRIVACY_VERSION,
      });
      setDone(true);
      toast.success("¡Cuenta creada exitosamente!");
    } catch (err) {
      const apiMsg = getApiError(err, "No fue posible crear la cuenta.");
      setError(apiMsg);
      toast.error(apiMsg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="grid min-h-[calc(100vh-4.5rem)] lg:grid-cols-[0.9fr_1.1fr]">
      {/* Banner lateral izquierdo (estilo, colores y espaciado idéntico a LoginPage) */}
      <section className="hidden bg-slate-950 px-10 py-16 text-white lg:flex lg:flex-col lg:justify-between">
        <div>
          <Link to="/" className="inline-flex items-center gap-2 font-semibold">
            <PackageCheck className="size-5 text-emerald-400" />
            LogisTrack
          </Link>
          <div className="mt-20 max-w-md">
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-emerald-300">
              Cuenta de cliente
            </p>
            <h1 className="mt-5 text-5xl font-semibold leading-[1.05] tracking-tight">
              Toda la logística en una misma dirección.
            </h1>
            <p className="mt-6 text-lg leading-8 text-slate-300">
              Registra tus envíos, consulta el estado de tus paquetes en tiempo
              real y gestiona toda tu operación fácilmente.
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <ShieldCheck className="size-4 text-emerald-400" />
          Acceso inmediato sin bloqueo por verificación
        </div>
      </section>

      {/* Sección central/derecha del formulario */}
      <section className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-xl">
          <div className="mb-6 lg:hidden">
            <Link
              to="/"
              className="inline-flex items-center gap-2 font-semibold"
            >
              <PackageCheck className="size-5 text-emerald-600" />
              LogisTrack
            </Link>
          </div>

          {done ? (
            <RegistrationSuccess email={form.email} />
          ) : (
            <RegisterForm
              form={form}
              onChange={update}
              onSubmit={submit}
              loading={loading}
              error={error}
              strength={strength}
            />
          )}
        </div>
      </section>
    </main>
  );
}
