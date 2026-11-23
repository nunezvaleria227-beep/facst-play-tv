# Avisos para los clientes de Facst Play TV

La app lee este archivo al abrir: `docs/avisos.json`. Cambiarlo NO publica una versión nueva de la app
(los cambios en `docs/` no compilan nada) y los clientes lo ven en unos minutos.

Cada aviso se muestra una sola vez por aparato al abrir la app, y mientras esté vigente aparece en **Más → Avisos**.

```json
{
  "avisos": [
    {
      "id": "mantenimiento-20-nov",
      "titulo": "Mantenimiento",
      "mensaje": "El jueves de 2 a 4 de la mañana puede haber cortes por mantenimiento.",
      "desde": "2026-11-19",
      "hasta": "2026-11-21",
      "siempre": false
    }
  ]
}
```

- `id`: un nombre único. Si cambias el texto de un aviso y quieres que se vuelva a mostrar, cambia también el `id`.
- `desde` / `hasta` (opcionales): fechas año-mes-día, incluidas. Fuera de esas fechas el aviso no aparece.
- `siempre` (opcional): `true` = se muestra cada vez que se abre la app (por ejemplo, "renueva tu cuenta").
- Sin avisos: `{"avisos": []}`
