package com.campudus.tableaux.router.auth.permission

import com.campudus.tableaux.database.domain.{ColumnType, Table}

object ComparisonObjects {

  def apply(): ComparisonObjects = {
    new ComparisonObjects()
  }

  def apply(table: Table): ComparisonObjects = {
    new ComparisonObjects(Some(table))
  }

  def apply(table: Table, column: ColumnType[_]): ComparisonObjects = {
    new ComparisonObjects(Some(table), Some(column))
  }

  def apply(column: ColumnType[_]): ComparisonObjects = {
    new ComparisonObjects(columnOpt = Some(column))
  }

  def apply(column: ColumnType[_], value: Any): ComparisonObjects = {
    new ComparisonObjects(columnOpt = Some(column), valueOpt = Option(value))
  }

  def apply(table: Table, column: ColumnType[_], value: Any): ComparisonObjects = {
    new ComparisonObjects(Some(table), Some(column), Option(value))
  }
}

/**
  * Container for optional comparison objects. For example ScopeMedia doesn't need
  * any comparison object, while ScopeColumn can have a Table, a Column and a Langtag (is in value object).
  */
case class ComparisonObjects(
    tableOpt: Option[Table] = None,
    columnOpt: Option[ColumnType[_]] = None,
    valueOpt: Option[Any] = None
) {

  def merge(column: ColumnType[_]): ComparisonObjects = {
    new ComparisonObjects(this.tableOpt, Some(column))
  }

  def merge(value: Any): ComparisonObjects = {
    new ComparisonObjects(this.tableOpt, this.columnOpt, Some(value))
  }
}
