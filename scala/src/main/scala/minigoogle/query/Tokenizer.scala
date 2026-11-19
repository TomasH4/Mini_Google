package minigoogle.query

import scala.annotation.tailrec

/** Fichas léxicas de una consulta. */
enum Token:
  case LParen, RParen, And, Or, Not
  case Term(text: String)
  case Phrase(text: String)

/** Lexer de consultas. Reconoce las mismas fichas que la versión Java:
  *
  *   - `(` y `)`.
  *   - Operadores `AND`, `OR`, `NOT` (sin distinguir mayúsculas).
  *   - Términos: secuencias de letras o dígitos.
  *   - Frases entre comillas dobles. Si falta la comilla de cierre, la frase llega hasta el final.
  *
  * Cualquier otro carácter (espacios, `.,;:!?`, etc.) separa términos y se descarta.
  */
object Tokenizer:

  def tokenize(input: String): List[Token] =
    @tailrec
    def loop(i: Int, wordStart: Int, acc: List[Token]): List[Token] =
      // Cierra el término que se venía acumulando (si lo hay) antes de procesar un separador.
      def flushed: List[Token] = if wordStart >= 0 then word(input.substring(wordStart, i)) :: acc else acc

      if i >= input.length then flushed.reverse
      else
        input.charAt(i) match
          case '(' => loop(i + 1, -1, Token.LParen :: flushed)
          case ')' => loop(i + 1, -1, Token.RParen :: flushed)
          case '"' =>
            val closing = input.indexOf('"', i + 1)
            val end = if closing == -1 then input.length else closing
            loop(end + 1, -1, Token.Phrase(input.substring(i + 1, end)) :: flushed)
          case c if Character.isLetterOrDigit(c) => loop(i + 1, if wordStart >= 0 then wordStart else i, acc)
          case _ => loop(i + 1, -1, flushed)

    loop(0, -1, Nil)

  private def word(text: String): Token = text.toUpperCase match
    case "AND" => Token.And
    case "OR" => Token.Or
    case "NOT" => Token.Not
    case _ => Token.Term(text)
