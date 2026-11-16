# Proyecto: Mini-Google (Motor de Búsqueda de Texto Completo en Memoria)

**Benchmark Puro de Backend: Scala 3 vs. Java 21+ vs. Python 3.12+**

---

## 1. Descripción General: ¿Qué es este proyecto y qué hace?

### ¿Qué es?

**Mini-Google** es un motor de búsqueda de texto completo (*Full-Text Search Engine*) que funciona **100% en memoria RAM**, construido desde cero sin utilizar bases de datos externas (como PostgreSQL o MongoDB) ni motores de búsqueda prefabricados (como Elasticsearch o Apache Lucene).

Es el equivalente en software al "cerebro" interno de buscadores comerciales como Google, Amazon o Mercado Libre, diseñado específicamente para funcionar como un **campo de pruebas puro** que permita medir y comparar de forma justa el rendimiento, la gestión de memoria, la concurrencia y la expresividad de **Scala, Java y Python**.

### ¿Qué hace en la práctica?

Desde el punto de vista de un usuario o sistema que lo consume, el programa realiza dos grandes tareas:

1. **Aprende y organiza miles de documentos (Indexación):**
Al arrancar, el programa lee una colección masiva de textos (por ejemplo, 50,000 artículos, libros o reseñas). En lugar de guardarlos como una simple lista donde tendría que leer todo desde cero cada vez que alguien busca algo, el sistema descompone cada texto, limpia las palabras y construye un "índice inteligente" en la memoria RAM (como el índice temático al final de un libro, pero a escala masiva).
2. **Responde búsquedas complejas en milisegundos (Consulta y Ranking):**
Una vez indexada la información, el motor recibe preguntas o búsquedas de miles de usuarios al mismo tiempo y entrega los **10 documentos más relevantes** en cuestión de milisegundos. Además, es capaz de entender búsquedas avanzadas, perdonar errores de ortografía, autocompletar lo que el usuario está escribiendo y seguir recibiendo documentos nuevos sin detenerse.

### Casos de uso reales que resuelve el sistema

Para que el proyecto tenga un alcance real de ingeniería, el buscador resuelve 6 escenarios cotidianos:

* **Búsquedas lógicas avanzadas:** Entiende reglas combinadas como `(backend OR microservicios) AND NOT "servidor lento"`.
* **Búsqueda de frases exactas:** Si buscas `"arquitectura limpia"`, encuentra únicamente documentos donde esas dos palabras aparezcan juntas y en ese orden exacto.
* **Ordenamiento por relevancia (Ranking):** Si buscas `"el mejor motor"`, sabe que `"motor"` es una palabra clave importante y que `"el"` es un conector común, ordenando primero los textos que realmente tratan sobre motores.
* **Corrección ortográfica (Fuzzy Search):** Si el usuario escribe mal `"concurrensia"`, el sistema deduce que quiso decir `"concurrencia"` y devuelve los resultados correctos.
* **Autocompletado instantáneo (Typeahead):** Mientras el usuario escribe `"prog..."`, el sistema sugiere al instante `"programacion funcional"` o `"programacion concurrente"`.
* **Actualización en vivo:** Permite que entren documentos nuevos al sistema mientras miles de usuarios están buscando al mismo tiempo, sin bloquearse ni arrojar errores.

---

## 2. ¿Por qué este proyecto diferencia de manera pura a Scala, Java y Python?

Si construyéramos una API CRUD tradicional con base de datos, el 95% del tiempo se iría esperando respuestas de red o de disco duro, midiendo la base de datos en lugar del lenguaje. Al operar **100% en RAM y con librerías estándar**, cada parte del buscador pone a prueba el ADN real de cada lenguaje:

| Componente del Buscador | Scala 3 (Funcional / Híbrido) | Java 21+ (Imperativo / DOP Moderno) | Python 3.12+ (Dinámico / Interpretado) |
| --- | --- | --- | --- |
| **1. Construcción del Índice** | Transformaciones ultra expresivas con colecciones inmutables (`flatMap`, `groupBy`, `foldLeft`), pero alto estrés para el *Garbage Collector* si se crean demasiados objetos intermedios. | Mayor verbosidad usando estructuras mutables (`HashMap`, arreglos primitivos `int[]`), pero control milimétrico de la memoria RAM y máxima velocidad del compilador JIT. | Código sumamente corto y rápido de escribir con `dict`, `set` y comprensiones de listas, pero con un consumo de RAM muy superior porque cada entero y texto es un `PyObject` completo. |
| **2. Parser Booleano (AST)** | Escenario ideal para lucir **Tipos de Datos Algebraicos (`enum` / `sealed trait`)**, *Pattern Matching* exhaustivo y recursión de cola (`@tailrec`). | Pone a prueba el nuevo paradigma de Java moderno: `sealed interfaces`, `records` inmutables y *Pattern Matching for switch*. | Evalúa el uso de `@dataclass` (con `__slots__` para ahorrar RAM) y `match / case` estructural, evidenciando el límite de recursión sin optimización de cola. |
| **3. Ranking BM25 y Fuzzy Search** | Comparación directa entre pipelines funcionales declarativos vs. vistas perezosas (`View`) para evitar recálculos costosos en CPU. | Terreno donde Java saca ventaja bruta en bucles matemáticos intensivos, manejo de `PriorityQueue` y cercanía en caché del procesador. | Deja al descubierto el costo real de ejecutar bucles matemáticos puros e indirección de punteros en el intérprete sin ayuda de código C externo. |
| **4. Concurrencia en Caliente** | Uso de **Fibras (`ZIO` / `Cats Effect`)**, estructuras inmutables compartidas sin *locks* (`Ref` / `STM`) o el Modelo de Actores. | Uso de **Virtual Threads (Project Loom)** combinados con `ConcurrentHashMap` y bloqueos de lectura/escritura (`StampedLock` / `ReadWriteLock`). | Exposición directa del **GIL (Global Interpreter Lock)**: `asyncio` no paraleliza el cómputo en CPU, obligando a evaluar el costo de `multiprocessing` o el modo experimental *free-threaded*. |

---

## 3. Anatomía Interna: ¿Cómo funciona el motor por dentro?

Un motor de búsqueda divide su trabajo en dos mundos separados: el **Flujo de Escritura (Indexación)** y el **Flujo de Lectura (Consulta)**.

### A. El Índice Invertido Posicional (El corazón de todo)

Un libro normal va de *Página → Palabras*. Un índice invertido da la vuelta a eso: va de *Palabra → Documentos y Posiciones*.

Supongamos que tenemos dos documentos:

* **Doc 1:** *"scala es rapido y funcional"*
* **Doc 2:** *"java es rapido y seguro"*

Antes de guardarlos, pasan por un **Pipeline de Análisis de Texto**:

1. **Minúsculas:** `"Scala"` → `"scala"`
2. **Limpieza de puntuación:** Quitar comas, puntos y signos.
3. **Tokenización:** Separar por espacios asignando un índice posicional (`0, 1, 2...`).

Con esto, la estructura principal en memoria RAM será un diccionario/mapa (`HashMap` / `dict`) así:

```text
"scala"     -> { Doc 1: [0] }
"java"      -> { Doc 2: [0] }
"es"        -> { Doc 1: [1], Doc 2: [1] }
"rapido"    -> { Doc 1: [2], Doc 2: [2] }
"y"         -> { Doc 1: [3], Doc 2: [3] }
"funcional" -> { Doc 1: [4] }
"seguro"    -> { Doc 2: [4] }

```

* **Crucial para el rendimiento:** Los IDs de documentos y las listas de posiciones **siempre deben quedar ordenados de menor a mayor** (`[2, 15, 89...]`). Gracias a que están ordenados, cruzar dos listas toma tiempo lineal $O(N + M)$ usando dos punteros, sin necesidad de bucles anidados.

---

### B. El Parser de Consultas (De texto a Árbol AST)

Cuando el usuario escribe `(scala OR java) AND NOT lento`, el motor convierte esa cadena en un **Árbol de Sintaxis Abstracta (AST)** en dos pasos:

1. **Tokenizer (Lexer):** Convierte el texto en fichas: `[LPAREN, WORD("scala"), OR, WORD("java"), RPAREN, AND, NOT, WORD("lento")]`.
2. **Recursive Descent Parser:** Aplica precedencia de operadores (`Paréntesis > NOT > AND > OR`) y produce un árbol de objetos:

```text
          [ AND ]
         /       \
     [ OR ]     [ NOT ]
     /    \        |
"scala"  "java"  "lento"

```

Para resolverlo, se evalúa de abajo hacia arriba devolviendo conjuntos de IDs de documentos:

* `"scala"` devuelve `{1}` y `"java"` devuelve `{2}`.
* `OR` une ambos: `{1, 2}`.
* `NOT "lento"` devuelve todos los documentos que *no* contienen `"lento"`: `{1, 2}`.
* `AND` intersecta ambos lados: `{1, 2}`.

---

### C. Búsqueda de Frases Exactas (`"rapido y seguro"`)

Aquí se utilizan las listas de posiciones guardadas en el índice:

1. Primero se buscan los documentos que contienen **todas** las palabras de la frase (intersección simple: solo el `Doc 2` tiene `"rapido"`, `"y"`, `"seguro"`).
2. Dentro del `Doc 2`, se toman las listas de posiciones de cada palabra: `"rapido": [2]`, `"y": [3]`, `"seguro": [4]`.
3. Se verifica si existe alguna posición $p$ en la primera palabra tal que $p+1$ esté en la segunda y $p+2$ esté en la tercera. Como $2 + 1 = 3$ y $2 + 2 = 4$, **hay coincidencia exacta**.

---

### D. El Algoritmo de Relevancia BM25 (La matemática)

Una vez filtrados los documentos que cumplen la consulta, **BM25** calcula un puntaje numérico para cada documento $D$ frente a las palabras de la consulta $q_1, q_2, \dots, q_n$:

$$\text{Score}(D, Q) = \sum_{i=1}^{n} \text{IDF}(q_i) \cdot \frac{f(q_i, D) \cdot (k_1 + 1)}{f(q_i, D) + k_1 \cdot \left(1 - b + b \cdot \frac{\vert{}D\vert{}}{\text{avgdl}}\right)}$$

Variables que utiliza la fórmula:

1. **$f(q_i, D)$ (Term Frequency):** Cuántas veces aparece la palabra $q_i$ en el documento $D$ (`posiciones.length`).
2. **$\vert{}D\vert{}$ y $\text{avgdl}$:** Cantidad de palabras del documento $D$ dividido entre el promedio de palabras de todos los documentos del sistema (evita que documentos muy largos ganen injustamente).
3. **$k_1 = 1.5$ y $b = 0.75$:** Constantes estándar de calibración.
4. **$\text{IDF}(q_i)$ (Inverse Document Frequency):** Mide qué tan rara es la palabra en todo el sistema:

$$\text{IDF}(q_i) = \ln \left( \frac{N - n(q_i) + 0.5}{n(q_i) + 0.5} + 1 \right)$$



Donde $N$ es el total de documentos indexados y $n(q_i)$ es en cuántos documentos aparece esa palabra.

**Optimización Top-K:** Para devolver el Top 10, **no se ordenan todos los resultados**. Se usa una cola de prioridad mínima (**Min-Heap**) de tamaño fijo $K = 10$, reduciendo la complejidad de $O(M \log M)$ a $O(M \log 10)$.

---

### E. Árbol Trie y Fuzzy Search (Autocompletado y Corrección)

* **Trie (Árbol de Prefijos):** Cada nodo representa una letra. Si se insertan `"casa"`, `"caso"` y `"carro"`, comparten el nodo `c -> a`. Cuando el usuario escribe `"ca"`, se baja dos nodos y se recorren sus hijos para sugerir las palabras más frecuentes.
* **Fuzzy Search (Distancia de Levenshtein):** Si una palabra buscada no existe en el índice, se recorre el diccionario/Trie calculando cuántos cambios de letras (inserción, borrado o sustitución) se necesitan para convertir una palabra en otra. Si la distancia es menor o igual a 2, se sustituye el término por la sugerencia más cercana.

---

## 4. Contrato Técnico Único (Arquitectura para los 3 Lenguajes)

Para que la comparativa sea 100% justa, los tres proyectos deben implementar exactamente estas **5 entidades base**:

1. **`Document` (Inmutable):**
* `id: Int`
* `title: String`
* `body: String`
* `length: Int` (total de tokens)


2. **`Posting` (Entrada en el índice):**
* `docId: Int`
* `positions: Array[Int]` / `int[]` / `list[int]` (ordenado ascendentemente)


3. **`QueryNode` (AST de la consulta):**
* `TermNode(word: String)`
* `PhraseNode(words: List[String])`
* `AndNode(left: QueryNode, right: QueryNode)`
* `OrNode(left: QueryNode, right: QueryNode)`
* `NotNode(child: QueryNode)`


4. **`InvertedIndex` (Estructura central):**
* `index: Map[String, List[Posting]]`
* `documents: Map[Int, Document]`
* `avgDocLength: Double`


5. **`SearchResult`:**
* `docId: Int`
* `title: String`
* `score: Double`



---

## 5. Detalles de Implementación Idiomática por Lenguaje

Aunque comparten el mismo contrato, cada lenguaje debe aprovechar sus herramientas nativas:

* **Scala 3:**
* Modela `QueryNode` como un **`enum`** nativo (`enum QueryNode: case Term(...), And(...)`) y evalúalo usando **`match` exhaustivo**.
* Usa `case class` para `Document`, `Posting` y `SearchResult`.
* En el Nivel 4 (concurrencia), usa **Fibras (`ZIO` o `Cats Effect`)** con una referencia atómica (`Ref`) que contenga estructuras inmutables para que las lecturas nunca bloqueen a las escrituras.


* **Java 21+:**
* Modela `QueryNode` con **`sealed interface`** y **`record`** (`record AndNode(QueryNode left, QueryNode right) implements QueryNode {}`) usando *Pattern Matching for switch*.
* Usa arreglos primitivos **`int[]`** para las posiciones de cada palabra (ahorra hasta un 70% de RAM frente a `List<Integer>`).
* En el Nivel 4, usa **`Virtual Threads`** (`Executors.newVirtualThreadPerTaskExecutor()`) y un **`ReentrantReadWriteLock`** (o `StampedLock`) protegiendo el índice.


* **Python 3.12+:**
* Modela `QueryNode` con **`@dataclass(slots=True, frozen=True)`** (`slots=True` es vital para reducir el consumo de memoria de los objetos en Python) y evalúalo con **`match / case`**.
* Usa **`heapq`** de la librería estándar para el Top-K de BM25 y **`bisect`** para búsquedas binarias en listas ordenadas.
* En el Nivel 4, implementa el benchmark con `ThreadPoolExecutor` (para demostrar el cuello de botella del GIL) y opcionalmente compáralo contra `ProcessPoolExecutor`.



---

## 6. Reglas de Pureza, Fuentes de Datos y Métricas

### Fuentes de Datos Recomendadas

* **Opción A (Datos reales):** Resúmenes de Wikipedia (*Wikipedia Abstracts*) o el dataset *IMDb 50k Movie Reviews*.
* **Opción B (Libros):** 30-40 libros en `.txt` plano de *Project Gutenberg* cortados por párrafos (`\n\n`).
* **Opción C (Sintético controlado):** El script generador incluido al final de este documento, basado en la distribución de la Ley de Zipf.

### Reglas de Juego

1. **Formato único de entrada (`.tsv`):** Todos los documentos se entregan en un único archivo de texto separado por tabulaciones (`ID \t Título \t Texto`). Así ningún lenguaje depende de librerías externas para parsear JSON o XML; solo se usa `.split("\t")` nativo.
2. **Cero librerías en C/Rust:** En Python queda prohibido usar `NumPy`, `Pandas` o envoltorios en C para el cálculo matemático, y en Java/Scala queda prohibido usar `Lucene`. Todo debe implementarse con las colecciones y herramientas estándar de cada lenguaje.
3. **Medición con datos precargados en RAM:** Los tres programas primero leerán el archivo `.tsv` a una lista en memoria. El cronómetro solo se inicia cuando empieza el procesamiento en RAM para aislar la velocidad del disco SSD.

### Métricas a Registrar

* **Tiempo de Indexación (ms):** Cuánto tarda cada lenguaje en transformar el texto crudo en el índice invertido y el árbol Trie.
* **Huella de Memoria RAM (MB):** Cuánta memoria consume mantener 50,000+ documentos indexados con sus posiciones.
* **Throughput de Búsqueda (Queries/seg):** Cuántas consultas por segundo responde cada motor bajo carga concurrente.
* **Latencia de Cola (p95 y p99):** Cuánto tardan el 1% de las consultas más lentas (pausas de GC en la JVM vs. bloqueos de CPU en Python).
* **Mantenibilidad y Expresividad:** Líneas de código, claridad arquitectónica y garantías en tiempo de compilación.

---

## 7. Plan de Implementación por Fases

* **Nivel 1 (Base):** Carga del archivo `.tsv`, limpieza de texto (minúsculas, signos), construcción del Índice Invertido simple y búsqueda por una palabra.
* **Nivel 2 (Consultas Booleanas):** Parser del árbol lógico completo (`AND`, `OR`, `NOT`, paréntesis). *Prueba maestra: los 3 lenguajes deben devolver exactamente los mismos IDs.*
* **Nivel 3 (Precisión y Ranking):** Índice Posicional para búsqueda de `"frases exactas"` + algoritmo matemático **BM25** con `Min-Heap` para devolver el Top 10 ordenado por relevancia.
* **Nivel 4 (UX y Concurrencia Pesada):** Árbol **Trie** para autocompletado + Corrección ortográfica (*Fuzzy Search* con Levenshtein) + Benchmark multihilo con lectores y escritores simultáneos en caliente.

---

## 8. Script Generador del Dataset de Prueba

Ejecuta este script una sola vez para generar `corpus.tsv` (50,000 documentos con distribución realista tipo Ley de Zipf) y `queries.txt` (5,000 consultas de prueba) listos para alimentar a Scala, Java y Python:

```python
import random

SEED = 42
NUM_DOCS = 50_000
NUM_QUERIES = 5_000
VOCAB_SIZE = 15_000

def generate_dataset():
    rng = random.Random(SEED)
    
    # 1. Vocabulario base + palabras clave técnicas para consultas predecibles
    core_terms = [
        "scala", "java", "python", "backend", "concurrencia", "memoria",
        "rendimiento", "algoritmo", "indice", "busqueda", "arquitectura",
        "microservicios", "datos", "sistema", "servidor", "rapido", "lento",
        "funcional", "objetos", "hilos", "fibras", "latencia", "distribuido"
    ]
    vocab = core_terms + [f"palabra{i}" for i in range(VOCAB_SIZE - len(core_terms))]
    
    # Pesos tipo Ley de Zipf (unas pocas palabras son muy frecuentes, la mayoría son raras)
    weights = [1.0 / ((i + 1) ** 0.85) for i in range(len(vocab))]
    
    # 2. Generar corpus.tsv
    with open("corpus.tsv", "w", encoding="utf-8") as f:
        for doc_id in range(1, NUM_DOCS + 1):
            title_len = rng.randint(4, 9)
            body_len = rng.randint(60, 180)
            
            title = " ".join(rng.choices(vocab, weights=weights, k=title_len))
            body = " ".join(rng.choices(vocab, weights=weights, k=body_len))
            f.write(f"{doc_id}\t{title}\t{body}\n")

    # 3. Generar queries.txt (mezcla de simples, booleanas y frases exactas)
    with open("queries.txt", "w", encoding="utf-8") as f:
        for _ in range(NUM_QUERIES):
            q_type = rng.random()
            w1, w2, w3 = rng.sample(core_terms, 3)
            if q_type < 0.35:
                f.write(f"{w1} {w2}\n")
            elif q_type < 0.70:
                f.write(f"({w1} OR {w2}) AND NOT {w3}\n")
            else:
                f.write(f'"{w1} {w2}" AND {w3}\n')

if __name__ == "__main__":
    generate_dataset()
    print("Listo: corpus.tsv y queries.txt generados con semilla fija.")

```