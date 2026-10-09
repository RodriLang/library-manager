# Mercado Pago - configuración desde Administración Anaquel

La integración OAuth/Marketplace de Mercado Pago puede configurarse globalmente desde el panel ADMIN.

## Única variable sensible que debe permanecer en el entorno

```properties
STORE_PAYMENT_ENCRYPTION_KEY=<clave maestra larga y aleatoria>
```

Esta clave cifra en base de datos tanto los secretos globales de la aplicación como los tokens OAuth de cada librería.

## Configuración global administrable

Endpoint ADMIN:

```http
GET /api/admin/integrations/mercado-pago
PUT /api/admin/integrations/mercado-pago
```

Se pueden administrar:

- integración global habilitada/deshabilitada;
- Client ID;
- Client Secret;
- Webhook Secret;
- OAuth Redirect URI;
- URL pública de Anaquel API;
- URL de Anaquel UI.

Client Secret y Webhook Secret se guardan cifrados y nunca se devuelven completos al frontend.
Un valor vacío en esos dos campos durante un PUT conserva el secreto existente.

## Compatibilidad con variables de entorno

Mientras no exista un registro en `mercado_pago_platform_config`, se usan como fallback:

- `MERCADO_PAGO_CLIENT_ID`
- `MERCADO_PAGO_CLIENT_SECRET`
- `MERCADO_PAGO_WEBHOOK_SECRET`
- `MERCADO_PAGO_OAUTH_REDIRECT_URI`
- `PUBLIC_API_BASE_URL`
- `FRONTEND_URL`

Al guardar por primera vez desde Administración, la configuración persistida pasa a ser la fuente efectiva.

## Configuración por librería

No cambia: cada librería sólo autoriza/desautoriza su cuenta desde Tienda > Pagos mediante OAuth.

## Lectura tolerante de configuración

Los endpoints GET de estado (`/api/admin/integrations/mercado-pago` y `/api/store/payments/mercado-pago`) son tolerantes a una configuración parcial o inválida. Devuelven la integración como no configurada/deshabilitada en lugar de lanzar una excepción por URLs o secretos faltantes.

La validación estricta se mantiene al guardar la configuración y al ejecutar operaciones reales (OAuth, creación/consulta de pagos y validación de webhooks).
