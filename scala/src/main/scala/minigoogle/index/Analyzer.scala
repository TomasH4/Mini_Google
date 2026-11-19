package minigoogle.index

import java.util.regex.Pattern
import scala.annotation.tailrec

/** Pipeline de análisis de texto usado al indexar. Aplica las mismas reglas que la versión Java:
  *
  *   1. Minúsculas.
  *   1. Separación por espacios en blanco. La posición de cada palabra es su índice en esa separación.
  *   1. Limpieza de la puntuación al inicio y al final de cada palabra (la puntuación interna se conserva).
  *
  * Las palabras que quedan vacías tras la limpieza se descartan, pero no desplazan las posiciones de las demás.
  */
object Analyzer:
  private val Whitespace = Pattern.compile("\\s+")

  /** Equivale a `\p{L}` o `\p{N}` en una expresión regular de Java: letra o número de cualquier alfabeto. */
  private def isLetterOrNumber(codePoint: Int): Boolean =
    Character.isLetter(codePoint) || {
      val category = Character.getType(codePoint)
      category == Character.DECIMAL_DIGIT_NUMBER || category == Character.LETTER_NUMBER ||
      category == Character.OTHER_NUMBER
    }

  /** Quita los caracteres que no son letras ni números al inicio y al final de una palabra. Mismo resultado que
    * `replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "")` de la versión Java, sin el costo de una regex por
    * palabra (se recorren los extremos por code point).
    */
  def normalize(word: String): String =
    @tailrec def firstKept(i: Int): Int =
      if i >= word.length then word.length
      else
        val codePoint = word.codePointAt(i)
        if isLetterOrNumber(codePoint) then i else firstKept(i + Character.charCount(codePoint))

    @tailrec def lastKept(j: Int, start: Int): Int =
      if j <= start then start
      else
        val codePoint = word.codePointBefore(j)
        if isLetterOrNumber(codePoint) then j else lastKept(j - Character.charCount(codePoint), start)

    val start = firstKept(0)
    val end = lastKept(word.length, start)
    if start == 0 && end == word.length then word else word.substring(start, end)

  /** Términos del texto junto con su posición, en orden de aparición. */
  def tokenize(text: String): Iterator[(String, Int)] =
    Whitespace
      .split(text.toLowerCase)
      .iterator
      .zipWithIndex
      .map((word, position) => (normalize(word), position))
      .filter((term, _) => term.nonEmpty)

  /** Agrupa las posiciones por término: término -> posiciones ordenadas de menor a mayor. */
  def termPositions(text: String): Map[String, IArray[Int]] =
    tokenize(text).toVector
      .groupMap(_._1)(_._2)
      .view
      .mapValues(positions => IArray.from(positions))
      .toMap

  /** Número de palabras del documento (|D| en BM25). Mismo criterio que la versión Java. */
  def wordCount(text: String): Int = Whitespace.split(text).length
