# Shipping Stage 6

Esta iteración completa las funcionalidades que no dependen de credenciales Andreani:

- selección pública de sucursal/punto de retiro para Correo Argentino y OCA;
- snapshot del punto seleccionado en el pedido;
- OCA domicilio + sucursal con operativas separadas;
- reglas comerciales por carrier;
- cálculo de paquete conservador basado en volumen/peso;
- tracking con backoff ante errores del carrier;
- logística inversa OCA y rótulo de devolución;
- reingreso opt-in de stock al completar devoluciones;
- split shipments con dimensiones recalculadas por bulto.

## Deliberadamente no implementado sin contrato oficial

- webhooks de tracking de OCA/Correo Argentino: se mantiene polling con backoff hasta disponer de un contrato oficial aplicable;
- manifiestos externos: los contratos genéricos quedan preparados pero no se invoca un endpoint no documentado;
- Andreani real: queda pendiente de credenciales/documentación de la cuenta.
