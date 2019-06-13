package com.campudus.tableaux.router.auth

import com.campudus.tableaux.helper.JsonUtils.asSeqOf
import org.vertx.scala.core.json._

import scala.collection.JavaConverters._

object RoleModel {

  def apply(jsonObject: JsonObject): RoleModel = {
    new RoleModel(jsonObject)
  }
}

case class RoleModel(jsonObject: JsonObject) {

  val role2permissions: Map[String, Seq[Permission]] =
    jsonObject
      .fieldNames()
      .asScala
      .map(
        key => {
          val permissionsJson: Seq[JsonObject] = asSeqOf[JsonObject](jsonObject.getJsonArray(key))
          (key, permissionsJson.map(permissionJson => Permission(permissionJson)))
        }
      )
      .toMap

  override def toString: String =
    role2permissions
      .map({
        case (key, permission) => s"$key => ${permission.toString}"
      })
      .mkString("\n")

  def getPermissionsFor(roleName: String): Seq[Permission] = role2permissions.getOrElse(roleName, Seq.empty[Permission])

}

object Permission {

  def apply(jsonObject: JsonObject): Permission = {
    val permissionType: PermissionType = PermissionType(jsonObject.getString("type"))
    val actionString: Seq[String] = asSeqOf[String](jsonObject.getJsonArray("action"))
    val actions: Seq[Action] = actionString.map(key => Action(key))
    val scope: Scope = Scope(jsonObject.getString("scope"))
    val condition: ConditionContainer = ConditionContainer(jsonObject.getJsonObject("condition"))

    new Permission(permissionType, actions, scope, condition)
  }
}

case class Permission(permissionType: PermissionType,
                      actions: Seq[Action],
                      scope: Scope,
                      condition: ConditionContainer) {}

sealed trait PermissionType

object PermissionType {

  def apply(permissionTypeString: String): PermissionType = {
    permissionTypeString match {
      case "grant" => Grant
      case "deny" => Deny
      case _ => throw new IllegalArgumentException(s"Invalid argument for PermissionType $permissionTypeString")
    }
  }
}

case object Grant extends PermissionType

case object Deny extends PermissionType

sealed trait Action {
  val name: String
  override def toString: String = name
}

object Action {

  def apply(action: String): Action = {
    action match {
      case View.name => View
      case EditDisplayProperty.name => EditDisplayProperty
      case CreateRow.name => CreateRow
      case DeleteRow.name => DeleteRow
      case Create.name => Create
      case Delete.name => Delete
      case EditStructureProperty.name => EditStructureProperty
      case EditCellAnnotation.name => EditCellAnnotation
      case EditRowAnnotation.name => EditRowAnnotation
      case ViewCellValue.name => ViewCellValue
      case EditCellValue.name => EditCellValue
      case _ => throw new IllegalArgumentException(s"Invalid argument for PermissionType $action")
    }
  }
}

case object Create extends Action { override val name = "create" }
case object View extends Action { override val name = "view" }
case object Edit extends Action { override val name = "edit" } // only for media
case object Delete extends Action { override val name = "delete" }
case object EditDisplayProperty extends Action { override val name = "editDisplayProperty" }
case object CreateRow extends Action { override val name = "createRow" }
case object DeleteRow extends Action { override val name = "deleteRow" }
case object EditStructureProperty extends Action { override val name = "editStructureProperty" }
case object EditCellAnnotation extends Action { override val name = "editCellAnnotation" }
case object EditRowAnnotation extends Action { override val name = "editRowAnnotation" }
case object ViewCellValue extends Action { override val name = "viewCellValue" }
case object EditCellValue extends Action { override val name = "editCellValue" }

sealed trait Scope {
  val name: String
  override def toString: String = name
}

object Scope {

  def apply(scope: String): Scope = {
    scope match {
      case Table.name => Table
      case Column.name => Column
      case Row.name => Row
      case Cell.name => Cell
      case Media.name => Media
      case _ => throw new IllegalArgumentException(s"Invalid argument for PermissionType $scope")
    }
  }
}

case object Table extends Scope { override val name = "table" }
case object Column extends Scope { override val name = "column" }
case object Row extends Scope { override val name = "row" }
case object Cell extends Scope { override val name = "cell" }
case object Media extends Scope { override val name = "media" }

object ConditionContainer {

  def apply(jsonObjectOrNull: JsonObject): ConditionContainer = {

    val jsonObject: JsonObject = Option(jsonObjectOrNull).getOrElse(Json.emptyObj())

    val ct: ConditionOption = Option(jsonObject.getJsonObject("table")).map(ConditionTable).getOrElse(NoneCondition)
    val cc: ConditionOption = Option(jsonObject.getJsonObject("column")).map(ConditionColumn).getOrElse(NoneCondition)
    val cl: ConditionOption = Option(jsonObject.getString("langtag")).map(ConditionLangtag).getOrElse(NoneCondition)

    new ConditionContainer(ct, cc, cl)
  }
}

case class ConditionContainer(conditionTable: ConditionOption,
                              conditionColumn: ConditionOption,
                              conditionLangtag: ConditionOption) {}

sealed trait ConditionOption

case class ConditionTable(jsonObject: JsonObject) extends ConditionOption {
  // TODO implement regex validation for all possible fields
  //  - id
  //  - name
  //  - hidden
  //  - langtags
  //  - displayInfos
  //  - tableType
  //  - tableGroup
}

case class ConditionColumn(jsonObject: JsonObject) extends ConditionOption {
  // TODO implement regex validation for all possible fields
  //  - description
  //  - displayName
  //  - id
  //  - identifier
  //  - kind
  //  - multilanguage
  //  - name
  //  - ordering
}
case class ConditionLangtag(langtagRegex: String) extends ConditionOption {
  // TODO implement regex validation

}

case object NoneCondition extends ConditionOption
