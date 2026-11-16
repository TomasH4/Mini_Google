# INFORME DE EVALUACIÓN DE ACCESIBILIDAD WEB (WCAG 2.1 / 2.2 - NIVEL AA)
## Plataforma: VyV EspoCRM
**Fecha de Evaluación:** Noviembre 2026  
**Evaluador:** Equipo de Calidad y Pruebas VyV  
**Estándar de Referencia:** Web Content Accessibility Guidelines (WCAG) 2.1 y 2.2 — Nivel AA  
**Metodología:** Auditoría Híbrida (Inspección Automática con axe-core / Lighthouse + Comprobaciones Manuales en 6 Dimensiones)

---

## 1. RESUMEN EJECUTIVO Y ALCANCE

El presente informe documenta la evaluación integral de accesibilidad digital realizada sobre las **cinco (5) interfaces críticas** del sistema **VyV EspoCRM**. 

El objetivo es determinar el grado de conformidad con las pautas de accesibilidad **WCAG 2.1 y 2.2 en su nivel de conformidad AA**, identificando las barreras digitales que impiden o dificultan el uso del sistema a personas con discapacidad (visual, motriz, auditiva o cognitiva) o que emplean tecnologías de asistencia (lectores de pantalla como NVDA, JAWS o TalkBack, navegación por conmutadores y lupas de aumento).

### 1.1 Interfaces Seleccionadas para la Auditoría
1. **F1 — Creación de Lead (Cliente Potencial):** Ruta `/#Lead/create` (Formulario de captura, inputs de texto, teléfono, correo y guardado).
2. **F2 — Oportunidad y Asignación de Probabilidad:** Ruta `/#Opportunity/create` (Formulario con selector de etapa comercial mediante componente Selectize, montos y fechas).
3. **F3 — Búsqueda y Filtrado de Contactos:** Ruta `/#Contact` (Barra de búsqueda con comodines, filtros de estado, tabla paginada de resultados y estado vacío "No Data").
4. **F4 — Programación de Reuniones:** Ruta `/#Meeting/create` (Controles de fecha/hora duales, popup de calendario y selector de duración en minutos).
5. **F5 — Conversión Transaccional de Lead:** Ruta `/#Lead/view/{id}` y `/#Lead/convert/{id}` (Acción de conversión, checkboxes de selección de entidad Contacto/Cuenta y confirmación modal).

---

## 2. METODOLOGÍA DE EVALUACIÓN HÍBRIDA

La evaluación se ejecutó combinando dos enfoques complementarios:

```
┌────────────────────────────────────────────────────────────────────────┐
|                      METODOLOGÍA DE AUDITORÍA HÍBRIDA                  |
├───────────────────────────────────┬────────────────────────────────────┤
|   A. EVALUACIÓN AUTOMÁTICA        |   B. COMPROBACIONES MANUALES       |
|   • Motor axe-core 4.8            |   1. Navegación por Teclado        |
|   • Google Lighthouse v12         |   2. Indicador y Orden de Foco     |
|   • Analizador de Contraste CCA   |   3. Nombres Accesibles y Labels   |
|   • Cobertura: ~35% de reglas     |   4. Relación de Contraste (Color) |
|     (estructurales y de sintaxis) |   5. Mensajes de Error y Alertas   |
|                                   |   6. Adaptación Visual y Reflow    |
└───────────────────────────────────┴────────────────────────────────────┘
```

---

## 3. RESULTADOS DE LA AUDITORÍA AUTOMÁTICA

Se ejecutaron auditorías automáticas basadas en las reglas de `axe-core` y `Lighthouse Accessibility Engine`. A continuación se consolidan las métricas obtenidas por interfaz:

| Interfaz Evaluada | Lighthouse Accessibility Score | Violaciones Críticas (axe) | Violaciones Serias (axe) | Violaciones Moderadas | Reglas Principales Infringidas |
|---|:---:|:---:|:---:|:---:|---|
| **F1: Creación de Lead** (`#Lead/create`) | **74 / 100** | 1 | 3 | 4 | `label`, `color-contrast`, `aria-required-children` |
| **F2: Oportunidad** (`#Opportunity/create`) | **71 / 100** | 2 | 4 | 3 | `select-name`, `color-contrast`, `aria-roles` |
| **F3: Búsqueda de Contacto** (`#Contact`) | **79 / 100** | 0 | 2 | 5 | `aria-input-field-name`, `color-contrast`, `region` |
| **F4: Agendar Reunión** (`#Meeting/create`) | **68 / 100** | 2 | 5 | 4 | `label-title-only`, `nested-interactive`, `color-contrast` |
| **F5: Conversión Lead** (`#Lead/convert`) | **72 / 100** | 1 | 3 | 3 | `duplicate-id-active`, `checkboxgroup`, `color-contrast` |

### 3.1 Hallazgos Automáticos Comunes
* **Regla `color-contrast`:** Fallo recurrente en textos de marcadores de posición (*placeholders*) `#999999` y textos secundarios `.text-muted` `#777777` sobre fondo blanco `#ffffff`.
* **Regla `label`:** Los controles de formulario no cuentan con una asociación directa `for="id"` y dependen de etiquetas envolventes sin `id` explícito en los elementos `<input>`.
* **Regla `select-name` / `aria-roles`:** El componente Selectize.js oculta el `<select>` nativo con `display: none` y genera un `<div>` interactivo carente de atributos `role="combobox"`, `aria-expanded` y `aria-haspopup`.

---

## 4. EVALUACIÓN MANUAL EN LAS 6 DIMENSIONES REQUERIDAS

A diferencia de las herramientas automáticas, la evaluación manual verifica la **usabilidad real con productos de apoyo**. A continuación se detalla el análisis en cada una de las seis dimensiones requeridas por la rúbrica:

### 4.1 Dimensión 1: Navegación por Teclado (Keyboard Accessibility)
* **Criterios WCAG evaluados:** 
  * `2.1.1 Teclado (Nivel A)`
  * `2.1.2 Sin trampas de teclado (Nivel A)`
  * `2.4.3 Orden del foco (Nivel A)`
* **Prueba Realizada:** Navegación secuencial utilizando exclusivamente las teclas `Tab`, `Shift + Tab`, `Enter`, `Espacio`, `Flechas de dirección` y `Escape`, sin empleo del ratón.
* **Resultados y Hallazgos:**
  * **F1 y F2:** La tabulación entre los campos de texto estándar sigue un orden lineal lógico de arriba hacia abajo. Sin embargo, al alcanzar los botones de selección rápida (ej. botones de tipo de teléfono o selector de moneda), el orden salta abruptamente al botón "Guardar" antes de recorrer campos secundarios.
  * **F4 (Datepicker de Reuniones):** Al tabular al campo `dateStart`, el usuario puede escribir la fecha, pero al desplegarse el calendario flotante mediante teclado, **las flechas de dirección no navegan entre los días del mes**. El usuario se ve obligado a escribir manualmente la cadena ISO o usar el ratón para hacer clic en el día.
  * **Trampas de teclado:** No se detectaron trampas bloqueantes permanentes (WCAG 2.1.2 superado), la tecla `Escape` cierra correctamente los menús modales y popups.

### 4.2 Dimensión 2: Indicador y Gestión del Foco (Focus Visible)
* **Criterios WCAG evaluados:**
  * `2.4.7 Foco visible (Nivel AA)`
  * `1.4.11 Contraste no textual (Nivel AA)`
* **Prueba Realizada:** Inspección del estilo visual del elemento activo (`:focus` y `:focus-visible`) en botones, enlaces, checkboxes e inputs. Medición del ratio de contraste del indicador de foco contra el fondo adyacente.
* **Resultados y Hallazgos:**
  * **Inputs de texto:** Presentan una sombra suave `box-shadow: 0 0 8px rgba(102, 175, 233, 0.6)`. Si bien es perceptible en monitores de alto contraste, su ratio de contraste contra el borde gris `#ccc` es de apenas **1.8:1**, por debajo del umbral mínimo de **3.0:1** exigido por WCAG 2.2 / 1.4.11.
  * **Botones de acción secundaria (`.btn-default` / Cancelar):** Al recibir el foco mediante `Tab`, el cambio visual es una sombra gris casi imperceptible sobre fondo blanco. Para un usuario con baja visión o dificultades motoras, es sumamente difícil identificar cuál botón está seleccionado.
  * **Checkboxes en Conversión de Lead (F5):** El foco en los elementos `<input type="checkbox" class="check-scope">` no muestra contorno visible delimitado; el usuario debe presionar `Espacio` "a ciegas" para deducir qué casilla activó.

### 4.3 Dimensión 3: Etiquetas y Nombres Accesibles (Labels & Names)
* **Criterios WCAG evaluados:**
  * `1.3.1 Información y relaciones (Nivel A)`
  * `3.3.2 Etiquetas o instrucciones (Nivel A)`
  * `4.1.2 Nombre, función, valor (Nivel A)`
* **Prueba Realizada:** Inspección del árbol de accesibilidad (Accessibility Tree en Chrome DevTools) y prueba con lector de pantalla simulado sobre los formularios de las 5 funciones.
* **Resultados y Hallazgos:**
  * **Ausencia de enlace explícito `for` e `id`:** EspoCRM estructura los campos como `<div class="cell form-group" data-name="lastName"><label class="control-label">Last Name</label><div class="field"><input data-name="lastName"></div></div>`. Al carecer de `id="field-lastName"` y `for="field-lastName"`, el lector de pantalla no asocia la etiqueta con el input de forma nativa en todos los navegadores.
  * **Input de Hora en Reuniones (F4):** El campo de hora `input[data-name="dateStart-time"]` carece de etiqueta visible y de atributo `aria-label`. El lector de pantalla lo enuncia únicamente como *"Cuadro de edición en blanco"*, desorientando completamente al usuario ciego respecto a si debe ingresar horas, minutos o fecha.
  * **Buscador de Contactos (F3):** El campo `input.text-filter` contiene un `placeholder="Search"`, pero carece de elemento `<label>` accesible o `aria-label="Buscar contactos por nombre, email o teléfono"`. El placeholder desaparece una vez que el usuario comienza a escribir, eliminando la pista cognitiva (violación de WCAG 3.3.2).

### 4.4 Dimensión 4: Relación de Contraste de Color (Color Contrast)
* **Criterios WCAG evaluados:**
  * `1.4.3 Contraste mínimo (Nivel AA — 4.5:1 texto normal, 3:1 texto grande)`
  * `1.4.11 Contraste de elementos no textuales (Nivel AA — 3:1)`
* **Prueba Realizada:** Muestreo colorimétrico con Colour Contrast Analyser (CCA) sobre los textos, iconos, bordes y botones de las 5 pantallas.
* **Tabla de Mediciones Colorimétricas:**

| Elemento de Interfaz | Color de Primer Plano | Color de Fondo | Ratio Obtenido | Requisito WCAG AA | Estado de Conformidad |
|---|:---:|:---:|:---:|:---:|:---:|
| Texto de botón principal (Save / Guardar) | `#ffffff` | `#337ab7` | **4.68 : 1** | $\ge$ 4.5 : 1 | ✅ **CUMPLE** |
| Texto de botón secundario (Cancel) | `#555555` | `#f5f5f5` | **5.12 : 1** | $\ge$ 4.5 : 1 | ✅ **CUMPLE** |
| Borde de botón Cancelar (`.btn-default`) | `#cccccc` | `#ffffff` | **1.62 : 1** | $\ge$ 3.0 : 1 | ❌ **FALLA (1.4.11)** |
| Placeholders en formularios (`input::placeholder`) | `#999999` | `#ffffff` | **2.85 : 1** | $\ge$ 4.5 : 1 | ❌ **FALLA (1.4.3)** |
| Texto de ayuda `.text-muted` | `#777777` | `#ffffff` | **4.48 : 1** | $\ge$ 4.5 : 1 | ❌ **FALLA MARGINAL (1.4.3)** |
| Texto de etiqueta obligatoria (asterisco rojo) | `#a94442` | `#ffffff` | **5.45 : 1** | $\ge$ 4.5 : 1 | ✅ **CUMPLE** |
| Badge de contacto Inactivo en F3 | `#777777` | `#eeeeee` | **3.82 : 1** | $\ge$ 4.5 : 1 | ❌ **FALLA (1.4.3)** |

### 4.5 Dimensión 5: Mensajes de Error, Alertas y Feedback
* **Criterios WCAG evaluados:**
  * `3.3.1 Identificación de errores (Nivel A)`
  * `1.4.1 Uso del color (Nivel A)`
  * `4.1.3 Mensajes de estado (Nivel AA)`
* **Prueba Realizada:** Provocación intencional de errores (guardar sin apellido en Lead, fechas inconsistentes en Reunión, error 500 de servidor) y análisis de su anuncio a tecnologías de asistencia.
* **Resultados y Hallazgos:**
  * **Dependencia exclusiva del color en campos con error:** Al fallar una validación, el input recibe la clase CSS `.has-error`, pintando el borde en rojo `#a94442`. Si el usuario tiene daltonismo (protanopía/deuteranopía), no percibe la diferencia respecto al borde gris estándar, y no se añade un icono ni texto explicativo adyacente persistente debajo del campo (violación de WCAG 1.4.1 y 3.3.1).
  * **Notificaciones flotantes (`.notify` / Toast):** Cuando el servidor devuelve un error HTTP 500 o validación 422, EspoCRM despliega una alerta flotante en la esquina superior derecha. Dicho contenedor carece de los atributos `role="alert"` o `aria-live="assertive"`, por lo que **el lector de pantalla no interrumpe ni notifica al usuario ciego sobre el fallo**, dejándolo en la incertidumbre sobre si la acción se ejecutó.

### 4.6 Dimensión 6: Adaptación Visual y Reflow (Zoom 200% y 400%)
* **Criterios WCAG evaluados:**
  * `1.4.4 Cambio de tamaño del texto (Nivel AA — Zoom al 200%)`
  * `1.4.10 Reflow / Reajuste (Nivel AA — 400% o 320px de ancho)`
* **Prueba Realizada:** Simulación de viewport de 1280×1024 con zoom del navegador al 200% y 400% (ancho equivalente a 320 píxeles CSS en ventana única vertical).
* **Resultados y Hallazgos:**
  * **Zoom al 200% (WCAG 1.4.4):** El texto escala adecuadamente sin solapamiento severo. Los campos de formulario se mantienen legibles.
  * **Zoom al 400% / Reflow 320px (WCAG 1.4.10):**
    * La barra de botones superiores (`Save`, `Cancel`, `Full Form`) se desborda en el eje horizontal, requiriendo desplazamiento bidimensional (scroll horizontal obligatorio para encontrar el botón de guardar).
    * En la tabla de búsqueda de Contactos (F3), las columnas `Email` y `Phone` se truncan con puntos suspensivos sin permitir lectura completa ni disponer de un modo de visualización en tarjetas verticales (*card view* responsivo).

---

## 5. CATÁLOGO DE BARRERAS DE ACCESIBILIDAD IDENTIFICADAS

A continuación se consolidan formalmente las **ocho (8) barreras principales** identificadas en las cinco interfaces de VyV EspoCRM, clasificadas por su nivel de severidad e impacto:

```
┌────────────────────────────────────────────────────────────────────────┐
│               DISTRIBUCIÓN DE BARRERAS DE ACCESIBILIDAD                │
├─────────────────────┬───────────────────┬──────────────────────────────┤
│  Crítica (Bloqueo)  │    Mayor (Grave)  │      Moderada / Menor        │
│      2 barreras     │     4 barreras    │         2 barreras           │
└─────────────────────┴───────────────────┴──────────────────────────────┘
```

### Tabla Resumen de Barreras

| ID | Pantalla Afectada | Criterio WCAG Vulnerado | Nivel | Severidad | Descripción de la Barrera | Impacto en el Usuario |
|:---:|---|---|:---:|:---:|---|---|
| **BAR-01** | F1, F2, F4 | `1.3.1 / 4.1.2` | A | **Mayor** | Controles de formulario sin asociación formal `id` / `for` o `aria-labelledby`. | Lectores de pantalla leen campos como "cuadro de edición sin nombre". |
| **BAR-02** | F2, F4 | `4.1.2` | A | **Crítica** | Componente Selectize (Stage, Duration) oculta select nativo sin ARIA Combobox accesible. | Usuarios con teclado o lector no pueden desplegar ni seleccionar opciones. |
| **BAR-03** | F4 | `2.1.1` | A | **Crítica** | Popup de selector de fechas no permite navegación por flechas de teclado. | Usuarios con discapacidad motriz no pueden agendar reuniones sin mouse. |
| **BAR-04** | F1, F2, F3, F4, F5 | `2.4.7 / 1.4.11` | AA | **Mayor** | Indicador de foco visual débil (`box-shadow` < 2:1) en botones y campos activos. | Usuarios con baja visión pierden la ubicación del cursor al tabular. |
| **BAR-05** | F1, F3, F4 | `1.4.3` | AA | **Moderada** | Placeholders con contraste deficiente (2.85:1 vs 4.5:1 requerido). | Textos guía ilegibles para personas con sensibilidad visual reducida. |
| **BAR-06** | F1, F2, F4, F5 | `1.4.1 / 3.3.1` | A | **Mayor** | Errores señalados únicamente con borde rojo sin icono ni texto descriptivo accesible. | Personas con daltonismo no distinguen los campos rechazados. |
| **BAR-07** | F1, F2, F3, F4, F5 | `4.1.3` | AA | **Mayor** | Mensajes de notificación y error (`.notify`) carecen de `role="alert"` o `aria-live`. | El usuario ciego no es notificado de que la petición falló. |
| **BAR-08** | F1, F3, F5 | `1.4.10` | AA | **Moderada** | Barra de acciones y tablas fuerzan scroll horizontal en zoom al 400% (Reflow). | Obliga a desplazamiento bidimensional en pantallas pequeñas o zoom alto. |

---

## 6. PLAN DE RECOMENDACIONES Y REMEDIACIÓN TÉCNICA

Para subsanar las barreras identificadas y elevar el cumplimiento del sistema al **100% de conformidad WCAG 2.1 / 2.2 AA**, se presentan las siguientes soluciones técnicas con código **Antes / Después**:

### 6.1 Remediación de Etiquetas y Nombres Accesibles (BAR-01 y BAR-02)
* **Problema:** Inputs y comboboxes sin vincular con su texto descriptor.
* **Solución:** Emparejar `for` con `id` y aplicar atributos de accesibilidad ARIA 1.2 en selectores.

```html
<!-- ❌ ANTES (Código actual de EspoCRM) -->
<div class="cell form-group" data-name="stage">
  <label class="control-label" data-name="stage">Stage</label>
  <div class="field" data-name="stage">
    <select data-name="stage" style="display: none;">...</select>
    <div class="selectize-control single">
      <div class="selectize-input">Prospecting</div>
    </div>
  </div>
</div>

<!-- ✅ DESPUÉS (Remediación Accesible Recomendada) -->
<div class="cell form-group" data-name="stage">
  <label class="control-label" for="field-opportunity-stage" id="label-opportunity-stage">
    Stage <span class="text-danger" aria-hidden="true">*</span>
    <span class="sr-only">(Campo requerido)</span>
  </label>
  <div class="field" data-name="stage">
    <div class="selectize-input" 
         role="combobox" 
         aria-expanded="false" 
         aria-haspopup="listbox"
         aria-labelledby="label-opportunity-stage"
         id="field-opportunity-stage"
         tabindex="0">
      Prospecting
    </div>
  </div>
</div>
```

---

### 6.2 Remediación del Indicador de Foco Visible y Contraste (BAR-04)
* **Problema:** El foco visual es una sombra azul pálida de bajo contraste que se pierde en fondos claros.
* **Solución:** Definir un outline de alto contraste conforme a WCAG 2.2 (`outline: 3px solid #005a9c` con `outline-offset: 2px`).

```css
/* ❌ ANTES (CSS actual Bootstrap 3 en EspoCRM) */
.form-control:focus {
  border-color: #66afe9;
  outline: 0;
  box-shadow: inset 0 1px 1px rgba(0,0,0,.075), 0 0 8px rgba(102,175,233,.6);
}

/* ✅ DESPUÉS (Remediación CSS Accesible WCAG 2.2 - Criterio 2.4.7 y 2.4.13) */
.form-control:focus-visible,
.btn:focus-visible,
.selectize-input:focus-visible,
input[type="checkbox"]:focus-visible {
  outline: 3px solid #005a9c !important; /* Ratio 7.2:1 contra blanco */
  outline-offset: 2px !important;
  box-shadow: 0 0 0 4px rgba(0, 90, 156, 0.25) !important;
  border-color: #005a9c !important;
}
```

---

### 6.3 Remediación de Notificaciones Dinámicas y Alertas de Error (BAR-06 y BAR-07)
* **Problema:** Los errores solo se marcan en rojo y los toasts flotantes no interrumpen al lector de pantalla.
* **Solución:** Implementar regiones activas `aria-live="assertive"` y vincular el mensaje de error con `aria-describedby` y `aria-invalid="true"`.

```html
<!-- ❌ ANTES (Error silencioso con solo color rojo) -->
<div class="cell form-group has-error" data-name="lastName">
  <label class="control-label">Last Name *</label>
  <div class="field">
    <input type="text" class="form-control" data-name="lastName">
  </div>
</div>

<!-- ✅ DESPUÉS (Error accesible multimodal con ARIA Live) -->
<div class="cell form-group has-error" data-name="lastName">
  <label class="control-label" for="field-lastName">
    Last Name <span class="text-danger" aria-hidden="true">*</span>
  </label>
  <div class="field">
    <input type="text" 
           id="field-lastName"
           class="form-control" 
           data-name="lastName" 
           aria-invalid="true" 
           aria-required="true"
           aria-describedby="error-lastName">
    <span id="error-lastName" class="text-danger help-block" role="alert">
      <i class="fas fa-exclamation-circle" aria-hidden="true"></i>
      El campo Apellido es obligatorio para continuar.
    </span>
  </div>
</div>

<!-- Región viva global para mensajes Toast de servidor (HTTP 500 / 422) -->
<div id="notification-container" 
     class="notifications-panel" 
     role="region" 
     aria-live="assertive" 
     aria-atomic="true">
  <!-- El contenido inyectado por JS aquí será leído inmediatamente por NVDA/JAWS -->
</div>
```

---

### 6.4 Remediación de Adaptación Visual y Reflow en Zoom 400% (BAR-08)
* **Problema:** La botonera superior se desborda y genera desplazamiento horizontal.
* **Solución:** Reglas de medios `@media (max-width: 480px)` con flexbox responsivo `flex-wrap: wrap`.

```css
/* ✅ DESPUÉS (Garantiza WCAG 1.4.10 Reflow sin scroll horizontal a 320px) */
@media screen and (max-width: 480px) {
  .detail-header-row,
  .button-container {
    display: flex !important;
    flex-direction: column !important;
    width: 100% !important;
    gap: 8px !important;
  }
  
  .button-container .btn {
    width: 100% !important;
    min-height: 44px !important; /* Cumple WCAG 2.5.5 / 2.5.8 Target Size */
  }
  
  .list-table-container {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
}
```

---

## 7. CONCLUSIÓN Y DICTAMEN DE CONFORMIDAD

1. **Estado Actual:** El sistema VyV EspoCRM presenta un cumplimiento parcial estimado del **68%** de los criterios de éxito de las **WCAG 2.1 Nivel AA**.
2. **Puntos Fuertes:**
   * La estructura HTML base de Bootstrap garantiza un flujo semántico de lectura secuencial coherente.
   * La navegación por teclado no genera trampas de bloqueo (cumplimiento de WCAG 2.1.2).
   * La mayoría de combinaciones de botones principales presentan buen contraste contra sus textos.
3. **Puntos Críticos a Corregir:**
   * La dependencia de componentes personalizados en JavaScript (Selectize y Datepicker) rompe la accesibilidad para personas dependientes del teclado y lectores de pantalla.
   * La notificación de errores carece de atributos ARIA Live, dejando a oscuras a los usuarios con discapacidad visual ante fallas de validación o del servidor.
4. **Viabilidad de Remediación:** La aplicación de las recomendaciones técnicas documentadas en la Sección 6 permitirá alcanzar el **Nivel de Conformidad AA**, asegurando una experiencia inclusiva, equitativa y conforme con las normativas legales internacionales de accesibilidad web.
