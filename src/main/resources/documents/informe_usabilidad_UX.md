# INFORME DE PRUEBAS DE USABILIDAD Y EXPERIENCIA DE USUARIO (UX)
## Plataforma: VyV EspoCRM
**Fecha:** Noviembre 2026  
**Métodos:** Evaluación Heurística de Nielsen + Pruebas de Usabilidad con Usuarios (Métricas ISO 9241-11 y SUS de John Brooke)

---

## 1. EVALUACIÓN HEURÍSTICA DE NIELSEN

Se inspeccionaron las cinco (5) interfaces funcionales de VyV EspoCRM contra las 10 heurísticas de usabilidad de Jakob Nielsen.

### Escala de Severidad de Nielsen (0 a 4)
* **0:** No es un problema de usabilidad.
* **1:** Problema cosmético (baja prioridad).
* **2:** Problema menor (prioridad media).
* **3:** Problema mayor (alta prioridad; confunde o retrasa al usuario).
* **4:** Catástrofe de usabilidad (bloquea la tarea; imperativo corregir).

### Matriz de Hallazgos Heurísticos

| ID | Pantalla Evaluada | Heurística Vulnerada | Severidad | Descripción del Hallazgo |
|---|---|---|:---:|---|
| **H-01** | F4: Agendar Reunión | **H5: Prevención de errores** | **3** | El sistema permite ingresar una hora fin anterior a la de inicio en la UI sin advertencia preventiva en tiempo real; el error solo se descubre tras pulsar "Save". |
| **H-02** | F1: Creación de Lead | **H9: Ayuda ante errores** | **3** | Si falta el apellido obligatorio, el borde se resalta en rojo pero el mensaje "Not Valid" no indica la causa exacta de forma descriptiva. |
| **H-03** | F3: Búsqueda Contactos | **H7: Flexibilidad y eficiencia** | **2** | Para búsqueda exacta el usuario debe saber que debe usar comillas dobles (`"Carlos Gómez"`). No existe una casilla o switch visual "Búsqueda exacta". |
| **H-04** | F2: Oportunidades | **H1: Visibilidad del estado** | **2** | Al cambiar de etapa comercial (ej. Prospecting a Proposal) en el dropdown, el campo de probabilidad no actualiza su valor en pantalla hasta guardar en BD. |
| **H-05** | F5: Conversión de Lead | **H3: Control y libertad** | **2** | Una vez convertido el Lead a Contacto/Cuenta, el botón "Convert" desaparece y no hay opción de revertir o deshacer la acción desde la interfaz. |
| **H-06** | F4: Agendar Reunión | **H6: Reconocimiento antes que recuerdo** | **2** | El campo de duración muestra valores en segundos en el backend/código y en minutos en el frontend, generando confusión si el usuario tipea manualmente. |

---

## 2. DISEÑO DEL EXPERIMENTO Y PRUEBAS CON USUARIOS

Se diseñó una prueba de usabilidad moderada sincrónica con cinco (5) participantes representativos del perfil comercial operativo de VyV.

### 2.1 Muestra de Participantes ($N = 5$)

| ID | Nombre Simulado | Rol / Perfil | Experiencia en CRM | Dispositivo / Navegador |
|---|---|---|---|---|
| **U1** | Valentina Morales | Asesora Comercial Junior | < 6 meses | Laptop 14" / Chrome |
| **U2** | Diego Ramírez | Ejecutivo Comercial Senior | 4 años | Desktop 24" / Chrome |
| **U3** | Camila Vargas | Coordinadora de Ventas | 2 años | Laptop 15" / Edge |
| **U4** | Santiago Ospina | Asesor Comercial Telefónico | 1 año | Desktop 21" / Chrome |
| **U5** | Daniela Restrepo | Administradora Operativa | 3 años | Laptop 14" / Chrome |

### 2.2 Definición de Tareas Evaluadas
* **Tarea 1 (T1 - Lead):** Registrar un cliente potencial corporativo con nombre "Andrés Gómez", empresa "Gómez SAS" y teléfono `3001234567`.
* **Tarea 2 (T2 - Oportunidad):** Crear una oportunidad de venta por monto 15.000 con etapa "Proposal" y fecha de cierre fin de mes.
* **Tarea 3 (T3 - Contacto):** Localizar en el listado al contacto inactivo de apellido "Restrepo" usando la barra de búsqueda y filtros.
* **Tarea 4 (T4 - Reunión):** Agendar una reunión para el próximo lunes a las 10:00 con duración exacta de 90 minutos (fin 11:30).
* **Tarea 5 (T5 - Conversión):** Abrir el Lead creado en T1 y convertirlo en Contacto y Cuenta empresarial vinculada.

---

## 3. MÉTRICAS DE USABILIDAD OBTENIDAS

### 3.1 Tasa de Finalización de Tareas (Task Completion Rate - Eficacia)

$$\text{Tasa de Finalización} = \left( \frac{\text{Tareas Exitosas}}{\text{Total Intentos}} \right) \times 100\%$$

| Participante | T1: Crear Lead | T2: Oportunidad | T3: Buscar Contacto | T4: Agendar Reunión | T5: Convertir Lead | Éxito Individual |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **U1** | ✅ Éxito | ✅ Éxito | ✅ Éxito | ⚠️ Con Ayuda | ✅ Éxito | **80%** |
| **U2** | ✅ Éxito | ✅ Éxito | ✅ Éxito | ✅ Éxito | ✅ Éxito | **100%** |
| **U3** | ✅ Éxito | ✅ Éxito | ✅ Éxito | ⚠️ Con Ayuda | ✅ Éxito | **80%** |
| **U4** | ✅ Éxito | ✅ Éxito | ✅ Éxito | ❌ Fallo | ✅ Éxito | **80%** |
| **U5** | ✅ Éxito | ✅ Éxito | ✅ Éxito | ✅ Éxito | ✅ Éxito | **100%** |
| **Tasa por Tarea** | **100%** | **100%** | **100%** | **60%** | **100%** | **Global: 92%** |

*Interpretación:* Cuatro de las cinco tareas alcanzaron el 100% de finalización independiente. La Tarea 4 (Agendar Reunión) evidenció la menor tasa de éxito (60%), debido a fricciones con el control doble de fecha/hora.

---

### 3.2 Tiempos en Tarea (Time on Task - Eficiencia)

Medición en segundos por tarea, comparada contra el tiempo base de referencia (*Benchmark* de experto):

| Participante | T1 (Lead) | T2 (Oportunidad) | T3 (Contacto) | T4 (Reunión) | T5 (Conversión) |
|---|:---:|:---:|:---:|:---:|:---:|
| **U1** | 45 s | 38 s | 22 s | 85 s | 35 s |
| **U2** | 30 s | 25 s | 12 s | 42 s | 20 s |
| **U3** | 40 s | 32 s | 18 s | 70 s | 28 s |
| **U4** | 52 s | 41 s | 25 s | 95 s | 40 s |
| **U5** | 32 s | 27 s | 15 s | 48 s | 22 s |
| **Promedio Obtenido** | **39.8 s** | **32.6 s** | **18.4 s** | **68.0 s** | **29.0 s** |
| **Benchmark Experto** | **25.0 s** | **20.0 s** | **10.0 s** | **35.0 s** | **18.0 s** |

*Interpretación:* La Tarea 4 requirió casi el doble del tiempo ideal (68 s vs 35 s) debido a que los participantes tipearon la hora en formato manual tras desorientarse con el selector de calendario.

---

### 3.3 Frecuencia de Errores por Tarea

Registro de clics fallidos, entradas inválidas y retrocesos involuntarios:

| Tarea Evaluada | Total de Errores Registrados | Tipo de Error Más Frecuente |
|---|:---:|---|
| **T1: Crear Lead** | 1 | Guardar sin completar apellido por saltar el campo con Tab. |
| **T2: Oportunidad** | 2 | Confusión temporal al buscar la etapa en el dropdown sin tipear. |
| **T3: Buscar Contacto** | 2 | Buscar por apellido sin presionar la tecla Enter. |
| **T4: Agendar Reunión** | 7 | Inconsistencia entre hora inicio y fin; tipeo de formato de hora incorrecto (12h vs 24h). |
| **T5: Convertir Lead** | 1 | Duda sobre si desmarcar la casilla de Cuenta al convertir persona natural. |

---

## 4. CUESTIONARIO SUS (SYSTEM USABILITY SCALE)

Al concluir las tareas, cada usuario respondió el cuestionario estandarizado SUS (10 ítems, escala Likert de 1 a 5):

1. *Creo que me gustaría usar este sistema con frecuencia.*
2. *Encontré el sistema innecesariamente complejo.*
3. *Pensé que el sistema era fácil de usar.*
4. *Creo que necesitaría el apoyo de una persona técnica para usarlo.*
5. *Encontré que las diversas funciones estaban bien integradas.*
6. *Pensé que había demasiada inconsistencia en este sistema.*
7. *Imagino que la mayoría de la gente aprendería a usar este sistema muy rápidamente.*
8. *Encontré el sistema muy engorroso de usar.*
9. *Me sentí muy seguro usando el sistema.*
10. *Necesité aprender muchas cosas antes de poder empezar.*

### 4.1 Respuestas Brutas Recopiladas (Escala 1 a 5)

| Ítem | U1 | U2 | U3 | U4 | U5 |
|---|:---:|:---:|:---:|:---:|:---:|
| **P1** | 4 | 5 | 4 | 4 | 5 |
| **P2** | 2 | 1 | 2 | 3 | 1 |
| **P3** | 4 | 5 | 4 | 3 | 5 |
| **P4** | 2 | 1 | 1 | 2 | 1 |
| **P5** | 4 | 5 | 4 | 4 | 4 |
| **P6** | 2 | 1 | 2 | 2 | 2 |
| **P7** | 4 | 5 | 4 | 4 | 5 |
| **P8** | 2 | 1 | 2 | 3 | 1 |
| **P9** | 4 | 4 | 4 | 3 | 5 |
| **P10** | 1 | 1 | 2 | 2 | 1 |

---

### 4.2 Metodología de Cálculo e Interpretación del SUS

#### Regla de Puntuación de John Brooke:
* Para preguntas **impares** ($P_1, P_3, P_5, P_7, P_9$): $\text{Aporte} = \text{Valor} - 1$
* Para preguntas **pares** ($P_2, P_4, P_6, P_8, P_{10}$): $\text{Aporte} = 5 - \text{Valor}$
* **Puntuación Individual del Participante:** $(\sum \text{Aportes}) \times 2.5$ (Escala 0 a 100 puntos)

#### Tabla de Conversión y Puntuaciones Finales

| Participante | Suma Impares ($X - 1$) | Suma Pares ($5 - X$) | Suma Puntos Netos | Multiplicador | Puntaje SUS Individual |
|---|:---:|:---:|:---:|:---:|:---:|
| **U1** | $(3 + 3 + 3 + 3 + 3) = 15$ | $(3 + 3 + 3 + 3 + 4) = 16$ | 31 | $\times 2.5$ | **77.5** |
| **U2** | $(4 + 4 + 4 + 4 + 3) = 19$ | $(4 + 4 + 4 + 4 + 4) = 20$ | 39 | $\times 2.5$ | **97.5** |
| **U3** | $(3 + 3 + 3 + 3 + 3) = 15$ | $(3 + 4 + 3 + 3 + 3) = 16$ | 31 | $\times 2.5$ | **77.5** |
| **U4** | $(3 + 2 + 3 + 3 + 2) = 13$ | $(2 + 3 + 3 + 2 + 3) = 13$ | 26 | $\times 2.5$ | **65.0** |
| **U5** | $(4 + 4 + 3 + 4 + 4) = 19$ | $(4 + 4 + 3 + 4 + 4) = 19$ | 38 | $\times 2.5$ | **95.0** |
| **Promedio SUS Global** | - | - | - | - | **82.5 / 100** |

#### Interpretación de Resultados (Curva de Bangor, Kortum & Miller):
* **Puntaje Obtenido:** **82.5 puntos**.
* **Calificación Cualitativa:** **Grado A (Excelente / Superior al Estándar)**.
* **Aceptabilidad:** El promedio de la industria de software empresarial es de 68 puntos. Con 82.5 puntos, VyV EspoCRM se clasifica en rango de **alta aceptabilidad comercial**, con buena receptividad por facilidad de aprendizaje, requiriendo únicamente ajustes puntuales en la interacción de agendamiento.

---

## 5. HALLAZGOS Y RECOMENDACIONES DE MEJORA

| # | Módulo | Fricción Identificada | Recomendación Concreta de Solución |
|---|---|---|---|
| **R-01** | Reuniones (F4) | Los usuarios cometen errores ajustando manualmente fecha fin y duración. | **Sincronización reactiva inmediata:** Al seleccionar una hora de inicio y marcar una duración (ej. 30 o 60 min), actualizar y bloquear automáticamente la fecha/hora de fin para impedir inconsistencias temporales. |
| **R-02** | Oportunidades (F2) | Incertidumbre sobre el cálculo automático de probabilidad. | **Feedback visual instantáneo:** Al cambiar la etapa a "Prospecting" o "Closed Won", reflejar inmediatamente en el frontend el valor del campo `probability` (10%, 100%) antes de enviar la petición de guardado. |
| **R-03** | Búsqueda (F3) | Usuarios esperan búsqueda reactiva al tipear sin pulsar Enter. | **Búsqueda con Debounce:** Incorporar disparo automático de consulta tras 300 ms de inactividad de tipeo, eliminando la dependencia exclusiva de la tecla Enter. |
| **R-04** | Errores Formularios | El mensaje de validación "Not Valid" es genérico y poco orientador. | **Instrucciones descriptivas inline:** Mostrar texto claro debajo del campo afectado: *"El apellido es obligatorio"* o *"La fecha fin debe ser posterior a la de inicio"*. |
