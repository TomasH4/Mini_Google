# INFORME TÉCNICO DE PRUEBAS DE SEGURIDAD
## Plataforma: VyV EspoCRM
**Fecha:** Noviembre 2026  
**Alcance:** 5 Funcionalidades Principales (Lead, Opportunity, Contact, Meeting, Lead Conversion)  
**Marco de Referencia:** OWASP Top 10  
**Técnicas Aplicadas:** SAST (Análisis Estático) + DAST (Análisis Dinámico) + SCA (Revisión de Dependencias)

---

## 1. RESUMEN EJECUTIVO Y ALCANCE

Se realizó una auditoría de seguridad integral sobre la plataforma **VyV EspoCRM** combinando tres enfoques:
1. **SAST (Static Application Security Testing):** Auditoría del código fuente de dominio PHP (`Espo\VyV`) y controladores.
2. **DAST (Dynamic Application Security Testing):** Pruebas activas contra endpoints REST y la UI (`http://localhost:8080`).
3. **SCA (Software Composition Analysis):** Análisis de vulnerabilidades conocidas (CVE) en dependencias PHP (Composer) y JavaScript (npm).

---

## 2. HERRAMIENTAS COMPLEMENTARIAS UTILIZADAS Y MODO DE EMPLEO

En cumplimiento de los requerimientos de evaluación y como alternativa a SonarQube, se implementó un conjunto de herramientas complementarias especializadas para cada capa de la arquitectura:

```
┌────────────────────────────────────────────────────────────────────────┐
│             HERRAMIENTAS COMPLEMENTARIAS DE SEGURIDAD                  │
├─────────────────────┬───────────────────┬──────────────────────────────┤
│  1. Composer Audit  │   2. NPM Audit    │ 3. DAST Fuzzer & HTTP Engine │
│   (SCA Backend PHP) │ (SCA Frontend JS) │  (Inyección Activa / Headers)│
└─────────────────────┴───────────────────┴──────────────────────────────┘
```

### 2.1 `composer audit` (SCA para Backend PHP)
* **Propósito:** Análisis automatizado de vulnerabilidades en el árbol de dependencias de PHP.
* **Modo de empleo:** Ejecutado directamente dentro del contenedor oficial de producción (`docker exec espocrm composer audit`) analizando el archivo de bloqueo `composer.lock`. Contrasta las versiones instaladas contra la base de datos oficial de seguridad de Packagist y GitHub Advisory Database.

### 2.2 `npm audit` (SCA para Frontend y Tooling JS)
* **Propósito:** Análisis de composición de software para dependencias de cliente y herramientas de prueba (Cypress, preprocesadores, Selectize).
* **Modo de empleo:** Ejecutado en el entorno de desarrollo (`npm audit --prefix espocrm_VyV`) inspeccionando `package-lock.json` y el directorio `node_modules` contra la base de datos de vulnerabilidades de npm.

### 2.3 Cliente REST Automatizado / Fuzzer Dinámico (DAST Activo)
* **Propósito:** Pruebas dinámicas de penetración de caja negra sobre la aplicación en ejecución.
* **Modo de empleo:** Se desarrollaron scripts de inyección activa ejecutados vía HTTP hacia los endpoints REST (`http://localhost:8080/api/v1/`). Se enviaron cadenas de prueba para validar defensas contra:
  * **Inyección SQL (SQLi):** Envío de cargas útiles booleanas y de unión (`' OR '1'='1`) en parámetros de filtrado.
  * **Cross-Site Scripting (XSS):** Envío de etiquetas ejecutables (`<script>alert(1)</script>`, `<img src=x onerror=...>`) en formularios de creación de Leads.

### 2.4 Inspector de Cabeceras HTTP de Seguridad (DAST de Servidor Web)
* **Propósito:** Auditoría de configuración de seguridad de transporte y protección del navegador.
* **Modo de empleo:** Inspección de respuestas HTTP del servidor Apache mediante solicitudes `HEAD` y `GET` directas para verificar la presencia, ausencia y rigidez de directivas de defensa en profundidad (`Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security`).

---

## 3. REVISIÓN DE DEPENDENCIAS (SCA — SOFTWARE COMPOSITION ANALYSIS)

### 2.1 Dependencias Backend (`composer audit`)
Ejecutado sobre `composer.lock` en el contenedor `espocrm`. Se identificaron **9 avisos de seguridad que afectan a 5 paquetes**:

| Paquete | Versión Afectada | CVE / Advisory | Severidad | Descripción de la Vulnerabilidad |
|---|---|---|:---:|---|
| `phpseclib/phpseclib` | `<3.0.58` | PKSA-h7cz-q7zq-mdkt | **Alta** | Recuento de iteraciones PBKDF ilimitado en claves cifradas; permite DoS por agotamiento de CPU. |
| `symfony/http-client` | `<6.4.50` | CVE-2026-121971 | **Media** | Fuga de credenciales de autenticación hacia otros orígenes mediante referencias de ruta de red. |
| `symfony/http-client` | `<6.4.50` | CVE-2026-123119 | **Media** | Rebinding DNS no fija la IP de hosts en caché, permitiendo enumeración de puertos internos (SSRF). |
| `symfony/http-foundation` | `<6.4.50` | CVE-2026-118514 | **Media** | Solicitudes HTTP HEAD evaden el control de acceso de métodos de seguridad (`security.access_control`). |
| `symfony/http-foundation` | `<6.4.50` | PKSA-b2gg-vk1g-6d5q | **Media** | Inyección de cabecera Host permite que URLs generadas apunten a dominios controlados por atacantes. |
| `symfony/mime` | `<6.4.49` | CVE-2026-45070 | **Media** | Inyección de encabezados de correo electrónico mediante caracteres no-token en parámetros MIME. |
| `symfony/routing` | `<6.4.50` | CVE-2026-118516 | **Media** | Marcadores de posición de host coinciden con rutas en matchers compilados, eludiendo control de acceso. |

### 2.2 Dependencias Frontend (`npm audit`)
Ejecutado sobre `package.json` / `node_modules` de EspoCRM. Se identificaron **19 vulnerabilidades (5 Altas, 12 Moderadas, 2 Bajas)**:

| Paquete | Severidad | Advisory ID | Impacto |
|---|:---:|---|---|
| `basic-ftp` | **Alta** | GHSA-c475-qrg2-pj4r | DoS de CPU por tiempo cuadrático en parser de listado Unix (ReDoS). |
| `deepmerge` | **Moderada** | GHSA-gcqx-w48m-w2h4 | *Prototype Pollution* en `mergeObject()` permite inyección en prototipos de objetos. |
| `csv-parse` (en Selectize) | **Moderada** | GHSA-8cw4-87c7-c6xx | Reemplazo de prototipo accesible mediante la ruta de columnas. |
| `glob` / `minimatch` | **Moderada** | GHSA-3g3m-2fjr-6pw4 | Evasión de cancelación de `AbortSignal` y traversal en sistema de archivos. |

---

## 4. ANÁLISIS ESTÁTICO DE CÓDIGO (SAST)

Se inspeccionaron las clases de dominio desarrolladas en `application/Espo/VyV/`:

### Hallazgos de Buenas Prácticas Implementadas:
* **Tipado Estricto:** Uso de `declare(strict_types=1)` en todas las clases, previniendo errores de coerción de tipos y desbordamientos.
* **Separación de Capas:** Repositorios desacoplados mediante interfaces (`LeadRepository`, `OpportunityRepository`, etc.), evitando concatenación cruda de SQL en la capa de negocio.
* **Validación de Entradas:** En `LeadCreator.php` se implementa `filter_var($email, FILTER_VALIDATE_EMAIL)` y en `ContactSearcher.php` se acota defensivamente el término con `mb_substr($rawQuery, 0, 100)` para prevenir sobrecargas.
* **Transaccionalidad ACID:** En `LeadConverter.php` se encierran las operaciones múltiples dentro de `beginTransaction()`, `commit()` y `rollback()`, evitando estados inconsistentes o registros huérfanos ante fallos.

### Hallazgos de Riesgo Detectados (SAST):
* **Falta de Validación de Formato Telefónico en Backend:** `LeadCreator` valida únicamente longitud no vacía para `phoneNumber`, dejando la validación de formato (ej. 10 dígitos) solo a nivel de frontend, lo que permite inyectar caracteres no numéricos vía API directa.

---

## 5. ANÁLISIS DINÁMICO DE SEGURIDAD (DAST)

Se ejecutaron pruebas dinámicas contra la instancia en ejecución (`http://localhost:8080`):

### 5.1 Prueba de Inyección SQL (OWASP A03: Injection)
* **Objetivo:** Determinar si los parámetros de búsqueda o filtrado concatenan entradas sin parametrizar.
* **Vector Inyectado:** `' OR '1'='1` en el parámetro de consulta `where[0][value]` hacia `/api/v1/Contact`.
* **Resultado:** La API retornó `total: 0`. El ORM de EspoCRM utilizó sentencias preparadas (Prepared Statements) con enlace de parámetros (`PDO::PARAM_STR`), neutralizando el ataque.

### 5.2 Prueba de Cross-Site Scripting (XSS - OWASP A03: Injection)
* **Objetivo:** Inyectar scripts ejecutables en los campos de captura de Leads.
* **Vector Inyectado:** `{"firstName": "<script>alert('XSS')</script>", "lastName": "<img src=x onerror=alert(1)>"}`.
* **Resultado:** La API bloqueó la petición con código **HTTP 400** y mensaje:
  ```json
  {"messageTranslation":{"label":"validationFailure","scope":null,"data":{"field":"firstName","type":"pattern"}}}
  ```
  El validador de patrón regex rechaza etiquetas HTML antes de tocar la base de datos.

### 5.3 Análisis de Cabeceras HTTP de Seguridad (OWASP A05: Security Misconfiguration)
Inspección de las cabeceras de respuesta HTTP del servidor Apache:

| Cabecera de Seguridad | Estado en Servidor | Evaluación de Riesgo |
|---|:---:|---|
| `X-Content-Type-Options: nosniff` | ✅ **Presente** | Protege contra ataques de confusión de tipos MIME. |
| `Content-Security-Policy (CSP)` | ⚠️ **Débil** | Presente pero incluye `'unsafe-eval'`, lo que reduce la protección contra XSS avanzado. |
| `Strict-Transport-Security (HSTS)` | ❌ **Ausente** | No implementada (el entorno opera en HTTP plano sin redirección forzada a HTTPS). |
| `X-Frame-Options` / `frame-ancestors` | ❌ **Ausente** | La plataforma es susceptible de ser incrustada en un `<iframe>` externo (**Clickjacking**). |
| `Referrer-Policy` | ❌ **Ausente** | Puede filtrar URLs y tokens en encabezados Referer a sitios de terceros. |

---

## 6. MATRIZ DE RIESGOS OWASP TOP 10 (2021)

Alineación de los hallazgos con el estándar OWASP:

| Categoría OWASP | Estado en VyV EspoCRM | Severidad | Evidencia / Hallazgo Principal |
|---|:---:|:---:|---|
| **A01: Broken Access Control** | Vulnerable | **Media** | Falla CVE-2026-118514 en `symfony/http-foundation` permite que solicitudes HTTP HEAD omitan filtros de control de acceso. |
| **A02: Cryptographic Failures** | Vulnerable | **Alta** | Operación en HTTP no cifrado y vulnerabilidad DoS en `phpseclib` por iteraciones ilimitadas en claves cifradas. |
| **A03: Injection** | Mitigado | **Baja** | Consultas parametrizadas (anti-SQLi) y filtros de regex en nombres (anti-XSS) validados exitosamente en DAST. |
| **A04: Insecure Design** | Mitigado | **Baja** | Transacciones atómicas en conversión de leads y prevención de doble conversión con código 409. |
| **A05: Security Misconfiguration** | Vulnerable | **Media** | Ausencia de cabeceras `X-Frame-Options` (Clickjacking), HSTS y uso de `'unsafe-eval'` en CSP. |
| **A06: Vulnerable and Outdated Components** | Vulnerable | **Alta** | 9 avisos activos en Composer (Symfony, phpseclib) y 19 en npm (`basic-ftp`, `deepmerge`, `csv-parse`). |
| **A07: Identification and Authentication Failures** | Mitigado | **Baja** | Autenticación requerida vía API; retorno estricto de 401 en peticiones no firmadas. |
| **A08: Software and Data Integrity Failures** | Vulnerable | **Media** | Prototipos contaminables (*Prototype Pollution*) detectados en dependencias frontend `deepmerge` y `csv-parse`. |
| **A09: Security Logging and Monitoring Failures** | Aceptable | **Baja** | Registro en logs de Apache y en logs de EspoCRM de accesos y errores 400/500. |
| **A10: Server-Side Request Forgery (SSRF)** | Vulnerable | **Media** | Falla CVE-2026-123119 en `symfony/http-client` permite evadir comprobación de IPs internas por rebinding DNS. |

---

## 7. PLAN DE REMEDIACIÓN Y RECOMENDACIONES TÉCNICAS

### 7.1 Actualización de Dependencias Vulnerables (SCA)
1. **Actualizar Symfony y phpseclib:**
   ```bash
   composer update symfony/http-client symfony/http-foundation symfony/mime symfony/routing phpseclib/phpseclib --with-dependencies
   ```
2. **Actualizar dependencias de desarrollo y frontend:**
   ```bash
   npm audit fix
   ```

### 7.2 Fortalecimiento de Cabeceras HTTP en Apache (`.htaccess` / VirtualHost)
Incorporar las siguientes directivas para mitigar Clickjacking, degradación de protocolo y XSS:

```apache
# Mitigacion de Clickjacking
Header always set X-Frame-Options "SAMEORIGIN"

# Politica de referencia segura
Header always set Referrer-Policy "strict-origin-when-cross-origin"

# En caso de desplegar con certificado SSL:
# Header always set Strict-Transport-Security "max-age=31536000; includeSubDomains"
```

### 7.3 Restricción de Content Security Policy (CSP)
Eliminar la directiva `'unsafe-eval'` del CSP de EspoCRM reemplazándola por ejecución de scripts estáticos empaquetados o con nonces criptográficos únicos por petición.

