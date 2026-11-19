# Módulo Dataset y Benchmarking

Este módulo centraliza las herramientas para la generación determinista de datos de prueba, corpus y la consolidación de reportes experimentales para los 3 lenguajes (**Java, Scala y Go**).

## Generador Determinista (`generate_dataset.py`)

Garantiza la **reproducibilidad experimental** requerida por la rúbrica del proyecto. Gracias a una semilla fija ($\text{SEED} = 42$), cualquier miembro del equipo generará exactamente los mismos archivos y los mismos hashes byte por byte.

### Uso

Desde la carpeta `dataset/`:

```bash
# 1. Generar todos los escenarios oficiales (Tier 1, Tier 2 y Tier 3):
python generate_dataset.py --scenario all

# 2. Generar solo el escenario Tier 1 (25k docs, 10k queries):
python generate_dataset.py --scenario tier1

# 3. Generar solo el escenario Tier 2 (75k docs, 25k queries):
python generate_dataset.py --scenario tier2

# 4. Generar solo el escenario Tier 3 (150k docs, 50k queries):
python generate_dataset.py --scenario tier3

# 5. Generación personalizada:
python generate_dataset.py --docs 1000 --queries 200 --corpus mi_corpus.tsv --queries-file mis_queries.txt
```

## Estructura de la Carpeta

- `generate_dataset.py`: Script de generación determinista de corpus y consultas.
- `corpus.tsv` / `queries.txt`: Archivos de muestra estándar.
- `reportes/`: Directorio donde se almacenan los reportes JSON de resultados de cada lenguaje:
  - `reporte_java_25k.json`, `reporte_java_75k.json`, etc.
  - `reporte_scala_25k.json`, `reporte_scala_75k.json`, etc.
  - `reporte_go_25k.json`, etc.
