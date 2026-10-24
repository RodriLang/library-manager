# Optimización SST — experimento controlado

Base: anaquel-back-v155-log-security.zip.

- El parser FAST_SAX resuelve cadenas mediante `DiskBackedSharedStrings.getStringAt(index)`, sin construir `XSSFRichTextString` para cada celda seleccionada.
- La caché LRU conserva Strings; la implementación de SharedStrings.getItemAt sigue disponible para interoperabilidad con POI.
- Se reutiliza un buffer byte[] para lecturas SST, con crecimiento bajo demanda y límite derivado del máximo permitido por celda. Evita reservar un byte[] nuevo para cada falta de caché.
- Conservados los límites de caché (1024 por defecto; mínimo 128, máximo 4096; 2 MiB de texto estimado), las métricas y la persistencia de progreso.
- El test DiskBackedSharedStringsTest comprueba ambas rutas (String directo y API POI).

Validación pendiente: Maven no está instalado en este entorno. Ejecutar `mvn -Dtest=DiskBackedSharedStringsTest test` y luego `mvn test` antes del deploy.

Línea base de SBS #22 (Render gratis): total 595702 ms; SAX 515700 ms aprox; GC acumulado ~387600 ms; SST 501433 reads ~224400 ms; 25% hit.

Importante: no se garantiza mejora de tiempo total; esta versión se enfoca en reducir asignaciones. Comparar mismos logs y heap antes/después. Si no mejora, no aumentar caché sin medir.
