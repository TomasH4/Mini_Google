package minigoogle

import minigoogle.bench.{CorpusGenerator, GeneratorConfig, LatencyStats, Stats}
import minigoogle.loader.{CorpusLoader, DocumentLoader}
import minigoogle.query.QueryParser
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

class LoaderTest extends munit.FunSuite:

  test("reads text from .pdf files") {
    val bytes = new java.io.ByteArrayOutputStream()
    scala.util.Using.resource(new org.apache.pdfbox.pdmodel.PDDocument()) { pdf =>
      val page = new org.apache.pdfbox.pdmodel.PDPage()
      pdf.addPage(page)
      scala.util.Using.resource(new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) { content =>
        content.beginText()
        content.setFont(
          new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),
          12
        )
        content.newLineAtOffset(50, 700)
        content.showText("motor de busqueda en pdf")
        content.endText()
      }
      pdf.save(bytes)
    }
    val text = DocumentLoader.read(new ByteArrayInputStream(bytes.toByteArray), "informe.pdf")
    assert(text.exists(_.contains("motor de busqueda en pdf")), text.toString)
  }

  test("reads text from .docx files") {
    val bytes = new java.io.ByteArrayOutputStream()
    scala.util.Using.resource(new org.apache.poi.xwpf.usermodel.XWPFDocument()) { docx =>
      docx.createParagraph().createRun().setText("motor de busqueda en word")
      docx.write(bytes)
    }
    val text = DocumentLoader.read(new ByteArrayInputStream(bytes.toByteArray), "informe.docx")
    assert(text.exists(_.contains("motor de busqueda en word")), text.toString)
  }

  test("a corrupt .pdf is an error value, not an exception") {
    assert(DocumentLoader.read(new ByteArrayInputStream("no soy un pdf".getBytes(UTF_8)), "roto.pdf").isLeft)
  }

  test("unsupported extensions are reported as errors") {
    val result = DocumentLoader.read(new ByteArrayInputStream(Array.emptyByteArray), "imagen.png")
    assert(result.isLeft)
  }

  test("TSV corpus: ID<TAB>title<TAB>text per line; malformed lines are reported and skipped") {
    val file = Files.createTempFile("corpus", ".tsv")
    try
      val content = "7\tUno\tprimer texto\nlinea sin tabs\nx\tID invalido\ttexto\n\n9\tDos\tsegundo\ttexto\n"
      Files.writeString(file, content, UTF_8)
      val corpus = CorpusLoader.fromTsv(file).fold(e => fail(e.message), identity)
      assertEquals(
        corpus.documents.map(d => (d.id, d.title, d.body)),
        Vector((7, "Uno", "primer texto"), (9, "Dos", "segundo\ttexto"))
      )
      assertEquals(corpus.errors.map(_.source.split(':').last), Vector("2", "3"))
    finally Files.deleteIfExists(file)
  }

  test("a missing directory is an error") {
    assert(CorpusLoader.fromDirectory(java.nio.file.Paths.get("no-existe-123")).isLeft)
  }

class BenchToolsTest extends munit.FunSuite:

  test("latency percentiles (nearest rank)") {
    val stats = LatencyStats.fromNanos((1L to 100L).map(_ * 1000000L))
    assertEqualsDouble(stats.p50Ms, 50.0, 1e-9)
    assertEqualsDouble(stats.p95Ms, 95.0, 1e-9)
    assertEqualsDouble(stats.maxMs, 100.0, 1e-9)
    assertEqualsDouble(stats.meanMs, 50.5, 1e-9)
  }

  test("median") {
    assertEqualsDouble(Stats.median(List(3.0, 1.0, 2.0)), 2.0, 1e-12)
    assertEqualsDouble(Stats.median(List(4.0, 1.0, 2.0, 3.0)), 2.5, 1e-12)
  }

  test("synthetic words are unique") {
    val words = (0 until 10000).map(CorpusGenerator.synthetic)
    assertEquals(words.distinct.size, words.size)
  }

  test("the generator is deterministic and its queries are valid") {
    val dir = Files.createTempDirectory("gen")
    def generate(name: String): (String, String) =
      val config = GeneratorConfig(50, 42L, 20, 60, 2000, dir.resolve(s"$name.tsv"), 30, Some(dir.resolve(s"$name.txt")))
      CorpusGenerator.generate(config).fold(e => fail(e), identity)
      (Files.readString(config.out, UTF_8), Files.readString(dir.resolve(s"$name.txt"), UTF_8))
    val first = generate("a")
    val second = generate("b")
    assertEquals(first, second)
    val queries = first._2.linesIterator.toList
    assertEquals(queries.size, 30)
    queries.foreach(q => assert(QueryParser.parse(q).isRight, s"invalid generated query: $q"))
    val rows = first._1.linesIterator.map(_.split("\t", -1)).toList
    assertEquals(rows.size, 50)
    assert(rows.forall(_.length == 3), "every line must be ID<TAB>title<TAB>text")
    assertEquals(rows.map(_.head), (1 to 50).map(_.toString).toList)
    val loaded = CorpusLoader.fromTsv(dir.resolve("a.tsv")).fold(e => fail(e.message), identity)
    assertEquals((loaded.documents.size, loaded.errors.size), (50, 0))
  }
