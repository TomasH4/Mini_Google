package minigoogle.index

import minigoogle.model.{Document, Posting}
import scala.collection.immutable.IntMap

enum IndexError(val message: String):
  case DuplicateDocumentId(id: Int) extends IndexError(s"Document id $id is already in the index")

/** Índice invertido posicional inmutable.
  *
  *   - `postings`: término -> `PostingList` (IDs de documento ordenados y las posiciones en cada uno).
  *   - `documents`: documentos indexados por ID (`IntMap`: claves `Int` sin boxing).
  *   - `totalLength`: suma de las longitudes de todos los documentos (para calcular `avgdl`).
  *
  * Agregar un documento devuelve un índice nuevo y deja intacto el anterior (comparten estructura). Por eso una
  * consulta en curso nunca ve un índice a medio actualizar y el índice se puede leer desde varios hilos sin locks.
  */
final case class InvertedIndex private (
    postings: Map[String, PostingList],
    documents: IntMap[Document],
    totalLength: Long
):

  /** Todos los IDs en orden ascendente (universo del operador NOT). Se calcula una vez por versión del índice. */
  lazy val allDocIds: IArray[Int] =
    val ids = documents.keysIterator.toArray
    java.util.Arrays.sort(ids)
    IArray.unsafeFromArray(ids)

  /** N: cantidad de documentos indexados. */
  def size: Int = allDocIds.length

  def vocabularySize: Int = postings.size

  def avgDocLength: Double = if size == 0 then 0.0 else totalLength.toDouble / size

  def document(id: Int): Option[Document] = documents.get(id)

  def postingList(term: String): PostingList = postings.getOrElse(term, PostingList.empty)

  /** IDs (ordenados) de los documentos que contienen el término. */
  def docIds(term: String): IArray[Int] = postingList(term).docIds

  /** n(q): cantidad de documentos que contienen el término. */
  def documentFrequency(term: String): Int = postingList(term).size

  /** Posting del término en un documento (búsqueda binaria). */
  def posting(term: String, docId: Int): Option[Posting] =
    val list = postingList(term)
    val i = list.indexOf(docId)
    Option.when(i >= 0)(Posting(docId, list.positionsAt(i)))

  /** f(q, D): veces que aparece el término en el documento. */
  def termFrequency(term: String, docId: Int): Int =
    val list = postingList(term)
    val i = list.indexOf(docId)
    if i >= 0 then list.frequencyAt(i) else 0

  /** Devuelve un índice nuevo que incluye el documento (actualización en vivo). Falla si el ID ya existe. */
  def add(doc: Document): Either[IndexError, InvertedIndex] =
    if documents.contains(doc.id) then Left(IndexError.DuplicateDocumentId(doc.id))
    else
      val updatedPostings = Analyzer.termPositions(doc.body).foldLeft(postings) { case (acc, (term, positions)) =>
        acc.updated(term, acc.getOrElse(term, PostingList.empty).inserted(doc.id, positions))
      }
      Right(new InvertedIndex(updatedPostings, documents.updated(doc.id, doc), totalLength + doc.length))

object InvertedIndex:
  val empty: InvertedIndex = new InvertedIndex(Map.empty, IntMap.empty, 0L)

  /** Construcción en bloque: `flatMap` produce los pares (término, posting) de todos los documentos en orden de ID
    * y `groupMap` los agrupa por término conservando ese orden, así cada lista queda ordenada por ID. Es mucho
    * más rápido que llamar a `add` por cada documento, porque no crea una versión intermedia del índice por
    * documento.
    */
  def build(documents: IterableOnce[Document]): Either[IndexError, InvertedIndex] =
    val sorted = documents.iterator.toVector.sortBy(_.id)
    val ids = sorted.map(_.id)
    ids.lazyZip(ids.drop(1)).collectFirst { case (a, b) if a == b => a } match
      case Some(duplicated) => Left(IndexError.DuplicateDocumentId(duplicated))
      case None =>
        val postings = sorted
          .flatMap(doc => Analyzer.termPositions(doc.body).map((term, positions) => term -> Posting(doc.id, positions)))
          .groupMap(_._1)(_._2)
          .view
          .mapValues(PostingList.fromSorted)
          .toMap
        val byId = IntMap.from(sorted.map(doc => doc.id -> doc))
        Right(new InvertedIndex(postings, byId, sorted.map(_.length.toLong).sum))
