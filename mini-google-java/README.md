# Mini-Google: Implementación en Java 21+

Implementación en Java 21 del motor de búsqueda de texto completo en memoria con soporte para concurrencia masiva mediante **Virtual Threads (Project Loom)**.

## Características

- **Índice invertido posicional:** Mapeo de términos a documentos y posiciones relativas en memoria RAM.
- **Parser de consultas AST:** Soporte para operadores lógicos `AND`, `OR`, `NOT` y frases exactas entre comillas (`"..."`) con precedencia de operadores.
- **Ranking BM25:** Cálculo de relevancia algorítmica con retención de Top-K vía Min-Heap (`PriorityQueue`).
- **Concurrencia con Virtual Threads:** Evaluación simultánea de miles de consultas concurrentes sin saturar el sistema operativo (`Executors.newVirtualThreadPerTaskExecutor()`).
- **Modo interactivo y modo benchmark:** Consola de búsqueda en vivo y arnés de medición experimental automatizado con exportación a JSON.

## Requisitos

- **Java 21+** (JDK 21 instalado).
- **Maven 3.9+**

## Compilación

Desde el directorio `mini-google-java/`:

```bash
mvn clean package
```

Esto compila el código y genera el ejecutable uber-jar en `target/mini-google-1.0-SNAPSHOT.jar`.

## Ejecución

### 1. Modo Consola Interactiva
Carga los documentos de prueba por defecto o un corpus TSV:

```bash
# Con los documentos internos por defecto:
java -jar target/mini-google-1.0-SNAPSHOT.jar

# Con un archivo TSV personalizado:
java -jar target/mini-google-1.0-SNAPSHOT.jar --tsv ../dataset/corpus_25k.tsv
```

### 2. Modo Benchmark Automatizado
Ejecuta la suite experimental para registrar métricas de tiempo de indexación, latencias (media, p50, p95, p99), throughput secuencial y throughput concurrente:

```bash
java -jar target/mini-google-1.0-SNAPSHOT.jar --benchmark ../dataset/corpus_25k.tsv ../dataset/queries_25k.txt ../dataset/reportes/reporte_java_25k.json
```
