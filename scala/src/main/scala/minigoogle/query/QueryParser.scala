package minigoogle.query

import minigoogle.index.Analyzer
import scala.annotation.tailrec

final case class ParseError(message: String)

/** Parser de descenso recursivo: convierte las fichas en un AST `Query`.
  *
  * Gramática (precedencia de mayor a menor: paréntesis > NOT > AND > OR; AND y OR asocian a la izquierda):
  * {{{
  *   expr    ::= andExpr ("OR" andExpr)*
  *   andExpr ::= factor (["AND"] factor)*      -- sin operador entre dos operandos = AND implícito
  *   factor  ::= "NOT" factor | "(" expr ")" | PHRASE | TERM
  * }}}
  *
  * Es puro: cada función recibe las fichas pendientes y devuelve el nodo construido junto con las fichas que
  * sobran, o un `ParseError`. No hay estado mutable ni excepciones.
  */
object QueryParser:
  private type Parsed = Either[ParseError, (Query, List[Token])]

  def parse(input: String): Either[ParseError, Query] = parseTokens(Tokenizer.tokenize(input))

  def parseTokens(tokens: List[Token]): Either[ParseError, Query] =
    if tokens.isEmpty then Left(ParseError("empty query"))
    else
      parseOr(tokens).flatMap {
        case (query, Nil) => Right(query)
        case (_, next :: _) => Left(ParseError(s"unexpected ${describe(next)}"))
      }

  private def parseOr(tokens: List[Token]): Parsed =
    parseAnd(tokens).flatMap((left, rest) => orTail(left, rest))

  @tailrec
  private def orTail(left: Query, tokens: List[Token]): Parsed = tokens match
    case Token.Or :: rest =>
      parseAnd(rest) match
        case Right((right, remaining)) => orTail(Query.Or(left, right), remaining)
        case Left(error) => Left(error)
    case _ => Right((left, tokens))

  private def parseAnd(tokens: List[Token]): Parsed =
    parseFactor(tokens).flatMap((left, rest) => andTail(left, rest))

  @tailrec
  private def andTail(left: Query, tokens: List[Token]): Parsed =
    val operand = tokens match
      case Token.And :: rest => Some(rest)
      // AND implícito: dos operandos seguidos (p. ej. `informe NOT seguridad`) equivalen a unirlos con AND.
      case (Token.Term(_) | Token.Phrase(_) | Token.Not | Token.LParen) :: _ => Some(tokens)
      case _ => None
    operand match
      case None => Right((left, tokens))
      case Some(rest) =>
        parseFactor(rest) match
          case Right((right, remaining)) => andTail(Query.And(left, right), remaining)
          case Left(error) => Left(error)

  private def parseFactor(tokens: List[Token]): Parsed = tokens match
    case Token.Not :: rest => parseFactor(rest).map((child, remaining) => (Query.Not(child), remaining))
    case Token.LParen :: rest =>
      parseOr(rest).flatMap {
        case (inner, Token.RParen :: remaining) => Right((inner, remaining))
        case _ => Left(ParseError("missing closing parenthesis"))
      }
    case Token.Phrase(text) :: rest => Right((Query.Phrase(phraseWords(text)), rest))
    case Token.Term(text) :: rest => Right((Query.Term(text.toLowerCase), rest))
    case other :: _ => Left(ParseError(s"expected a term but found ${describe(other)}"))
    case Nil => Left(ParseError("query ended unexpectedly"))

  /** Palabras de una frase, normalizadas con las mismas reglas del índice. */
  private def phraseWords(text: String): List[String] =
    text.split("\\s+").iterator.map(word => Analyzer.normalize(word.toLowerCase)).filter(_.nonEmpty).toList

  private def describe(token: Token): String = token match
    case Token.LParen => "'('"
    case Token.RParen => "')'"
    case Token.And => "AND"
    case Token.Or => "OR"
    case Token.Not => "NOT"
    case Token.Term(text) => s"term '$text'"
    case Token.Phrase(text) => s"phrase \"$text\""
