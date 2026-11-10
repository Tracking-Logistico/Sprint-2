import { test } from "node:test";
import assert from "node:assert/strict";
import { formatIncidentDate } from "../src/features/incidents/utils/incidentDate.ts";

test("21:25 UTC se muestra como 16:25 en Colombia", () => {
  const expected = new Intl.DateTimeFormat("es-CO", {
    timeZone: "America/Bogota", dateStyle: "medium", timeStyle: "short",
  }).format(new Date("2026-10-31T16:25:00-05:00"));
  assert.equal(formatIncidentDate("2026-10-31T21:25:00Z"), expected);
  assert.equal(formatIncidentDate("2026-10-31T21:25:00"), expected);
  assert.equal(formatIncidentDate("2026-10-31T21:25:00+00:00"), expected);
  assert.equal(formatIncidentDate("2026-10-31T16:25:00-05:00"), expected);
  assert.match(expected, /4:25|16:25/);
});

test("la conversión respeta el cambio al día anterior", () => {
  assert.equal(formatIncidentDate("2026-11-01T02:30:00Z"),
    formatIncidentDate("2026-10-31T21:30:00-05:00"));
  assert.match(formatIncidentDate("2026-11-01T02:30:00Z"), /31/);
});

test("una fecha inválida tiene una representación controlada", () => {
  assert.equal(formatIncidentDate("fecha-invalida"), "Fecha no disponible");
});
