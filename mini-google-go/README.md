# Mini-Google: Implementación en Go (Golang)

Tercer lenguaje del proyecto comparativo de paradigmas. Implementación planificada en **Go 1.22+**.

## Objetivos Arquitecturales de la Implementación en Go

1. **Compilación a Código Nativo:** Ausencia de máquina virtual o compilación JIT; ejecución directa en binario nativo de alta eficiencia.
2. **Concurrencia con Goroutines y Canales:** Uso del scheduler M:N de Go para procesamiento masivo de consultas concurrentes con consumo mínimo de stack de memoria (~2 KB por goroutine).
3. **Gestión de Memoria y GC de Baja Latencia:** Estructuras de datos contiguas (`slices` primitivos) para minimizar la indirección de punteros y mantener pausas de Garbage Collection por debajo de 1 ms.
4. **Estructura Modular Planificada:**
   - `pkg/model/`: Tipos de datos para documentos, postings y resultados.
   - `pkg/index/`: Índice invertido posicional concurrente.
   - `pkg/query/`: Lexer, parser recursivo descendente y AST booleano.
   - `pkg/search/`: Motor de evaluación y ranking BM25 con Min-Heap.
   - `pkg/benchmark/`: Arnés experimental compatible con el formato JSON unificado.

## Estado
* **Estado:** Pendiente de implementación (Fase 3 del proyecto).
