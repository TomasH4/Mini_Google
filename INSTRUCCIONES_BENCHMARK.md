# Guía Oficial de Benchmarking y Escenarios de Prueba

**Proyecto:** Mini-Google (Comparativo de Paradigmas y Lenguajes)  
**Entornos:** Java 21 (Virtual Threads) vs. Scala 3 (Funcional / Fibras) vs. Tercer Lenguaje (Python 3.12 / Go / Rust)

---

## 1. Principio de Reproducibilidad Experimental

Para cumplir con la rúbrica de la materia y garantizar una **competencia 100% justa e idéntica**, los motores de búsqueda de cada lenguaje deben procesar **exactamente los mismos datos y responder las mismas consultas en el mismo orden**.

Por restricciones de tamaño de GitHub (límite de 100 MB por archivo), los datasets masivos no se versionan directamente en Git. En su lugar, utilizamos un generador determinista basado en una semilla fija:

$$\text{SEED} = 42$$

> [!IMPORTANT]
> Gracias a la semilla determinista, cualquier miembro del equipo que ejecute el generador en su computadora (Windows, Mac o Linux) obtendrá **exactamente los mismos archivos, con el mismo contenido y los mismos hashes SHA-256 byte por byte**.

---

## 2. Cómo Generar los Datos en tu Computadora

### Paso 1: Actualizar el repositorio
```bash
git pull origin main
```

### Paso 2: Generar todos los escenarios
Desde la carpeta `dataset/` del proyecto, ejecuta:

```bash
cd dataset
python generate_dataset.py --scenario all
```

En menos de 10 segundos tendrás listos los 3 escenarios oficiales en tu directorio local.

*(Opcional: Si solo deseas generar un escenario específico, puedes usar `--scenario tier1`, `--scenario tier2` o `--scenario tier3`).*

---

## 3. Especificación Técnica de los Archivos

### A. Archivos de Corpus (`.tsv`)
* **Codificación:** UTF-8 sin BOM.
* **Separador de columnas:** Carácter tabulador (`\t`).
* **Columnas (3):**
  1. `ID`: Identificador numérico secuencial del documento (`1, 2, 3...`).
  2. `Título`: Título estructurado del artículo técnico.
  3. `Cuerpo`: Texto completo del artículo (sin tabulaciones ni saltos de línea internos).

### B. Archivos de Consultas (`.txt`)
* Cada línea representa una consulta independiente a resolver por el motor.
* Tipos de consultas incluidas:
  * **Términos simples:** `concurrencia`, `latencia`, `inmutabilidad`, `virtual threads`.
  * **Frases exactas (posicionales):** `"hilos virtuales"`, `"recolector de basura"`, `"ranking bm25"`.
  * **Consultas booleanas complejas:** `(java OR scala) AND NOT python`, `("indice invertido" OR trie) AND memoria`.

---

## 4. Definición de los 3 Escenarios de Prueba

| Escenario | Archivos de Entrada | Volumen | Consultas | Propósito Experimental en la Sustentación |
| :--- | :--- | :---: | :---: | :--- |
| **Tier 1 (Base Sólida)** | `corpus_25k.tsv`<br>`queries_25k.txt` | **25.000 docs** | **10.000 queries** | Calibración inicial, estabilización del compilador JIT y medición de latencias con muestra estadística suficiente. |
| **Tier 2 (Carga Pesada)** | `corpus_75k.tsv`<br>`queries_75k.txt` | **75.000 docs** | **25.000 queries** | Régimen de trabajo continuo. Provoca ciclos reales de Garbage Collection (GC) y evalúa throughput concurrente sostenido. |
| **Tier 3 (Estrés Extremo)** | `corpus_150k.tsv`<br>`queries_150k.txt` | **150.000 docs** | **50.000 queries** | Prueba de límite con heap > 1 GB. Evalúa degradación de latencia percentil p99 y manejo de memoria en escenarios masivos. |

---

## 5. Métricas Obligatorias a Registrar

Cada implementación debe medir y registrar cuantitativamente los siguientes factores:

1. **Tiempo de Indexación (`indexTimeMs`):** Milisegundos transcurridos desde que se inicia el parseo del `.tsv` en memoria hasta que el índice invertido posicional está completamente construido.
2. **Consumo de Memoria RAM (`memoryUsedMb`):** Memoria retenida en megabytes tras finalizar la indexación (forzar recolección de basura antes y después para aislar el peso real del índice).
3. **Throughput Secuencial (`sequentialThroughputQps`):** Consultas procesadas por segundo de forma secuencial ($\frac{\text{Total Consultas}}{\text{Tiempo en Segundos}}$).
4. **Percentiles de Latencia (ms):**
   * **Media:** Promedio aritmético del tiempo de respuesta.
   * **p50 (Mediana):** Tiempo bajo el cual responde el 50% de las consultas.
   * **p95:** Latencia del percentil 95.
   * **p99:** Latencia de cola del 1% de consultas más lentas (clave para evaluar pausas de GC).
5. **Throughput Concurrente (`concurrentThroughputQps`):** Consultas procesadas por segundo ejecutando todas las tareas simultáneamente (Virtual Threads en Java, Fibras ZIO/Cats en Scala, Thread/Process Pool en Python).

---

## 6. Formato Estándar del Reporte JSON

Para permitir la consolidación automática y el ranking numérico exigido por la rúbrica, cada lenguaje debe exportar su resultado en un archivo JSON con esta estructura:

```json
{
  "language": "Java 21",
  "totalDocuments": 25000,
  "indexTimeMs": 5875,
  "memoryUsedMb": 178.40,
  "totalQueries": 10000,
  "sequentialTimeMs": 33997,
  "sequentialThroughputQps": 294.14,
  "meanLatencyMs": 3.3965,
  "p50LatencyMs": 2.2380,
  "p95LatencyMs": 10.3522,
  "p99LatencyMs": 17.1328,
  "concurrentTimeMs": 14196,
  "concurrentThroughputQps": 704.41,
  "virtualThreadsCount": 10000
}
```

* Nombres de archivos de salida sugeridos:
  * Java: `reporte_java_25k.json`, `reporte_java_75k.json`, `reporte_java_150k.json`
  * Scala: `reporte_scala_25k.json`, `reporte_scala_75k.json`, `reporte_scala_150k.json`
  * Tercer lenguaje: `reporte_python_25k.json`, etc.

---

## 7. Ejecución de Referencia en Java 21

Compilar el módulo Java:
```bash
cd mini-google-java
mvn package -DskipTests
```

Ejecutar cada escenario (leyendo los datos de `../dataset/` y guardando en `../dataset/reportes/`):
```bash
# Tier 1 (25k)
java -jar target/mini-google-1.0-SNAPSHOT.jar --benchmark ../dataset/corpus_25k.tsv ../dataset/queries_25k.txt ../dataset/reportes/reporte_java_25k.json

# Tier 2 (75k)
java -jar target/mini-google-1.0-SNAPSHOT.jar --benchmark ../dataset/corpus_75k.tsv ../dataset/queries_75k.txt ../dataset/reportes/reporte_java_75k.json

# Tier 3 (150k)
java -Xmx4g -jar target/mini-google-1.0-SNAPSHOT.jar --benchmark ../dataset/corpus_150k.tsv ../dataset/queries_150k.txt ../dataset/reportes/reporte_java_150k.json
```
