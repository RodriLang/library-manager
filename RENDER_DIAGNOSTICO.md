# Diagnóstico de precios en Render

## Qué mide
Cada 5.000 filas: `PRICE LIST PARSER RUNTIME` (tiempo de pared del intervalo, CPU del hilo, tiempo total de GC de toda la JVM durante el intervalo, heap usado/máximo), `PRICE LIST PARSER SST PROGRESS` (caché, disco) y métricas existentes de `PRICE LIST PARSER PROGRESS`.

`intervalJvmGcMs` mide tiempo acumulado de los recolectores en la JVM, no necesariamente el tiempo de pausa experimentado por ese hilo; puede incluir actividad simultánea de GC. `intervalThreadCpuMs=-1` indica métrica no disponible. La memoria en MiB es instantánea; no es el pico.

## Interpretación
- Si wall supera ampliamente CPU y GC es bajo: investigar espera de I/O, cuotas de CPU, scheduling o bloqueo.
- Si GC crece mucho y el heap se aproxima al máximo: revisar presión de asignaciones y heap.
- Si `diskReadMs` crece fuertemente y `hitPercent` es bajo: evaluar SST disk I/O y política de caché.
- Si todos los endpoints se vuelven lentos: revisar métricas de CPU/RAM de Render; no atribuir a SST solamente.

## Prueba
1. Desplegar en desarrollo y confirmar `PRICE LIST PARSER SST ENGINE=DISK_BACKED_V1`.
2. Ejecutar primero Waldhuter sin otra importación simultánea; guardar todos los logs desde `PREVIEW START` hasta `PREVIEW COMPLETED` y las métricas de CPU/memoria de Render para esa ventana.
3. Repetir SBS únicamente si Waldhuter no muestra presión crítica de memoria o CPU; no usar producción para pruebas de carga.
4. Comparar tiempos de parser, `intervalThreadCpuMs`, `intervalJvmGcMs`, `diskReadMs`, hit rate y carga de endpoints HTTP.

La versión no cambia la configuración de `JAVA_TOOL_OPTIONS` ni los recursos de Render. Si se habilitan logs de GC, dirigirlos a stdout con rotación externa y sin bloquear la JVM por falta de espacio en disco.
