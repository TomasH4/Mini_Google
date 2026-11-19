# Implementación de DeepEval sobre las pruebas Stagehand

## Visión general
Hemos incorporado la librería **DeepEval** para evaluar automáticamente la calidad de las respuestas generadas por Gemini en cada prueba *Stagehand* (F1‑F5).  El objetivo es obtener métricas cuantitativas (Faithfulness, Relevancy y Hallucination) sin tener que revisar manualmente la salida del LLM.

## Pasos de la integración

1. **Actualización del modelo**
   - En `tests_stagehand/.env` cambiamos `GEMINI_MODEL` a `google/gemini-3.8-flash`, modelo soportado y recomendado por la API.

2. **Instalación de dependencias**
   ```bash
   pip install --user deepeval google-generativeai
   ```
   - `deepeval` aporta las métricas.
   - `google-generativeai` permite conectar con Gemini.

3. **Definición de expectativas**
   - Archivo `tests_stagehand/deepeval_expectations.json` contiene, por cada prueba, una lista de frases que describen lo que *debe* aparecer en la salida del LLM.
   - Ejemplo para `F2_opportunity_probability`:
   ```json
   {
     "F2_opportunity_probability": {
       "expected": [
         "Probability set to 10% for Prospecting",
         "Probability set to 100% for Closed Won",
         "Probability set to 0% for Closed Lost",
         "Probability updated dynamically on Proposal"
       ]
     }
   }
   ```

4. **Runner de DeepEval** (`tests_stagehand/deepeval_runner.py`)
   - Ejecuta cada test Stagehand mediante `subprocess.run("tsx …")`.
   - Extrae la salida del LLM (líneas que contienen `[Stagehand]`).
   - Carga las expectativas del JSON y crea una cadena de referencia.
   - Aplica tres métricas de DeepEval:
     * `FaithfulnessMetric`
     * `AnswerRelevancyMetric`
     * `HallucinationMetric`
   - Cada métrica devuelve un `score` entre 0 y 1 (umbral 0.8 para considerar “pasado”).
   - Los resultados se agregan en un reporte markdown.

5. **Reporte**
   - El script escribe `deepeval_report.md` en la carpeta de artefactos de Antigravity (`<appDataDir>/brain/<conversation-id>/deepeval_report.md`).
   - Formato ejemplo:
   ```markdown
   ## F2_opportunity_probability
   - **Faithfulness**: 0.96
   - **Relevance**: 0.92
   - **Hallucination**: 0.99
   ```

6. **Comando npm**
   - Añadimos a `package.json` la línea:
   ```json
   "test:deepeval": "python -u tests_stagehand/deepeval_runner.py"
   ```
   - Así se ejecuta con `npm run test:deepeval` igual que los demás tests.

## Beneficios obtenidos
- **Automatización completa** de la evaluación de IA.
- Métricas objetivas que facilitan la detección de regresiones.
- Integración sin romper los tests existentes ni modificar la aplicación EspoCRM.
- Reporte centralizado y legible para todo el equipo.

---
*Este documento se generó el 2026‑11‑12 a las 21:26.*

