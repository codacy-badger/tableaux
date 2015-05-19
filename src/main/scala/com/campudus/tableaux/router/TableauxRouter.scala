package com.campudus.tableaux.router

import com.campudus.tableaux.database.structure.{EmptyReturn, SetReturn, GetReturn}
import com.campudus.tableaux.helper.HelperFunctions
import HelperFunctions._
import com.campudus.tableaux.{TableauxConfig, NoJsonFoundException}
import com.campudus.tableaux.controller.{SystemController, TableauxController}
import com.campudus.tableaux.database._
import org.vertx.scala.core.http.HttpServerRequest
import org.vertx.scala.platform.Verticle
import org.vertx.scala.router.routing._

import scala.util.matching.Regex

object TableauxRouter {
  def apply(config: TableauxConfig, controllerCurry: (TableauxConfig) => TableauxController): TableauxRouter = {
    new TableauxRouter(config, controllerCurry(config))
  }
}

class TableauxRouter(override val config: TableauxConfig, val controller: TableauxController) extends BaseRouter {

  val TableIdColumnsIdRowsId: Regex = "/tables/(\\d+)/columns/(\\d+)/rows/(\\d+)".r
  val TableIdColumnsId: Regex = "/tables/(\\d+)/columns/(\\d+)".r
  val TableIdColumns: Regex = "/tables/(\\d+)/columns".r
  val TableIdRowsId: Regex = "/tables/(\\d+)/rows/(\\d+)".r
  val TableIdRows: Regex = "/tables/(\\d+)/rows".r
  val TableId: Regex = "/tables/(\\d+)".r

  val TableIdComplete: Regex = "/completetable/(\\d+)".r

  override def routes(implicit req: HttpServerRequest):  Routing = {
    case Get("/tables") => asyncGetReply(controller.getAllTables())
    case Get(TableId(tableId)) => asyncGetReply(controller.getTable(tableId.toLong))
    case Get(TableIdComplete(tableId)) => asyncGetReply(controller.getCompleteTable(tableId.toLong))
    case Get(TableIdColumns(tableId)) => asyncGetReply(controller.getColumns(tableId.toLong))
    case Get(TableIdColumnsId(tableId, columnId)) => asyncGetReply(controller.getColumn(tableId.toLong, columnId.toLong))
    case Get(TableIdRows(tableId)) => asyncGetReply(controller.getRows(tableId.toLong))
    case Get(TableIdRowsId(tableId, rowId)) => asyncGetReply(controller.getRow(tableId.toLong, rowId.toLong))
    case Get(TableIdColumnsIdRowsId(tableId, columnId, rowId)) => asyncGetReply(controller.getCell(tableId.toLong, columnId.toLong, rowId.toLong))

    case Post("/tables") => asyncSetReply {
      getJson(req) flatMap { json =>
        if (json.getFieldNames.contains("columns")) {
          if (json.getFieldNames.contains("rows")) {
            controller.createTable(json.getString("name"), jsonToSeqOfColumnNameAndType(json), jsonToSeqOfRowsWithValue(json))
          } else {
            controller.createTable(json.getString("name"), jsonToSeqOfColumnNameAndType(json), Seq())
          }
        } else {
          controller.createTable(json.getString("name"))
        }
      }
    }
    case Post(TableIdColumns(tableId)) => asyncSetReply {
      getJson(req) flatMap (json => controller.createColumn(tableId.toLong, jsonToSeqOfColumnNameAndType(json)))
    }
    case Post(TableIdRows(tableId)) => asyncSetReply {
      getJson(req) flatMap (json => controller.createRow(tableId.toLong, Some(jsonToSeqOfRowsWithColumnIdAndValue(json)))) recoverWith {
        case _: NoJsonFoundException => controller.createRow(tableId.toLong, None)
      }
    }
    case Post(TableIdColumnsIdRowsId(tableId, columnId, rowId)) => asyncSetReply {
      getJson(req) flatMap {
        json => controller.fillCell(tableId.toLong, columnId.toLong, rowId.toLong, jsonToValues(json))
      }
    }
    case Post(TableId(tableId)) => asyncEmptyReply(getJson(req) flatMap (json => controller.changeTableName(tableId.toLong, json.getString("name"))))
    case Post(TableIdColumnsId(tableId, columnId)) => asyncEmptyReply {
      getJson(req) flatMap {
        json =>
          val (optName, optOrd, optKind) = getColumnChanges(json)
          controller.changeColumn(tableId.toLong, columnId.toLong, optName, optOrd, optKind)
      }
    }

    case Delete(TableId(tableId)) => asyncEmptyReply(controller.deleteTable(tableId.toLong))
    case Delete(TableIdColumnsId(tableId, columnId)) => asyncEmptyReply(controller.deleteColumn(tableId.toLong, columnId.toLong))
    case Delete(TableIdRowsId(tableId, rowId)) => asyncEmptyReply(controller.deleteRow(tableId.toLong, rowId.toLong))
  }
}