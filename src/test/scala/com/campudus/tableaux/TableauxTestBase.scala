package com.campudus.tableaux


import com.campudus.tableaux.database.DatabaseConnection
import com.campudus.tableaux.database.model.SystemModel
import com.campudus.tableaux.testtools.RequestCreation._
import com.typesafe.scalalogging.LazyLogging
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.{OpenOptions, AsyncFile}
import io.vertx.core.http.{HttpClient, HttpClientRequest, HttpClientResponse, HttpMethod}
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.Pump
import io.vertx.core.{AsyncResult, Handler, DeploymentOptions, Vertx}
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.scala.FunctionConverters._
import io.vertx.scala.FutureHelper._
import io.vertx.scala.SQLConnection
import org.junit.runner.RunWith
import org.junit.{After, Before}
import org.vertx.scala.core.json._

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

case class TestCustomException(message: String, id: String, statusCode: Int) extends Throwable {
  override def toString: String = s"TestCustomException(status=$statusCode,id=$id,message=$message)"
}

trait TestAssertionHelper {
  def fail[A](message: String)(implicit c: TestContext): Unit = {
    c.fail(message)
  }

  def assertEquals[A](message: String, excepted: A, actual: A)(implicit c: TestContext): TestContext = {
    c.assertEquals(excepted, actual, message)
  }

  def assertEquals[A](excepted: A, actual: A)(implicit c: TestContext): TestContext = {
    c.assertEquals(excepted, actual)
  }

  def assertNull(excepted: Any)(implicit c: TestContext): TestContext = {
    c.assertNull(excepted)
  }

  def assertTrue(message: String, condition: Boolean)(implicit c: TestContext): TestContext = {
    c.assertTrue(condition, message)
  }

  def assertTrue(condition: Boolean)(implicit c: TestContext): TestContext = {
    c.assertTrue(condition)
  }

  def assertNotSame[A](first: A, second: A)(implicit c: TestContext): TestContext = {
    c.assertNotEquals(first, second)
  }
}

@RunWith(classOf[VertxUnitRunner])
trait TableauxTestBase extends TestConfig with LazyLogging with TestAssertionHelper with JsonCompatible {

  override val verticle = new Starter
  implicit lazy val executionContext = verticle.executionContext

  val vertx: Vertx = Vertx.vertx()
  private var deploymentId: String = ""

  @Before
  def before(context: TestContext) {
    val async = context.async()

    val options = new DeploymentOptions()
      .setConfig(config)

    val completionHandler = {
      case Success(id) =>
        logger.info(s"Verticle deployed with ID $id")
        this.deploymentId = id

        val sqlConnection = SQLConnection(verticle, databaseConfig)
        val dbConnection = DatabaseConnection(verticle, sqlConnection)
        val system = SystemModel(dbConnection)

        for {
          _ <- system.uninstall()
          _ <- system.install()
        } yield {
          async.complete()
        }
      case Failure(e) =>
        logger.error("Verticle couldn't be deployed.", e)
        context.fail(e)
        async.complete()
    }: Try[String] => Unit

    vertx.deployVerticle(verticle, options, completionHandler)
  }

  @After
  def after(context: TestContext) {
    val async = context.async()

    vertx.undeploy(deploymentId, {
      case Success(_) =>
        logger.info("Verticle undeployed!")
        vertx.close({
          case Success(_) =>
            logger.info("Vertx closed!")
            async.complete()
          case Failure(e) =>
            logger.error("Vertx couldn't be closed!", e)
            context.fail(e)
            async.complete()
        }: Try[Void] => Unit)
      case Failure(e) =>
        logger.error("Verticle couldn't be undeployed!", e)
        context.fail(e)
        async.complete()
    }: Try[Void] => Unit)
  }

  def okTest(f: => Future[_])(implicit context: TestContext): Unit = {
    val async = context.async()
    (try f catch {
      case ex: Throwable => Future.failed(ex)
    }) onComplete {
      case Success(_) => async.complete()
      case Failure(ex) =>
        logger.error("failed test", ex)
        context.fail(ex)
        async.complete()
    }
  }

  def exceptionTest(id: String)(f: => Future[_])(implicit context: TestContext): Unit = {
    val async = context.async()
    f onComplete {
      case Success(_) =>
        val msg = s"Test with id $id should fail but got no exception."
        logger.error(msg)
        context.fail(msg)
        async.complete()
      case Failure(ex: TestCustomException) =>
        context.assertEquals(id, ex.id)
        async.complete()
      case Failure(ex) =>
        val msg = s"Test with id $id failed but got wrong exception (${ex.getMessage})."
        logger.error(msg)
        context.fail(msg)
        async.complete()
    }
  }

  def createClient(): HttpClient = {
    vertx.createHttpClient()
  }

  def sendRequest(method: String, path: String): Future[JsonObject] = {
    val p = Promise[JsonObject]()
    httpJsonRequest(method, path, p).end()
    p.future
  }

  def sendRequest(method: String, path: String, jsonObj: JsonObject): Future[JsonObject] = {
    val p = Promise[JsonObject]()
    httpJsonRequest(method, path, p).setChunked(true).write(jsonObj.encode()).end()
    p.future
  }

  def sendRequest(method: String, path: String, body: String): Future[JsonObject] = {
    val p = Promise[JsonObject]()
    httpJsonRequest(method, path, p).setChunked(true).write(body).end()
    p.future
  }

  def jsonResponse(p: Promise[JsonObject]): HttpClientResponse => Unit = { resp: HttpClientResponse =>
    def jsonBodyHandler(buf: Buffer): Unit = {
      if (resp.statusCode() != 200) {
        p.failure(TestCustomException(buf.toString, resp.statusMessage(), resp.statusCode()))
      } else {
        try {
          p.success(Json.fromObjectString(buf.toString))
        } catch {
          case ex: Exception => p.failure(ex)
        }
      }
    }

    resp.bodyHandler(jsonBodyHandler(_: Buffer))
  }

  private def httpJsonRequest(method: String, path: String, p: Promise[JsonObject]): HttpClientRequest = {
    httpRequest(method, path, jsonResponse(p), { x => p.failure(x) })
  }

  def httpRequest(method: String, path: String, responseHandler: HttpClientResponse => Unit, exceptionHandler: Throwable => Unit): HttpClientRequest = {
    val _method = HttpMethod.valueOf(method.toUpperCase)

    createClient()
      .request(_method, port, "localhost", path)
      .handler(responseHandler)
      .exceptionHandler(exceptionHandler)
  }

  def setupDefaultTable(name: String = "Test Table 1", tableNum: Int = 1): Future[Long] = {
    val postTable = Json.obj("name" -> name)
    val createStringColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "text", "name" -> "Test Column 1", "identifier" -> true)))
    val createNumberColumnJson = Json.obj("columns" -> Json.arr(Json.obj("kind" -> "numeric", "name" -> "Test Column 2")))
    val fillStringCellJson = Json.obj("value" -> s"table${tableNum}row1")
    val fillStringCellJson2 = Json.obj("value" -> s"table${tableNum}row2")
    val fillNumberCellJson = Json.obj("value" -> 1)
    val fillNumberCellJson2 = Json.obj("value" -> 2)

    for {
      tableId <- sendRequest("POST", "/tables", postTable) map { js => js.getLong("id") }
      _ <- sendRequest("POST", s"/tables/$tableId/columns", createStringColumnJson)
      _ <- sendRequest("POST", s"/tables/$tableId/columns", createNumberColumnJson)
      _ <- sendRequest("POST", s"/tables/$tableId/rows")
      _ <- sendRequest("POST", s"/tables/$tableId/rows")
      _ <- sendRequest("POST", s"/tables/$tableId/columns/1/rows/1", fillStringCellJson)
      _ <- sendRequest("POST", s"/tables/$tableId/columns/1/rows/2", fillStringCellJson2)
      _ <- sendRequest("POST", s"/tables/$tableId/columns/2/rows/1", fillNumberCellJson)
      _ <- sendRequest("POST", s"/tables/$tableId/columns/2/rows/2", fillNumberCellJson2)
    } yield tableId
  }

  protected def uploadFile(method: String, url: String, file: String, mimeType: String): Future[JsonObject] = futurify {
    p: Promise[JsonObject] =>
      val filePath = getClass.getResource(file).toURI.getPath
      val fileName = file.substring(file.lastIndexOf("/") + 1)

      def requestHandler(req: HttpClientRequest): Unit = {
        val boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO"
        val header =
          "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
            "Content-Type: " + mimeType + "\r\n\r\n"

        val footer = "\r\n--" + boundary + "--\r\n"

        val contentLength = String.valueOf(vertx.fileSystem.propsBlocking(filePath).size() + header.length + footer.length)
        req.putHeader("Content-length", contentLength)
        req.putHeader("Content-type", s"multipart/form-data; boundary=$boundary")

        logger.info(s"Loading file '$filePath' from disc, content-length=$contentLength")

        req.write(header)

        import io.vertx.scala.FunctionConverters._

        val asyncFile: Future[AsyncFile] = vertx.fileSystem().open(filePath, new OpenOptions(), _: Handler[AsyncResult[AsyncFile]])

        asyncFile.map({
          file =>
            val pump = Pump.pump(file, req)

            file.exceptionHandler({
              e: Throwable =>
                pump.stop()
                req.end("")
                p.failure(e)
            })

            file.endHandler({
              _: Void =>
                file.close({
                  case Success(_) =>
                    logger.info(s"File loaded, ending request, ${pump.numberPumped()} bytes pumped.")
                    req.end(footer)
                  case Failure(e) =>
                    req.end("")
                    p.failure(e)
                }: Try[Void] => Unit)
            })

            pump.start()
        })
      }

      requestHandler(httpRequest(method, url, jsonResponse(p), {
        x =>
          if (!p.isCompleted) {
            p.failure(x)
          }
      }))
  }

  protected def createFullTableWithMultilanguageColumns(tableName: String): Future[(Long, Seq[Long], Seq[Long])] = {
    def valuesRow(columnIds: Seq[Long]) = Json.obj(
      "columns" -> Json.arr(
        Json.obj("id" -> columnIds.head),
        Json.obj("id" -> columnIds(1)),
        Json.obj("id" -> columnIds(2)),
        Json.obj("id" -> columnIds(3)),
        Json.obj("id" -> columnIds(4)),
        Json.obj("id" -> columnIds(5)),
        Json.obj("id" -> columnIds(6))
      ),
      "rows" -> Json.arr(
        Json.obj("values" ->
          Json.arr(
            Json.obj(
              "de_DE" -> s"Hallo, $tableName Welt!",
              "en_US" -> s"Hello, $tableName World!"
            ),
            Json.obj("de_DE" -> true),
            Json.obj("de_DE" -> 3.1415926),
            Json.obj("en_US" -> s"Hello, $tableName Col 1 Row 1!"),
            Json.obj("en_US" -> s"Hello, $tableName Col 2 Row 1!"),
            Json.obj("de_DE" -> "2015-01-01"),
            Json.obj("de_DE" -> "2015-01-01T14:37:47.110+01")
          )
        ),
        Json.obj("values" ->
          Json.arr(
            Json.obj(
              "de_DE" -> s"Hallo, $tableName Welt2!",
              "en_US" -> s"Hello, $tableName World2!"
            ),
            Json.obj("de_DE" -> false),
            Json.obj("de_DE" -> 2.1415926),
            Json.obj("en_US" -> s"Hello, $tableName Col 1 Row 2!"),
            Json.obj("en_US" -> s"Hello, $tableName Col 2 Row 2!"),
            Json.obj("de_DE" -> "2015-01-02"),
            Json.obj("de_DE" -> "2015-01-02T14:37:47.110+01")
          )
        )
      )
    )

    import scala.collection.JavaConverters._
    for {
      (tableId, columnIds) <- createTableWithMultilanguageColumns(tableName)
      rows <- sendRequest("POST", s"/tables/$tableId/rows", valuesRow(columnIds))
      _ = logger.info(s"Row is $rows")
      rowIds = rows.getJsonArray("rows").asScala.map(_.asInstanceOf[JsonObject].getLong("id").toLong).toSeq
    } yield (tableId, columnIds, rowIds)
  }

  protected def createSimpleTableWithMultilanguageColumn(tableName: String, columnName: String): Future[(Long, Long)] = {
    for {
      table <- sendRequest("POST", "/tables", Json.obj("name" -> tableName))
      tableId = table.getLong("id").toLong
      columns <- sendRequest("POST", s"/tables/$tableId/columns", Json.obj("columns" -> Json.arr(
        Json.obj("kind" -> "text", "name" -> columnName, "multilanguage" -> true)
      )))
      columnId = columns.getJsonArray("columns").getJsonObject(0).getLong("id").toLong
    } yield {
      (tableId, columnId)
    }
  }

  protected def createTableWithMultilanguageColumns(tableName: String): Future[(Long, Seq[Long])] = {
    val createMultilanguageColumn = Json.obj(
      "columns" ->
        Json.arr(
          Json.obj("kind" -> "text", "name" -> "Test Column 1", "multilanguage" -> true, "identifier" -> true),
          Json.obj("kind" -> "boolean", "name" -> "Test Column 2", "multilanguage" -> true),
          Json.obj("kind" -> "numeric", "name" -> "Test Column 3", "multilanguage" -> true),
          Json.obj("kind" -> "richtext", "name" -> "Test Column 4", "multilanguage" -> true),
          Json.obj("kind" -> "shorttext", "name" -> "Test Column 5", "multilanguage" -> true),
          Json.obj("kind" -> "date", "name" -> "Test Column 6", "multilanguage" -> true),
          Json.obj("kind" -> "datetime", "name" -> "Test Column 7", "multilanguage" -> true)
        )
    )

    import scala.collection.JavaConverters._
    for {
      tableId <- sendRequest("POST", "/tables", Json.obj("name" -> tableName)) map (_.getLong("id"))
      columns <- sendRequest("POST", s"/tables/$tableId/columns", createMultilanguageColumn)
      columnIds = columns.getArray("columns").asScala.map(_.asInstanceOf[JsonObject].getLong("id").toLong).toSeq
    } yield {
      (tableId.toLong, columnIds)
    }
  }

  protected case class LinkTo(tableId: Long, columnId: Long)

  protected def createTableWithComplexColumns(tableName: String, linkTo: LinkTo): Future[(Long, Seq[Long], Long)] = {
    val createColumns = Json.obj(
      "columns" -> Json.arr(
        Json.obj("kind" -> "text", "name" -> "column 1 (text)"),
        Json.obj("kind" -> "text", "name" -> "column 2 (text multilanguage)", "multilanguage" -> true),
        Json.obj("kind" -> "numeric", "name" -> "column 3 (numeric)"),
        Json.obj("kind" -> "numeric", "name" -> "column 4 (numeric multilanguage)", "multilanguage" -> true),
        Json.obj("kind" -> "richtext", "name" -> "column 5 (richtext)"),
        Json.obj("kind" -> "richtext", "name" -> "column 6 (richtext multilanguage)", "multilanguage" -> true),
        Json.obj("kind" -> "date", "name" -> "column 7 (date)"),
        Json.obj("kind" -> "date", "name" -> "column 8 (date multilanguage)", "multilanguage" -> true),
        Json.obj("kind" -> "attachment", "name" -> "column 9 (attachment)")
      )
    )

    def createLinkColumn(fromColumnId: Long, linkTo: LinkTo) = Json.obj("columns" -> Json.arr(Json.obj(
      "kind" -> "link",
      "name" -> "column 10 (link)",
      "toTable" -> linkTo.tableId
    )))

    import scala.collection.JavaConverters._
    for {
      table <- sendRequest("POST", "/tables", Json.obj("name" -> tableName))
      tableId = table.getLong("id").toLong

      columns <- sendRequest("POST", s"/tables/$tableId/columns", createColumns)
      columnIds = columns.getArray("columns").asScala.map(_.asInstanceOf[JsonObject].getLong("id").toLong).toList

      linkColumn <- sendRequest("POST", s"/tables/$tableId/columns", createLinkColumn(columnIds.head, linkTo))
      linkColumnId = linkColumn.getArray("columns").getJsonObject(0).getLong("id").toLong

    } yield (tableId, columnIds, linkColumnId)
  }

  protected def createSimpleTableWithCell(tableName: String, columnType: ColType): Future[(Long, Long, Long)] = {
    for {
      table <- sendRequest("POST", "/tables", Json.obj("name" -> tableName))
      tableId = table.getLong("id").toLong
      column <- sendRequest("POST", s"/tables/$tableId/columns", Json.obj("columns" -> Json.arr(columnType.json)))
      columnId = column.getJsonArray("columns").getJsonObject(0).getLong("id").toLong
      rowPost <- sendRequest("POST", s"/tables/$tableId/rows")
      rowId = rowPost.getLong("id").toLong
    } yield (tableId, columnId, rowId)
  }


  protected def createSimpleTableWithValues(tableName: String, columnTypes: Seq[ColType], rows: Seq[Seq[Any]]): Future[(Long, Seq[Long], Seq[Long])] = {
    import scala.collection.JavaConverters._
    for {
      table <- sendRequest("POST", "/tables", Json.obj("name" -> tableName))
      tableId = table.getLong("id").toLong
      column <- sendRequest("POST", s"/tables/$tableId/columns", Json.obj("columns" -> Json.arr(columnTypes.map(_.json): _*)))
      columnIds = column.getJsonArray("columns").asScala.toStream.map(_.asInstanceOf[JsonObject].getLong("id").toLong)
      columnsPost = Json.arr(columnIds.map(id => Json.obj("id" -> id)): _*)
      rowsPost = Json.arr(rows.map(values => Json.obj("values" -> Json.arr(values: _*))): _*)
      rowPost <- sendRequest("POST", s"/tables/$tableId/rows", Json.obj("columns" -> columnsPost, "rows" -> rowsPost))
      rowIds = rowPost.getJsonArray("rows").asScala.toStream.map(_.asInstanceOf[JsonObject].getLong("id").toLong)
    } yield (tableId, columnIds, rowIds)
  }

}