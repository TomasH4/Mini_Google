package minigoogle

import minigoogle.index.{Analyzer, InvertedIndex}
import minigoogle.loader.CorpusLoader
import minigoogle.model.{Document, SearchResult}
import minigoogle.search.{SearchEngine, TopK}
import scala.util.Random

class SearchEngineTest extends munit.FunSuite:
  private def doc(id: Int, body: String): Document = Document(id, s"doc$id", body, Analyzer.wordCount(body))

  private def engineFor(bodies: String*): SearchEngine =
    val docs = bodies.zipWithIndex.map((body, i) => doc(i + 1, body))
    SearchEngine(InvertedIndex.build(docs).fold(e => fail(e.message), identity))

  // Ejemplo de la documentación del proyecto.
  private val engine = engineFor("scala es rapido y funcional", "java es rapido y seguro")

  private def ids(query: String): List[Int] =
    engine.search(query).fold(e => fail(e.message), _.map(_.docId).sorted)

  test("boolean operators") {
    assertEquals(ids("scala"), List(1))
    assertEquals(ids("scala OR java"), List(1, 2))
    assertEquals(ids("rapido AND seguro"), List(2))
    assertEquals(ids("rapido AND NOT seguro"), List(1))
    assertEquals(ids("(scala OR java) AND NOT lento"), List(1, 2))
    assertEquals(ids("NOT rapido"), Nil)
  }

  test("exact phrases need adjacent words in order") {
    assertEquals(ids("\"rapido y seguro\""), List(2))
    assertEquals(ids("\"seguro y rapido\""), Nil)
    assertEquals(ids("\"scala rapido\""), Nil)
    assertEquals(ids("\"\""), Nil)
  }

  test("phrases with a repeated word") {
    val repeated = engineFor("uno uno dos", "uno dos uno")
    assertEquals(repeated.search("\"uno uno\"").map(_.map(_.docId)), Right(List(1)))
  }

  test("BM25 score matches the formula") {
    // N = 2, n(scala) = 1, f = 1, |D| = avgdl = 5  =>  IDF = ln(2) y el factor de frecuencia vale 1.
    val score = engine.search("scala").map(_.head.score).fold(e => fail(e.message), identity)
    assertEqualsDouble(score, math.log(2.0), 1e-12)
  }

  test("rare terms weigh more than common ones") {
    val ranked = engineFor("el motor el motor", "el el el el", "el coche el coche")
      .search("el OR motor")
      .fold(e => fail(e.message), identity)
    assertEquals(ranked.head.docId, 1)
  }

  test("negated terms do not change the score") {
    val withNot = engine.search("scala AND NOT java").map(_.map(_.score))
    val plain = engine.search("scala").map(_.map(_.score))
    assertEquals(withNot, plain)
  }

  test("returns at most 10 results, best first, ties broken by document id") {
    val many = engineFor(List.fill(15)("palabra comun")*)
    val results = many.search("palabra").fold(e => fail(e.message), identity)
    assertEquals(results.map(_.docId), (1 to 10).toList)
  }

  test("an invalid query is an error value") {
    assert(engine.search("scala AND").isLeft)
    assert(engine.search("(scala OR java").isLeft)
  }

class TopKTest extends munit.FunSuite:

  test("selects the same results as sorting everything") {
    val random = Random(7)
    val results = Vector.tabulate(500)(i => SearchResult(i, s"d$i", (random.nextInt(50) / 10.0)))
    val expected = results.sortBy(r => (-r.score, r.docId)).take(10).toList
    assertEquals(TopK.select(results, 10), expected)
  }

  test("fewer results than k and k = 0") {
    val results = List(SearchResult(1, "a", 1.0), SearchResult(2, "b", 2.0))
    assertEquals(TopK.select(results, 10).map(_.docId), List(2, 1))
    assertEquals(TopK.select(results, 0), Nil)
  }

class SampleDocumentsTest extends munit.FunSuite:
  private val corpus = CorpusLoader.fromClasspath().fold(e => fail(e.message), identity)
  private val engine = SearchEngine(InvertedIndex.build(corpus.documents).fold(e => fail(e.message), identity))

  private def titles(query: String): Set[String] =
    engine.search(query).fold(e => fail(e.message), _.map(_.title).toSet)

  test("loads the 11 sample documents in manifest order") {
    assertEquals(corpus.errors, Vector.empty)
    assertEquals(corpus.documents.size, 11)
    assertEquals(corpus.documents.head.title, "doc1")
    assertEquals(corpus.documents.last.title, "regresion.md")
  }

  test("queries from the execution guide") {
    assertEquals(titles("\"document two content\""), Set("doc2"))
    assert(titles("seguridad AND owasp").contains("informe_seguridad_OWASP.md"))
    assert(!titles("informe NOT seguridad").exists(_.contains("seguridad")))
  }
