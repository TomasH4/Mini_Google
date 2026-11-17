package minigoogle.query

/** Árbol de sintaxis abstracta (AST) de una consulta, como tipo de datos algebraico.
  *
  * El compilador conoce todos los casos, así que cada `match` sobre `Query` se verifica de forma exhaustiva.
  */
enum Query:
  case Term(word: String)
  case Phrase(words: List[String])
  case And(left: Query, right: Query)
  case Or(left: Query, right: Query)
  case Not(child: Query)

object Query:

  /** Términos que suman puntaje BM25: los que aparecen en forma positiva (las palabras de una frase cuentan una a
    * una). Los términos negados no puntúan: un documento no debe subir en el ranking por contener una palabra que
    * la consulta excluye. Un doble NOT vuelve a ser positivo.
    */
  def scoringTerms(query: Query): List[String] =
    def collect(node: Query, positive: Boolean): List[String] = node match
      case Term(word) => if positive then List(word) else Nil
      case Phrase(words) => if positive then words else Nil
      case And(left, right) => collect(left, positive) ++ collect(right, positive)
      case Or(left, right) => collect(left, positive) ++ collect(right, positive)
      case Not(child) => collect(child, !positive)
    collect(query, positive = true)
