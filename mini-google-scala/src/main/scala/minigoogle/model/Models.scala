package minigoogle.model

/** Documento inmutable.
  *
  * @param length número de palabras del documento (|D| en la fórmula BM25).
  */
final case class Document(id: Int, title: String, body: String, length: Int)

/** Aparición de un término dentro de un documento: el ID del documento y las posiciones (ordenadas de menor a
  * mayor) donde aparece. Existe un único posting por par (término, documento).
  */
final case class Posting(docId: Int, positions: IArray[Int]):
  def termFrequency: Int = positions.length

/** Resultado de una búsqueda ya puntuado con BM25. */
final case class SearchResult(docId: Int, title: String, score: Double)
