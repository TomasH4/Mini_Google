# Mini-Google en Go

Motor de búsqueda de texto completo en memoria para el corpus TSV compartido. Requiere **Go 1.22 o superior** y usa la biblioteca estándar.

## Ejecutar

Desde `mini-google-go/`:

```text
go run ./cmd/searchd interactive
go run ./cmd/searchd search -- '(java OR scala) AND NOT python'
go run ./cmd/searchd suggest -- concur
go run ./cmd/searchd fuzzy --distance 2 -- concurrensia
go run ./cmd/searchd search --fuzzy 2 -- concurrensia
```

El corpus predeterminado es `../dataset/corpus.tsv`. Usa `--corpus ruta.tsv` para cargar otro. Cada fila contiene ID entero positivo, título y cuerpo con tabulaciones como separadores, en UTF-8. Los IDs deben ser únicos. La búsqueda indexa el cuerpo y muestra el título.

Los operadores respetan la precedencia `NOT`, `AND`, `OR`. Los términos adyacentes implican `AND` y las comillas dobles delimitan frases exactas. El ranking usa BM25 con `k1=1.5` y `b=0.75`, los términos bajo un número par de negaciones puntúan y los empates se ordenan por ID ascendente, igual que en Scala. La búsqueda difusa opcional corrige términos positivos ausentes, con distancia de Levenshtein de hasta 2. Una frase entre comillas conserva su semántica exacta. Las sugerencias muestran término, frecuencia documental y distancia.

## Benchmark

```text
go run ./cmd/searchd bench --corpus ../dataset/corpus.tsv --queries ../dataset/queries.txt --workers 4 --out reporte_go.json --dump resultados_go.tsv
```

El comando imprime el JSON en la consola y `--out` guarda una copia. `--dump` exporta una fila por consulta con el número de coincidencias y hasta diez pares `id:puntaje`, en el formato TSV de Scala. La exportación se ejecuta fuera de las mediciones. Puedes usar los archivos generados por `dataset/generate_dataset.py` para los escenarios de 25.000, 75.000 y 150.000 documentos.

- `loadTimeMs` mide la lectura del corpus. `indexTimeMs` mide el parseo del TSV ya cargado en RAM y la construcción del índice, incluido el trie.
- `memoryUsedMb` estima la memoria retenida como la diferencia en `HeapAlloc` tras forzar GC antes y después de construir el índice. `indexAllocatedBytes` registra la diferencia acumulada de `TotalAlloc`.
- Cada fase de consultas incluye parseo, evaluación y ranking. Se ejecutan hasta 50 consultas de calentamiento configurable con `--warmup`. El benchmark usa búsqueda exacta.
- `--workers` fija el máximo de goroutines de consulta y su valor predeterminado es `GOMAXPROCS`. El throughput concurrente incluye la coordinación del lote. Sus percentiles miden desde que un trabajador empieza la consulta.
- `clock` identifica el reloj monotónico del reporte. En Windows se usa [QueryPerformanceCounter](https://learn.microsoft.com/en-us/windows/win32/api/profileapi/nf-profileapi-queryperformancecounter) para resolver consultas de duración submilisegundo.

El JSON usa los nombres de métricas compartidos del repositorio e identifica la concurrencia con `concurrentWorkers`. La comparación entre lenguajes requiere el mismo corpus, consultas, reglas de ranking y etapas medidas. Java, Scala y Go todavía tienen diferencias en esas reglas y en el protocolo de medición.

## Validar

```text
go vet ./...
go test -race ./...
go test '-bench=.' -benchmem ./...
```

En Windows, el detector de carreras requiere `CGO_ENABLED=1` y un [compilador compatible con mingw-w64](https://go.dev/doc/articles/race_detector#requirements). El binario normal de Go usa solo la biblioteca estándar.
