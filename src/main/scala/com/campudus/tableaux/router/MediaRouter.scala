package com.campudus.tableaux.router

import java.util.UUID

import com.campudus.tableaux.controller.MediaController
import com.campudus.tableaux.database.domain.{DomainObject, MultiLanguageValue}
import com.campudus.tableaux.database.model.FolderModel.FolderId
import com.campudus.tableaux.{ArgumentChecker, TableauxConfig}
import io.vertx.core.http.HttpServerFileUpload
import io.vertx.ext.web.RoutingContext
import io.vertx.scala.FunctionConverters._
import org.vertx.scala.core.json.JsonObject
import org.vertx.scala.router.RouterException
import org.vertx.scala.router.routing._

import scala.concurrent.{Future, Promise}
import scala.util.matching.Regex

sealed trait FileAction

case class UploadAction(fileName: String,
                        mimeType: String,
                        exceptionHandler: (Throwable => Unit) => _,
                        endHandler: (() => Unit) => _,
                        streamToFile: (String) => _) extends FileAction

object MediaRouter {
  def apply(config: TableauxConfig, controllerCurry: (TableauxConfig) => MediaController): MediaRouter = {
    new MediaRouter(config, controllerCurry(config))
  }
}

class MediaRouter(override val config: TableauxConfig, val controller: MediaController) extends BaseRouter {
  val FolderId: Regex = s"/folders/(\\d+)".r

  val FilesLang: Regex = s"/files/($langtagRegex)".r

  val FileId: Regex = s"/files/($uuidRegex)".r
  val FileIdLang: Regex = s"/files/($uuidRegex)/($langtagRegex)".r
  val FileIdLangStatic: Regex = s"/files/($uuidRegex)/($langtagRegex)/.*".r

  override def routes(implicit context: RoutingContext): Routing = {
    /**
      * Create folder
      */
    case Post("/folders") => asyncSetReply({
      getJson(context) flatMap { implicit json =>
        val name = json.getString("name")
        val description = json.getString("description")
        val parent = getNullableField("parent")

        controller.addNewFolder(name, description, parent)
      }
    })

    /**
      * Retrieve root folder
      */
    case Get("/folders") => asyncGetReply({
      import ArgumentChecker._

      //TODO this will result in a unhandled exception error
      val sortByLangtag = checked(hasParam(getStringParam("langtag", context), "langtag"))
      controller.retrieveRootFolder(sortByLangtag)
    })

    /**
      * Retrieve folder
      */
    case Get(FolderId(id, langtag)) => asyncGetReply({
      import ArgumentChecker._

      //TODO this will result in a unhandled exception error
      val sortByLangtag = checked(hasParam(getStringParam("langtag", context), "langtag"))
      controller.retrieveFolder(id.toLong, sortByLangtag)
    })

    /**
      * Change folder
      */
    case Put(FolderId(id)) => asyncSetReply({
      getJson(context) flatMap { implicit json =>
        val name = json.getString("name")
        val description = json.getString("description")
        val parent = getNullableField("parent")

        controller.changeFolder(id.toLong, name, description, parent)
      }
    })

    /**
      * Delete folder and its files
      */
    case Delete(FolderId(id)) => asyncSetReply(controller.deleteFolder(id.toLong))

    /**
      * Create file handle
      */
    case Post("/files") => asyncSetReply({
      getJson(context) flatMap { implicit json =>

        val title = MultiLanguageValue[String](getNullableField("title"))
        val description = MultiLanguageValue[String](getNullableField("description"))
        val externalName = MultiLanguageValue[String](getNullableField("externalName"))

        val folder = getNullableField("folder")

        controller.addFile(title, description, externalName, folder)
      }
    })

    /**
      * Retrieve file meta information
      */
    case Get(FileId(uuid)) => asyncGetReply(controller.retrieveFile(UUID.fromString(uuid)).map({ case (file, _) => file }))

    /**
      * Serve file
      */
    case Get(FileIdLangStatic(uuid, langtag)) => AsyncReply({
      for {
        (file, paths) <- controller.retrieveFile(UUID.fromString(uuid))
      } yield {
        val absolute = config.isWorkingDirectoryAbsolute

        val mimeType = file.file.mimeType.get(langtag)
        val path = paths.get(langtag).get

        Header("Content-type", mimeType.get, SendFile(path.toString(), absolute))
      }
    })

    /**
      * Change file meta information
      */
    case Put(FileId(uuid)) => asyncSetReply({
      getJson(context) flatMap { implicit json =>

        val title = MultiLanguageValue[String](getNullableField("title"))
        val description = MultiLanguageValue[String](getNullableField("description"))
        val externalName = MultiLanguageValue[String](getNullableField("externalName"))

        val folder = getNullableField[FolderId]("folder")

        controller.changeFile(UUID.fromString(uuid), title, description, externalName, folder)
      }
    })

    /**
      * Replace/upload language specific file and its meta information
      */
    case Put(FileIdLang(uuid, langtag)) => {
      handleUpload(context, (action: UploadAction) => {
        controller.replaceFile(UUID.fromString(uuid), langtag, action)
      })
    }

    /**
      * Delete file
      */
    case Delete(FileId(uuid)) => asyncSetReply(controller.deleteFile(UUID.fromString(uuid)))

    /**
      * Delete language specific stuff
      */
    case Delete(FileIdLang(uuid, langtag)) => asyncSetReply(controller.deleteFile(UUID.fromString(uuid), langtag))
  }

  def getNullableField[A](field: String)(implicit json: JsonObject): Option[A] = {
    Option(json.getValue(field).asInstanceOf[A])
  }

  def handleUpload(implicit context: RoutingContext, fn: (UploadAction) => Future[DomainObject]): AsyncReply = asyncSetReply({
    logger.info(s"Handle upload for ${context.request().absoluteURI()} ${context.fileUploads()}")

    val req = context.request()

    val p = Promise[DomainObject]()

    val timerId = vertx.setTimer(10000L, { timerId: java.lang.Long =>
      p.failure(RouterException(message = "No valid file upload received", id = "errors.upload.invalidRequest", statusCode = 400))
    })

    req.setExpectMultipart(true)

    // TODO this only can handle one file upload per request
    req.uploadHandler({ upload: HttpServerFileUpload =>
      logger.info("Received a file upload")

      vertx.cancelTimer(timerId)

      val setExceptionHandler = (exHandler: Throwable => Unit) => upload.exceptionHandler(exHandler)
      val setEndHandler = (fn: () => Unit) => upload.endHandler(fn())
      val setStreamToFile = (fPath: String) => upload.streamToFileSystem(fPath)

      val action = UploadAction(upload.filename(), upload.contentType(), setExceptionHandler, setEndHandler, setStreamToFile)

      fn(action).map(p.success).recoverWith({ case e => logger.error("Upload failed", e); p.failure(e); Future.failed(e) })
    })

    p.future
  })
}