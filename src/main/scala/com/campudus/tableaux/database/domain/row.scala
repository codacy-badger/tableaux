package com.campudus.tableaux.database.domain

import com.campudus.tableaux.database.model.TableauxModel._
import org.vertx.scala.core.json._

case class RawRow(id: RowId, rowLevelFlags: RowLevelFlags, cellLevelFlags: CellLevelFlags, values: Seq[_])

case class Row(table: Table, id: RowId, rowLevelFlags: RowLevelFlags, cellLevelFlags: CellLevelFlags, values: Seq[_]) extends DomainObject {
  override def getJson: JsonObject = {
    val json = Json.obj(
      "id" -> id,
      "values" -> compatibilityGet(values)
    )

    if (rowLevelFlags.finalFlag || rowLevelFlags.needsTranslationFlags.nonEmpty) {
      json.mergeIn(rowLevelFlags.getJson)
    }

    if (cellLevelFlags.flags.exists(_._2.nonEmpty)) {
      json.mergeIn(cellLevelFlags.getJson)
    }

    json
  }
}

case class RowSeq(rows: Seq[Row], page: Page = Page(Pagination(None, None), None)) extends DomainObject {
  override def getJson: JsonObject = Json.obj("page" -> compatibilityGet(page), "rows" -> (rows map (_.getJson)))
}

case class DependentRows(table: Table, column: ColumnType[_], rows: Seq[JsonObject]) extends DomainObject {
  override def getJson: JsonObject = Json.obj("table" -> table.getJson, "column" -> compatibilityGet(column), "rows" -> compatibilityGet(rows))
}

case class DependentRowsSeq(dependentRowsSeq: Seq[DependentRows]) extends DomainObject {
  override def getJson: JsonObject = Json.obj("dependentRows" -> compatibilityGet(dependentRowsSeq))
}