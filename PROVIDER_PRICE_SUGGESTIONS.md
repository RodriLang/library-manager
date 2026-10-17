# Sugerencias de precio al incorporar libros al inventario

Esta versión agrega una API de consulta de precios conocidos entre los proveedores activos de la librería.
No modifica el alta de inventario ni crea precios automáticamente: el frontend decide si precarga el `suggestedPrice`, y el usuario sigue pudiendo editar el precio de venta libremente.

## Fuente de datos

Se reutiliza `bookstore_provider_book_terms`:

- `latest_list_price`: último precio inequívoco conocido para ese proveedor/libro.
- `latest_list_effective_from`: vigencia informada por la lista que originó ese precio.
- Sólo se consideran proveedores activos que también estén activos en `bookstore_providers` para la librería actual.

No se agrega historial de precios de proveedor y no se requiere migración Flyway.

## Endpoints

### Un libro

`GET /api/inventory/price-suggestions/books/{bookId}`

### Varios libros

`POST /api/inventory/price-suggestions/batch`

Body:

```json
{
  "bookIds": [33702, 33703, 33704]
}
```

Máximo: 200 IDs por request.

## Respuesta

```json
{
  "bookId": 33702,
  "suggestedPrice": 12000.00,
  "autoApplicable": true,
  "status": "CONSISTENT",
  "freshness": "CURRENT",
  "latestPeriod": "2026-10",
  "prices": [
    {
      "providerId": 6,
      "providerName": "Waldhuter",
      "price": 12000.00,
      "effectiveFrom": "2026-10-01",
      "latestPeriod": true
    },
    {
      "providerId": 7,
      "providerName": "SBS",
      "price": 12000.00,
      "effectiveFrom": "2026-10-04",
      "latestPeriod": true
    },
    {
      "providerId": 5,
      "providerName": "Luongo",
      "price": 11000.00,
      "effectiveFrom": "2026-09-01",
      "latestPeriod": false
    }
  ]
}
```

## Reglas

`status`:

- `NO_DATA`: ningún proveedor activo de la librería tiene precio conocido.
- `SINGLE_SOURCE`: hay un único proveedor con información en el período más reciente.
- `CONSISTENT`: dos o más proveedores informaron el mismo precio en el período más reciente.
- `CONFLICT`: en el período más reciente existen dos o más precios diferentes. En este caso `suggestedPrice=null` y `autoApplicable=false`.

`freshness`:

- `CURRENT`: el período más reciente corresponde al mes corriente (o a uno futuro).
- `STALE`: el período más reciente es anterior al mes corriente.
- En `NO_DATA` se devuelve `null`.

La comparación de conflicto se realiza por mes/año. Una diferencia con un precio de meses anteriores no impide sugerir el precio del mes más reciente.
