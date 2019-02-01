package com.campudus.tableaux.router

import com.campudus.tableaux.{InvalidRequestException, RequestContext, TableauxConfig}
import com.campudus.tableaux.controller.{MediaController, StructureController, SystemController, TableauxController}
import com.campudus.tableaux.database.DatabaseConnection
import com.campudus.tableaux.database.model._
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.CookieHandler

object RouterRegistry {

  def init(tableauxConfig: TableauxConfig, dbConnection: DatabaseConnection): Router = {

    val mainRouter: Router = Router.router(tableauxConfig.vertx)

    // This cookie handler will be called for all routes
    mainRouter.route().handler(CookieHandler.create())

    implicit val requestContext: RequestContext = RequestContext()

    val systemModel = SystemModel(dbConnection)
    val structureModel = StructureModel(dbConnection)
    val tableauxModel = TableauxModel(dbConnection, structureModel)
    val folderModel = FolderModel(dbConnection)
    val fileModel = FileModel(dbConnection)
    val attachmentModel = AttachmentModel(dbConnection, fileModel)

    val systemRouter = SystemRouter(tableauxConfig, SystemController(_, systemModel, tableauxModel, structureModel))
    val tableauxRouter = TableauxRouter(tableauxConfig, TableauxController(_, tableauxModel))
    val mediaRouter = MediaRouter(tableauxConfig, MediaController(_, folderModel, fileModel, attachmentModel))
    val structureRouter = StructureRouter(tableauxConfig, StructureController(_, structureModel))
    val documentationRouter = DocumentationRouter(tableauxConfig)

    mainRouter.mountSubRouter("/system", systemRouter.route)
    mainRouter.mountSubRouter("/", structureRouter.route)
    mainRouter.mountSubRouter("/", tableauxRouter.route)
    mainRouter.mountSubRouter("/", mediaRouter.route)
    mainRouter.mountSubRouter("/docs", documentationRouter.route)

    mainRouter.get("/").handler(systemRouter.defaultRoute)
    mainRouter.get("/index.html").handler(systemRouter.defaultRoute)

    mainRouter.route().handler(systemRouter.noRouteMatched)

    mainRouter
  }
}
