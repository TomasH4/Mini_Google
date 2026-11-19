# Suite normalizada de Mini-Google

Desde la raíz, ejecuta `python benchmark/run.py`. Requiere Python 3.9+, Go 1.22+, Maven y un JDK 21+ con `java`, `javac` y `jar`. En Windows y Linux se consulta el pico residente del proceso directamente al sistema operativo. Las variables `MVN` y `GO` permiten indicar rutas a los ejecutables. Si el equipo usa cachés Maven independientes, `M2_JAVA` y `M2_SCALA` indican sus directorios.

La ejecución compila los artefactos, genera los datos con semilla 42 y comprueba la paridad en cada escala. Después lanza un proceso por lenguaje y repetición, rota el orden y deja dos segundos de enfriamiento. Todos los procesos heredan la afinidad de CPU del orquestador. Los dos procesos JVM usan el mismo JDK y los mismos límites de heap. La máquina debe estar disponible durante la prueba.

## Escenarios

| Escenario | Trabajo medido |
| --- | --- |
| A | Parsear el TSV en RAM y construir el índice posicional y el trie. Por defecto se evalúan 10.000 y 100.000 documentos. |
| B | Ejecutar 10.000 búsquedas deterministas en serie sobre la escala principal. El tiempo incluye interpretación de la consulta, recuperación y ranking. |
| C | Cuatro lectores ejecutan un total de 8.000 búsquedas y un escritor inserta 2.000 documentos con IDs nuevos. El QPS divide las búsquedas completadas por el tiempo hasta terminar las cinco tareas. |

`--documents` selecciona la escala principal. `--index-documents` selecciona la segunda escala de indexación y admite cero para omitirla. `--iterations` admite cinco o más repeticiones independientes. La planificación del sistema operativo determina qué altas son visibles durante cada búsqueda concurrente.

## Validación y mediciones

- Las 61 consultas de [golden_queries.txt](golden_queries.txt) cubren términos, operadores y negaciones anidadas, frases, repeticiones, prefijos y errores ortográficos. La compuerta compara por pares el top 10 completo y el puntaje de cada ID, con tolerancia absoluta de 0,0001. Un fallo publica el diagnóstico con resultados vacíos y detiene las mediciones.
- En la suite, `mem*` completa el prefijo y `~memmoria` corrige hasta dos ediciones. Se busca el término elegido por menor distancia, mayor frecuencia documental y orden lexicográfico. El trie de Go es nativo del módulo. Java y Scala usan el adaptador de vocabulario de la suite y sus propios motores para recuperar y puntuar documentos.
- El proceso padre cronometra el arranque hasta recibir la confirmación de primera consulta completada. Incluye lanzamiento, lectura de archivos, indexación y primera búsqueda. La caché de disco corresponde al estado del sistema operativo.
- Antes de la medición caliente se hacen 2.000 búsquedas y una carga mixta de 800 búsquedas con 200 altas sobre el primer índice. Se descarta ese índice, se fuerza GC y se cronometra la construcción de otro índice sobre los mismos bytes precargados.
- Los percentiles en microsegundos usan el rango más cercano sobre las 10.000 latencias secuenciales. La memoria retenida estima la diferencia de heap vivo entre dos GC con el corpus crudo residente. El RSS recoge el máximo de **todo el proceso** hasta el fin del escenario. Las magnitudes de memoria y tamaño se expresan en MiB.
- LOC incluye fuentes de producción y adaptadores de la suite correspondientes al artefacto. Excluye tests, líneas vacías, comentarios y dependencias. El tamaño corresponde al JAR con dependencias o al ejecutable Go de la suite. Ambos indicadores describen alcance y empaquetado además de concisión.

## Salida

En el reporte interactivo, **concisión del código** significa usar menos líneas efectivas para implementar la funcionalidad comparada. La implementación con menos LOC recibe 100 puntos en ese eje. Su influencia en el simulador depende del peso asignado a la concisión.

[results.json](results.json) contiene las medianas por lenguaje. En `metadata` se conservan las corridas individuales, el rango intercuartílico inclusivo (Q3 − Q1), las huellas SHA-256, el entorno y la medición de la segunda escala. Las cifras de arranque y RSS de la segunda escala corresponden a su proceso de indexación.

En `relative_x` el mejor valor siempre es 1. Para coste se divide el valor por el mínimo y para throughput se divide el máximo por el valor. En ambos casos un factor de 2 indica que la distancia al líder es de dos veces. Cada puntuación de radar equivale a 100 dividido por ese factor. Los ejes usan tiempo de indexación, QPS mixto, p99 secuencial, heap retenido y LOC. El eje de p99 refleja latencia de cola. La variabilidad entre procesos se consulta en el IQR.

Una presentación HTML local puede cargar `results-data.js` mediante una etiqueta `script` y leer `window.BENCHMARK_DATA`. El wrapper contiene el mismo objeto JSON.

