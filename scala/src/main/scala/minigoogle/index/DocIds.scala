package minigoogle.index

import scala.annotation.tailrec

/** Conjuntos de IDs de documentos representados como arreglos inmutables (`IArray[Int]`) ordenados de menor a
  * mayor.
  *
  * Como están ordenados, la intersección, la unión y la diferencia recorren ambos arreglos una sola vez con dos
  * punteros: O(N + M), sin bucles anidados ni tablas hash. `IArray[Int]` guarda enteros primitivos, sin boxing.
  *
  * Cada función usa un builder local para armar el resultado; desde fuera son funciones puras.
  */
object DocIds:
  type Ids = IArray[Int]

  val empty: Ids = IArray.empty[Int]

  /** IDs presentes en `a` y en `b` (AND). */
  def intersect(a: Ids, b: Ids): Ids =
    val out = Array.newBuilder[Int]
    @tailrec def loop(i: Int, j: Int): Unit =
      if i < a.length && j < b.length then
        if a(i) == b(j) then
          out += a(i)
          loop(i + 1, j + 1)
        else if a(i) < b(j) then loop(i + 1, j)
        else loop(i, j + 1)
    loop(0, 0)
    IArray.unsafeFromArray(out.result())

  /** IDs presentes en `a` o en `b` (OR). */
  def union(a: Ids, b: Ids): Ids =
    val out = Array.newBuilder[Int]
    out.sizeHint(a.length + b.length)
    @tailrec def loop(i: Int, j: Int): Unit =
      if i < a.length && j < b.length then
        if a(i) == b(j) then
          out += a(i)
          loop(i + 1, j + 1)
        else if a(i) < b(j) then
          out += a(i)
          loop(i + 1, j)
        else
          out += b(j)
          loop(i, j + 1)
      else
        for k <- i until a.length do out += a(k)
        for k <- j until b.length do out += b(k)
    loop(0, 0)
    IArray.unsafeFromArray(out.result())

  /** IDs presentes en `a` que no están en `b` (`a AND NOT b`). */
  def difference(a: Ids, b: Ids): Ids =
    val out = Array.newBuilder[Int]
    @tailrec def loop(i: Int, j: Int): Unit =
      if i < a.length then
        if j >= b.length || a(i) < b(j) then
          out += a(i)
          loop(i + 1, j)
        else if a(i) == b(j) then loop(i + 1, j + 1)
        else loop(i, j + 1)
    loop(0, 0)
    IArray.unsafeFromArray(out.result())

  /** Búsqueda binaria: O(log N). */
  def contains(ids: Ids, value: Int): Boolean = indexOf(ids, value) >= 0

  /** Posición de `value` en el arreglo, o -1 si no está. Búsqueda binaria: O(log N). */
  def indexOf(ids: Ids, value: Int): Int =
    val at = insertionPoint(ids, value)
    if at < ids.length && ids(at) == value then at else -1

  /** Primera posición cuyo valor es >= `value` (donde habría que insertarlo para mantener el orden). */
  def insertionPoint(ids: Ids, value: Int): Int =
    @tailrec def search(low: Int, high: Int): Int =
      if low >= high then low
      else
        val mid = (low + high) >>> 1
        if ids(mid) < value then search(mid + 1, high) else search(low, mid)
    search(0, ids.length)
