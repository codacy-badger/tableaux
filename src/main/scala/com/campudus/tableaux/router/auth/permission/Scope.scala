package com.campudus.tableaux.router.auth.permission

sealed trait Scope {
  val name: String
  override def toString: String = name
}

object Scope {

  def apply(scope: String): Scope = {
    scope match {
      case ScopeTable.name => ScopeTable
      case ScopeColumn.name => ScopeColumn
      case ScopeMedia.name => ScopeMedia
      case ScopeTableGroup.name => ScopeTableGroup
      case _ => throw new IllegalArgumentException(s"Invalid argument for PermissionType $scope")
    }
  }
}

case object ScopeTable extends Scope { override val name = "table" }
case object ScopeColumn extends Scope { override val name = "column" }
case object ScopeMedia extends Scope { override val name = "media" }
case object ScopeTableGroup extends Scope { override val name = "tableGroup" }
