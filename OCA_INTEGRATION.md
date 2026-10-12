# Integración OCA e-Pak

Implementación sobre el núcleo genérico de Shipping de Anaquel.

## Credenciales/configuración esperada

Credenciales cifradas de la integración `OCA` (sólo secretos):

- `usuario`: usuario e-Pak.
- `password`: contraseña e-Pak.

Settings de cuenta/configuración comercial:

- `cuit`: CUIT registrado en OCA, con guiones.
- `nroCuenta`: número de cuenta corriente OCA, por ejemplo `111757/001`.
- `operativa`: número de operativa contratada para entrega a domicilio.
- `operativaSucursal`: operativa Puerta a Sucursal cuando corresponda.
- `operativaDevolucion`: operativa de logística inversa cuando corresponda.
- `centroCosto`: centro de costo habilitado para la operativa.

Settings técnicos opcionales de la integración:

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
- Prueba de conexión: cotización mínima contra el ambiente configurado. Este test valida CUIT, operativa y acceso al cotizador; no valida el usuario/password e-Pak porque la API documentada no expone un login inocuo independiente de operaciones que crean o anulan envíos.

## Nota sobre operativas

OCA asigna las operativas según contrato. Anaquel no intenta inferirlas. Para ofrecer más de una modalidad (por ejemplo Puerta a Sucursal) hay que registrar un servicio distinto y asociarlo a la operativa correspondiente. El checkout actual expone HOME; la selección de sucursal queda apoyada por el endpoint genérico de pickup points, pero requiere la UX de selección de sucursal para persistir el `idci` destino antes de habilitar esa modalidad al comprador.

## Funcionalidades extendidas en Anaquel

Además de cotización, alta, tracking, cancelación y rótulos, Anaquel soporta:

- Retiro en sucursal cuando se configura `operativaSucursal` en los settings de OCA.
- Logística inversa puerta a puerta cuando se configura `operativaDevolucion` con la operativa real asignada a la cuenta.
- Rótulo de devolución usando los servicios de etiquetas de e-Pak con `logisticaInversa=true`.
- Reglas comerciales por integración: CP incluidos/excluidos, compra mínima/máxima, umbral propio de envío gratis, ajuste de precio, recomendación y prioridad.

No se deben copiar operativas de ejemplo de la documentación oficial a producción: cada librería debe usar las operativas asignadas por OCA a su cuenta.

## Compatibilidad y migración de configuración

Anaquel guarda como secretos únicamente `usuario` y `password`. Si un cliente anterior todavía envía `cuit`, `nroCuenta`, `operativa`, `operativaSucursal`, `operativaDevolucion`, `operativaLogisticaInversa` o `centroCosto` dentro de `credentials`, el backend los mueve automáticamente a `settings`. Los clientes OCA mantienen lectura legacy como fallback para no romper integraciones ya persistidas.

## Datos públicos de QA de la documentación OCA

La documentación oficial publica una cuenta genérica de pruebas (`test@oca.com.ar`, cuenta `111757/001`, CUIT `30-53625919-4`) y operativas de ejemplo. Son exclusivamente para QA y no deben persistirse automáticamente ni reutilizarse en producción.

## Diagnóstico de cotizaciones

Las cotizaciones OCA registran en backend los datos operativos necesarios para reproducir un fallo sin exponer `usuario` ni `password`: ambiente, operativa, CP de origen/destino, peso, volumen, valor declarado y tipo de entrega.

Si OCA responde sin una tabla de tarifa, el cliente intenta extraer el mensaje del proveedor y registra también una vista acotada de la respuesta XML. El warning devuelto por `/shipping/quotes` conserva el mensaje funcional de OCA cuando está disponible.
