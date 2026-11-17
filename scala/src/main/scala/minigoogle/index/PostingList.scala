package minigoogle.index

import minigoogle.model.Posting

/** Postings de un término en forma compacta e inmutable.
  *
  *   - `docIds`: IDs de los documentos que contienen el término, ordenados de menor a mayor, en un arreglo de
  *     enteros primitivos (sin un objeto por entrada).
  *   - `positions(i)`: posiciones (ordenadas) del término dentro del documento `docIds(i)`.
  *
  * Tener los IDs ordenados y contiguos permite cruzar listas con dos punteros y buscar con búsqueda binaria.
  */
final class PostingList private (val docIds: IArray[Int], positions: Vector[IArray[Int]]):

  def size: Int = docIds.length

  def positionsAt(i: Int): IArray[Int] = positions(i)

  /** f(q, D) del i-ésimo documento de la lista. */
  def frequencyAt(i: Int): Int = positions(i).length

  /** Índice del documento dentro de la lista, o -1 si no está. Búsqueda binaria: O(log N). */
  def indexOf(docId: Int): Int = DocIds.indexOf(docIds, docId)

  def postings: Iterator[Posting] = Iterator.range(0, size).map(i => Posting(docIds(i), positions(i)))

  /** Lista nueva con el documento insertado en su lugar (normalmente al final). La lista actual no cambia. */
  def inserted(docId: Int, docPositions: IArray[Int]): PostingList =
    val at = DocIds.insertionPoint(docIds, docId)
    val ids = new Array[Int](size + 1)
    for i <- 0 until at do ids(i) = docIds(i)
    ids(at) = docId
    for i <- at until size do ids(i + 1) = docIds(i)
    new PostingList(IArray.unsafeFromArray(ids), positions.patch(at, List(docPositions), 0))

object PostingList:
  val empty: PostingList = new PostingList(IArray.empty[Int], Vector.empty)

  /** Construye la lista a partir de postings ya ordenados por ID de documento. */
  def fromSorted(postings: Vector[Posting]): PostingList =
    val ids = new Array[Int](postings.size)
    for i <- postings.indices do ids(i) = postings(i).docId
    new PostingList(IArray.unsafeFromArray(ids), postings.map(_.positions))
