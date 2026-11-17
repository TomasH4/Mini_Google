import argparse
import random
from typing import List

SEED = 42
DEFAULT_DOCS = 10_000
DEFAULT_QUERIES = 2_000

# ---------------------------------------------------------------------------
# Bancos de contenido técnico realista sobre Ingeniería y Paradigmas
# ---------------------------------------------------------------------------

AREAS_TECNICAS = [
    "Programación Funcional", "Sistemas Distribuidos", "Arquitectura de Software",
    "Gestión de Memoria RAM", "Motores de Búsqueda", "Concurrencia y Paralelismo",
    "Compiladores y AST", "Optimización de Rendimiento", "Bases de Datos en Memoria",
    "Estructuras de Datos Avanzadas", "Algoritmos de Ranking", "Microservicios Modernos"
]

TITULOS_TEMPLATES = [
    "Análisis de {tema} con {lenguaje} en entornos de alta concurrencia",
    "Optimización de {concepto} en {tema}: métricas de latencia y rendimiento",
    "Patrones de {tema} aplicados a {componente} en tiempo real",
    "Comparativa de {concepto} frente a {concepto_alt} en arquitecturas modernas",
    "Diseño de un {componente} escalable basado en {tema}",
    "Impacto del {concepto} en el consumo de memoria y rendimiento de CPU",
    "Implementación eficiente de {componente} utilizando {lenguaje}",
    "Principios de {tema} para reducir la latencia de cola p99",
    "Evaluación experimental de {concepto} en {lenguaje} versus alternativas",
    "Arquitectura interna de un {componente} optimizado para memoria RAM"
]

TEMAS_MAP = {
    "lenguajes": ["Java 21", "Scala 3", "Python 3.12", "Rust", "Go", "Haskell"],
    "conceptos": [
        "inmutabilidad de datos", "recolección de basura", "evaluación perezosa",
        "hilos virtuales", "fibras ligeras", "pattern matching exhaustivo",
        "árboles de sintaxis abstracta", "índices invertidos posicionales",
        "recursión de cola", "funciones de orden superior", "bloqueos de lectura y escritura",
        "fórmulas de ranking BM25", "distancia de Levenshtein", "estructuras de datos Trie"
    ],
    "conceptos_alt": [
        "estructuras mutables concurrentes", "hilos nativos del sistema operativo",
        "bloqueos globales sincronizados", "búsqueda secuencial lineal",
        "recursión estándar sin optimización", "recorrido de árboles en profundidad"
    ],
    "componentes": [
        "motor de búsqueda en memoria", "parser de consultas booleanas",
        "índice invertido posicional", "sistema de autocompletado en caliente",
        "balanceador de carga reactivo", "clasificador automático de métricas",
        "arnés de benchmarking distribuido", "árbol Trie de prefijos"
    ]
}

PARRAFOS_TEMPLATES = [
    "En el desarrollo de software moderno enfocado en {tema}, la adopción de {concepto} permite maximizar la estabilidad y reducir drásticamente los errores en tiempo de ejecución. Al procesar grandes volúmenes de datos, {lenguaje} proporciona herramientas avanzadas como {concepto_extra} que facilitan la paralelización del trabajo sin comprometer la consistencia de los datos.",
    
    "La arquitectura de un {componente} requiere un control estricto sobre el consumo de memoria RAM y los ciclos de CPU. Cuando se sustituye {concepto_alt} por {concepto}, los tiempos de respuesta mejoran significativamente, especialmente bajo cargas con miles de solicitudes concurrentes por segundo.",
    
    "Desde la perspectiva del paradigma funcional, las operaciones basadas en transformaciones de tipo map, filter y reduce aseguran que los estados intermedios permanezcan inmutables. Esta característica es fundamental al construir un {componente}, donde múltiples subprocesos acceden simultáneamente al índice sin necesidad de bloqueos pesados.",
    
    "Durante las pruebas de benchmarking, se registraron métricas cuantitativas clave como la latencia media, el percentil p95 y la latencia máxima p99. Los resultados evidencian que {lenguaje} ofrece una ventaja sustancial en operaciones matemáticas intensivas gracias a su compilador optimizado y a la gestión predictiva de caché del procesador.",
    
    "El algoritmo matemático de ranking BM25 calcula la relevancia combinando la frecuencia de términos con la longitud media de los documentos indexados. Integrar una cola de prioridad Min-Heap de tamaño fijo K garantiza que la recuperación de los diez mejores resultados se ejecute en tiempo logarítmico O(N log K), evitando ordenamientos globales costosos.",
    
    "La detección y corrección de errores ortográficos mediante distancia de edición o árboles de prefijos Trie aporta una experiencia de usuario fluida y tolerante a fallos. Al recorrer los nodos del diccionario, el sistema evalúa candidatos cercanos minimizando la asignación de memoria intermedia en el montículo.",
    
    "En escenarios de alta disponibilidad, la concurrencia no bloqueante y el uso de estructuras concurrentes optimizadas previenen cuellos de botella. La comparación directa entre {lenguaje} y otros entornos revela diferencias determinantes en el comportamiento del recolector de basura y el tiempo de calentamiento del compilador JIT."
]

CONSULTAS_REALISTAS = [
    # Consultas Simples
    "java", "scala", "python", "concurrencia", "inmutabilidad",
    "memoria", "rendimiento", "latencia", "algoritmo", "ranking",
    "bm25", "microservicios", "virtual threads", "trie",
    # Consultas Frase Exacta
    '"hilos virtuales"', '"programacion funcional"', '"memoria ram"',
    '"indice invertido"', '"ranking bm25"', '"alta concurrencia"',
    '"recolector de basura"', '"pattern matching"', '"latencia p99"',
    '"cola de prioridad"', '"arbol de sintaxis abstracta"',
    # Consultas Booleanas Complejas
    '(java OR scala) AND NOT python',
    '(concurrencia OR rendimiento) AND NOT bloqueos',
    '("hilos virtuales" OR fibras) AND latencia',
    '("indice invertido" OR trie) AND memoria',
    '(bm25 OR ranking) AND "tiempo real"',
    '("programacion funcional" AND inmutabilidad) OR concurrencia',
    'microservicios AND ("alta disponibilidad" OR escalabilidad)',
    '(latencia OR rendimiento) AND NOT lento'
]

def generar_titulo(rng: random.Random) -> str:
    template = rng.choice(TITULOS_TEMPLATES)
    return template.format(
        tema=rng.choice(AREAS_TECNICAS),
        lenguaje=rng.choice(TEMAS_MAP["lenguajes"]),
        concepto=rng.choice(TEMAS_MAP["conceptos"]),
        concepto_alt=rng.choice(TEMAS_MAP["conceptos_alt"]),
        componente=rng.choice(TEMAS_MAP["componentes"])
    )

def generar_cuerpo(rng: random.Random) -> str:
    num_parrafos = rng.randint(2, 4)
    plantillas_seleccionadas = rng.sample(PARRAFOS_TEMPLATES, num_parrafos)
    parrafos = []
    
    for p in plantillas_seleccionadas:
        texto = p.format(
            tema=rng.choice(AREAS_TECNICAS),
            lenguaje=rng.choice(TEMAS_MAP["lenguajes"]),
            concepto=rng.choice(TEMAS_MAP["conceptos"]),
            concepto_extra=rng.choice(TEMAS_MAP["conceptos"]),
            concepto_alt=rng.choice(TEMAS_MAP["conceptos_alt"]),
            componente=rng.choice(TEMAS_MAP["componentes"])
        )
        parrafos.append(texto)
        
    return " ".join(parrafos)

def generate_dataset(num_docs: int, num_queries: int):
    print(f"=== Generando Dataset Técnico Realista ===")
    print(f"Documentos a generar: {num_docs:,}")
    print(f"Consultas a generar:  {num_queries:,}")
    
    rng = random.Random(SEED)
    
    # 1. Generar corpus.tsv
    with open("corpus.tsv", "w", encoding="utf-8") as f:
        for doc_id in range(1, num_docs + 1):
            title = generar_titulo(rng)
            body = generar_cuerpo(rng)
            # Limpiar posibles saltos o tabulaciones para formato TSV estricto
            title_clean = title.replace("\t", " ").replace("\n", " ").strip()
            body_clean = body.replace("\t", " ").replace("\n", " ").strip()
            f.write(f"{doc_id}\t{title_clean}\t{body_clean}\n")
            
    # 2. Generar queries.txt con consultas realistas y variadas
    with open("queries.txt", "w", encoding="utf-8") as f:
        for _ in range(num_queries):
            query = rng.choice(CONSULTAS_REALISTAS)
            f.write(f"{query}\n")
            
    print(f"[OK] Archivos generados exitosamente:")
    print(f"     -> corpus.tsv  ({num_docs:,} artículos técnicos estructurados)")
    print(f"     -> queries.txt ({num_queries:,} consultas booleanas y de frases reales)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generador de dataset técnico realista para Mini-Google")
    parser.add_argument("--docs", type=int, default=DEFAULT_DOCS, help=f"Cantidad de documentos (default: {DEFAULT_DOCS:,})")
    parser.add_argument("--queries", type=int, default=DEFAULT_QUERIES, help=f"Cantidad de consultas (default: {DEFAULT_QUERIES:,})")
    args = parser.parse_args()
    
    generate_dataset(args.docs, args.queries)
