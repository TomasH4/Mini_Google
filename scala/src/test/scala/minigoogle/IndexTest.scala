package minigoogle

import minigoogle.index.{Analyzer, DocIds, IndexError, InvertedIndex}
import minigoogle.model.Document

class DocIdsTest extends munit.FunSuite:
  private def ids(values: Int*): IArray[Int] = IArray.from(values)

  test("intersect, union and difference over sorted arrays") {
    val a = ids(1, 3, 5, 7)
    val b = ids(3, 4, 5, 8)
    assertEquals(DocIds.intersect(a, b).toList, List(3, 5))
    assertEquals(DocIds.union(a, b).toList, List(1, 3, 4, 5, 7, 8))
    assertEquals(DocIds.difference(a, b).toList, List(1, 7))
  }

  test("operations with empty sets") {
    assertEquals(DocIds.intersect(ids(1, 2), DocIds.empty).toList, Nil)
    assertEquals(DocIds.union(DocIds.empty, ids(1, 2)).toList, List(1, 2))
    assertEquals(DocIds.difference(ids(1, 2), DocIds.empty).toList, List(1, 2))
  }

  test("contains, indexOf and insertionPoint use binary search") {
    val values = ids(2, 4, 6, 8, 10)
    assert(values.forall(DocIds.contains(values, _)))
    assert(!List(1, 3, 5, 11).exists(DocIds.contains(values, _)))
    assert(!DocIds.contains(DocIds.empty, 1))
    assertEquals(DocIds.indexOf(values, 8), 3)
    assertEquals(DocIds.indexOf(values, 7), -1)
    assertEquals(List(1, 2, 5, 10, 11).map(DocIds.insertionPoint(values, _)), List(0, 0, 2, 4, 5))
  }

class AnalyzerTest extends munit.FunSuite:

  test("lower-cases, strips edge punctuation and keeps the original positions") {
    assertEquals(Analyzer.tokenize("Hola, mundo -- ¡Scala!").toList, List("hola" -> 0, "mundo" -> 1, "scala" -> 3))
  }

  test("internal punctuation is kept") {
    assertEquals(Analyzer.tokenize("e-mail don't").map(_._1).toList, List("e-mail", "don't"))
  }

  test("normalize gives the same result as the regex used by the Java version") {
    val regex = java.util.regex.Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")
    val alphabet = "aZñÁ9²½Ⅻ.,;-¡!?\"'()[] _🙂\uD800ß"
    val random = scala.util.Random(3)
    val samples = List("", "...", "¡Hola!", "(scala)", "x", "2024.", "«texto»", "🙂emoji🙂") ++
      List.fill(3000)(String(Array.fill(random.nextInt(8))(alphabet(random.nextInt(alphabet.length)))))
    samples.foreach(word => assertEquals(Analyzer.normalize(word), regex.matcher(word).replaceAll(""), s"word: '$word'"))
  }

class InvertedIndexTest extends munit.FunSuite:
  private def doc(id: Int, body: String): Document = Document(id, s"doc$id", body, Analyzer.wordCount(body))

  private val index = InvertedIndex
    .build(List(doc(1, "scala es rapido y funcional"), doc(2, "java es rapido y seguro"), doc(3, "scala scala scala")))
    .fold(e => fail(e.message), identity)

  test("one posting per document with all its positions") {
    assertEquals(index.postingList("scala").docIds.toList, List(1, 3))
    assertEquals(index.posting("scala", 3).map(_.positions.toList), Some(List(0, 1, 2)))
  }

  test("document frequency and term frequency") {
    assertEquals(index.documentFrequency("scala"), 2)
    assertEquals(index.termFrequency("scala", 3), 3)
    assertEquals(index.termFrequency("scala", 2), 0)
    assertEquals(index.documentFrequency("inexistente"), 0)
  }

  test("average document length") {
    assertEqualsDouble(index.avgDocLength, (5 + 5 + 3) / 3.0, 1e-12)
  }

  test("adding returns a new index and leaves the old one untouched") {
    val bigger = index.add(doc(4, "rust es seguro")).fold(e => fail(e.message), identity)
    assertEquals(bigger.size, 4)
    assertEquals(index.size, 3)
    assertEquals(index.documentFrequency("rust"), 0)
    assertEquals(bigger.documentFrequency("rust"), 1)
  }

  test("documents added out of order keep the posting lists sorted") {
    val shuffled = InvertedIndex.build(List(doc(5, "x"), doc(2, "x"), doc(9, "x"), doc(1, "x"))).fold(e => fail(e.message), identity)
    assertEquals(shuffled.docIds("x").toList, List(1, 2, 5, 9))
    assertEquals(shuffled.allDocIds.toList, List(1, 2, 5, 9))
  }

  test("a duplicated document id is rejected") {
    assertEquals(index.add(doc(1, "otro")), Left(IndexError.DuplicateDocumentId(1)))
    assertEquals(InvertedIndex.build(List(doc(1, "a"), doc(2, "b"), doc(1, "c"))), Left(IndexError.DuplicateDocumentId(1)))
  }

  test("bulk build and incremental add produce the same index") {
    val docs = List(doc(3, "c a b"), doc(1, "a b a"), doc(2, "b, c."))
    val bulk = InvertedIndex.build(docs).fold(e => fail(e.message), identity)
    val incremental = docs
      .foldLeft[Either[IndexError, InvertedIndex]](Right(InvertedIndex.empty))((acc, d) => acc.flatMap(_.add(d)))
      .fold(e => fail(e.message), identity)
    def snapshot(i: InvertedIndex) =
      (i.postings.view.mapValues(_.postings.map(p => (p.docId, p.positions.toList)).toList).toMap, i.documents, i.totalLength)
    assertEquals(snapshot(bulk), snapshot(incremental))
  }
