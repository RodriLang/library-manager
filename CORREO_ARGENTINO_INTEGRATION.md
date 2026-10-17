# Correo Argentino / PAQ.AR

Implementación basada en los manuales oficiales de Correo Argentino:

- API MiCorreo: token Basic -> JWT, `/rates`, `/agencies`, `/shipping/import`.
- PAQ.AR API 2.0: `Apikey` + `agreement`, `/auth`, `/orders`, `/orders/{tracking}/cancel`, `/labels`, `/tracking`, `/agencies`.

## Credenciales admitidas en Anaquel

### MiCorreo (cotización)

- `usuario`
- `password`
- `customerId`

### PAQ.AR API 2.0 (crear/cancelar/rótulo/tracking/sucursales)

- `agreement`
- `apiKey`
- `sellerId` (opcional)
- `serviceType` (opcional, default `CP`)

Se pueden guardar ambos juegos de credenciales en la misma integración `CORREO_ARGENTINO`.

## Settings opcionales

- `environment`: `TEST` (default) o `PRODUCTION`
- `miCorreoBaseUrl`: override del host MiCorreo
- `paqArBaseUrl`: override del host PAQ.AR 2.0
- `labelFormat`: `10x15` (default) o `label`
- `serviceType`: default `CP`
- `extClient`: opcional para tracking PAQ.AR

## Endpoints Anaquel agregados

- `POST /api/store/shipping/integrations/CORREO_ARGENTINO/test`
- `GET /api/store/shipping/integrations/CORREO_ARGENTINO/pickup-points?postalCode=...`
- `POST /api/store/orders/{orderId}/shipments/{shipmentId}/provider/create`
- `GET /api/store/orders/{orderId}/shipments/{shipmentId}/label`

El endpoint genérico de cotización del Store incorpora automáticamente Correo Argentino cuando la integración está habilitada y existen credenciales MiCorreo válidas. Por ahora se publica la tarifa a domicilio; la tarifa a sucursal queda reservada hasta que el checkout persista `agencyId`.
