# Mini-Google: implementación en Scala 3

Versión en Scala 3 del motor de búsqueda en memoria, funcionalmente equivalente a la versión Java de la raíz del
repositorio. Incluye además las herramientas de medición del proyecto final: modo benchmark, generador de corpus
sintético y un script que ejecuta todos los escenarios.

El proyecto final compara tres implementaciones del mismo motor: **Java 21** (raíz del repositorio), **Scala 3**
(esta carpeta) y **Go**.

## Qué hace

- Índice invertido posicional en memoria, inmutable.
- Parser booleano con `AND`, `OR`, `NOT` y paréntesis `( )`.
- Frases exactas entre comillas (`"..."`).
- Ranking BM25 (k1 = 1.5, b = 0.75) con Top-10 mediante un montículo de tamaño 10.
- Lectura de documentos `.txt`, `.md`, `.pdf` y `.docx` (mismas librerías y versiones que la versión Java).
- Consola interactiva con los mismos mensajes que la versión Java.

## Requisitos

- JDK 21 (el mismo que usa la versión Java).
- Maven 3.9 o superior.

No hace falta instalar Scala: Maven descarga el compilador de Scala 3.3.4 la primera vez.

## Compilar y probar

Desde la carpeta `mini-google-scala/`:

```bash
mvn clean package
```

Eso compila, ejecuta los 48 tests y genera `target/mini-google-scala-1.0-SNAPSHOT.jar` con todas las
dependencias. Solo los tests:

```bash
mvn test
```

El compilador corre con `-deprecation -feature -unchecked -Wunused:all -Werror`: cualquier advertencia (por ejemplo
un import o una variable sin usar) hace fallar la compilación.

### IntelliJ IDEA

1. Instalar el plugin **Scala** (Settings → Plugins).
2. File → Open… → seleccionar `mini-google-scala/pom.xml` → Open as Project.
3. File → Project Structure… → SDK: Java 21.
4. Abrir `src/main/scala/minigoogle/Main.scala` y ejecutar `Main`.

## Ejecutar

Consola interactiva con los 11 documentos de ejemplo (los mismos de `src/main/resources/documents` de la versión
Java; el `pom.xml` de Scala reutiliza esa carpeta):

```bash
java -jar target/mini-google-scala-1.0-SNAPSHOT.jar
```

```text
Loaded 11 documents into the index.
Mini-Google (Scala) ready. Type a query and press ENTER. Empty line to exit.
query> (seguridad OR owasp) AND NOT "inyeccion sql"
Top results:
- informe_seguridad_OWASP.md (score 7,7123)
```

Una sola consulta (útil en scripts):

```bash
java -jar target/mini-google-scala-1.0-SNAPSHOT.jar search -- seguridad AND owasp
```

Usar otros documentos sin recompilar:

```bash
java -jar target/mini-google-scala-1.0-SNAPSHOT.jar interactive --docs-dir C:/ruta/a/mis/documentos
java -jar target/mini-google-scala-1.0-SNAPSHOT.jar interactive --corpus bench-data/corpus-1000.tsv
```

Con `--docs-dir` se cargan los archivos listados en el `manifest.txt` de esa carpeta o, si no hay manifest, todos
los `.txt`, `.md`, `.pdf` y `.docx` en orden alfabético. Un archivo que no se puede leer se informa y se omite; no
detiene la carga.

También se pueden agregar documentos igual que en la versión Java: copiarlos a `src/main/resources/documents/`,
agregarlos a `manifest.txt` y recompilar.

## Estructura del código

| Archivo | Responsabilidad |
| --- | --- |
| `model/Models.scala` | `Document`, `Posting` y `SearchResult` (case classes inmutables). |
| `index/Analyzer.scala` | Pipeline de texto: minúsculas, separación por espacios, limpieza de puntuación en los bordes. |
| `index/PostingList.scala` | Postings de un término: IDs ordenados en un `IArray[Int]` y posiciones por documento. |
| `index/InvertedIndex.scala` | Índice inmutable: construcción en bloque (`build`) y actualización en vivo (`add`). |
| `index/DocIds.scala` | Intersección, unión y diferencia de IDs ordenados con dos punteros; búsqueda binaria. |
| `query/Tokenizer.scala` | Lexer: `Token` como `enum`. |
| `query/Query.scala` | AST de la consulta como tipo algebraico (`enum Query`). |
| `query/QueryParser.scala` | Parser de descenso recursivo puro que devuelve `Either[ParseError, Query]`. |
| `search/SearchEngine.scala` | Evaluación del AST, frases exactas y BM25. |
| `search/TopK.scala` | Top-K con un montículo inmutable (`TreeSet`) recorrido con `foldLeft`. |
| `loader/` | Lectura de archivos y de corpus (classpath, carpeta o TSV). |
| `bench/` | Benchmark, métricas (latencia, memoria, GC) y generador de corpus. |
| `Main.scala`, `Cli.scala` | Línea de comandos. |

## Programación funcional aplicada

Para el criterio de uso funcional, estos son los puntos concretos del código:

- **Tipos de datos algebraicos y pattern matching exhaustivo**: `enum Query`, `enum Token`, `enum IndexError`. El
  compilador verifica que cada `match` cubra todos los casos (`SearchEngine.matching`, `Query.scoringTerms`).
- **Inmutabilidad**: el índice, las listas de postings y los documentos son inmutables. `add` devuelve un índice
  nuevo que comparte estructura con el anterior; las consultas en curso nunca ven un estado intermedio y el
  índice se puede leer desde varios hilos sin locks.
- **Errores como valores**: el parser, la carga de archivos y la construcción del índice devuelven `Either`. Una
  consulta mal escrita produce un mensaje y la consola sigue funcionando; no se lanzan excepciones.
- **Recursión de cola (`@tailrec`)**: tokenizer, encadenamiento de `AND`/`OR` en el parser, cruces de listas en
  `DocIds`, búsquedas binarias, generador de oraciones.
- **Funciones de orden superior**: `foldLeft` (Top-K, puntaje BM25, carga incremental), `flatMap` + `groupMap`
  (construcción del índice), `partitionMap` (separar documentos leídos de errores), `map`/`filter`/`exists`/`forall`
  (frases exactas), `groupMapReduce` (términos repetidos en la consulta), for-comprehensions sobre `Either`.
- **Funciones puras**: el parser recibe la lista de fichas y devuelve el nodo más las fichas restantes, sin estado.

Excepción pragmática: algunas funciones (`DocIds`, `PostingList`) arman su resultado con un builder local por
rendimiento. Desde fuera siguen siendo funciones puras (mismo resultado para la misma entrada, sin efectos
visibles). La entrada/salida queda en los bordes (`Main`, `Benchmark`, `CorpusGenerator`).

## Comparación con la versión Java

Se ejecutaron las mismas consultas en las dos versiones, cada una con su JAR, sobre los mismos 11 documentos y con
el mismo JDK 21 (17/11/2026). Entre paréntesis, el puntaje BM25 que muestra cada consola.

| Consulta | Java | Scala |
| --- | --- | --- |
| `seguridad` | informe_seguridad_OWASP.md (0,0835) | informe_seguridad_OWASP.md (4,0796) |
| `accesibilidad` | informe_accesibilidad_WCAG.md (0,3711) | informe_accesibilidad_WCAG.md (3,3029) |
| `usabilidad` | informe_usabilidad_UX.md (0,5685), informe_accesibilidad_WCAG.md (0,1398) | informe_usabilidad_UX.md (2,5861), informe_accesibilidad_WCAG.md (0,6361) |
| `"deep eval"` | Sin resultados | Sin resultados |
| `"document two content"` | doc2 (5,9421) | doc2 (5,9421) |
| `seguridad AND owasp` | informe_seguridad_OWASP.md (0,9046) | informe_seguridad_OWASP.md (7,7123) |
| `accesibilidad OR usabilidad` | informe_usabilidad_UX.md (0,5685), informe_accesibilidad_WCAG.md (0,5109) | informe_accesibilidad_WCAG.md (3,9390), informe_usabilidad_UX.md (2,5861) |
| `(seguridad OR owasp) AND NOT "inyeccion sql"` | informe_seguridad_OWASP.md (2,4403) | informe_seguridad_OWASP.md (7,7123) |
| `java AND concurrency` | doc2 (4,5498), doc5 (4,5186) | doc2 (4,5498), doc5 (4,5186) |
| `"virtual threads"` | doc2 (5,5988), doc5 (5,5604) | doc2 (5,5988), doc5 (5,5604) |
| `"quick brown fox"` | doc1 (11,1203) | doc1 (11,1203) |
| `NOT informe` | 8 documentos, puntaje 0 | Los mismos 8 documentos, puntaje 0 |
| `informe NOT seguridad` | `IllegalArgumentException`: el programa se cierra | informe_accesibilidad_WCAG.md (0,8598), informe_usabilidad_UX.md (0,6675) |
| `seguridad owasp` | `IllegalArgumentException`: el programa se cierra | informe_seguridad_OWASP.md (7,7123) |

Resumen:

- **Mismos documentos**: en las 12 consultas que Java puede procesar, las dos versiones encuentran exactamente los
  mismos documentos.
- **Mismos puntajes**: en 6 de esas 12 coinciden también los puntajes, hasta el cuarto decimal. Son las consultas
  donde cada término aparece una sola vez por documento (o no hay resultados), así que el error de IDF de Java no
  influye. Esto confirma que la fórmula BM25 es la misma en las dos versiones.
- **Orden distinto**: solo en `accesibilidad OR usabilidad`, por el IDF de Java (ver abajo).
- **Errores**: Java se cierra con las dos últimas consultas. `informe NOT seguridad` es un ejemplo de su propia guía
  de ejecución. Las consultas del tipo `palabra palabra` (sin operador) eran el 35% de las consultas del generador
  original de `proyecto_final.md`. Por eso el generador de esta carpeta escribe siempre el operador (`AND`), así la
  versión Java actual puede correr el benchmark.

### Por qué difieren

Son diferencias intencionales de la versión Scala:

1. **IDF de BM25**: Scala usa n(q) = cantidad de *documentos* que contienen el término, como dice la fórmula del
   proyecto. La versión Java crea un posting por cada aparición de la palabra, así que su n(q) cuenta apariciones
   y sus puntajes cambian cuando una palabra se repite dentro de un documento. Por eso el orden de algunos
   resultados difiere (por ejemplo `accesibilidad OR usabilidad`).
2. **Términos negados**: en Scala no suman puntaje (un documento no debe subir por contener una palabra que la
   consulta excluye). Java los incluye en el cálculo.
3. **AND implícito**: `informe NOT seguridad` (ejemplo de la guía) equivale a `informe AND NOT seguridad`. En la
   versión Java esa consulta lanza `IllegalArgumentException` y cierra el programa.
4. **Consultas inválidas**: Scala muestra `Invalid query: ...` y sigue; Java termina con una excepción.
5. **Empates en el puntaje**: se ordenan por ID de documento ascendente, así el resultado es siempre el mismo.
6. **`.pdf` y `.docx`**: Scala elige el lector según la extensión. El `Main` de Java lee todos los archivos como
   texto plano (su `DocumentLoader` no se usa).
7. **Palabras de una frase**: se limpian con las mismas reglas del índice (puntuación al inicio y al final).

## Benchmark

### Ejecutar todos los escenarios

Con el JAR compilado y `JAVA_HOME` apuntando al JDK 21:

```bash
powershell -ExecutionPolicy Bypass -File scripts/run-benchmarks.ps1
```

El script ejecuta:

| Escenario | Datos | Consultas |
| --- | --- | --- |
| `sample` | Los 11 documentos reales | `queries/sample-queries.txt` (16) |
| `synthetic-1000` | 1.000 documentos sintéticos | 200 generadas |
| `synthetic-10000` | 10.000 documentos sintéticos | 200 generadas |
| `synthetic-50000` | 50.000 documentos sintéticos (≈12 millones de palabras) | 200 generadas |

Cada escenario corre en una JVM nueva con heap fijo (`-Xms2g -Xmx2g`, G1). Los corpus se generan la primera vez en
`bench-data/` (ignorado por git, se regenera idéntico). Los resultados se agregan a `bench-results/results.csv` y
el top-10 de cada consulta queda en `bench-results/<escenario>-results.tsv`.

Parámetros del script: `-Sizes 1000,10000`, `-Heap 4g`, `-Seed 42`, `-Queries 200`, `-Warmup 3`, `-Runs 10`,
`-IndexRuns 5`.

### Ejecutar un escenario a mano

```bash
java -jar target/mini-google-scala-1.0-SNAPSHOT.jar generate --docs 10000 --seed 42 --out bench-data/corpus-10000.tsv --queries 200 --queries-out bench-data/queries-10000.txt
```

```bash
java -Xms2g -Xmx2g -jar target/mini-google-scala-1.0-SNAPSHOT.jar bench --scenario synthetic-10000 --corpus bench-data/corpus-10000.tsv --queries bench-data/queries-10000.txt --out bench-results/results.csv --dump bench-results/synthetic-10000-results.tsv
```

Opciones de `bench`: `--scenario`, `--corpus <tsv>` o `--docs-dir <carpeta>` (sin ninguna, los 11 documentos de
ejemplo), `--queries <archivo>`, `--warmup` (3), `--runs` (10), `--index-runs` (5), `--out` (CSV), `--dump` (TSV).

Opciones de `generate`: `--out` (obligatorio), `--docs` (1000), `--seed` (42), `--min-words` (80), `--max-words`
(400), `--vocabulary` (20000), `--queries` (200), `--queries-out`.

Dataset de referencia de `proyecto_final.md` (50.000 documentos y 5.000 consultas), para usar en los tres lenguajes:

```bash
java -jar target/mini-google-scala-1.0-SNAPSHOT.jar generate --docs 50000 --queries 5000 --seed 42 --out corpus.tsv --queries-out queries.txt
```

### Qué mide

1. **Carga** (`load_ms`): lectura del corpus, separada de la indexación.
2. **Indexación**: el índice se construye `index-runs` veces desde cero. Se reporta mediana, mínimo y máximo, la
   actividad del GC durante la última construcción y la memoria que retiene el índice (heap usado después de
   construirlo menos heap usado antes, ambos tras forzar GC).
3. **Consultas**: cada consulta se ejecuta completa (tokenizar, parsear, evaluar, ordenar). Primero `warmup`
   pasadas sin medir para que el JIT compile el código; luego `runs` pasadas midiendo cada ejecución con
   `System.nanoTime`. El throughput es ejecuciones / tiempo total de las pasadas medidas.
4. **Pico de memoria** (`heap_peak_mb`): suma de los picos de cada zona del heap durante todo el escenario. Es una
   cota superior del pico real.

### Formato del CSV

Una fila por escenario. El mismo formato sirve para las otras dos implementaciones (Java y Go), así la
consolidación puede leer los tres archivos igual.

| Columna | Significado |
| --- | --- |
| `language`, `scenario` | Lenguaje y nombre del escenario. |
| `documents`, `vocabulary`, `tokens` | Documentos indexados, términos distintos y total de palabras. |
| `queries`, `invalid_queries` | Consultas medidas y consultas descartadas por inválidas. |
| `matched_docs` | Suma de documentos que cumplen cada consulta (sin límite de 10). Sirve para comprobar que los tres lenguajes devuelven lo mismo. |
| `load_ms` | Tiempo de carga del corpus. |
| `index_ms_median`, `index_ms_min`, `index_ms_max` | Tiempo de construcción del índice. |
| `index_gc_count`, `index_gc_ms` | Recolecciones de basura y su tiempo durante la última construcción. |
| `heap_index_mb`, `heap_peak_mb` | Memoria retenida por el índice y pico de heap. |
| `query_mean_ms`, `query_p50_ms`, `query_p95_ms`, `query_p99_ms`, `query_max_ms` | Latencia por consulta. |
| `throughput_qps` | Consultas por segundo. |
| `runtime`, `timestamp` | Versión de la JVM y fecha (UTC). |

El archivo `--dump` tiene una línea por consulta: `consulta<TAB>documentos que la cumplen<TAB>id:score;...` (top-10).
Comparar estos archivos entre lenguajes es la "prueba maestra" de `proyecto_final.md`: los tres deben devolver los
mismos IDs.

### Formato del corpus

El definido en `proyecto_final.md`: archivo de texto UTF-8 con un documento por línea, `ID<TAB>título<TAB>texto`.
Los IDs se toman del archivo, así los tres lenguajes reportan los mismos. Se indexa el texto (como en la versión
Java); el título es el que se muestra en los resultados. Las líneas que no tienen ese formato se informan y se
omiten.

El generador usa `java.util.Random` y `StrictMath`, así que la misma semilla produce el mismo archivo byte a byte
en cualquier máquina (saltos de línea `\n`). Java y Go pueden leer estos mismos archivos para medir con
exactamente los mismos datos.

El texto mezcla 30 conectores frecuentes, unos 150 términos técnicos y palabras sintéticas, con frecuencias que
siguen una distribución de Zipf (como en un texto real), oraciones con mayúscula inicial, comas y puntos. Las
consultas generadas rotan entre término simple, `AND`, `OR`, `AND NOT`, frase exacta (tomada del propio corpus,
así siempre tiene al menos un resultado) y combinaciones con paréntesis.

### Nota para la versión Go

- La versión Go puede leer los mismos `corpus-*.tsv` y `queries-*.txt` y escribir el CSV con las mismas columnas
  (`language` = `go`, `runtime` = versión de Go).
- Equivalentes en Go: `time.Now()` / `time.Since` para las latencias; `runtime.GC()` seguido de
  `runtime.ReadMemStats` (`HeapAlloc`) para la memoria del índice; `MemStats.NumGC` y `PauseTotalNs` para las
  columnas `index_gc_*`.
- Go compila a nativo y no tiene JIT: las pasadas de calentamiento le afectan poco, pero conviene hacerlas igual
  para que las condiciones sean las mismas.
- Las columnas de memoria de Scala y Java miden el heap de la JVM, que no incluye el resto del proceso. Para comparar
  memoria con Go de forma justa, conviene medir además el pico de memoria del proceso completo (working set) de las
  tres versiones con la misma herramienta externa.
