package minigoogle.search

import minigoogle.model.SearchResult
import scala.collection.immutable.TreeSet

/** Selección de los K mejores resultados sin ordenar la lista completa.
  *
  * Recorre los resultados con `foldLeft` manteniendo un montículo mínimo inmutable de tamaño K (un `TreeSet`
  * ordenado del peor al mejor). Cada paso cuesta O(log K), así que el total es O(M log K) en lugar de
  * O(M log M).
  */
object TopK:

  /** Peor resultado primero: menor puntaje y, a igual puntaje, mayor ID de documento. */
  val worstFirst: Ordering[SearchResult] = new Ordering[SearchResult]:
    def compare(x: SearchResult, y: SearchResult): Int =
      val byScore = java.lang.Double.compare(x.score, y.score)
      if byScore != 0 then byScore else Integer.compare(y.docId, x.docId)

  /** Los `k` mejores resultados, del mejor al peor. Desempate determinista: menor ID de documento primero. */
  def select(results: IterableOnce[SearchResult], k: Int): List[SearchResult] =
    if k <= 0 then Nil
    else
      val heap = results.iterator.foldLeft(TreeSet.empty[SearchResult](using worstFirst)) { (heap, result) =>
        if heap.size < k then heap + result
        else if worstFirst.gt(result, heap.head) then heap - heap.head + result
        else heap
      }
      heap.toList.reverse
