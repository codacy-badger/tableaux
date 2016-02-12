package com.campudus.tableaux

import com.campudus.tableaux.ArgumentChecker._
import org.junit.Assert._
import org.junit.Test
import org.vertx.scala.core.json.Json

class ArgumentCheckerTest {

  @Test
  def checkValidNotNull(): Unit = {
    assertEquals(OkArg(123), notNull(123, ""))
    assertEquals(OkArg("abc"), notNull("abc", ""))
    assertEquals(OkArg(""), notNull("", ""))
    assertEquals(OkArg(0), notNull(0, ""))
    assertEquals(OkArg(Nil), notNull(Nil, ""))
  }

  @Test
  def checkInvalidNotNull(): Unit = {
    assertEquals(FailArg(InvalidJsonException("Warning: test is null", "null")), notNull(null, "test"))
  }

  @Test
  def checkValidGreaterZero(): Unit = {
    assertEquals(OkArg(123), greaterZero(123))
    assertEquals(OkArg(1), greaterZero(1))
    assertEquals(OkArg(Long.MaxValue), greaterZero(Long.MaxValue))
  }

  @Test
  def checkInvalidGreaterZero(): Unit = {
    assertEquals(FailArg(InvalidJsonException("Argument -1 is not greater than zero", "invalid")), greaterZero(-1))
    assertEquals(FailArg(InvalidJsonException("Argument 0 is not greater than zero", "invalid")), greaterZero(0))
    assertEquals(FailArg(InvalidJsonException(s"Argument ${Long.MinValue} is not greater than zero", "invalid")), greaterZero(Long.MinValue))
  }

  @Test
  def checkValidGreaterThan(): Unit = {
    assertEquals(OkArg(123), greaterThan(123, 121, "test"))
    assertEquals(OkArg(-10), greaterThan(-10, -20, "test"))
    assertEquals(OkArg(Long.MaxValue), greaterThan(Long.MaxValue, Long.MaxValue - 1, "test"))
  }

  @Test
  def checkInvalidGreaterThan(): Unit = {
    assertEquals(FailArg(InvalidJsonException("Argument test (-1) is less than 0.", "invalid")), greaterThan(-1, 0, "test"))
    assertEquals(FailArg(InvalidJsonException("Argument test (100) is less than 1000.", "invalid")), greaterThan(100, 1000, "test"))
    assertEquals(FailArg(InvalidJsonException(s"Argument test (${Long.MinValue}) is less than 0.", "invalid")), greaterThan(Long.MinValue, 0, "test"))
  }

  @Test
  def checkValidNonEmpty(): Unit = {
    assertEquals(OkArg(Seq(123)), nonEmpty(Seq(123), "test"))
    assertEquals(OkArg(Seq("abc")), nonEmpty(Seq("abc"), "test"))
    assertEquals(OkArg(Seq("")), nonEmpty(Seq(""), "test"))
    assertEquals(OkArg(Seq(Seq(123))), nonEmpty(Seq(Seq(123)), "test"))
    assertEquals(OkArg(Seq(123, 123)), nonEmpty(Seq(123, 123), "test"))
    assertEquals(OkArg(Seq(Seq(123), Seq(123))), nonEmpty(Seq(Seq(123), Seq(123)), "test"))
  }

  @Test
  def checkInvalidNonEmpty(): Unit = {
    assertEquals(FailArg(InvalidJsonException("Warning: test is empty.", "empty")), nonEmpty(Seq(), "test"))
  }

  @Test
  def checkValidArguments(): Unit = {
    checkArguments(notNull(123, "test"), greaterZero(1), greaterZero(2), notNull("foo", "test"), nonEmpty(Seq(123), "test"))
  }

  @Test
  def checkInvalidArguments(): Unit = {
    try {
      checkArguments(notNull(null, "test"), greaterZero(1), greaterZero(-4), notNull("foo", "test"))
      fail("Should throw an exception")
    } catch {
      case ex: IllegalArgumentException =>
        assertEquals("(0) Warning: test is null\n(2) Argument -4 is not greater than zero", ex.getMessage)
      case _: Throwable => fail("Should throw an IllegalArgumentException")
    }
  }

  @Test
  def checkIsDefinedAnyOption(): Unit = {
    checkArguments(isDefined(Seq(Some(1), Some("text"), None)))

    try {
      checkArguments(isDefined(Seq(None, None, None)))
      fail("Should throw an exception")
    } catch {
      case ex: IllegalArgumentException => assertEquals("(0) Non of these options has a value. ()", ex.getMessage)
      case _: Throwable => fail(s"Should throw an IllegalArgumentException")
    }
  }

  @Test
  def checkIsDefined(): Unit = {
    checkArguments(isDefined(Some("is defined"), "test"))

    try {
      checkArguments(isDefined(None, "text"))
      fail("Should throw an exception")
    } catch {
      case ex: IllegalArgumentException => assertEquals("(0) query parameter text not found", ex.getMessage)
      case _: Throwable => fail(s"Should throw an IllegalArgumentException")
    }
  }

  @Test
  def checkOneOf(): Unit = {
    checkArguments(oneOf("b", List("a", "b", "c"), "oneof"))

    try {
      checkArguments(oneOf("d", List("a", "b", "c"), "oneof"))
      fail("Should throw an exception")
    } catch {
      case ex: IllegalArgumentException => assertEquals("(0) 'oneof' value needs to be one of 'a', 'b', 'c'.", ex.getMessage)
      case _: Throwable => fail("Should throw an IllegalArgumentException")
    }
  }

  @Test
  def checkHasValueOfType(): Unit = {
    val json = Json.obj(
      "string" -> "valid", // valid
      "no_string" -> 123, // invalid

      "array" -> Json.arr(1, 2, 3), // valid
      "no_array" -> "no array", // invalid

      "long" -> Long.MaxValue, // valid
      "no_long" -> "no long" // invalid
    )

    assertEquals(OkArg(json.getString("string")), hasString("string", json))
    assertEquals(OkArg(json.getJsonArray("array")), hasArray("array", json))
    assertEquals(OkArg(json.getLong("long").toLong), hasLong("long", json))

    assertEquals(FailArg(InvalidJsonException("Warning: test is null", "null")), hasString("test", json))
    assertEquals(FailArg(InvalidJsonException("Warning: test is null", "null")), hasArray("test", json))
    assertEquals(FailArg(InvalidJsonException("Warning: test is null", "null")), hasLong("test", json))

    assertEquals(FailArg(InvalidJsonException(
      "Warning: no_string should be another type. Error: java.lang.Integer cannot be cast to java.lang.CharSequence", "invalid")),
      hasString("no_string", json)
    )

    assertEquals(FailArg(InvalidJsonException(
      "Warning: no_array should be another type. Error: java.lang.String cannot be cast to io.vertx.core.json.JsonArray", "invalid")),
      hasArray("no_array", json)
    )

    assertEquals(FailArg(InvalidJsonException(
      "Warning: no_long should be another type. Error: java.lang.String cannot be cast to java.lang.Number", "invalid")),
      hasLong("no_long", json)
    )
  }

  @Test
  def checkForAllObjectValues(): Unit = {

    val okJson1 = Json.obj("de_DE" -> "Eine beliebige Zeichenkette")
    val okJson2 = Json.obj("de_DE" -> "Eine beliebige Zeichenkette", "en_US" -> "A random string")
    val okJson3 = Json.obj()
    val failJson1 = Json.obj("de_DE" -> false)
    val failJson2 = Json.obj("de_DE" -> "Eine beliebige Zeichenkette", "en_US" -> true)
    val failJson3 = Json.obj("de_DE" -> null)

    assertEquals(OkArg(okJson1), checkForAllValues[String](okJson1, _.isInstanceOf[String], "obj1"))
    assertEquals(OkArg(okJson2), checkForAllValues[String](okJson2, _.isInstanceOf[String], "obj2"))
    assertEquals(OkArg(okJson3), checkForAllValues[String](okJson3, _.isInstanceOf[String], "obj3"))

    assertEquals(FailArg(InvalidJsonException(
      "Warning: obj4 has incorrectly typed value at key 'de_DE'.", "invalid")),
      checkForAllValues[String](failJson1, _.isInstanceOf[String], "obj4"))

    assertEquals(FailArg(InvalidJsonException(
      "Warning: obj5 has incorrectly typed value at key 'en_US'.", "invalid")),
      checkForAllValues[String](failJson2, _.isInstanceOf[String], "obj5"))

    assertEquals(FailArg(InvalidJsonException(
      "Warning: obj6 has value 'de_DE' pointing at null.", "invalid")),
      checkForAllValues[String](failJson3, _.isInstanceOf[String], "obj6"))

  }
}
