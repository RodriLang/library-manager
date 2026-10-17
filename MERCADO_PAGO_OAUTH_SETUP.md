# Mercado Pago OAuth / Marketplace — configuración de Anaquel

La conexión de cada librería se realiza con OAuth Authorization Code + PKCE. Los libreros no cargan Access Tokens ni secretos.

## Variables del backend

- `STORE_PAYMENT_ENCRYPTION_KEY`: clave maestra usada para cifrar access/refresh tokens por librería.
- `MERCADO_PAGO_CLIENT_ID`: App ID / Client ID de la aplicación Mercado Pago de Anaquel.
- `MERCADO_PAGO_CLIENT_SECRET`: Client Secret de la aplicación de Anaquel.
- `MERCADO_PAGO_WEBHOOK_SECRET`: firma secreta del Webhook configurado una sola vez para la aplicación Anaquel.
- `PUBLIC_API_BASE_URL`: URL pública HTTPS de Anaquel API.
- `MERCADO_PAGO_OAUTH_REDIRECT_URI` (opcional): si se omite se usa `${PUBLIC_API_BASE_URL}/api/store/payments/mercado-pago/oauth/callback`.
- `FRONTEND_URL`: URL de Anaquel UI a la que vuelve el librero tras autorizar.

## Configuración en Mercado Pago

1. Crear/usar la aplicación de Anaquel como integración que opera en nombre de terceros.
2. Habilitar Authorization Code y, preferentemente, PKCE.
3. Mantener permisos de lectura, escritura y acceso offline para disponer de refresh token.
4. Registrar exactamente la Redirect URI usada por el backend.
5. Configurar una sola URL de Webhook para el evento Order:
   `${PUBLIC_API_BASE_URL}/api/storefront/payments/mercado-pago/webhook`
6. Guardar la clave secreta de ese Webhook en `MERCADO_PAGO_WEBHOOK_SECRET`.

## Flujo de una librería

Anaquel UI → Conectar con Mercado Pago → autorización en Mercado Pago → callback de Anaquel API → intercambio de `code` por `access_token` + `refresh_token` → almacenamiento cifrado → pagos habilitados.

El backend renueva el access token automáticamente cuando está próximo a vencer. Si no puede renovarlo, pausa Mercado Pago para esa librería y solicita volver a conectar.
