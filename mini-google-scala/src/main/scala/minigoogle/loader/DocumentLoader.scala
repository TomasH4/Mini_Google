package minigoogle.loader

import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import scala.util.{Try, Using}

/** Error al leer un archivo o una línea del corpus. */
final case class LoadError(source: String, reason: String):
  def message: String = s"$source: $reason"

object LoadError:
  def from(source: String, error: Throwable): LoadError =
    LoadError(source, Option(error.getMessage).getOrElse(error.getClass.getSimpleName))

/** Extrae el texto de un archivo según su extensión: `.txt`, `.md`, `.pdf` o `.docx`. */
object DocumentLoader:
  val SupportedExtensions: Set[String] = Set("txt", "md", "pdf", "docx")

  def extensionOf(fileName: String): String =
    val dot = fileName.lastIndexOf('.')
    if dot == -1 then "" else fileName.substring(dot + 1).toLowerCase

  def isSupported(fileName: String): Boolean = SupportedExtensions.contains(extensionOf(fileName))

  /** Lee todo el contenido de `in`. No cierra el stream: lo cierra quien lo abrió. */
  def read(in: InputStream, fileName: String): Either[LoadError, String] =
    def attempt(extract: => String): Either[LoadError, String] =
      Try(extract).toEither.left.map(LoadError.from(fileName, _))

    extensionOf(fileName) match
      case "txt" | "md" => attempt(new String(in.readAllBytes(), UTF_8))
      case "pdf" =>
        attempt(Using.resource(Loader.loadPDF(in.readAllBytes()))(pdf => new PDFTextStripper().getText(pdf)))
      case "docx" =>
        attempt(Using.resource(new XWPFDocument(in)) { docx =>
          Using.resource(new XWPFWordExtractor(docx))(_.getText)
        })
      case other => Left(LoadError(fileName, s"unsupported file type '.$other'"))
