package com.campudus.tableaux.api.content

import com.campudus.tableaux.database.DatabaseConnection
import com.campudus.tableaux.testtools.RequestCreation.{CurrencyCol, MultiCountry}
import com.campudus.tableaux.testtools.TableauxTestBase
import io.vertx.core.json.JsonArray
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.scala.SQLConnection
import org.junit.{Ignore, Test}
import org.junit.runner.RunWith
import org.skyscreamer.jsonassert.{JSONAssert, JSONCompareMode}
import org.vertx.scala.core.json.{Json, JsonObject}

@RunWith(classOf[VertxUnitRunner])
class CreateHistoryTest extends TableauxTestBase {

  @Test
  def changeSimpleValue_historyAfterDefaultTableCreation(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "event": "cell_changed",
          |  "columnType": "text",
          |  "languageType": "neutral",
          |  "value": "table1row1"
          |}
        """.stripMargin

      for {
        _ <- createDefaultTable()
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows").get[JsonObject](0)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_changeACellInEmptyRow(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "event": "cell_changed",
          |  "columnType": "text",
          |  "languageType": "neutral",
          |  "value": "my first change"
          |}
        """.stripMargin

      val newValue = Json.obj("value" -> "my first change")

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows").get[JsonObject](0)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_changeACellMultipleTimes(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "numeric",
          |    "languageType": "neutral",
          |    "value": 42
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "numeric",
          |    "languageType": "neutral",
          |    "value": 1337
          |  },{
          |    "event": "cell_changed",
          |    "columnType": "numeric",
          |    "languageType": "neutral",
          |    "value": 1123581321
          |  }
          |]
        """.stripMargin

      for {
        _ <- createEmptyDefaultTable()

        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", Json.obj("value" -> 42))
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", Json.obj("value" -> 1337))
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", Json.obj("value" -> 1123581321))
        history <- sendRequest("GET", "/tables/1/columns/2/rows/1/history")
        historyRows = history.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyRows.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_text(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "text",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "first change"
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "text",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "second change"
          |    }
          |  }
          |]
        """.stripMargin

      val newValue1 = Json.obj("value" -> Json.obj("de-DE" -> "first change"))
      val newValue2 = Json.obj("value" -> Json.obj("de-DE" -> "second change"))

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue1)
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue2)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_boolean(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "boolean",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": true
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "boolean",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": false
          |    }
          |  }
          |]
        """.stripMargin

      val newValue1 = Json.obj("value" -> Json.obj("de-DE" -> true))
      val newValue2 = Json.obj("value" -> Json.obj("de-DE" -> false))

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", newValue1)
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", newValue2)
        test <- sendRequest("GET", "/tables/1/columns/2/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_numeric(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "numeric",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": 42
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "numeric",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": 1337
          |    }
          |  }
          |]
        """.stripMargin

      val newValue1 = Json.obj("value" -> Json.obj("de-DE" -> 42))
      val newValue2 = Json.obj("value" -> Json.obj("de-DE" -> 1337))

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", newValue1)
        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", newValue2)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_datetime(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "datetime",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "2019-01-18T00:00:00.000Z"
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "datetime",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "2018-12-12T00:00:00.000Z"
          |    }
          |  }
          |]
        """.stripMargin

      val newValue1 = Json.obj("value" -> Json.obj("de-DE" -> "2019-01-18T00:00:00.000Z"))
      val newValue2 = Json.obj("value" -> Json.obj("de-DE" -> "2018-12-12T00:00:00.000Z"))

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/7/rows/1", newValue1)
        _ <- sendRequest("POST", "/tables/1/columns/7/rows/1", newValue2)
        test <- sendRequest("GET", "/tables/1/columns/7/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_currency(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "currency",
          |    "languageType": "country",
          |    "value": {
          |      "DE": 2999.99
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "currency",
          |    "languageType": "country",
          |    "value": {
          |      "DE": 4000
          |    }
          |  }
          |]
        """.stripMargin

      val multiCountryCurrencyColumn = MultiCountry(CurrencyCol("currency-column"), Seq("DE", "GB"))

      for {
        _ <- createSimpleTableWithCell("table1", multiCountryCurrencyColumn)

        _ <- sendRequest("PUT", s"/tables/1/columns/1/rows/1", Json.obj("value" -> Json.obj("DE" -> 2999.99)))
        _ <- sendRequest("PUT", s"/tables/1/columns/1/rows/1", Json.obj("value" -> Json.obj("DE" -> 4000)))
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_multipleLanguagesAtOnce(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "value": {
          |      "de-DE": "first de-DE change"
          |    }
          |  }, {
          |    "value": {
          |      "en-GB": "first en-GB change"
          |    }
          |  }
          |]
        """.stripMargin

      val newValue1 = Json.obj("value" -> Json.obj("de-DE" -> "first de-DE change", "en-GB" -> "first en-GB change"))

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue1)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

}

@RunWith(classOf[VertxUnitRunner])
class CreateSimpleLinkHistoryTest extends LinkTestBase {

  def getLinksJsonArray(obj: JsonObject, pos: Int = 0): JsonArray = {
    obj.getJsonArray("rows", Json.emptyArr()).getJsonObject(pos).getJsonArray("value")
  }

  @Test
  def changeLink_addLink(implicit c: TestContext): Unit = {
    okTest {

      val putLink = Json.obj("value" -> Json.obj("values" -> Json.arr(1)))

      val expected =
        """
          |[
          |  {"id": 1, "value": "table2row1"}
          |]
          |""".stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLink)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLink_addTwoLinksAtOnce(implicit c: TestContext): Unit = {
    okTest {

      val putLinks = Json.obj("value" -> Json.obj("values" -> Json.arr(1, 2)))

      val expected =
        """
          |[
          |  {"id": 1, "value": "table2row1"},
          |  {"id": 2, "value": "table2row2"}
          |]
        """.stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLinks)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLink_singleLanguageMultiIdentifiers(implicit c: TestContext): Unit = {
    okTest {

      val putLink = Json.obj("value" -> Json.obj("values" -> Json.arr(1)))

      val expected =
        """
          |[
          |  {"id": 1, "value": "table2row1 1"}
          |]
          |""".stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        // Change target table structure to have a second identifier
        _ <- sendRequest("POST", s"/tables/2/columns/2", Json.obj("identifier" -> true))

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLink)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLink_addSecondLink(implicit c: TestContext): Unit = {
    okTest {

      val postLink1 = Json.obj("value" -> Json.obj("values" -> Json.arr(1)))
      val postLink2 = Json.obj("value" -> Json.obj("values" -> Json.arr(2)))

      val expected =
        """
          |[
          |  {"id": 1, "value": "table2row1"},
          |  {"id": 2, "value": "table2row2"}
          |]
          |""".stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", postLink1)
        _ <- sendRequest("POST", s"/tables/1/columns/$linkColumnId/rows/1", postLink2)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLink_addThirdLinkToExistingTwo(implicit c: TestContext): Unit = {
    okTest {

      val putLink = Json.obj("value" -> Json.obj("values" -> Json.arr(3, 5)))
      val postThirdLink = Json.obj("value" -> Json.obj("values" -> Json.arr(4)))

      val expected =
        """
          |[
          |  {"id": 3, "value": "table2RowId1"},
          |  {"id": 5, "value": "table2RowId3"},
          |  {"id": 4, "value": "table2RowId2"}
          |]
          |""".stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLink)
        _ <- sendRequest("POST", s"/tables/1/columns/$linkColumnId/rows/1", postThirdLink)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLink_deleteOneOfTwoLinks(implicit c: TestContext): Unit = {
    okTest {

      val putLinks = Json.obj("value" -> Json.obj("values" -> Json.arr(1, 2)))

      val expected =
        """
          |[
          |  {"id": 1, "value": "table2row1"}
          |]
        """.stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLinks)
        _ <- sendRequest("DELETE", s"/tables/1/columns/$linkColumnId/rows/1/link/2")
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLink_deleteOneOfThreeLinks(implicit c: TestContext): Unit = {
    okTest {

      val putLinks = Json.obj("value" -> Json.obj("values" -> Json.arr(3, 4, 5)))

      val expected =
        """
          |[
          |  {"id": 3, "value": "table2RowId1"},
          |  {"id": 5, "value": "table2RowId3"}
          |]
        """.stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLinks)
        _ <- sendRequest("DELETE", s"/tables/1/columns/$linkColumnId/rows/1/link/4")
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateSimpleLinkOrderHistoryTest extends LinkTestBase {

  def getLinksJsonArray(obj: JsonObject, pos: Int = 0): JsonArray = {
    obj.getJsonArray("rows", Json.emptyArr()).getJsonObject(pos).getJsonArray("value")
  }

  @Test
  def changeLinkOrder_reverseOrder(implicit c: TestContext): Unit = {
    okTest {

      val putLinks = s"""
                        |{"value":
                        |  { "values": [3, 4, 5] }
                        |}
                        |""".stripMargin

      val expected =
        """
          |[
          |  {"id": 5, "value": "table2RowId3"},
          |  {"id": 4, "value": "table2RowId2"},
          |  {"id": 3, "value": "table2RowId1"}
          |]
        """.stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", Json.fromObjectString(putLinks))

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1/link/3/order", Json.obj("location" -> "end"))
        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1/link/5/order", Json.obj("location" -> "start"))

        links <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1")
        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test, 1)
      } yield {
        import scala.collection.JavaConverters._

        assertEquals(List(5, 4, 3),
                     links.getJsonArray("value").asScala.map({ case obj: JsonObject => obj.getLong("id") }))
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateMultiLanguageLinkHistoryTest extends LinkTestBase {

  def getLinksJsonArray(obj: JsonObject, pos: Int = 0): JsonArray = {
    obj.getJsonArray("rows", Json.emptyArr()).getJsonObject(pos).getJsonArray("value")
  }

  @Test
  def changeLink_MultiIdentifiers_MultiLangAndSingleLangNumeric(implicit c: TestContext): Unit = {
    okTest {

      val expected =
        """
          |[
          |  {"id":1,"value":{"de-DE":"Hallo, Table 2 Welt! 3.1415926","en-GB":"Hello, Table 2 World! 3.1415926"}},
          |  {"id":2,"value":{"de-DE":"Hallo, Table 2 Welt2! 2.1415926","en-GB":"Hello, Table 2 World2! 2.1415926"}}
          |]
          |""".stripMargin

      val postLinkColumn = Json.obj(
        "columns" -> Json.arr(
          Json.obj(
            "name" -> "Test Link 1",
            "kind" -> "link",
            "toTable" -> 2
          )
        )
      )
      val putLinkValue = Json.obj("value" -> Json.obj("values" -> Json.arr(1, 2)))

      for {
        _ <- createFullTableWithMultilanguageColumns("Table 1")
        _ <- createFullTableWithMultilanguageColumns("Table 2")

        // Change target table structure to have a second identifier
        _ <- sendRequest("POST", s"/tables/2/columns/3", Json.obj("identifier" -> true))

        // Add link column
        linkColumn <- sendRequest("POST", s"/tables/1/columns", postLinkColumn)
        linkColumnId = linkColumn.getJsonArray("columns").get[JsonObject](0).getNumber("id")

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLinkValue)
        test <- sendRequest("GET", "/tables/1/columns/8/rows/1/history")
        historyAfterCreation = getLinksJsonArray(test)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateHistoryCompatibilityTest extends TableauxTestBase {
// For migrated systems it is necessary to also write a history entry for a currently existing cell value

  @Test
  def changeSimpleValue_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {

      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val initialValue = """{ "value": "value before history feature" }"""
      val firstChangedValue = """{ "value": "my first change with history feature" }"""

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""UPDATE
                                  |user_table_1
                                  |SET column_1 = 'value before history feature'
                                  |WHERE id = 1""".stripMargin)

        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", firstChangedValue)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")

        rows = test.getJsonArray("rows")
        initialHistoryCreation = rows.get[JsonObject](0)
        firstHistoryCreation = rows.get[JsonObject](1)
      } yield {
        JSONAssert.assertEquals(initialValue, initialHistoryCreation.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(firstChangedValue, firstHistoryCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_secondChangeWithHistoryFeature_shouldAgainCreateSingleHistoryEntries(
      implicit c: TestContext): Unit = {
    okTest {

      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val initialValue = """{ "value": "value before history feature" }"""
      val change1 = """{ "value": "first change" }"""
      val change2 = """{ "value": "second change" }"""

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""UPDATE
                                  |user_table_1
                                  |SET column_1 = 'value before history feature'
                                  |WHERE id = 1""".stripMargin)

        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change1)
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change2)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")

        rows = test.getJsonArray("rows")
        initialHistory = rows.get[JsonObject](0)
        history1 = rows.get[JsonObject](1)
        history2 = rows.get[JsonObject](2)
      } yield {
        JSONAssert.assertEquals(initialValue, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  @Ignore
  def changeMultilanguageValue_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(
      implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "columnType": "text",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "first change"
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "columnType": "text",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "second change"
          |    }
          |  }
          |]
        """.stripMargin

      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val initialValue = """{ "value": { "de-DE": "language value before history feature" } }"""
      val change1 = """{ "value": { "de-DE": "language first change" } }"""
      val change2 = """{ "value": { "de-DE": "language second change" } }"""

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change1)
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change2)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")

        rows = test.getJsonArray("rows")
        initialHistory = rows.get[JsonObject](0)
        history1 = rows.get[JsonObject](1)
        history2 = rows.get[JsonObject](2)
      } yield {
        JSONAssert.assertEquals(initialValue, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}
