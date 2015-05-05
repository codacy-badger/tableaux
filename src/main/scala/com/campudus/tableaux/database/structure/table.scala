package com.campudus.tableaux.database.structure

import com.campudus.tableaux.database.DomainObject
import com.campudus.tableaux.database.Tableaux._
import org.vertx.scala.core.json._

case class Table(id: IdType, name: String) extends DomainObject {
  def getJson: JsonObject = Json.obj("tableId" -> id, "tableName" -> name)

  def setJson: JsonObject = Json.obj("tableId" -> id)
}

case class TableSeq(tables: Seq[Table]) extends DomainObject {
  def getJson: JsonObject = Json.obj("tables" -> (tables map { t => Json.obj("id" -> t.id, "name" -> t.name) }))

  def setJson: JsonObject = Json.obj("tables" -> (tables map { t => Json.obj("id" -> t.id, "name" -> t.name) }))
}

case class CompleteTable(table: Table, columnList: Seq[ColumnType[_]], rowList: RowSeq) extends DomainObject {
  def getJson: JsonObject = table.getJson.mergeIn(Json.obj("columns" -> (columnList map { _.getJson.getArray("columns").get[JsonObject](0) }))).mergeIn(rowList.getJson)

  def setJson: JsonObject = table.setJson.mergeIn(Json.obj("columns" -> (columnList map { _.setJson.getArray("columns").get[JsonObject](0) }))).mergeIn(rowList.setJson)
}
