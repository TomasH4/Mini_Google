package minigoogle.bench

import minigoogle.Cli
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.{Random, Try, Using}

final case class GeneratorConfig(
    documents: Int,
    seed: Long,
    minWords: Int,
    maxWords: Int,
    vocabulary: Int,
    out: Path,
    queries: Int,
    queriesOut: Option[Path]
)

/** Distribución de Zipf sobre los rangos 0..size-1: el rango r aparece con probabilidad proporcional a
  * 1 / (r + 1)^exponent, como la frecuencia de las palabras en un texto real.
  */
final class Zipf(size: Int, exponent: Double):
  private val cumulative: Array[Double] =
    // StrictMath da el mismo resultado en cualquier JVM: el corpus es idéntico en todas las máquinas.
    val weights = Array.tabulate(size)(rank => 1.0 / StrictMath.pow(rank + 1.0, exponent))
    val total = weights.sum
    weights.scanLeft(0.0)(_ + _).tail.map(_ / total)

  def sample(random: Random): Int =
    val u = random.nextDouble()
    @tailrec def search(low: Int, high: Int): Int =
      if low >= high then low
      else
        val mid = (low + high) >>> 1
        if cumulative(mid) < u then search(mid + 1, high) else search(low, mid)
    search(0, size - 1)

/** Genera un corpus sintético reproducible (misma semilla = mismo archivo, byte a byte) con el formato de
  * `proyecto_final.md` (`ID<TAB>título<TAB>texto`, un documento por línea) y un archivo de consultas de prueba.
  * Reemplaza al script de Python del documento del proyecto: Java, Scala y Go leen estos mismos archivos.
  *
  * El texto mezcla conectores frecuentes, vocabulario técnico y palabras sintéticas con frecuencias tipo Zipf,
  * con oraciones, mayúsculas y comas para ejercitar la limpieza del analizador.
  */
object CorpusGenerator:
  private val Flags =
    Set("docs", "seed", "min-words", "max-words", "vocabulary", "out", "queries", "queries-out")

  private val MaxPhraseSamples = 2000

  val StopWords: Vector[String] = Vector(
    "de", "la", "que", "el", "en", "y", "a", "los", "se", "del", "las", "un", "por", "con", "no", "una", "su",
    "para", "es", "al", "lo", "como", "mas", "o", "pero", "sus", "le", "ha", "me", "si"
  )

  val TechWords: Vector[String] = Vector(
    "sistema", "datos", "busqueda", "indice", "motor", "consulta", "documento", "servidor", "usuario", "red",
    "memoria", "rendimiento", "seguridad", "backend", "microservicios", "arquitectura", "limpia", "concurrencia",
    "programacion", "funcional", "concurrente", "lenguaje", "scala", "java", "golang", "goroutine", "compilador",
    "algoritmo", "ranking", "relevancia", "frase", "termino", "texto", "palabra", "cache", "proceso", "hilo",
    "latencia", "throughput", "escalabilidad", "prueba", "calidad", "codigo", "error", "excepcion", "lento",
    "rapido", "seguro", "estable", "moderno", "cliente", "api", "http", "base", "tabla", "cola", "pila", "arbol",
    "grafo", "lista", "mapa", "conjunto", "vector", "matriz", "funcion", "clase", "objeto", "tipo", "patron",
    "modulo", "paquete", "libreria", "framework", "despliegue", "contenedor", "nube", "plataforma", "servicio",
    "evento", "mensaje", "flujo", "canal", "actor", "fibra", "bloqueo", "transaccion", "consistencia",
    "disponibilidad", "particion", "replica", "nodo", "cluster", "balanceo", "carga", "metrica", "monitoreo",
    "registro", "auditoria", "accesibilidad", "usabilidad", "interfaz", "componente", "pantalla", "formulario",
    "boton", "navegacion", "contraste", "teclado", "lector", "owasp", "inyeccion", "sql", "autenticacion",
    "autorizacion", "sesion", "token", "cifrado", "vulnerabilidad", "riesgo", "ataque", "defensa", "firewall",
    "parche", "version", "regresion", "integracion", "unitaria", "automatizacion", "pipeline", "entrega",
    "continua", "revision", "refactorizacion", "deuda", "tecnica", "documentacion", "requisito", "historia",
    "sprint", "equipo", "proyecto", "producto"
  )

  private val Syllables: Vector[String] =
    Vector("ka", "lo", "mi", "ra", "te", "su", "no", "pa", "ve", "di", "ro", "la", "me", "ti", "sa", "co", "bu", "fe",
      "gi", "ja")

  def main(args: List[String]): Int =
    parseConfig(args).flatMap(generate) match
      case Left(error) =>
        System.err.println(s"Error: $error")
        1
      case Right(_) => 0

  def parseConfig(args: List[String]): Either[String, GeneratorConfig] =
    for
      flags <- Cli.parse(args, Flags)
      out <- Cli.path(flags, "out").toRight("--out <file.tsv> is required")
      documents <- Cli.int(flags, "docs", 1000, min = 1)
      seed <- Cli.long(flags, "seed", 42L)
      minWords <- Cli.int(flags, "min-words", 80, min = 1)
      maxWords <- Cli.int(flags, "max-words", 400, min = minWords)
      vocabulary <- Cli.int(flags, "vocabulary", 20000, min = 500)
      queries <- Cli.int(flags, "queries", 200)
    yield GeneratorConfig(documents, seed, minWords, maxWords, vocabulary, out, queries, Cli.path(flags, "queries-out"))

  /** Escribe el corpus (y las consultas si se pidió). Devuelve las frases de muestra tomadas del corpus. */
  def generate(config: GeneratorConfig): Either[String, Vector[String]] =
    Try {
      val vocab = vocabulary(config.vocabulary)
      val zipf = Zipf(vocab.size, 1.0)
      val random = Random(config.seed)
      Option(config.out.toAbsolutePath.getParent).foreach(Files.createDirectories(_))

      val phrases = Using.resource(Files.newBufferedWriter(config.out, UTF_8)) { writer =>
        (1 to config.documents).foldLeft(Vector.empty[String]) { (samples, id) =>
          val title = Vector.fill(4 + random.nextInt(6))(vocab(zipf.sample(random))).mkString(" ").capitalize
          val length = config.minWords + random.nextInt(config.maxWords - config.minWords + 1)
          val words = Vector.fill(length)(vocab(zipf.sample(random)))
          writer.write(id.toString)
          writer.write('\t')
          writer.write(title)
          writer.write('\t')
          writer.write(render(words, random))
          writer.write('\n')
          if samples.size < MaxPhraseSamples && random.nextInt(20) == 0 then samples :+ phraseFrom(words, random)
          else samples
        }
      }

      config.queriesOut.foreach { path =>
        val queries = makeQueries(config.queries, vocab, phrases, Random(config.seed + 1))
        Option(path.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
        Files.write(path, queries.asJava, UTF_8)
      }
      println(s"Generated ${config.documents} documents in ${config.out}")
      config.queriesOut.foreach(path => println(s"Generated ${config.queries} queries in $path"))
      phrases
    }.toEither.left.map(error => s"cannot generate corpus: ${error.getMessage}")

  /** Vocabulario ordenado por frecuencia: conectores, términos técnicos y palabras sintéticas sin repetir. */
  def vocabulary(size: Int): Vector[String] =
    (StopWords.iterator ++ TechWords.iterator ++ Iterator.from(0).map(synthetic)).distinct.take(size).toVector

  /** Palabra sintética única para cada rango: el número en base 20 escrito con sílabas (mínimo dos). */
  def synthetic(rank: Int): String =
    val base = Syllables.size
    @tailrec def digits(n: Int, acc: List[Int]): List[Int] =
      if n < base then n :: acc else digits(n / base, (n % base) :: acc)
    val ds = digits(rank, Nil)
    (if ds.size < 2 then 0 :: ds else ds).map(Syllables).mkString

  /** Agrupa las palabras en oraciones de 8 a 20 palabras, con mayúscula inicial, comas ocasionales y punto. */
  private def render(words: Vector[String], random: Random): String =
    @tailrec def sentences(rest: Vector[String], acc: Vector[String]): Vector[String] =
      if rest.isEmpty then acc
      else
        val (sentence, remaining) = rest.splitAt(8 + random.nextInt(13))
        val text = sentence.zipWithIndex.map { (word, i) =>
          val cased = if i == 0 then word.capitalize else word
          if i < sentence.size - 1 && random.nextInt(12) == 0 then cased + "," else cased
        }
        sentences(remaining, acc :+ (text.mkString(" ") + "."))
    sentences(words, Vector.empty).mkString(" ")

  /** 2 o 3 palabras consecutivas del documento: una frase exacta con al menos un resultado garantizado. */
  private def phraseFrom(words: Vector[String], random: Random): String =
    val size = 2 + random.nextInt(2)
    val start = random.nextInt(math.max(1, words.size - size))
    words.slice(start, start + size).mkString(" ")

  /** Mezcla fija de los tipos de consulta del proyecto: término, AND, OR, NOT, frase y combinaciones. */
  private def makeQueries(count: Int, vocab: Vector[String], phrases: Vector[String], random: Random): Vector[String] =
    // Términos técnicos más 100 palabras sintéticas de frecuencia media (sin conectores).
    val pool = vocab.slice(StopWords.size, math.min(vocab.size, StopWords.size + TechWords.size + 100))
    def word(): String = pool(random.nextInt(pool.size))
    def phrase(): String = if phrases.isEmpty then word() else "\"" + phrases(random.nextInt(phrases.size)) + "\""
    Vector.tabulate(count) { i =>
      i % 7 match
        case 0 => word()
        case 1 => s"${word()} AND ${word()}"
        case 2 => s"${word()} OR ${word()}"
        case 3 => s"${word()} AND NOT ${word()}"
        case 4 => phrase()
        case 5 => s"(${word()} OR ${word()}) AND NOT ${word()}"
        case _ => s"(${word()} OR ${word()}) AND ${phrase()}"
    }
