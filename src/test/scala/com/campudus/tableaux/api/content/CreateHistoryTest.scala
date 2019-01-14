package com.campudus.tableaux.api.content

import com.campudus.tableaux.api.media.MediaTestBase
import com.campudus.tableaux.database.DatabaseConnection
import com.campudus.tableaux.testtools.RequestCreation.{CurrencyCol, MultiCountry, Rows}
import com.campudus.tableaux.testtools.TableauxTestBase
import io.vertx.core.json.JsonArray
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.scala.SQLConnection
import io.vertx.scala.ext.web.Cookie
import org.junit.runner.RunWith
import org.junit.{Ignore, Test}
import org.skyscreamer.jsonassert.{JSONAssert, JSONCompareMode}
import org.vertx.scala.core.json.{Json, JsonObject}

import scala.concurrent.Future

trait TestHelper extends MediaTestBase {

  def getLinksJsonArray(obj: JsonObject, pos: Int = 0): JsonArray = {
    obj.getJsonArray("rows", Json.emptyArr()).getJsonObject(pos).getJsonArray("value")
  }

  protected def createTestAttachment(name: String)(implicit c: TestContext): Future[String] = {

    val path = s"/com/campudus/tableaux/uploads/Screen.Shot.png"
    val mimetype = "application/png"

    val file = Json.obj("title" -> Json.obj("de-DE" -> name))

    for {
      fileUuid <- createFile("de-DE", path, mimetype, None) map (_.getString("uuid"))
      _ <- sendRequest("PUT", s"/files/$fileUuid", file)

    } yield fileUuid
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateHistoryTest extends TableauxTestBase {

  @Test
  def changeSimpleValue_historyAfterDefaultTableCreation(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "event": "cell_changed",
          |  "historyType": "cell",
          |  "valueType": "text",
          |  "languageType": "neutral",
          |  "value": "table1row1"
          |}
        """.stripMargin

      for {
        _ <- createDefaultTable()
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
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
          |  "historyType": "cell",
          |  "valueType": "text",
          |  "languageType": "neutral",
          |  "value": "my first change"
          |}
        """.stripMargin

      val newValue = Json.obj("value" -> "my first change")

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
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
          |    "historyType": "cell",
          |    "valueType": "numeric",
          |    "languageType": "neutral",
          |    "value": 42
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "numeric",
          |    "languageType": "neutral",
          |    "value": 1337
          |  },{
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "numeric",
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
        history <- sendRequest("GET", "/tables/1/columns/2/rows/1/history?historyType=cell")
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
          |    "historyType": "cell",
          |    "valueType": "text",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "first change"
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "text",
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
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_boolean(implicit c: TestContext): Unit = {
    okTest {
      // Booleans always gets a initial history entry on first change
      val expected =
        """
          |[
          |  {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "boolean",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": false
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "boolean",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": true
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "boolean",
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
        test <- sendRequest("GET", "/tables/1/columns/2/rows/1/history?historyType=cell")
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
          |    "historyType": "cell",
          |    "valueType": "numeric",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": 42
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "numeric",
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
          |    "historyType": "cell",
          |    "valueType": "datetime",
          |    "languageType": "language",
          |    "value": {
          |      "de-DE": "2019-01-18T00:00:00.000Z"
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "datetime",
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
        test <- sendRequest("GET", "/tables/1/columns/7/rows/1/history?historyType=cell")
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
          |    "historyType": "cell",
          |    "valueType": "currency",
          |    "languageType": "country",
          |    "value": {
          |      "DE": 2999.99
          |    }
          |  }, {
          |    "event": "cell_changed",
          |    "historyType": "cell",
          |    "valueType": "currency",
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
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        historyAfterCreation = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def createRowsWithValues(implicit c: TestContext): Unit = {
    okTest {

      val rowData = """{
                      |  "columns": [ {"id": 1}, {"id": 2}, {"id": 3} ],
                      |  "rows": [{
                      |    "values": [
                      |      {
                      |        "de-DE": "value de",
                      |        "en-GB": "value en"
                      |      }, {
                      |        "de-DE": true,
                      |        "en-GB": false
                      |      }, {
                      |        "de-DE": 111,
                      |        "en-GB": 222
                      |      }
                      |    ]
                      |  }]
                      |}""".stripMargin

      val expectedText = """[ {"value":{"de-DE": "value de"}}, {"value":{"en-GB": "value en"}} ]"""
      val expectedBoolean = """[ {"value":{"de-DE": true}}, {"value":{"en-GB": false}} ]"""
      val expectedNumeric = """[ {"value":{"de-DE": 111}}, {"value":{"en-GB": 222}} ]"""

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows", rowData)

        textHistory <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        booleanHistory <- sendRequest("GET", "/tables/1/columns/2/rows/1/history?historyType=cell")
        numericHistory <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        textRows = textHistory.getJsonArray("rows")
        booleanRows = booleanHistory.getJsonArray("rows")
        numericRows = numericHistory.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expectedText, textRows.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedBoolean, booleanRows.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedNumeric, numericRows.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def createCompleteTable(implicit c: TestContext): Unit = {
    okTest {
      val createCompleteTableJson = """{
                                      |  "name": "TestTable",
                                      |  "columns": [
                                      |    {"kind": "text", "name": "Test Column 1"},
                                      |    {"kind": "numeric", "name": "Test Column 2"}
                                      |  ],
                                      |  "rows": [
                                      |    {"values": ["Test Field 1", 1]},
                                      |    {"values": ["Test Field 2", 2]}
                                      |  ]
                                      |}""".stripMargin

      for {
        _ <- sendRequest("POST", "/completetable", createCompleteTableJson)

        textHistory1 <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        textHistory2 <- sendRequest("GET", "/tables/1/columns/1/rows/2/history?historyType=cell")
        numericHistory1 <- sendRequest("GET", "/tables/1/columns/2/rows/1/history?historyType=cell")
        numericHistory2 <- sendRequest("GET", "/tables/1/columns/2/rows/2/history?historyType=cell")
        textRows1 = textHistory1.getJsonArray("rows").getJsonObject(0)
        textRows2 = textHistory2.getJsonArray("rows").getJsonObject(0)
        numericRows1 = numericHistory1.getJsonArray("rows").getJsonObject(0)
        numericRows2 = numericHistory2.getJsonArray("rows").getJsonObject(0)
      } yield {
        JSONAssert.assertEquals("""{"value": "Test Field 1"}""", textRows1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{"value": "Test Field 2"}""", textRows2.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{"value": 1}""", numericRows1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{"value": 2}""", numericRows2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteCell_singleLang(implicit c: TestContext): Unit = {
    okTest {
      for {
        _ <- createDefaultTable()
        _ <- sendRequest("DELETE", "/tables/1/columns/1/rows/1")
        _ <- sendRequest("DELETE", "/tables/1/columns/2/rows/1")

        textHistoryRows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        numericHistoryRows <- sendRequest("GET", "/tables/1/columns/2/rows/1/history?historyType=cell")

        textHistory = textHistoryRows.getJsonArray("rows").get[JsonObject](1)
        numericHistory = numericHistoryRows.getJsonArray("rows").get[JsonObject](1)
      } yield {
        JSONAssert.assertEquals("""{"value": null}""", textHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{"value": null}""", numericHistory.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteCell_multiLang(implicit c: TestContext): Unit = {
    okTest {
      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", """{"value": {"de-DE": "first change"}}""")
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", """{"value": {"de-DE": true}}""")
        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", """{"value": {"de-DE": 42}}""")

        _ <- sendRequest("DELETE", "/tables/1/columns/1/rows/1")
        _ <- sendRequest("DELETE", "/tables/1/columns/2/rows/1")
        _ <- sendRequest("DELETE", "/tables/1/columns/3/rows/1")

        textHistoryRows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history/de-DE")
        numericHistoryRows <- sendRequest("GET", "/tables/1/columns/2/rows/1/history/de-DE")
        booelanHistoryRows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history/de-DE")

        textHistory = textHistoryRows.getJsonArray("rows").get[JsonObject](1)
        //for boolean setting one value also sets all other langtags to false, so take pos=2
        numericHistory = numericHistoryRows.getJsonArray("rows").get[JsonObject](2)
        booleanHistory = booelanHistoryRows.getJsonArray("rows").get[JsonObject](1)
      } yield {
        JSONAssert.assertEquals("""{"value": {"de-DE": null}}""", textHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{"value": {"de-DE": null}}""", numericHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{"value": {"de-DE": null}}""", booleanHistory.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_withUserNameInCookie(implicit c: TestContext): Unit = {
    okTest {
      val cookie = Cookie.cookie("userName", "Alice")

      val expectedRowCreated = """{ "event": "row_created", "author": "Alice" }"""
      val expectedCellChanged = """{ "event": "cell_changed", "author": "Alice" }"""

      val newValue = Json.obj("value" -> "my first change")

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows", Some(cookie))
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue, Some(cookie))
        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history").map(_.getJsonArray("rows"))
        rowCreated = rows.get[JsonObject](0)
        cellChanged = rows.get[JsonObject](1)
      } yield {
        JSONAssert.assertEquals(expectedRowCreated, rowCreated.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedCellChanged, cellChanged.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateSimpleLinkHistoryTest extends LinkTestBase with TestHelper {

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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
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
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        historyAfterCreation = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteCell_link(implicit c: TestContext): Unit = {
    okTest {
      val putLinks = """{"value": {"values": [1, 2]} }"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLinks)
        _ <- sendRequest("DELETE", "/tables/1/columns/3/rows/1")

        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        history = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals("[]", history.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateBidirectionalLinkHistoryTest extends LinkTestBase with TestHelper {

  // TODO
  // very bidirectional is very complex
  // wwe have to handle deletion, adding, etc. and there shouldn't be created duplicate history entries

  @Test
  @Ignore
  def changeLink_addOneLinkBidirectional(implicit c: TestContext): Unit = {
    okTest {

      val putLink = Json.obj("value" -> Json.obj("values" -> Json.arr(5)))

      val linkTable = """[ {"id": 5, "value": "table2RowId3"} ]""".stripMargin
      val targetLinkTable = """[ {"id": 1, "value": "table1row1"} ]""".stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLink)
        history <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        targetHistory <- sendRequest("GET", "/tables/2/columns/3/rows/5/history?historyType=cell")
        historyLinks = getLinksJsonArray(history)
        historyTargetLinks = getLinksJsonArray(targetHistory)
      } yield {
        JSONAssert.assertEquals(linkTable, historyLinks.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(targetLinkTable, historyTargetLinks.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  @Ignore
  def changeLink_addTwoLinksBidirectional(implicit c: TestContext): Unit = {
    okTest {

      val putLink = Json.obj("value" -> Json.obj("values" -> Json.arr(4, 5)))

      val linkTable = """[ {"id": 4, "value": "table2RowId2"},  {"id": 5, "value": "table2RowId3"} ]""".stripMargin
      val targetLinkTable1 = """[ {"id": 1, "value": "table1row1"} ]""".stripMargin
      val targetLinkTable2 = """[ {"id": 1, "value": "table1row1"} ]""".stripMargin

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1", putLink)
        history <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        targetHistory1 <- sendRequest("GET", "/tables/2/columns/3/rows/4/history?historyType=cell")
        targetHistory2 <- sendRequest("GET", "/tables/2/columns/3/rows/5/history?historyType=cell")
        historyLinks = getLinksJsonArray(history)
        historyTargetLinks1 = getLinksJsonArray(targetHistory1)
        historyTargetLinks2 = getLinksJsonArray(targetHistory2)
      } yield {
        JSONAssert.assertEquals(linkTable, historyLinks.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(targetLinkTable1, historyTargetLinks1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(targetLinkTable2, historyTargetLinks2.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateSimpleLinkOrderHistoryTest extends LinkTestBase with TestHelper {

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
        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history?historyType=cell")
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
class CreateMultiLanguageLinkHistoryTest extends LinkTestBase with TestHelper {

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
        test <- sendRequest("GET", "/tables/1/columns/8/rows/1/history?historyType=cell")
        historyAfterCreation = getLinksJsonArray(test)
      } yield {
        JSONAssert.assertEquals(expected, historyAfterCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateHistoryCompatibilityTest extends LinkTestBase with TestHelper {
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
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")

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
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")

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
  def changeMultilanguageValue_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(
      implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val initialValueDE = """{ "value": { "de-DE": "de-DE init" } }"""
      val initialValueEN = """{ "value": { "en-GB": "en-GB init" } }"""
      val change1 = """{ "value": { "de-DE": "de-DE first change" } }"""
      val change2 = """{ "value": { "de-DE": "de-DE second change" } }"""
      val change3 = """{ "value": { "en-GB": "en-GB first change" } }"""

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""INSERT INTO user_table_lang_1(id, langtag,column_1)
                                  |  VALUES
                                  |(1, E'de-DE', E'de-DE init'),
                                  |(1, E'en-GB', E'en-GB init')
                                  |""".stripMargin)

        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change1)
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change2)
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", change3)
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")

        rows = test.getJsonArray("rows")
        initialHistoryDE = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
        history2 = rows.getJsonObject(2)
        initialHistoryEN = rows.getJsonObject(3)
        history3 = rows.getJsonObject(4)
      } yield {
        JSONAssert.assertEquals(initialValueDE, initialHistoryDE.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change2, history2.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(initialValueEN, initialHistoryEN.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(change3, history3.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeCurrencyPOST_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(
      implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedValues =
        """[
          |  {"value": {"DE": 11}},
          |  {"value": {"GB": 22}},
          |  {"value": {"DE": 33}},
          |  {"value": {"GB": 44}}
          |]""".stripMargin

      val multiCountryCurrencyColumn = MultiCountry(CurrencyCol("currency-column"), Seq("DE", "GB"))

      for {
        _ <- createSimpleTableWithCell("table1", multiCountryCurrencyColumn)

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""INSERT INTO user_table_lang_1(id, langtag, column_1)
                                  |  VALUES
                                  |(1, E'DE', 11),
                                  |(1, E'GB', 22)
                                  |""".stripMargin)

        _ <- sendRequest("POST", s"/tables/1/columns/1/rows/1", """{"value": {"DE": 33, "GB": 44}}""")
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expectedValues, rows.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeCurrencyPUT_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedValues =
        """[
          |  {"value": {"DE": 11}},
          |  {"value": {"GB": 22}},
          |  {"value": {"DE": 33}},
          |  {"value": {"GB": 44}}
          |]""".stripMargin

      val multiCountryCurrencyColumn = MultiCountry(CurrencyCol("currency-column"), Seq("DE", "GB"))

      for {
        _ <- createSimpleTableWithCell("table1", multiCountryCurrencyColumn)

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""INSERT INTO user_table_lang_1(id, langtag, column_1)
                                  |  VALUES
                                  |(1, E'DE', 11),
                                  |(1, E'GB', 22)
                                  |""".stripMargin)

        _ <- sendRequest("PUT", s"/tables/1/columns/1/rows/1", """{"value": {"DE": 33, "GB": 44}}""")
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
      } yield {
        JSONAssert.assertEquals(expectedValues, rows.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLinkValue_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedInitialLinks = """{ "value": [ {"id":3}, {"id": 4} ] }"""
      val expectedAfterPostLinks = """{ "value": [ {"id":3}, {"id": 4}, {"id": 5} ] }"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- dbConnection.query("""INSERT INTO link_table_1
                                  |  (id_1, id_2)
                                  |VALUES
                                  |  (1, 3),
                                  |  (1, 4)
                                  |  """.stripMargin)

        _ <- sendRequest("POST", s"/tables/1/columns/$linkColumnId/rows/1", """{ "value": [ 5 ] }""")
        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks, history1.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLinkValue_twoLinkChanges_onlyFirstOneShouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedInitialLinks = """{ "value": [ {"id":3} ] }"""
      val expectedAfterPostLinks1 = """{ "value": [ {"id":3}, {"id":4} ] }"""
      val expectedAfterPostLinks2 = """{ "value": [ {"id":3}, {"id":4}, {"id":5} ] }"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- dbConnection.query("""INSERT INTO link_table_1
                                  |  (id_1, id_2)
                                  |VALUES
                                  |  (1, 3)
                                  |  """.stripMargin)

        _ <- sendRequest("POST", s"/tables/1/columns/$linkColumnId/rows/1", """{ "value": [ 4 ] }""")
        _ <- sendRequest("POST", s"/tables/1/columns/$linkColumnId/rows/1", """{ "value": [ 5 ] }""")
        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
        history2 = rows.getJsonObject(2)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteLinkValue_threeLinks_deleteOneOfThem(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedInitialLinks = """{ "value": [ {"id":3}, {"id":4}, {"id":5} ] }"""
      val expectedAfterPostLinks1 = """{ "value": [ {"id":3}, {"id":5} ] }"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- dbConnection.query("""INSERT INTO link_table_1
                                  |  (id_1, id_2)
                                  |VALUES
                                  |  (1, 3),
                                  |  (1, 4),
                                  |  (1, 5)
                                  |  """.stripMargin)

        _ <- sendRequest("DELETE", s"/tables/1/columns/$linkColumnId/rows/1/link/4")
        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks1, history1.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeLinkOrder_reverseOrderInTwoSteps_createOnlyOneInitHistory(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedInitialLinks = """{ "value": [ {"id":3}, {"id":4}, {"id":5} ] }"""
      val expectedAfterPostLinks1 = """{ "value": [ {"id":4}, {"id":5}, {"id":3} ] }"""
      val expectedAfterPostLinks2 = """{ "value": [ {"id":5}, {"id":4}, {"id":3} ] }"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- dbConnection.query("""INSERT INTO link_table_1
                                  |  (id_1, id_2)
                                  |VALUES
                                  |  (1, 3),
                                  |  (1, 4),
                                  |  (1, 5)
                                  |  """.stripMargin)

        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1/link/3/order", s""" {"location": "end"} """)
        _ <- sendRequest("PUT", s"/tables/1/columns/$linkColumnId/rows/1/link/5/order", s""" {"location": "start"} """)

        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
        history2 = rows.getJsonObject(2)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteLinkValue_threeLinks_deleteTwoTimesOnlyOneInitHistory(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedInitialLinks = """{ "value": [ {"id":3}, {"id":4}, {"id":5} ] }"""
      val expectedAfterPostLinks1 = """{ "value": [ {"id":3}, {"id":5} ] }"""
      val expectedAfterPostLinks2 = """{ "value": [ {"id":5} ] }"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        _ <- dbConnection.query("""INSERT INTO link_table_1
                                  |  (id_1, id_2)
                                  |VALUES
                                  |  (1, 3),
                                  |  (1, 4),
                                  |  (1, 5)
                                  |  """.stripMargin)

        _ <- sendRequest("DELETE", s"/tables/1/columns/$linkColumnId/rows/1/link/4")
        _ <- sendRequest("DELETE", s"/tables/1/columns/$linkColumnId/rows/1/link/3")
        test <- sendRequest("GET", s"/tables/1/columns/$linkColumnId/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
        history2 = rows.getJsonObject(2)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPostLinks2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_booleanInitHistoryWithValueFalse(implicit c: TestContext): Unit = {
    okTest {
      // Booleans always gets a initial history entry on first change
      val expectedInitialLinks = """{ "value": false} """
      val expectedAfterPost1 = """{ "value": true }"""

      val booleanColumn =
        s"""{"columns": [{"kind": "boolean", "name": "Boolean Column", "languageType": "neutral"} ] }"""

      for {
        _ <- createEmptyDefaultTable("history test")

        // create simple boolean column
        _ <- sendRequest("POST", "/tables/1/columns", booleanColumn)

        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", expectedAfterPost1)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPost1, history1.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_booleanInitHistoryWithSameValue(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      // Booleans always gets a initial history entry on first change
      val expectedInitialLinks = """{ "value": true} """
      val expectedAfterPost1 = """{ "value": true }"""

      val booleanColumn =
        s"""{"columns": [{"kind": "boolean", "name": "Boolean Column", "languageType": "neutral"} ] }"""

      for {
        _ <- createEmptyDefaultTable("history test")

        // create simple boolean column
        _ <- sendRequest("POST", "/tables/1/columns", booleanColumn)

        _ <- sendRequest("POST", "/tables/1/rows")

        // manually update value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""UPDATE user_table_1 SET column_3 = TRUE WHERE id = 1""".stripMargin)

        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", expectedAfterPost1)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPost1, history1.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeSimpleValue_boolean(implicit c: TestContext): Unit = {
    okTest {
      // Booleans always gets a initial history entry on first change
      val expectedInitialLinks = """{ "value": false} """
      val expectedAfterPost1 = """{ "value": true }"""
      val expectedAfterPost2 = """{ "value": false }"""

      val booleanColumn =
        s"""{"columns": [{"kind": "boolean", "name": "Boolean Column", "languageType": "neutral"} ] }"""

      for {
        _ <- createEmptyDefaultTable("history test")

        // create simple boolean column
        _ <- sendRequest("POST", "/tables/1/columns", booleanColumn)

        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", expectedAfterPost1)
        _ <- sendRequest("POST", "/tables/1/columns/3/rows/1", expectedAfterPost2)
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
        history2 = rows.getJsonObject(2)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPost1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPost2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def changeMultilanguageValue_boolean(implicit c: TestContext): Unit = {
    okTest {
      // Booleans always gets a initial history entry on first change
      val expectedInitialLinks = """{ "value": {"de-DE": false} }"""
      val expectedAfterPost1 = """{ "value": {"de-DE": true} }"""
      val expectedAfterPost2 = """{ "value": {"de-DE": false} }"""

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", expectedAfterPost1)
        _ <- sendRequest("POST", "/tables/1/columns/2/rows/1", expectedAfterPost2)
        test <- sendRequest("GET", "/tables/1/columns/2/rows/1/history?historyType=cell")
        rows = test.getJsonArray("rows")
        initialHistory = rows.getJsonObject(0)
        history1 = rows.getJsonObject(1)
        history2 = rows.getJsonObject(2)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPost1, history1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(expectedAfterPost2, history2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteSimpleCell_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {

      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val initialValue = """{ "value": "value before history feature" }"""

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""UPDATE
                                  |user_table_1
                                  |SET column_1 = 'value before history feature'
                                  |WHERE id = 1""".stripMargin)

        _ <- sendRequest("DELETE", "/tables/1/columns/1/rows/1")
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell")

        rows = test.getJsonArray("rows")
        initialHistoryCreation = rows.get[JsonObject](0)
        firstHistoryCreation = rows.get[JsonObject](1)
      } yield {
        JSONAssert.assertEquals(initialValue, initialHistoryCreation.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{ "value": null }""", firstHistoryCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteMultilanguageCell_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(
      implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val initialValueDE = """{ "value": { "de-DE": "de-DE init" } }"""

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""INSERT INTO user_table_lang_1(id, langtag, column_1)
                                  |  VALUES
                                  |(1, E'de-DE', E'de-DE init'),
                                  |(1, E'en-GB', E'en-GB init')
                                  |""".stripMargin)

        _ <- sendRequest("DELETE", "/tables/1/columns/1/rows/1")
        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history/de-DE")

        rows = test.getJsonArray("rows")
        initialHistoryDE = rows.getJsonObject(0)
        history = rows.getJsonObject(1)
      } yield {
        JSONAssert.assertEquals(initialValueDE, initialHistoryDE.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("""{ "value": { "de-DE": null } }""", history.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteLinkCell_threeLinks_deleteTwoTimesOnlyOneInitHistory(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)

      val expectedInitialLinks = """[ {"id":3}, {"id":4}, {"id":5} ]"""

      for {
        linkColumnId <- setupTwoTablesWithEmptyLinks()

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query("""INSERT INTO link_table_1
                                  |  (id_1, id_2)
                                  |VALUES
                                  |  (1, 3),
                                  |  (1, 4),
                                  |  (1, 5)
                                  |  """.stripMargin)

        _ <- sendRequest("DELETE", s"/tables/1/columns/$linkColumnId/rows/1")
        test <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell")
        initialHistory = getLinksJsonArray(test)
        history = getLinksJsonArray(test, 1)
      } yield {
        JSONAssert.assertEquals(expectedInitialLinks, initialHistory.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals("[]", history.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def addAttachment_firstChangeWithHistoryFeature_shouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query(s"""INSERT INTO system_attachment
                                   |  (table_id, column_id, row_id, attachment_uuid, ordering)
                                   |VALUES
                                   |  (1, 3, 1, '$fileUuid1', 1)
                                   |  """.stripMargin)

        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.obj("uuid" -> fileUuid2)))
        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
        initialHistory = rows.get[JsonObject](0).getJsonArray("value").getJsonObject(0)
        history = rows.get[JsonObject](1).getJsonArray("value").getJsonObject(0)
      } yield {
        assertJSONEquals(Json.obj("uuid" -> fileUuid1), initialHistory, JSONCompareMode.LENIENT)
        assertJSONEquals(Json.obj("uuid" -> fileUuid1), history, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def addAttachment_twoTimes_shouldOnlyCreateOneInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")
        fileUuid3 <- createTestAttachment("Test 3")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query(s"""INSERT INTO system_attachment
                                   |  (table_id, column_id, row_id, attachment_uuid, ordering)
                                   |VALUES
                                   |  (1, 3, 1, '$fileUuid1', 1)
                                   |  """.stripMargin)

        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.obj("uuid" -> fileUuid2)))
        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.obj("uuid" -> fileUuid3)))
        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
      } yield {
        assertEquals(3, rows.size())
      }
    }
  }

  @Test
  def deleteAttachmentCell_shouldCreateInitialHistoryEntry(implicit c: TestContext): Unit = {
    okTest {
      val sqlConnection = SQLConnection(this.vertxAccess(), databaseConfig)
      val dbConnection = DatabaseConnection(this.vertxAccess(), sqlConnection)
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")

        // manually insert a value that simulates cell value changes before implementation of the history feature
        _ <- dbConnection.query(s"""INSERT INTO system_attachment
                                   |  (table_id, column_id, row_id, attachment_uuid, ordering)
                                   |VALUES
                                   |  (1, 3, 1, '$fileUuid1', 1),
                                   |  (1, 3, 1, '$fileUuid2', 2)
                                   |  """.stripMargin)

        _ <- sendRequest("DELETE", "/tables/1/columns/3/rows/1")

        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
        initialCountBeforeDeletion = rows.get[JsonObject](0).getJsonArray("value")
        attachmentCountAfterDeletion = rows.get[JsonObject](1).getJsonArray("value")
      } yield {
        assertEquals(2, initialCountBeforeDeletion.size())
        assertEquals(0, attachmentCountAfterDeletion.size())
      }
    }
  }

}

@RunWith(classOf[VertxUnitRunner])
class CreateAttachmentHistoryTest extends MediaTestBase with TestHelper {

  @Test
  def addAttachment_toEmptyCell(implicit c: TestContext): Unit = {
    okTest {
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid <- createTestAttachment("Test 1")

        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.obj("uuid" -> fileUuid)))
        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
        currentUuid = rows.get[JsonObject](0).getJsonArray("value").getJsonObject(0).getString("uuid")
      } yield {
        assertEquals(fileUuid, currentUuid)
      }
    }
  }

  @Test
  def addAttachment_toCellContainingOneAttachment(implicit c: TestContext): Unit = {
    okTest {
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")

        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.obj("uuid" -> fileUuid1)))
        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.obj("uuid" -> fileUuid2)))
        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
        firstHistory = rows.get[JsonObject](0).getJsonArray("value").getJsonObject(0)
        secondHistory1 = rows.get[JsonObject](1).getJsonArray("value").getJsonObject(0)
        secondHistory2 = rows.get[JsonObject](1).getJsonArray("value").getJsonObject(1)
      } yield {
        assertJSONEquals(Json.obj("uuid" -> fileUuid1), firstHistory, JSONCompareMode.LENIENT)
        assertJSONEquals(Json.obj("uuid" -> fileUuid1), secondHistory1, JSONCompareMode.LENIENT)
        assertJSONEquals(Json.obj("uuid" -> fileUuid2), secondHistory2, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def addAttachments_addThreeAttachmentsAtOnce(implicit c: TestContext): Unit = {
    okTest {
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")

        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.arr(fileUuid1, fileUuid2)))

        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
        history1 = rows.get[JsonObject](0).getJsonArray("value").getJsonObject(0)
        history2 = rows.get[JsonObject](0).getJsonArray("value").getJsonObject(1)
      } yield {
        assertJSONEquals(Json.obj("uuid" -> fileUuid1), history1, JSONCompareMode.LENIENT)
        assertJSONEquals(Json.obj("uuid" -> fileUuid2), history2, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteAttachment_fromCellContainingTwoAttachments(implicit c: TestContext): Unit = {
    okTest {
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")

        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.arr(fileUuid1, fileUuid2)))
        _ <- sendRequest("DELETE", s"/tables/1/columns/3/rows/1/attachment/$fileUuid1")

        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))

        afterDeletionHistory = rows.get[JsonObject](1).getJsonArray("value").getJsonObject(0)
      } yield {

        assertJSONEquals(Json.obj("uuid" -> fileUuid2), afterDeletionHistory, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteAttachment_fromCellContainingThreeAttachments(implicit c: TestContext): Unit = {
    okTest {
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")
        fileUuid3 <- createTestAttachment("Test 3")

        _ <- sendRequest("POST",
                         s"/tables/1/columns/3/rows/1",
                         Json.obj("value" -> Json.arr(fileUuid1, fileUuid2, fileUuid3)))
        _ <- sendRequest("DELETE", s"/tables/1/columns/3/rows/1/attachment/$fileUuid2")

        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))

        afterDeletionHistory1 = rows.get[JsonObject](1).getJsonArray("value").getJsonObject(0)
        afterDeletionHistory2 = rows.get[JsonObject](1).getJsonArray("value").getJsonObject(1)
      } yield {
        assertJSONEquals(Json.obj("uuid" -> fileUuid1), afterDeletionHistory1, JSONCompareMode.LENIENT)
        assertJSONEquals(Json.obj("uuid" -> fileUuid3), afterDeletionHistory2, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def deleteCell_attachment(implicit c: TestContext): Unit = {
    okTest {
      val attachmentColumn = """{"columns": [{"kind": "attachment", "name": "Downloads"}] }"""

      for {
        _ <- createDefaultTable()
        _ <- sendRequest("POST", s"/tables/1/columns", attachmentColumn)

        fileUuid1 <- createTestAttachment("Test 1")
        fileUuid2 <- createTestAttachment("Test 2")
        _ <- sendRequest("POST", s"/tables/1/columns/3/rows/1", Json.obj("value" -> Json.arr(fileUuid1, fileUuid2)))

        _ <- sendRequest("DELETE", "/tables/1/columns/3/rows/1")

        rows <- sendRequest("GET", "/tables/1/columns/3/rows/1/history?historyType=cell").map(_.getJsonArray("rows"))
        attachmentCountBeforeDeletion = rows.get[JsonObject](0).getJsonArray("value")
        attachmentCountAfterDeletion = rows.get[JsonObject](1).getJsonArray("value")
      } yield {
        assertEquals(2, attachmentCountBeforeDeletion.size())
        assertEquals(0, attachmentCountAfterDeletion.size())
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateAnnotationHistoryTest extends TableauxTestBase with TestHelper {

  @Test
  def addAnnotation_twoComments(implicit c: TestContext): Unit = {
    okTest {
      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        uuid1 <- sendRequest("POST",
                             "/tables/1/columns/1/rows/1/annotations",
                             Json.obj("type" -> "info", "value" -> "Test 1")).map(_.getString("uuid"))

        uuid2 <- sendRequest("POST",
                             "/tables/1/columns/1/rows/1/annotations",
                             Json.obj("type" -> "info", "value" -> "Test 2")).map(_.getString("uuid"))

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=comment").map(_.getJsonArray("rows"))

        testHistory1 = rows.get[JsonObject](0)
        testHistory2 = rows.get[JsonObject](1)
      } yield {
        assertJSONEquals(s"""{"value": "Test 1", "uuid": "$uuid1"}""", testHistory1.toString)
        assertJSONEquals(s"""{"value": "Test 2", "uuid": "$uuid2"}""", testHistory2.toString)
      }
    }
  }

  @Test
  def removeAnnotation_twoCommentsRemoveBoth(implicit c: TestContext): Unit = {
    okTest {
      for {

        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        // add annotations
        uuid1 <- sendRequest("POST",
                             "/tables/1/columns/1/rows/1/annotations",
                             Json.obj("type" -> "info", "value" -> "Test 1")).map(_.getString("uuid"))
        uuid2 <- sendRequest("POST",
                             "/tables/1/columns/1/rows/1/annotations",
                             Json.obj("type" -> "info", "value" -> "Test 2")).map(_.getString("uuid"))

        // remove annotations
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid1")
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid2")

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=comment").map(_.getJsonArray("rows"))

        testHistory1 = rows.get[JsonObject](2)
        testHistory2 = rows.get[JsonObject](3)
      } yield {
        assertJSONEquals(s"""{"value": "Test 1", "uuid": "$uuid1"}""", testHistory1.toString, JSONCompareMode.LENIENT)
        assertJSONEquals(s"""{"value": "Test 2", "uuid": "$uuid2"}""", testHistory2.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def addAnnotations_threeFlagAnnotations(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String) = Json.obj("type" -> "flag", "value" -> value)

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        uuid_check_me <- sendRequest("POST", "/tables/1/columns/1/rows/1/annotations", annotation("check-me"))
          .map(_.getString("uuid"))

        uuid_important <- sendRequest("POST", "/tables/1/columns/1/rows/1/annotations", annotation("important"))
          .map(_.getString("uuid"))

        uuid_postpone <- sendRequest("POST", "/tables/1/columns/1/rows/1/annotations", annotation("postpone"))
          .map(_.getString("uuid"))

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))

        row1 = rows.get[JsonObject](0)
        row2 = rows.get[JsonObject](1)
        row3 = rows.get[JsonObject](2)
      } yield {
        assertEquals(3, rows.size())
        assertJSONEquals(s"""{"value": "check-me", "uuid": "$uuid_check_me"}""", row1.toString)
        assertJSONEquals(s"""{"value": "important", "uuid": "$uuid_important"}""", row2.toString)
        assertJSONEquals(s"""{"value": "postpone", "uuid": "$uuid_postpone"}""", row3.toString)
      }
    }
  }

  @Test
  def removeAnnotation_oneOfTwoAnnotations(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String) = Json.obj("type" -> "flag", "value" -> value)

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        // add annotations
        uuid_important <- sendRequest("POST", "/tables/1/columns/1/rows/1/annotations", annotation("important"))
          .map(_.getString("uuid"))
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1/annotations", annotation("postpone"))
          .map(_.getString("uuid"))

        // remove annotation
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid_important")

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))

        row = rows.get[JsonObject](2)
      } yield {
        // 2 added | 1 removed
        assertEquals(3, rows.size())
        assertJSONEquals(s"""{"value": "important", "uuid": "$uuid_important"}""", row.toString)
      }
    }
  }

  @Test
  def addAnnotations_addAnnotationForOneLanguage(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String, langtags: JsonArray) =
        Json.obj("type" -> "flag", "value" -> value, "langtags" -> langtags)

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        uuid <- sendRequest("POST",
                            "/tables/1/columns/1/rows/1/annotations",
                            annotation("needs_translation", Json.arr("de-DE"))).map(_.getString("uuid"))

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))

        row1 = rows.get[JsonObject](0)

      } yield {
        assertEquals(1, rows.size())
        assertJSONEquals(s"""{"value": {"de-DE": "needs_translation"}, "uuid": "$uuid"}""", row1.toString)
      }
    }
  }

  @Test
  def addAnnotations_addAnnotationForTwoLanguagesAtOnce(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String, langtags: JsonArray) =
        Json.obj("type" -> "flag", "value" -> value, "langtags" -> langtags)

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        uuid <- sendRequest("POST",
                            "/tables/1/columns/1/rows/1/annotations",
                            annotation("needs_translation", Json.arr("de-DE", "en-GB"))).map(_.getString("uuid"))

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))
      } yield {
        assertEquals(2, rows.size())

        val expected =
          s"""[
             | {"value": {"de-DE": "needs_translation"}, "uuid": "$uuid", "event": "annotation_added"},
             | {"value": {"en-GB": "needs_translation"}, "uuid": "$uuid", "event": "annotation_added"}
           ]""".stripMargin

        // assert whole array because ordering is not guaranteed
        assertJSONEquals(expected, rows.toString)
      }
    }
  }

  @Test
  def removeAnnotation_removeAnnotationForAllLanguages(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String, langtags: JsonArray) =
        Json.obj("type" -> "flag", "value" -> value, "langtags" -> langtags)

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        // add two annotations
        uuid <- sendRequest("POST",
                            "/tables/1/columns/1/rows/1/annotations",
                            annotation("needs_translation", Json.arr("de-DE", "en-GB"))).map(_.getString("uuid"))

        // remove both annotations
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid")

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))
      } yield {
        // 2 added | 2 removed
        assertEquals(4, rows.size())

        val expected =
          s"""[
             | {"value": {"de-DE": "needs_translation"}, "uuid": "$uuid", "event": "annotation_added"},
             | {"value": {"en-GB": "needs_translation"}, "uuid": "$uuid", "event": "annotation_added"},
             | {"value": {"de-DE": "needs_translation"}, "uuid": "$uuid", "event": "annotation_removed"},
             | {"value": {"en-GB": "needs_translation"}, "uuid": "$uuid", "event": "annotation_removed"}
           ]""".stripMargin

        // assert whole array because ordering is not guaranteed
        assertJSONEquals(expected, rows.toString)
      }
    }
  }

  @Test
  def removeAnnotation_removeAnnotationForOneSpecificLanguage(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String, langtags: JsonArray) =
        Json.obj("type" -> "flag", "value" -> value, "langtags" -> langtags)

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        // add two annotations
        uuid <- sendRequest("POST",
                            "/tables/1/columns/1/rows/1/annotations",
                            annotation("needs_translation", Json.arr("de-DE", "en-GB"))).map(_.getString("uuid"))

        // remove german annotation
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid/de-DE")

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))

        row = rows.get[JsonObject](2)
      } yield {
        // 2 added | 1 removed
        assertEquals(3, rows.size())
        assertJSONEquals(s"""{"value": {"de-DE": "needs_translation"}, "uuid": "$uuid"}""", row.toString)
      }
    }
  }

  @Test
  def removeAnnotations_removeAnnotationOneByOne(implicit c: TestContext): Unit = {
    okTest {

      def annotation(value: String, langtags: JsonArray) =
        Json.obj("type" -> "flag", "value" -> value, "langtags" -> langtags)

      for {
        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        // add two annotations
        uuid <- sendRequest("POST",
                            "/tables/1/columns/1/rows/1/annotations",
                            annotation("needs_translation", Json.arr("de-DE", "en-GB"))).map(_.getString("uuid"))

        // remove them one by one
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid/de-DE")
        _ <- sendRequest("DELETE", s"/tables/1/columns/1/rows/1/annotations/$uuid/en-GB")

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=cell_flag").map(
          _.getJsonArray("rows"))

        row1 = rows.get[JsonObject](2)
        row2 = rows.get[JsonObject](3)
      } yield {
        // 2 added | 2 removed
        assertEquals(4, rows.size())
        assertJSONEquals(s"""{"value": {"de-DE": "needs_translation"}, "uuid": "$uuid"}""", row1.toString)
        assertJSONEquals(s"""{"value": {"en-GB": "needs_translation"}, "uuid": "$uuid"}""", row2.toString)
      }
    }
  }

  @Test
  def addRowAnnotation_addFinalFlag(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "rowId": 1,
          |  "event": "annotation_added",
          |  "historyType": "row_flag",
          |  "valueType": "final"
          |}
        """.stripMargin

      for {

        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        _ <- sendRequest("PATCH", "/tables/1/rows/1/annotations", Json.obj("final" -> true))

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=row_flag").map(
          _.getJsonArray("rows"))

        row = rows.get[JsonObject](0)
      } yield {
        assertJSONEquals(expected, row.toString)
      }
    }
  }

  @Test
  def removeRowAnnotation_removeFinalFlag(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "event": "annotation_removed",
          |  "historyType": "row_flag",
          |  "valueType": "final"
          |}
        """.stripMargin

      for {

        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")

        _ <- sendRequest("PATCH", "/tables/1/rows/1/annotations", Json.obj("final" -> false))

        rows <- sendRequest("GET", "/tables/1/columns/1/rows/1/history?historyType=row_flag").map(
          _.getJsonArray("rows"))

        row = rows.get[JsonObject](0)
      } yield {
        assertJSONEquals(expected, row.toString)
      }
    }
  }

  @Test
  def addRowAnnotation_addFinalFlagToMultipleRows(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "event": "annotation_added",
          |  "historyType": "row_flag",
          |  "valueType": "final"
          |}
        """.stripMargin

      for {

        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/rows")

        _ <- sendRequest("PATCH", "/tables/1/rows/annotations", Json.obj("final" -> true))

        rows <- sendRequest("GET", "/tables/1/history?historyType=row_flag").map(_.getJsonArray("rows"))

        row1 = rows.get[JsonObject](0)
        row2 = rows.get[JsonObject](1)
        row3 = rows.get[JsonObject](2)
      } yield {
        assertEquals(3, rows.size())
        assertJSONEquals(expected, row1.toString)
        assertJSONEquals(expected, row2.toString)
        assertJSONEquals(expected, row3.toString)
      }
    }
  }

  @Test
  def removeRowAnnotation_removeFinalFlagFromMultipleRows(implicit c: TestContext): Unit = {
    okTest {
      val expected =
        """
          |{
          |  "event": "annotation_removed",
          |  "historyType": "row_flag",
          |  "valueType": "final"
          |}
        """.stripMargin

      for {

        _ <- createTableWithMultilanguageColumns("history test")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/rows")

        _ <- sendRequest("PATCH", "/tables/1/rows/annotations", Json.obj("final" -> false))

        rows <- sendRequest("GET", "/tables/1/history?historyType=row_flag").map(_.getJsonArray("rows"))

        row1 = rows.get[JsonObject](0)
        row2 = rows.get[JsonObject](1)
        row3 = rows.get[JsonObject](2)
      } yield {
        assertEquals(3, rows.size())
        assertJSONEquals(expected, row1.toString)
        assertJSONEquals(expected, row2.toString)
        assertJSONEquals(expected, row3.toString)
      }
    }
  }
}

@RunWith(classOf[VertxUnitRunner])
class CreateRowHistoryTest extends TableauxTestBase with TestHelper {

  @Test
  def createRow_oneEmptyRow(implicit c: TestContext): Unit = {
    okTest {
      val rowCreated = """{ "event": "row_created" }"""
      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")

        test <- sendRequest("GET", "/tables/1/columns/1/rows/1/history")

        rows = test.getJsonArray("rows")
        rowHistoryCreation = rows.get[JsonObject](0)
      } yield {
        JSONAssert.assertEquals(rowCreated, rowHistoryCreation.toString, JSONCompareMode.LENIENT)
      }
    }
  }

  @Test
  def createRow_threeEmptyRows(implicit c: TestContext): Unit = {
    okTest {
      val rowCreated = """{ "event": "row_created" }"""
      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/rows")

        test1 <- sendRequest("GET", "/tables/1/columns/1/rows/1/history").map(_.getJsonArray("rows").get[JsonObject](0))
        test2 <- sendRequest("GET", "/tables/1/columns/1/rows/1/history").map(_.getJsonArray("rows").get[JsonObject](0))
        test3 <- sendRequest("GET", "/tables/1/columns/1/rows/1/history").map(_.getJsonArray("rows").get[JsonObject](0))

        allRows <- sendRequest("GET", "/tables/1/history").map(_.getJsonArray("rows"))
      } yield {
        JSONAssert.assertEquals(rowCreated, test1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(rowCreated, test2.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(rowCreated, test3.toString, JSONCompareMode.LENIENT)

        assertEquals(3, allRows.size())
      }
    }
  }

  @Test
  def createRows_withValues(implicit c: TestContext): Unit = {
    okTest {
      val createStringColumnJson =
        Json.obj(
          "columns" -> Json.arr(
            Json.obj("kind" -> "shorttext", "name" -> "column", "identifier" -> true, "languageType" -> "language")))

      for {
        // prepare table
        _ <- sendRequest("POST", "/tables", Json.obj("name" -> "test")).map(_.getLong("id"))
        columns <- sendRequest("POST", s"/tables/1/columns", createStringColumnJson).map(_.getJsonArray("columns"))

        // add rows
        _ <- sendRequest("POST",
                         s"/tables/1/rows",
                         Rows(columns, Json.obj("column" -> Json.obj("de-DE" -> "a", "en-GB" -> "b"))))
        _ <- sendRequest("POST",
                         s"/tables/1/rows",
                         Rows(columns, Json.obj("column" -> Json.obj("de-DE" -> "c", "en-GB" -> "d"))))

        rowsCreated <- sendRequest("GET", s"/tables/1/history?historyType=row").map(_.getJsonArray("rows"))
      } yield {
        assertEquals(2, rowsCreated.size())
      }
    }
  }

  @Test
  def createRows_duplicateRow(implicit c: TestContext): Unit = {
    okTest {
      val newValue = Json.obj("value" -> "any change")

      for {
        _ <- createEmptyDefaultTable()
        _ <- sendRequest("POST", "/tables/1/rows")
        _ <- sendRequest("POST", "/tables/1/columns/1/rows/1", newValue)

        _ <- sendRequest("POST", "/tables/1/rows/1/duplicate")

        rowsCreated <- sendRequest("GET", "/tables/1/history?historyType=row").map(_.getJsonArray("rows"))
        row1 = rowsCreated.get[JsonObject](0)
        row2 = rowsCreated.get[JsonObject](1)
      } yield {
        assertEquals(2, rowsCreated.size())
        JSONAssert.assertEquals(s"""{ "rowId": 1, "event": "row_created" }""", row1.toString, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(s"""{ "rowId": 2, "event": "row_created" }""", row2.toString, JSONCompareMode.LENIENT)
      }
    }
  }
}
