import type { FormEvent } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface Perfil {
  nombre: string;
  telefono: string;
  direccion: string;
}

interface EditProfileDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  profile: Perfil;
  onChange: (profile: Perfil) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  loading: boolean;
}

export function EditProfileDialog({
  open,
  onOpenChange,
  profile,
  onChange,
  onSubmit,
  loading,
}: EditProfileDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar datos personales</DialogTitle>
          <DialogDescription>
            Actualiza la información de tu cuenta.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="grid gap-4">
          <div className="grid gap-2">
            <Label htmlFor="nombre">Nombre</Label>
            <Input
              id="nombre"
              name="nombre"
              required
              value={profile.nombre}
              onChange={(event) =>
                onChange({ ...profile, nombre: event.target.value })
              }
            />
          </div>
          <p className="text-xs text-muted-foreground">
            La cédula y la fecha de nacimiento se gestionan por separado y no
            pueden modificarse desde este formulario.
          </p>
          <div className="grid gap-2">
            <Label htmlFor="telefono">Teléfono</Label>
            <Input
              id="telefono"
              name="telefono"
              type="tel"
              value={profile.telefono}
              onChange={(event) =>
                onChange({ ...profile, telefono: event.target.value })
              }
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="direccion">Dirección</Label>
            <Input
              id="direccion"
              name="direccion"
              value={profile.direccion}
              onChange={(event) =>
                onChange({ ...profile, direccion: event.target.value })
              }
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={loading}>
              {loading ? "Guardando..." : "Guardar cambios"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
