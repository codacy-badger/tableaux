package com.campudus.tableaux

import org.junit.Test
import org.vertx.testtools.VertxAssert._
import org.vertx.scala.core.json.Json

/**
 * @author <a href="http://www.campudus.com">Joern Bernhardt</a>.
 */
class CreationTest extends TableauxTestBase {

  val createTableJson = Json.obj("tableName" -> "Test Nr. 1")

  @Test
  def createTable(): Unit = okTest {
    val expectedJson = Json.obj("tableId" -> 1, "tableName" -> "Test Nr. 1")
    val expectedJson2 = Json.obj("tableId" -> 2, "tableName" -> "Test Nr. 1")

    for {
      test1 <- sendRequestWithJson("POST", createTableJson, "/tables")
      test2 <- sendRequestWithJson("POST", createTableJson, "/tables")
    } yield {
      assertEquals(expectedJson, test1)
      assertEquals(expectedJson2, test2)
    }
  }

  @Test
  def createStringColumn(): Unit = okTest {
    val jsonObj = Json.obj("type" -> "text", "columnName" -> "Test Column 1")
    val expectedJson = Json.obj("tableId" -> 1, "columnId" -> 1, "columnName" -> "Test Column 1", "type" -> "text")
    val expectedJson2 = Json.obj("tableId" -> 1, "columnId" -> 2, "columnName" -> "Test Column 1", "type" -> "text")

    for {
      t <- sendRequestWithJson("POST", createTableJson, "/tables")
      test1 <- sendRequestWithJson("POST", jsonObj, "/tables/1/columns")
      test2 <- sendRequestWithJson("POST", jsonObj, "/tables/1/columns")
    } yield {
      assertEquals(expectedJson, test1)
      assertEquals(expectedJson2, test2)
    }
  }

  @Test
  def createNumberColumn(): Unit = okTest {
    val jsonObj = Json.obj("type" -> "numeric", "columnName" -> "Test Column 1")
    val expectedJson = Json.obj("tableId" -> 1, "columnId" -> 1, "columnName" -> "Test Column 1", "type" -> "numeric")
    val expectedJson2 = Json.obj("tableId" -> 1, "columnId" -> 2, "columnName" -> "Test Column 1", "type" -> "numeric")

    for {
      t <- sendRequestWithJson("POST", createTableJson, "/tables")
      test1 <- sendRequestWithJson("POST", jsonObj, "/tables/1/columns")
      test2 <- sendRequestWithJson("POST", jsonObj, "/tables/1/columns")
    } yield {
      assertEquals(expectedJson, test1)
      assertEquals(expectedJson2, test2)
    }
  }

  @Test
  def createRow(): Unit = okTest {
    val expectedJson = Json.obj("tableId" -> 1, "rowId" -> 1)
    val expectedJson2 = Json.obj("tableId" -> 1, "rowId" -> 2)

    for {
      t <- sendRequestWithJson("POST", createTableJson, "/tables")
      test1 <- sendRequest("POST", "/tables/1/rows")
      test2 <- sendRequest("POST", "/tables/1/rows")
    } yield {
      assertEquals(expectedJson, test1)
      assertEquals(expectedJson2, test2)
    }
  }

}