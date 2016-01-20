package com.campudus.tableaux

import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.Test
import org.junit.runner.RunWith
import org.vertx.scala.core.json.Json

import scala.concurrent.Future

@RunWith(classOf[VertxUnitRunner])
class TableOrderingTest extends TableauxTestBase {

  private def createEmptyTable(name: String) = {
    sendRequest("POST", "/tables", Json.obj("name" -> name)).map(_.getLong("id"))
  }

  private def regularTableJson(id: Long, name: String) = Json.obj("id" -> id, "name" -> name, "hidden" -> false)

  @Test
  def createTablesShouldStayInOrderOfCreation(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      tableId3 <- createEmptyTable("Third")
      tableId4 <- createEmptyTable("Fourth")
      tableId5 <- createEmptyTable("Fifth")
      tables <- sendRequest("GET", "/tables")
    } yield {

      val expected = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(1, "First"),
        regularTableJson(2, "Second"),
        regularTableJson(3, "Third"),
        regularTableJson(4, "Fourth"),
        regularTableJson(5, "Fifth")
      ))

      assertEquals(expected, tables)
    }
  }

  @Test
  def orderTableCanBeSetToStart(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      tableId3 <- createEmptyTable("Third")
      tableId4 <- createEmptyTable("Fourth")
      tableId5 <- createEmptyTable("Fifth")
      orderResult1 <- sendRequest("POST", s"/tables/$tableId5/order", Json.obj("location" -> "start"))
      tables1 <- sendRequest("GET", "/tables")
      orderResult2 <- sendRequest("POST", s"/tables/$tableId3/order", Json.obj("location" -> "start"))
      tables2 <- sendRequest("GET", "/tables")
    } yield {

      assertEquals(Json.obj("status" -> "ok"), orderResult1)
      assertEquals(Json.obj("status" -> "ok"), orderResult2)

      val expected1 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(5, "Fifth"),
        regularTableJson(1, "First"),
        regularTableJson(2, "Second"),
        regularTableJson(3, "Third"),
        regularTableJson(4, "Fourth")
      ))

      assertEquals(expected1, tables1)

      val expected2 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(3, "Third"),
        regularTableJson(5, "Fifth"),
        regularTableJson(1, "First"),
        regularTableJson(2, "Second"),
        regularTableJson(4, "Fourth")
      ))

      assertEquals(expected2, tables2)
    }
  }

  @Test
  def orderTableCanBeSetToEnd(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      tableId3 <- createEmptyTable("Third")
      tableId4 <- createEmptyTable("Fourth")
      tableId5 <- createEmptyTable("Fifth")
      orderResult1 <- sendRequest("POST", s"/tables/$tableId1/order", Json.obj("location" -> "end"))
      tables1 <- sendRequest("GET", "/tables")
      orderResult2 <- sendRequest("POST", s"/tables/$tableId3/order", Json.obj("location" -> "end"))
      tables2 <- sendRequest("GET", "/tables")
    } yield {

      assertEquals(Json.obj("status" -> "ok"), orderResult1)
      assertEquals(Json.obj("status" -> "ok"), orderResult2)

      val expected1 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(2, "Second"),
        regularTableJson(3, "Third"),
        regularTableJson(4, "Fourth"),
        regularTableJson(5, "Fifth"),
        regularTableJson(1, "First")
      ))

      assertEquals(expected1, tables1)

      val expected2 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(2, "Second"),
        regularTableJson(4, "Fourth"),
        regularTableJson(5, "Fifth"),
        regularTableJson(1, "First"),
        regularTableJson(3, "Third")
      ))

      assertEquals(expected2, tables2)
    }
  }

  @Test
  def orderTableCanBeSetToBefore(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      tableId3 <- createEmptyTable("Third")
      tableId4 <- createEmptyTable("Fourth")
      tableId5 <- createEmptyTable("Fifth")
      orderResult1 <- sendRequest("POST", s"/tables/$tableId1/order", Json.obj("location" -> "before", "id" -> tableId3))
      tables1 <- sendRequest("GET", "/tables")
      orderResult2 <- sendRequest("POST", s"/tables/$tableId3/order", Json.obj("location" -> "before", "id" -> tableId5))
      tables2 <- sendRequest("GET", "/tables")
    } yield {

      assertEquals(Json.obj("status" -> "ok"), orderResult1)
      assertEquals(Json.obj("status" -> "ok"), orderResult2)

      val expected1 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(2, "Second"),
        regularTableJson(1, "First"),
        regularTableJson(3, "Third"),
        regularTableJson(4, "Fourth"),
        regularTableJson(5, "Fifth")
      ))

      assertEquals(expected1, tables1)

      val expected2 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(2, "Second"),
        regularTableJson(1, "First"),
        regularTableJson(4, "Fourth"),
        regularTableJson(3, "Third"),
        regularTableJson(5, "Fifth")
      ))

      assertEquals(expected2, tables2)
    }
  }

  @Test
  def orderTableBeforeWithoutId(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      orderResult <- sendRequest("POST", s"/tables/$tableId1/order", Json.obj("location" -> "before")) recover {
        case TestCustomException(message, id, statusCode) =>
          (message, id, statusCode)
      }
      tables1 <- sendRequest("GET", "/tables")
    } yield {

      assertEquals(("(0) query parameter id not found", "error.arguments", 422), orderResult)

      val expected1 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(1, "First"),
        regularTableJson(2, "Second")
      ))

      assertEquals(expected1, tables1)
    }
  }

  @Test
  def orderTableWithInvalidLocation(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      orderResult <- sendRequest("POST", s"/tables/$tableId1/order", Json.obj("location" -> "blubb")) recover {
        case TestCustomException(message, id, statusCode) =>
          (message, id, statusCode)
      }
      tables1 <- sendRequest("GET", "/tables")
    } yield {

      assertEquals(("(1) 'location' value needs to be one of 'start', 'end', 'before'.", "error.arguments", 422), orderResult)

      val expected1 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(1, "First"),
        regularTableJson(2, "Second")
      ))

      assertEquals(expected1, tables1)
    }
  }

  @Test
  def orderTableWithNullLocation(implicit c: TestContext): Unit = okTest {
    for {
      tableId1 <- createEmptyTable("First")
      tableId2 <- createEmptyTable("Second")
      orderResult <- sendRequest("POST", s"/tables/$tableId1/order", Json.obj("id" -> 2)) recover {
        case TestCustomException(message, id, statusCode) =>
          (message, id, statusCode)
      }
      tables1 <- sendRequest("GET", "/tables")
    } yield {

      assertEquals(("(0) Warning: location is null\n" +
        "(1) 'location' value needs to be one of 'start', 'end', 'before'.", "error.arguments", 422), orderResult)

      val expected1 = Json.obj("status" -> "ok", "tables" -> Json.arr(
        regularTableJson(1, "First"),
        regularTableJson(2, "Second")
      ))

      assertEquals(expected1, tables1)
    }
  }

}
