# Integración OCA e-Pak

Implementación sobre el núcleo genérico de Shipping de Anaquel.

## Credenciales/configuración esperada

Credenciales cifradas de la integración `OCA`:

- `cuit`: CUIT registrado en OCA, con guiones.
- `usuario`: usuario e-Pak.
- `password`: contraseña e-Pak.
- `nroCuenta`: número de cuenta corriente OCA, por ejemplo `111757/001`.
- `operativa`: número de operativa contratada (Puerta a Puerta para el checkout HOME actual).
- `centroCosto`: centro de costo habilitado para la operativa.

Settings opcionales de la integración:

- `environment`: `QA` (default) o `PRODUCTION`.
- `baseUrl`: override explícito de la URL e-Pak.
- `confirmarRetiro`: `true` por defecto.
- `idFranjaHoraria`: `1` por defecto (8 a 17).
- `idCentroImposicionOrigen`: `0` por defecto para retiro en domicilio.
- `seguro`: `false` por defecto; si es `true`, se informa el valor declarado del bulto.
- `labelFormat`: `10x15` por defecto; cualquier otro valor usa PDF A4.

## Funciones implementadas

- Cotización HOME: `Tarifar_Envio_Corporativo`.
- Sucursales por CP: `GetCentrosImposicionConServiciosByCP`.
- Crear envío: `IngresoORMultiplesRetiros_v2`.
- Tracking: `Tracking_Pieza`.
- Etiqueta PDF: `GetPdfDeEtiquetasPorOrdenOrNumeroEnvioParaEtiquetadora` / A4.
- Cancelación: `AnularOrdenGenerada`.
- Prueba de conexión: cotización mínima contra el ambiente configurado.

## Nota sobre operativas

OCA asigna las operativas según contrato. Anaquel no intenta inferirlas. Para ofrecer más de una modalidad (por ejemplo Puerta a Sucursal) hay que registrar un servicio distinto y asociarlo a la operativa correspondiente. El checkout actual expone HOME; la selección de sucursal queda apoyada por el endpoint genérico de pickup points, pero requiere la UX de selección de sucursal para persistir el `idci` destino antes de habilitar esa modalidad al comprador.
