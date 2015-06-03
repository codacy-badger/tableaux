package com.campudus.tableaux

import java.net.URLEncoder

import com.campudus.tableaux.database.DatabaseConnection
import com.campudus.tableaux.database.domain.{File, Folder}
import com.campudus.tableaux.database.model.{FileModel, FolderModel}
import com.campudus.tableaux.helper.FutureUtils
import org.junit.{Ignore, Test}
import org.vertx.java.core.json.JsonObject
import org.vertx.scala.core.http.HttpClientRequest
import org.vertx.scala.core.json.Json
import org.vertx.scala.core.streams.Pump
import org.vertx.testtools.VertxAssert._

import scala.concurrent.{Future, Promise}

class MediaTest extends TableauxTestBase {

  def createFileModel(): FileModel = {
    val dbConnection = DatabaseConnection(tableauxConfig)

    new FileModel(dbConnection)
  }

  def createFolderModel(): FolderModel = {
    val dbConnection = DatabaseConnection(tableauxConfig)

    new FolderModel(dbConnection)
  }

  @Test
  @Ignore
  def testModel(): Unit = okTest {
    val file = File("lulu", "lala", "llu")

    for {
      model <- Future.successful(createFileModel())

      insertedFile1 <- model.add(file)
      insertedFile2 <- model.add(file)

      retrievedFile <- model.retrieve(insertedFile1.uuid.get)
      updatedFile <- model.update(File(retrievedFile.uuid.get, "blub", "flab", "test"))

      allFiles <- model.retrieveAll()

      size <- model.size()
      _ <- model.delete(insertedFile1)
      sizeAfterDelete <- model.size()
    } yield {
      assertEquals(insertedFile1, retrievedFile)

      assert(retrievedFile.updatedAt.isEmpty)
      assert(updatedFile.createdAt.isDefined)
      assert(updatedFile.updatedAt.isDefined)

      assert(updatedFile.updatedAt.get.isAfter(updatedFile.createdAt.get))

      assertEquals(2, allFiles.toList.size)

      assertEquals(2, size)
      assertEquals(1, sizeAfterDelete)
    }
  }

  @Test
  @Ignore
  def testFolder(): Unit = okTest {
    val folder = Folder(None, name = "hallo", description = "Test", None, None, None)

    for {
      model <- Future.successful(createFolderModel())

      insertedFolder1 <- model.add(folder)
      insertedFolder2 <- model.add(folder)

      retrievedFolder <- model.retrieve(insertedFolder1.id.get)
      updatedFolder <- model.update(Folder(retrievedFolder.id, name = "blub", description = "flab", None, None, None))

      folders <- model.retrieveAll()

      size <- model.size()
      _ <- model.delete(insertedFolder1)
      sizeAfterDelete <- model.size()
    } yield {
      assertEquals(insertedFolder1, retrievedFolder)

      assert(retrievedFolder.updatedAt.isEmpty)
      assert(updatedFolder.createdAt.isDefined)
      assert(updatedFolder.updatedAt.isDefined)

      assert(updatedFolder.updatedAt.get.isAfter(updatedFolder.createdAt.get))

      assertEquals(2, folders.toList.size)

      assertEquals(2, size)
      assertEquals(1, sizeAfterDelete)
    }
  }

  @Test
  def uploadFileWithNonAsciiCharacterName(): Unit = okTest {
    val file = "/com/campudus/tableaux/uploads/Screen Shöt.jpg"
    val mimetype = "image/jpeg"

    val put = Json.obj("name" -> "情暮夜告書究", "description" -> "情暮夜告書究情暮夜告書究")

    for {
      uploadResponse <- uploadFile(file, mimetype)
      puttedFile <- sendRequestWithJson("PUT", put, s"/files/${uploadResponse.getString("uuid")}")
      deletedFile <- sendRequest("DELETE", s"/files/${uploadResponse.getString("uuid")}")
    } yield {
      assertEquals(true, uploadResponse.getBoolean("tmp"))
      assertEquals("Screen Shöt.jpg", uploadResponse.getString("name"))
      assertEquals(uploadResponse.getString("name"), uploadResponse.getString("filename"))

      assertEquals(false, puttedFile.containsField("tmp"))
      assertEquals(put.getString("name"), puttedFile.getString("name"))
      assertEquals(put.getString("description"), puttedFile.getString("description"))
      assertEquals(uploadResponse.getString("filename"), puttedFile.getString("filename"))

      assertEquals(puttedFile, deletedFile)
    }
  }

  @Test
  def retreiveFile(): Unit = okTest {
    val fileName = "Scr$en Shot.pdf"
    val file = s"/com/campudus/tableaux/uploads/$fileName"
    val mimetype = "application/pdf"
    val size = vertx.fileSystem.propsSync(getClass.getResource(file).toURI.getPath).size()

    val put = Json.obj("name" -> "Test PDF", "description" -> "A description about that PDF.")

    import FutureUtils._

    for {
      uploadResponse <- uploadFile(file, mimetype)
      _ <- sendRequestWithJson("PUT", put, s"/files/${uploadResponse.getString("uuid")}")
      request <- promisify { p: Promise[Unit] =>

        val url = s"/files/${uploadResponse.getString("uuid")}/" + URLEncoder.encode(fileName, "UTF-8")

        logger.info(s"Load file ${url.toString}")

        val req = httpRequest("GET", url.toString, {
          resp =>
            assertEquals(200, resp.statusCode())

            assertEquals("Should get the correct MIME type", mimetype, resp.headers().get("content-type").get.head)
            assertEquals("Should get the correct content length", String.valueOf(size), resp.headers().get("content-length").get.head)

            resp.bodyHandler { buf =>
              assertEquals("Should get the same size back as the file really is", size, buf.length())

              testComplete()
              p.success()
            }
        }).exceptionHandler({ ext =>
          fail(ext.toString)

          testComplete()
          p.failure(ext)
        }).end()
      }
      _ <- sendRequest("DELETE", s"/files/${uploadResponse.getString("uuid")}")
    } yield request
  }

  private def uploadFile(file: String, mimeType: String): Future[JsonObject] = {
    val filePath = getClass.getResource(file).toURI.getPath
    val fileName = file.substring(file.lastIndexOf("/") + 1)

    def requestHandler(req: HttpClientRequest): Unit = {
      val boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO"
      val header =
        "--" + boundary + "\r\n" +
          "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
          "Content-Type: " + mimeType + "\r\n\r\n"
      val footer = "\r\n--" + boundary + "--\r\n"

      val contentLength = String.valueOf(vertx.fileSystem.propsSync(filePath).size() + header.length + footer.length)
      req.putHeader("Content-length", contentLength)
      req.putHeader("Content-type", s"multipart/form-data; boundary=$boundary")

      logger.info(s"Loading file '$filePath' from disc, content-length=$contentLength")
      req.write(header)
      vertx.fileSystem.open(filePath, { ar =>
        assertTrue(s"Should be able to open file $filePath", ar.succeeded())
        val file = ar.result()
        val pump = Pump.createPump(file, req)
        file.endHandler({
          file.close({ ar =>
            if (ar.succeeded()) {
              logger.info(s"File loaded, ending request, ${pump.bytesPumped()} bytes pumped.")
              req.end(footer)
            } else {
              fail(ar.cause().getMessage)
            }
          })
        })

        pump.start()
      })
    }

    import FutureUtils._

    promisify { p: Promise[JsonObject] =>
      requestHandler(httpRequest("POST", "/files", jsonResponse(p)))
    }
  }
}
