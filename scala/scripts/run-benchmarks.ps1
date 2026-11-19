<#
.SYNOPSIS
  Ejecuta todos los escenarios de benchmark de la implementación Scala.

.DESCRIPTION
  1. Escenario "sample": los 11 documentos reales del repositorio.
  2. Escenarios sintéticos (por defecto 1.000, 10.000 y 50.000 documentos). Si el corpus no existe, lo genera
     con la semilla indicada (misma semilla = mismo corpus en cualquier máquina).

  Cada escenario corre en una JVM nueva con el heap fijo (-Xms = -Xmx) para que las condiciones sean iguales.
  Los resultados se agregan a bench-results/results.csv y el top-10 de cada consulta queda en
  bench-results/<escenario>-results.tsv.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/run-benchmarks.ps1
  powershell -ExecutionPolicy Bypass -File scripts/run-benchmarks.ps1 -Sizes 1000,10000 -Heap 4g
#>
param(
    [int[]]$Sizes = @(1000, 10000, 50000),
    [string]$Heap = "2g",
    [long]$Seed = 42,
    [int]$Queries = 200,
    [int]$Warmup = 3,
    [int]$Runs = 10,
    [int]$IndexRuns = 5
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "target\mini-google-scala-1.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    throw "No existe $jar. Compila primero con: mvn -f scala/pom.xml clean package"
}

$data = Join-Path $root "bench-data"
$results = Join-Path $root "bench-results"
New-Item -ItemType Directory -Force $data, $results | Out-Null
$csv = Join-Path $results "results.csv"
$jvm = @("-Xms$Heap", "-Xmx$Heap", "-XX:+UseG1GC")
$common = @("--warmup", $Warmup, "--runs", $Runs, "--index-runs", $IndexRuns, "--out", $csv)

# Usa el JDK de JAVA_HOME si está definido (debe ser el mismo JDK 21 que usa la versión Java).
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
& $java -version

function Invoke-Java([string[]]$arguments) {
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw "java terminó con código $LASTEXITCODE" }
}

Write-Host "== Escenario sample (11 documentos reales)"
Invoke-Java ($jvm + @("-jar", $jar, "bench", "--scenario", "sample",
    "--queries", (Join-Path $root "queries\sample-queries.txt"),
    "--dump", (Join-Path $results "sample-results.tsv")) + $common)

foreach ($n in $Sizes) {
    $corpus = Join-Path $data "corpus-$n.tsv"
    $queryFile = Join-Path $data "queries-$n.txt"
    if (-not (Test-Path $corpus) -or -not (Test-Path $queryFile)) {
        Write-Host "== Generando corpus de $n documentos (semilla $Seed)"
        Invoke-Java @("-jar", $jar, "generate", "--docs", $n, "--seed", $Seed, "--out", $corpus,
            "--queries", $Queries, "--queries-out", $queryFile)
    }
    Write-Host "== Escenario synthetic-$n"
    Invoke-Java ($jvm + @("-jar", $jar, "bench", "--scenario", "synthetic-$n",
        "--corpus", $corpus, "--queries", $queryFile,
        "--dump", (Join-Path $results "synthetic-$n-results.tsv")) + $common)
}

Write-Host "Listo. Resultados en $csv"
