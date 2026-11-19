# Mini-Google: Motor de Búsqueda de Texto Completo en Memoria

**Benchmark y Estudio Comparativo de Paradigmas: Java 21 vs. Scala 3 vs. Go 1.22**

Abre [el reporte interactivo](index.html) con doble clic desde la raíz para explorar las métricas, comparar el código y ajustar el simulador de decisión.

---

## 1. Descripción General

**Mini-Google** es un motor de búsqueda de texto completo (*Full-Text Search Engine*) que opera **100% en memoria RAM**, construido sin dependencias de motores prefabricados (como Elasticsearch o Apache Lucene) ni bases de datos externas.

El proyecto tiene como objetivo servir como un banco de pruebas experimental riguroso para comparar el rendimiento, consumo de memoria, latencias y concurrencia entre tres paradigmas y lenguajes:

1. **Java 21+:** Paradigma Imperativo / DOP Moderno con Virtual Threads (Project Loom).
2. **Scala 3:** Paradigma Funcional Puro / Híbrido con estructuras inmutables.
3. **Go 1.22+:** Compilación nativa, concurrencia ligera con Goroutines y baja latencia de Garbage Collection.

---

## 2. Estructura Modular del Repositorio

El proyecto está organizado en módulos independientes y limpios:

```text
classroom-project/
├── dataset/                     # Generador determinista de datos y reportes experimentales
│   ├── generate_dataset.py      # Script de generación de corpus TSV y queries TXT
│   ├── corpus.tsv / queries.txt # Datos base de muestra
│   └── reportes/                # Resultados y reportes JSON de benchmarking
│
├── mini-google-java/            # Implementación en Java 21 (Virtual Threads)
│   ├── pom.xml                  # Configuración de compilación Maven
│   ├── src/                     # Código fuente Java y recursos
│   └── README.md                # Guía de compilación y ejecución de Java
│
├── mini-google-scala/           # Implementación en Scala 3 (Programación Funcional)
│   ├── pom.xml                  # Configuración Maven (scala-maven-plugin)
│   ├── src/                     # Código fuente Scala y suite de tests (MUnit)
│   └── README.md                # Guía de compilación y ejecución de Scala
│
├── mini-google-go/              # Implementación en Go 1.22+
│   ├── cmd/searchd/              # Búsqueda interactiva y CLI de benchmark
│   ├── internal/                 # Índice, parser, ranking, trie y métricas
│   └── README.md                 # Guía de uso y validación de Go
│
├── INSTRUCCIONES_BENCHMARK.md   # Guía metodológica para la ejecución de pruebas
├── final_lenguajes_y_paradigmas.md # Rúbrica oficial y ponderación de criterios
└── proyecto_final.md            # Documento de diseño arquitectural detallado
```

---

## 3. Guía Rápida de Inicio

### A. Generar los Datos de Prueba
Desde la carpeta `dataset/`:
```bash
python generate_dataset.py --scenario tier1
```
*(Opcional: `--scenario all` genera los tres escenarios: Tier 1 de 25k, Tier 2 de 75k y Tier 3 de 150k documentos).*

### B. Ejecutar Java 21
Desde la carpeta `mini-google-java/`:
```bash
# Compilar:
mvn clean package

# Ejecutar benchmark:
java -jar target/mini-google-1.0-SNAPSHOT.jar --benchmark ../dataset/corpus_25k.tsv ../dataset/queries_25k.txt ../dataset/reportes/reporte_java_25k.json
```

### C. Ejecutar Scala 3
Desde la carpeta `mini-google-scala/`:
```bash
# Compilar y ejecutar pruebas:
mvn test

# Empaquetar JAR ejecutable:
mvn clean package
```

### D. Golang

Desde `mini-google-go/`:

```text
go run ./cmd/searchd interactive
go run ./cmd/searchd bench
```

Consulta la [guía de Go](mini-google-go/README.md) para elegir un corpus, ajustar la concurrencia y exportar resultados.

---

## 4. Métricas y Comparación Experimental

La comparación normalizada de Java, Scala y Go se ejecuta desde la raíz con `python benchmark/run.py`. La [guía de la suite](benchmark/README.md) describe la compuerta de paridad, las dos escalas y el [reporte consolidado](benchmark/results.json).

Para asegurar condiciones equitativas y reproducibles:
- Todos los lenguajes procesan **exactamente el mismo archivo TSV y las mismas consultas** generadas con semilla determinista ($\text{SEED}=42$).
- Java y Go exportan JSON. Scala exporta CSV. Los reportes registran:
  - Tiempo de indexación en RAM (`indexTimeMs`).
  - Memoria RAM utilizada (`memoryUsedMb`).
  - Throughput secuencial y percentiles de latencia (Media, p50, p95, p99).
  - Throughput concurrente en los benchmarks de Java y Go.
