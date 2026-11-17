package minigoogle.search

import minigoogle.index.{DocIds, InvertedIndex}
import minigoogle.model.{Document, SearchResult}
import minigoogle.query.{ParseError, Query, QueryParser}
import scala.annotation.tailrec

/** Constantes de calibración de BM25. */
final case class Bm25(k1: Double = 1.5, b: Double = 0.75)

/** Evalúa consultas sobre un índice inmutable y ordena los resultados con BM25.
  *
  * No guarda estado mutable: varios hilos pueden usar la misma instancia al mismo tiempo.
  */
final class SearchEngine(val index: InvertedIndex, params: Bm25 = Bm25(), topK: Int = 10):

  /** Tokeniza, parsea, evalúa y ordena. Devuelve un error si la consulta no es válida. */
  def search(input: String): Either[ParseError, List[SearchResult]] =
    QueryParser.parse(input).map(run)

  /** Top-K de una consulta ya parseada. */
  def run(query: Query): List[SearchResult] =
    rank(matching(query), Query.scoringTerms(query))

  /** IDs (ordenados) de todos los documentos que cumplen la consulta. Evaluación recursiva del AST. */
  def matching(query: Query): IArray[Int] = query match
    case Query.Term(word) => index.docIds(word)
    case Query.Phrase(words) => phrase(words)
    case Query.And(left, right) => DocIds.intersect(matching(left), matching(right))
    case Query.Or(left, right) => DocIds.union(matching(left), matching(right))
    case Query.Not(child) => DocIds.difference(index.allDocIds, matching(child))

  /** Frase exacta: primero los documentos que tienen todas las palabras, luego se verifican las posiciones. */
  private def phrase(words: List[String]): IArray[Int] = words match
    case Nil => DocIds.empty
    case first :: others =>
      val candidates = others.foldLeft(index.docIds(first))((acc, word) => DocIds.intersect(acc, index.docIds(word)))
      candidates.filter(docId => containsPhrase(words, docId))

  /** Existe una posición p de la primera palabra tal que la palabra i aparece en p + i. */
  private def containsPhrase(words: List[String], docId: Int): Boolean =
    words.map(word => index.posting(word, docId).fold(DocIds.empty)(_.positions)) match
      case first :: rest =>
        first.exists(start => rest.zipWithIndex.forall((positions, i) => DocIds.contains(positions, start + i + 1)))
      case Nil => false

  /** Peso de un término de la consulta: su IDF (multiplicado por las veces que aparece en la consulta) y su
    * frecuencia en cada documento candidato, en el mismo orden que los candidatos.
    */
  private final case class WeightedTerm(idf: Double, frequencies: IArray[Int])

  private def rank(docIds: IArray[Int], terms: List[String]): List[SearchResult] =
    val avgdl = index.avgDocLength
    val weighted = terms
      .groupMapReduce(identity)(_ => 1)(_ + _)
      .toList
      .sortBy(_._1) // orden fijo de suma: el puntaje no depende del orden interno del Map
      .map((term, repetitions) => WeightedTerm(repetitions * inverseDocumentFrequency(term), frequencies(term, docIds)))
    val scored = docIds.indices.iterator.flatMap { i =>
      index.document(docIds(i)).map(doc => SearchResult(doc.id, doc.title, score(doc, i, weighted, avgdl)))
    }
    TopK.select(scored, topK)

  /** IDF(q) = ln((N - n(q) + 0.5) / (n(q) + 0.5) + 1), con n(q) = documentos que contienen q. */
  private def inverseDocumentFrequency(term: String): Double =
    val n = index.documentFrequency(term).toDouble
    math.log((index.size - n + 0.5) / (n + 0.5) + 1.0)

  /** f(q, D) para cada candidato. Si hay pocos candidatos se usa búsqueda binaria en los postings; si hay muchos,
    * se recorren a la vez los candidatos y los postings (ambos ordenados por ID): O(N + M).
    */
  private def frequencies(term: String, docIds: IArray[Int]): IArray[Int] =
    val postings = index.postingList(term)
    if docIds.length.toLong * 16 < postings.size then docIds.map(index.termFrequency(term, _))
    else
      val out = new Array[Int](docIds.length)
      @tailrec def merge(i: Int, j: Int): Unit =
        if i < docIds.length && j < postings.size then
          val postingDoc = postings.docIds(j)
          if postingDoc == docIds(i) then
            out(i) = postings.frequencyAt(j)
            merge(i + 1, j + 1)
          else if postingDoc < docIds(i) then merge(i, j + 1)
          else merge(i + 1, j)
      merge(0, 0)
      IArray.unsafeFromArray(out)

  /** Score(D, Q) = Σ IDF(q) · f(q, D) · (k1 + 1) / (f(q, D) + k1 · (1 - b + b · |D| / avgdl)) */
  private def score(doc: Document, position: Int, terms: List[WeightedTerm], avgdl: Double): Double =
    val lengthNorm = params.k1 * (1 - params.b + params.b * doc.length / avgdl)
    terms.foldLeft(0.0) { (sum, term) =>
      val f = term.frequencies(position)
      if f == 0 then sum
      else sum + term.idf * (f * (params.k1 + 1)) / (f + lengthNorm)
    }
