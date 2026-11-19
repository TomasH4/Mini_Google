package minigoogle.bench

import minigoogle.Cli
import minigoogle.index.InvertedIndex
import minigoogle.loader.{Corpus, CorpusLoader}
import minigoogle.query.QueryParser
import minigoogle.search.SearchEngine
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.Locale
import scala.jdk.CollectionConverters.*
import scala.util.Try

final case class BenchConfig(
    scenario: String,
    corpus: Option[Path],
    docsDir: Option[Path],
    queries: Option[Path],
    warmup: Int,
    runs: Int,
    indexRuns: Int,
    out: Path,
    dump: Option[Path]
)

/** Una construcción del índice: tiempo, memoria retenida y actividad del GC durante la construcción. */
final case class BuildSample(millis: Double, retainedBytes: Long, gc: GcSnapshot)

final case class BenchReport(
    scenario: String,
    documents: Int,
    vocabulary: Int,
    tokens: Long,
    queries: Int,
    invalidQueries: Int,
    matchedDocs: Long,
    loadMs: Double,
    builds: Seq[BuildSample],
    heapPeakBytes: Long,
    latency: LatencyStats,
    throughputQps: Double,
    timestamp: Instant
):
  /** Columnas del CSV en orden. El mismo formato sirve para las otras implementaciones (ver README). */
  def columns: List[(String, String)] =
    val buildMillis = builds.map(_.millis)
    List(
      "language" -> "scala",
      "scenario" -> scenario,
      "documents" -> documents.toString,
      "vocabulary" -> vocabulary.toString,
      "tokens" -> tokens.toString,
      "queries" -> queries.toString,
      "invalid_queries" -> invalidQueries.toString,
      "matched_docs" -> matchedDocs.toString,
      "load_ms" -> BenchReport.decimal(loadMs),
      "index_ms_median" -> BenchReport.decimal(Stats.median(buildMillis)),
      "index_ms_min" -> BenchReport.decimal(buildMillis.min),
      "index_ms_max" -> BenchReport.decimal(buildMillis.max),
      "index_gc_count" -> builds.last.gc.collections.toString,
      "index_gc_ms" -> builds.last.gc.millis.toString,
      "heap_index_mb" -> BenchReport.decimal(BenchReport.megabytes(builds.last.retainedBytes)),
      "heap_peak_mb" -> BenchReport.decimal(BenchReport.megabytes(heapPeakBytes)),
      "query_mean_ms" -> BenchReport.decimal(latency.meanMs),
      "query_p50_ms" -> BenchReport.decimal(latency.p50Ms),
      "query_p95_ms" -> BenchReport.decimal(latency.p95Ms),
      "query_p99_ms" -> BenchReport.decimal(latency.p99Ms),
      "query_max_ms" -> BenchReport.decimal(latency.maxMs),
      "throughput_qps" -> BenchReport.decimal(throughputQps),
      "runtime" -> s"JVM ${System.getProperty("java.version")}",
      "timestamp" -> timestamp.toString
    )

object BenchReport:
  def decimal(value: Double): String = "%.4f".formatLocal(Locale.ROOT, value)
  def megabytes(bytes: Long): Double = bytes / (1024.0 * 1024.0)

/** Ejecuta un escenario de benchmark:
  *
  *   1. Carga el corpus (tiempo de carga, separado del de indexación).
  *   1. Construye el índice `index-runs` veces desde cero: tiempo (mediana), GC y memoria retenida.
  *   1. Ejecuta todas las consultas `warmup` veces sin medir (calentamiento del JIT) y luego `runs` veces
  *      midiendo cada ejecución completa (tokenizar + parsear + evaluar + ranking).
  *   1. Agrega una fila al CSV de resultados y, si se pide, escribe el top-10 de cada consulta para comparar
  *      resultados entre lenguajes.
  */
object Benchmark:
  private val Flags =
    Set("scenario", "corpus", "docs-dir", "queries", "warmup", "runs", "index-runs", "out", "dump")

  /** Consultas por defecto (las de la guía de ejecución de la versión Java). */
  val DefaultQueries: Vector[String] = Vector(
    "seguridad",
    "accesibilidad",
    "\"deep eval\"",
    "\"document two content\"",
    "seguridad AND owasp",
    "accesibilidad OR usabilidad",
    "informe NOT seguridad",
    "(seguridad OR owasp) AND NOT \"inyeccion sql\""
  )

  def main(args: List[String]): Int =
    parseConfig(args).flatMap(run) match
      case Left(error) =>
        System.err.println(s"Error: $error")
        1
      case Right(report) =>
        printSummary(report)
        0

  def parseConfig(args: List[String]): Either[String, BenchConfig] =
    for
      flags <- Cli.parse(args, Flags)
      warmup <- Cli.int(flags, "warmup", 3)
      runs <- Cli.int(flags, "runs", 10, min = 1)
      indexRuns <- Cli.int(flags, "index-runs", 5, min = 1)
    yield BenchConfig(
      scenario = flags.getOrElse("scenario", "sample"),
      corpus = Cli.path(flags, "corpus"),
      docsDir = Cli.path(flags, "docs-dir"),
      queries = Cli.path(flags, "queries"),
      warmup = warmup,
      runs = runs,
      indexRuns = indexRuns,
      out = Cli.path(flags, "out").getOrElse(Paths.get("bench-results", "results.csv")),
      dump = Cli.path(flags, "dump")
    )

  def run(config: BenchConfig): Either[String, BenchReport] =
    val (loaded, loadNanos) = timed(CorpusLoader.load(config.corpus, config.docsDir))
    for
      corpus <- loaded.left.map(_.message)
      _ <- Either.cond(corpus.documents.nonEmpty, (), "the corpus has no documents")
      queries <- loadQueries(config.queries)
      report <- execute(config, corpus, LatencyStats.toMillis(loadNanos), queries)
    yield
      appendCsv(config.out, report)
      report

  private def execute(
      config: BenchConfig,
      corpus: Corpus,
      loadMs: Double,
      queryLines: Vector[String]
  ): Either[String, BenchReport] =
    corpus.errors.foreach(error => System.err.println(s"Warning: skipped ${error.message}"))
    val (invalid, queries) = queryLines.partition(query => QueryParser.parse(query).isLeft)
    invalid.foreach(query => System.err.println(s"Warning: invalid query skipped: $query"))

    Memory.resetPeaks()
    // Las construcciones previas se descartan apenas se miden para que no inflen la memoria de la última.
    val discarded = (1 until config.indexRuns).map(_ => buildIndex(corpus).map(_._2))
    buildIndex(corpus).map { (index, lastBuild) =>
      val builds = discarded.flatMap(_.toOption) :+ lastBuild
      val engine = SearchEngine(index)

      val warmupChecksum = (1 to config.warmup).map(_ => runPass(engine, queries)._2).sum
      val (passes, wallNanos) = timed((1 to config.runs).map(_ => runPass(engine, queries)))
      val latencies = passes.flatMap(_._1)
      val checksum = warmupChecksum + passes.map(_._2).sum

      val matchedDocs = queries.map(query => QueryParser.parse(query).fold(_ => 0, engine.matching(_).length).toLong).sum
      config.dump.foreach(path => writeDump(path, engine, queries))
      println(s"checksum: $checksum")

      BenchReport(
        scenario = config.scenario,
        documents = index.size,
        vocabulary = index.vocabularySize,
        tokens = index.totalLength,
        queries = queries.size,
        invalidQueries = invalid.size,
        matchedDocs = matchedDocs,
        loadMs = loadMs,
        builds = builds,
        heapPeakBytes = Memory.peakHeapUsed(),
        latency = LatencyStats.fromNanos(latencies),
        throughputQps = if wallNanos == 0 then 0.0 else latencies.size / (wallNanos / 1e9),
        timestamp = Instant.now()
      )
    }

  /** Construye el índice desde cero midiendo tiempo, GC y heap retenido (antes y después, tras forzar GC). */
  private def buildIndex(corpus: Corpus): Either[String, (InvertedIndex, BuildSample)] =
    val heapBefore = Memory.settledHeapUsed()
    val gcBefore = Memory.gc()
    val (built, nanos) = timed(InvertedIndex.build(corpus.documents))
    val gcDuring = Memory.gc() - gcBefore
    built.left.map(_.message).map { index =>
      val retained = math.max(0L, Memory.settledHeapUsed() - heapBefore)
      (index, BuildSample(LatencyStats.toMillis(nanos), retained, gcDuring))
    }

  /** Ejecuta cada consulta una vez. Devuelve la latencia de cada una (ns) y un checksum de los resultados que
    * impide que el JIT descarte el trabajo.
    */
  private def runPass(engine: SearchEngine, queries: Vector[String]): (Vector[Long], Long) =
    queries.foldLeft((Vector.empty[Long], 0L)) { case ((latencies, checksum), query) =>
      val start = System.nanoTime()
      val result = engine.search(query)
      val elapsed = System.nanoTime() - start
      val hits = result.fold(_ => 0L, top => top.foldLeft(top.size.toLong)(_ + _.docId))
      (latencies :+ elapsed, checksum + hits)
    }

  private def timed[A](action: => A): (A, Long) =
    val start = System.nanoTime()
    val result = action
    (result, System.nanoTime() - start)

  private def loadQueries(file: Option[Path]): Either[String, Vector[String]] =
    file match
      case None => Right(DefaultQueries)
      case Some(path) =>
        Try(Files.readAllLines(path, UTF_8).asScala.toVector).toEither.left
          .map(error => s"cannot read queries file $path: ${error.getMessage}")
          .map(_.map(_.trim).filter(line => line.nonEmpty && !line.startsWith("#")))
          .filterOrElse(_.nonEmpty, s"queries file $path has no queries")

  private def appendCsv(path: Path, report: BenchReport): Unit =
    Option(path.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
    val writeHeader = !Files.exists(path) || Files.size(path) == 0
    val header = report.columns.map(_._1).mkString(",")
    val row = report.columns.map((_, value) => csvField(value)).mkString(",")
    val lines = if writeHeader then List(header, row) else List(row)
    Files.write(path, lines.asJava, UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)

  private def csvField(value: String): String =
    if value.exists(c => c == ',' || c == '"' || c == '\n') then "\"" + value.replace("\"", "\"\"") + "\""
    else value

  /** Archivo TSV: consulta, total de documentos que la cumplen y top-10 como `id:score;...`. Sirve para comprobar
    * que los tres lenguajes devuelven los mismos IDs.
    */
  private def writeDump(path: Path, engine: SearchEngine, queries: Vector[String]): Unit =
    val lines = queries.map { text =>
      QueryParser.parse(text) match
        case Left(error) => s"$text\tERROR\t${error.message}"
        case Right(query) =>
          val top = engine.run(query).map(r => s"${r.docId}:${"%.6f".formatLocal(Locale.ROOT, r.score)}")
          s"$text\t${engine.matching(query).length}\t${top.mkString(";")}"
    }
    Option(path.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
    Files.write(path, ("query\tmatched\ttop10" +: lines).asJava, UTF_8)

  private def printSummary(report: BenchReport): Unit =
    val columns = report.columns
    val width = columns.map(_._1.length).max
    println(s"Benchmark '${report.scenario}' finished:")
    columns.foreach((name, value) => println(s"  ${name.padTo(width, ' ')}  $value"))
