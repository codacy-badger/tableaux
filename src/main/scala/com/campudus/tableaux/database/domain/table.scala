package com.campudus.tableaux.database.domain

import com.campudus.tableaux.RequestContext
import com.campudus.tableaux.database.model.TableauxModel._
import com.campudus.tableaux.router.auth.permission.{ComparisonObjects, RoleModel, ScopeTable, ScopeTableSeq}
import org.vertx.scala.core.json._
import io.vertx.core.json.JsonObject

object TableType {

  def apply(string: String): TableType = {
    string match {
      case GenericTable.NAME => GenericTable
      case SettingsTable.NAME => SettingsTable
      case _ => throw new IllegalArgumentException("Unknown table type")
    }
  }
}

sealed trait TableType {
  val NAME: String
}

case object GenericTable extends TableType {
  override val NAME = "generic"
}

case object SettingsTable extends TableType {
  override val NAME = "settings"
}

object Table {

  def apply(id: TableId)(
      implicit requestContext: RequestContext,
      roleModel: RoleModel
  ): Table = {
    Table(id, "unknown", hidden = false, None, Seq.empty, GenericTable, None)
  }
}

case class Table(
    id: TableId,
    name: String,
    hidden: Boolean,
    langtags: Option[Seq[String]],
    displayInfos: Seq[DisplayInfo],
    tableType: TableType,
    tableGroup: Option[TableGroup]
)(
    implicit requestContext: RequestContext,
    roleModel: RoleModel = RoleModel()
) extends DomainObject {

  override def getJson: JsonObject = {
    val tableJson = Json.obj(
      "id" -> id,
      "name" -> name,
      "hidden" -> hidden,
      "displayName" -> Json.obj(),
      "description" -> Json.obj()
    )

    langtags.foreach(lt => tableJson.mergeIn(Json.obj("langtags" -> lt)))

    displayInfos.foreach { di =>
      {
        di.optionalName.map(name => {
          tableJson.mergeIn(
            Json.obj("displayName" -> tableJson.getJsonObject("displayName").mergeIn(Json.obj(di.langtag -> name)))
          )
        })
        di.optionalDescription.map(desc => {
          tableJson.mergeIn(
            Json.obj("description" -> tableJson.getJsonObject("description").mergeIn(Json.obj(di.langtag -> desc)))
          )
        })
      }
    }

    if (tableType != GenericTable) {
      tableJson.mergeIn(Json.obj("type" -> tableType.NAME))
    }

    tableGroup.foreach(tg => tableJson.put("group", tg.getJson))

    roleModel.enrichDomainObject(tableJson, ScopeTable, ComparisonObjects(this))
  }
}

case class TableSeq(tables: Seq[Table])(
    implicit requestContext: RequestContext,
    roleModel: RoleModel
) extends DomainObject {

  override def getJson: JsonObject = {
    val tableSeqJson = Json.obj("tables" -> compatibilityGet(tables))
    roleModel.enrichDomainObject(tableSeqJson, ScopeTableSeq)
  }
}

case class CompleteTable(table: Table, columns: Seq[ColumnType[_]], rowList: RowSeq) extends DomainObject {

  override def getJson: JsonObject = {
    table.getJson
      .mergeIn(Json.obj("columns" -> columns.map {
        _.getJson
      }))
      .mergeIn(rowList.getJson)
  }
}

case class TablesStructure(tables: Seq[Table], columnMap: Map[TableId, Seq[ColumnType[_]]]) extends DomainObject {
  override def getJson: JsonObject = {
    Json.obj("tables" -> tables.map(tbl => {
      tbl.getJson.mergeIn(
        Json.obj("columns" -> {
          val columns = columnMap.getOrElse(tbl.id, Seq[ColumnType[_]]())
          columns.map(_.getJson)
        })
      )
    }))
  }
}
