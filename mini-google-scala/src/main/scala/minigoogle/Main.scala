package minigoogle

import minigoogle.bench.{Benchmark, CorpusGenerator}
import minigoogle.index.InvertedIndex
import minigoogle.loader.CorpusLoader
import minigoogle.model.SearchResult
import minigoogle.query.ParseError
import minigoogle.search.SearchEngine
import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.tailrec

/** Punto de entrada. Sin argumentos abre la consola interactiva, igual que la versión Java. */
object Main:

  private val Usage =
    """Usage:
      |  java -jar mini-google-scala.jar                         interactive console (sample documents)
      |  java -jar mini-google-scala.jar interactive [source]    interactive console
      |  java -jar mini-google-scala.jar search [source] -- <query>
      |  java -jar mini-google-scala.jar bench [options]         run a benchmark scenario (see README)
      |  java -jar mini-google-scala.jar generate [options]      generate a synthetic corpus (see README)
      |
      |source:  --docs-dir <folder>  |  --corpus <file.tsv>     (default: documents inside the JAR)""".stripMargin

  private val SourceFlags = Set("docs-dir", "corpus")

  def main(args: Array[String]): Unit =
    val exitCode = args.toList match
      case Nil => interactive(Nil)
      case "interactive" :: rest => interactive(rest)
      case "search" :: rest => searchOnce(rest)
      case "bench" :: rest => Benchmark.main(rest)
      case "generate" :: rest => CorpusGenerator.main(rest)
      case ("help" | "-h" | "--help") :: _ =>
        println(Usage)
        0
      case other :: _ =>
        System.err.println(s"Unknown command: $other")
        System.err.println(Usage)
        2
    if exitCode != 0 then sys.exit(exitCode)

  /** Carga los documentos y construye el motor; informa por stderr los archivos que no se pudieron leer. */
  private def loadEngine(sourceArgs: List[String]): Either[String, SearchEngine] =
    for
      flags <- Cli.parse(sourceArgs, SourceFlags)
      corpus <- CorpusLoader.load(Cli.path(flags, "corpus"), Cli.path(flags, "docs-dir")).left.map(_.message)
      _ = corpus.errors.foreach(error => System.err.println(s"Warning: skipped ${error.message}"))
      index <- InvertedIndex.build(corpus.documents).left.map(_.message)
    yield
      println(s"Loaded ${index.size} documents into the index.")
      SearchEngine(index)

  private def interactive(sourceArgs: List[String]): Int =
    loadEngine(sourceArgs) match
      case Left(error) =>
        System.err.println(s"Error: $error")
        1
      case Right(engine) =>
        println("Mini-Google (Scala) ready. Type a query and press ENTER. Empty line to exit.")
        val reader = new BufferedReader(new InputStreamReader(System.in, UTF_8))

        @tailrec def loop(): Unit =
          print("query> ")
          Console.flush()
          Option(reader.readLine()).map(_.trim).filter(_.nonEmpty) match
            case None => ()
            case Some(line) =>
              printResults(engine.search(line))
              loop()

        loop()
        println("Goodbye!")
        0

  /** `search [--docs-dir X | --corpus Y] -- consulta...` ejecuta una sola consulta (útil en scripts). */
  private def searchOnce(args: List[String]): Int =
    val (sourceArgs, queryArgs) = args.span(_ != "--") match
      case (before, "--" :: after) => (before, after)
      case (before, _) => (Nil, before)
    if queryArgs.isEmpty then
      System.err.println(Usage)
      2
    else
      loadEngine(sourceArgs) match
        case Left(error) =>
          System.err.println(s"Error: $error")
          1
        case Right(engine) =>
          val result = engine.search(queryArgs.mkString(" "))
          printResults(result)
          if result.isLeft then 1 else 0

  private def printResults(result: Either[ParseError, List[SearchResult]]): Unit = result match
    case Left(error) => println(s"Invalid query: ${error.message}")
    case Right(Nil) => println("No results.")
    case Right(results) =>
      println("Top results:")
      results.foreach(r => println(f"- ${r.title} (score ${r.score}%.4f)"))
