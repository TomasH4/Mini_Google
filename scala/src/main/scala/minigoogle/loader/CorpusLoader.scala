package minigoogle.loader

import minigoogle.index.Analyzer
import minigoogle.model.Document
import java.nio.file.{Files, Path}
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

/** Documentos cargados y errores de los archivos (o líneas) que no se pudieron leer. Un archivo con error no
  * detiene la carga de los demás.
  */
final case class Corpus(documents: Vector[Document], errors: Vector[LoadError])

/** Fuentes de documentos. En archivos sueltos los IDs se asignan 1, 2, 3... en orden de carga, igual que la versión
  * Java; en el corpus TSV vienen en el propio archivo.
  */
object CorpusLoader:
  val ClasspathFolder = "/documents"

  /** Documentos de ejemplo empaquetados en el JAR (los mismos de la versión Java), en el orden de
    * `manifest.txt`.
    */
  def fromClasspath(folder: String = ClasspathFolder): Either[LoadError, Corpus] =
    readResource(s"$folder/manifest.txt", "manifest.txt").map { manifest =>
      assemble(manifestNames(manifest).map(name => name -> readResource(s"$folder/$name", name)))
    }

  /** Archivos de una carpeta: los de `manifest.txt` si existe; si no, todos los soportados por orden alfabético. */
  def fromDirectory(dir: Path): Either[LoadError, Corpus] =
    if !Files.isDirectory(dir) then Left(LoadError(dir.toString, "not a directory"))
    else
      val manifest = dir.resolve("manifest.txt")
      val names =
        if Files.isRegularFile(manifest) then readFile(manifest).map(manifestNames)
        else listSupported(dir)
      names.map(list => assemble(list.map(name => name -> readFile(dir.resolve(name)))))

  /** Corpus de benchmark con el formato definido en `proyecto_final.md`: un documento por línea,
    * `ID<TAB>título<TAB>texto`. Los IDs se toman del archivo, así los tres lenguajes reportan los mismos IDs.
    * Las líneas mal formadas se informan como errores y se omiten.
    */
  def fromTsv(file: Path): Either[LoadError, Corpus] =
    val fileName = file.getFileName.toString
    Try(Using.resource(Source.fromFile(file.toFile, "UTF-8"))(_.getLines().toVector)).toEither.left
      .map(LoadError.from(fileName, _))
      .map { lines =>
        val (errors, documents) = lines.zipWithIndex
          .filter((line, _) => line.nonEmpty)
          .partitionMap((line, i) => parseTsvLine(line).left.map(reason => LoadError(s"$fileName:${i + 1}", reason)))
        Corpus(documents, errors)
      }

  private def parseTsvLine(line: String): Either[String, Document] =
    line.split("\t", 3) match
      case Array(rawId, title, body) =>
        rawId.trim.toIntOption
          .toRight(s"invalid document ID '$rawId'")
          .map(id => Document(id, title, body, Analyzer.wordCount(body)))
      case _ => Left("expected ID<TAB>title<TAB>text")

  /** Elige la fuente según las opciones: corpus TSV, carpeta o, por defecto, los documentos del classpath. */
  def load(corpus: Option[Path], docsDir: Option[Path]): Either[LoadError, Corpus] =
    (corpus, docsDir) match
      case (Some(file), _) => fromTsv(file)
      case (None, Some(dir)) => fromDirectory(dir)
      case (None, None) => fromClasspath()

  /** Título visible: el nombre del archivo sin `.txt` (mismo criterio que la versión Java). */
  def titleOf(name: String): String = name.stripSuffix(".txt")

  private def assemble(entries: Seq[(String, Either[LoadError, String])]): Corpus =
    val (errors, loaded) = entries.partitionMap((name, result) => result.map(text => (name, text)))
    val documents = loaded.zipWithIndex.map { case ((name, text), i) =>
      Document(i + 1, titleOf(name), text, Analyzer.wordCount(text))
    }
    Corpus(documents.toVector, errors.toVector)

  private def manifestNames(manifest: String): Vector[String] =
    manifest.linesIterator.map(_.trim).filter(_.nonEmpty).toVector

  private def readResource(path: String, name: String): Either[LoadError, String] =
    Option(getClass.getResourceAsStream(path)) match
      case None => Left(LoadError(name, "not found in classpath"))
      case Some(in) => Using.resource(in)(DocumentLoader.read(_, name))

  private def readFile(path: Path): Either[LoadError, String] =
    val name = path.getFileName.toString
    Try(Files.newInputStream(path)).toEither.left
      .map(LoadError.from(name, _))
      .flatMap(in => Using.resource(in)(DocumentLoader.read(_, name)))

  private def listSupported(dir: Path): Either[LoadError, Vector[String]] =
    Try {
      Using.resource(Files.list(dir)) { stream =>
        stream.iterator.asScala
          .filter(Files.isRegularFile(_))
          .map(_.getFileName.toString)
          .filter(DocumentLoader.isSupported)
          .toVector
          .sorted
      }
    }.toEither.left.map(LoadError.from(dir.toString, _))
