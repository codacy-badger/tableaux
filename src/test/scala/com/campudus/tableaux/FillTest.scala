package com.campudus.tableaux

import com.campudus.tableaux.testtools.RequestCreation._
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.{Ignore, Test}
import org.junit.runner.RunWith
import org.vertx.scala.core.json.Json

@RunWith(classOf[VertxUnitRunner])
class FillTest extends TableauxTestBase {

  val createTableJson = Json.obj("name" -> "Test Nr. 1")
  val createStringColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "text", "name" -> "Test Column 1")))
  val createNumberColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "numeric", "name" -> "Test Column 2")))
  val createBooleanColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "boolean", "name" -> "Test Column 3")))
  val createDateColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "date", "name" -> "Test Column 4")))
  val createDateTimeColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "datetime", "name" -> "Test Column 5")))

  @Test
  def fillSingleCellWithNull(implicit c: TestContext): Unit = okTest {
    val fillStringCellJson = Json.obj("value" -> null)

    val expectedJson = Json.obj("status" -> "ok")

    val expectedCell = Json.obj("status" -> "ok", "value" -> null)

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createStringColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      fillResult <- sendRequest("POST", "/tables/1/columns/1/rows/1", fillStringCellJson)
      cellResult <- sendRequest("GET", "/tables/1/columns/1/rows/1")
    } yield {
      assertEquals(expectedJson, fillResult)
      assertEquals(expectedCell, cellResult)
    }
  }

  @Test
  def fillSingleStringCell(implicit c: TestContext): Unit = okTest {
    val fillStringCellJson = Json.obj("value" -> "Test Fill 1")

    val expectedJson = Json.obj("status" -> "ok")
    val expectedGet = Json.obj("status" -> "ok", "value" -> "Test Fill 1")

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createStringColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      test <- sendRequest("POST", "/tables/1/columns/1/rows/1", fillStringCellJson)
      getResult <- sendRequest("GET", "/tables/1/columns/1/rows/1")
    } yield {
      assertEquals(expectedJson, test)
      assertEquals(expectedGet, getResult)
    }
  }

  @Test
  def fillSingleNumberCell(implicit c: TestContext): Unit = okTest {
    val fillNumberCellJson = Json.obj("value" -> 101)

    val expectedJson = Json.obj("status" -> "ok")
    val expectedGet = Json.obj("status" -> "ok", "value" -> 101)

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createNumberColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      test <- sendRequest("POST", "/tables/1/columns/1/rows/1", fillNumberCellJson)
      getResult <- sendRequest("GET", "/tables/1/columns/1/rows/1")
    } yield {
      assertEquals(expectedJson, test)
      assertEquals(expectedGet, getResult)
    }
  }

  @Ignore("Bug is in underlying driver - cannot fix here")
  @Test
  def fillNumberCellWithFloatingNumber(implicit c: TestContext): Unit = okTest {
    val expectOk = Json.obj("status" -> "ok")

    for {
      (tableId, columnId, rowId) <- createSimpleTableWithCell("table1", Numeric("num-column"))

      test0 <- sendRequest("PUT", s"/tables/$tableId/columns/$columnId/rows/$rowId", Json.obj("value" -> 1234))
      getResult0 <- sendRequest("GET", s"/tables/$tableId/columns/$columnId/rows/$rowId")

      test3 <- sendRequest("PUT", s"/tables/$tableId/columns/$columnId/rows/$rowId", Json.obj("value" -> 123.123))
      getResult3 <- sendRequest("GET", s"/tables/$tableId/columns/$columnId/rows/$rowId")
    } yield {
      assertEquals(expectOk, test0)
      assertEquals(Json.obj("status" -> "ok", "value" -> 1234), getResult0)
      assertEquals(expectOk, test3)
      assertEquals(Json.obj("status" -> "ok", "value" -> 123.123), getResult3)
    }
  }

  @Test
  def fillSingleBooleanCell(implicit c: TestContext): Unit = okTest {
    val fillBooleanCellJson = Json.obj("value" -> true)

    val expectedJson = Json.obj("status" -> "ok")
    val expectedGet = Json.obj("status" -> "ok", "value" -> true)

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createBooleanColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      test <- sendRequest("POST", "/tables/1/columns/1/rows/1", fillBooleanCellJson)
      getResult <- sendRequest("GET", "/tables/1/columns/1/rows/1")
    } yield {
      assertEquals(expectedJson, test)
      assertEquals(expectedGet, getResult)
    }
  }

  @Test
  def fillDateCell(implicit c: TestContext): Unit = okTest {
    val expectedJson = Json.obj("status" -> "ok")
    val expectedGet = Json.obj("status" -> "ok", "value" -> "2015-01-01")

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createDateColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      test <- sendRequest("POST", "/tables/1/columns/1/rows/1", Json.obj("value" -> "2015-01-01"))
      getResult <- sendRequest("GET", "/tables/1/columns/1/rows/1")
    } yield {
      assertEquals(expectedJson, test)
      assertEquals(expectedGet, getResult)
    }
  }

  @Test
  def fillDateTimeCell(implicit c: TestContext): Unit = okTest {
    val expectedJson = Json.obj("status" -> "ok")
    val expectedGet = Json.obj("status" -> "ok", "value" -> "2015-01-01T13:37:47.111Z")

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createDateTimeColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      test <- sendRequest("POST", "/tables/1/columns/1/rows/1", Json.obj("value" -> "2015-01-01T14:37:47.111+01"))
      getResult <- sendRequest("GET", "/tables/1/columns/1/rows/1")
    } yield {
      assertEquals(expectedJson, test)
      assertEquals(expectedGet, getResult)
    }
  }

  @Test
  def fillTwoDifferentCell(implicit c: TestContext): Unit = okTest {
    val fillNumberCellJson = Json.obj("value" -> 101)
    val fillStringCellJson = Json.obj("value" -> "Test Fill 1")

    val expectedJson = Json.obj("status" -> "ok")
    val expectedJson2 = Json.obj("status" -> "ok")
    val expectedGet1 = Json.obj("status" -> "ok", "value" -> 101)
    val expectedGet2 = Json.obj("status" -> "ok", "value" -> "Test Fill 1")

    for {
      _ <- sendRequest("POST", "/tables", createTableJson)
      _ <- sendRequest("POST", "/tables/1/columns", createNumberColumnJson)
      _ <- sendRequest("POST", "/tables/1/columns", createStringColumnJson)
      _ <- sendRequest("POST", "/tables/1/rows")
      test1 <- sendRequest("POST", "/tables/1/columns/1/rows/1", fillNumberCellJson)
      test2 <- sendRequest("POST", "/tables/1/columns/2/rows/1", fillStringCellJson)
      getResult1 <- sendRequest("GET", "/tables/1/columns/1/rows/1")
      getResult2 <- sendRequest("GET", "/tables/1/columns/2/rows/1")
    } yield {
      assertEquals(expectedJson, test1)
      assertEquals(expectedJson2, test2)
      assertEquals(expectedGet1, getResult1)
      assertEquals(expectedGet2, getResult2)
    }
  }
}
