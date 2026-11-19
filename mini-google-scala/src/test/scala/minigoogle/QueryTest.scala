package minigoogle

import minigoogle.query.{Query, QueryParser, Token, Tokenizer}
import minigoogle.query.Query.*

class TokenizerTest extends munit.FunSuite:

  test("recognizes parentheses, operators, terms and phrases") {
    assertEquals(
      Tokenizer.tokenize("(scala OR java) AND NOT \"servidor lento\""),
      List(
        Token.LParen,
        Token.Term("scala"),
        Token.Or,
        Token.Term("java"),
        Token.RParen,
        Token.And,
        Token.Not,
        Token.Phrase("servidor lento")
      )
    )
  }

  test("operators are case-insensitive") {
    assertEquals(Tokenizer.tokenize("a and b or not c"), List(Token.Term("a"), Token.And, Token.Term("b"), Token.Or, Token.Not, Token.Term("c")))
  }

  test("punctuation separates terms and is dropped") {
    assertEquals(Tokenizer.tokenize("hola, mundo!"), List(Token.Term("hola"), Token.Term("mundo")))
  }

  test("an unclosed quote takes the rest of the input as the phrase") {
    assertEquals(Tokenizer.tokenize("a AND \"b c"), List(Token.Term("a"), Token.And, Token.Phrase("b c")))
  }

  test("letters with accents belong to the term") {
    assertEquals(Tokenizer.tokenize("inyección"), List(Token.Term("inyección")))
  }

class QueryParserTest extends munit.FunSuite:

  private def parse(text: String): Query = QueryParser.parse(text).fold(e => fail(e.message), identity)

  test("AND binds tighter than OR") {
    assertEquals(parse("a OR b AND c"), Or(Term("a"), And(Term("b"), Term("c"))))
  }

  test("NOT binds tighter than AND") {
    assertEquals(parse("NOT a AND b"), And(Not(Term("a")), Term("b")))
  }

  test("parentheses change the precedence") {
    assertEquals(parse("(a OR b) AND NOT c"), And(Or(Term("a"), Term("b")), Not(Term("c"))))
  }

  test("operators associate to the left") {
    assertEquals(parse("a AND b AND c"), And(And(Term("a"), Term("b")), Term("c")))
  }

  test("terms and phrase words are lower-cased and cleaned") {
    assertEquals(parse("Scala AND \"Arquitectura, LIMPIA.\""), And(Term("scala"), Phrase(List("arquitectura", "limpia"))))
  }

  test("adjacent operands are an implicit AND") {
    assertEquals(parse("informe NOT seguridad"), And(Term("informe"), Not(Term("seguridad"))))
    assertEquals(parse("a b OR c"), Or(And(Term("a"), Term("b")), Term("c")))
  }

  test("invalid queries return an error instead of throwing") {
    List("", "(a OR b", "a AND", "AND a", ")", "a )", "NOT", "a OR").foreach { text =>
      assert(QueryParser.parse(text).isLeft, s"expected an error for: '$text'")
    }
  }

  test("negated terms do not score; a double NOT scores again") {
    assertEquals(Query.scoringTerms(parse("a AND NOT b")), List("a"))
    assertEquals(Query.scoringTerms(parse("\"x y\" OR NOT NOT z")), List("x", "y", "z"))
  }
