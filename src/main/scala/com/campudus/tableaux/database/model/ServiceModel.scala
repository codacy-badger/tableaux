package com.campudus.tableaux.database.model

import com.campudus.tableaux.ShouldBeUniqueException
import com.campudus.tableaux.database._
import com.campudus.tableaux.database.domain._
import com.campudus.tableaux.database.model.ServiceModel.ServiceId
import com.campudus.tableaux.database.model.TableauxModel.Ordering
import com.campudus.tableaux.helper.ResultChecker._
import io.circe.parser.decode
import org.vertx.scala.core.json.{Json, JsonArray, JsonObject}

import scala.concurrent.Future

object ServiceModel {
  type ServiceId = Long
  type Ordering = Long

  def apply(connection: DatabaseConnection): ServiceModel = {
    new ServiceModel(connection)
  }
}

class ServiceModel(override protected[this] val connection: DatabaseConnection) extends DatabaseQuery {
  val table: String = "system_services"

  def update(
      serviceId: ServiceId,
      name: Option[String],
      serviceType: Option[ServiceType],
      ordering: Option[Ordering],
      displayName: Option[MultiLanguageValue[String]],
      description: Option[MultiLanguageValue[String]],
      active: Option[Boolean],
      config: Option[JsonObject],
      scope: Option[JsonObject]
  ): Future[Unit] = {

    val updateParamOpts = Map(
      "name" -> name,
      "type" -> serviceType,
      "ordering" -> ordering,
      "displayName" -> displayName,
      "description" -> description,
      "active" -> active,
      "config" -> config,
      "scope" -> scope
    )

    val paramsToUpdate = updateParamOpts
      .filter({ case (_, v) => v.isDefined })
      .map({ case (k, v) => (k, v.orNull) })

    val parameterUpdateString = paramsToUpdate.keys.toIndexedSeq.map(column => s"$column = ?").mkString(", ")
    val update = s"UPDATE $table SET $parameterUpdateString, updated_at = CURRENT_TIMESTAMP WHERE id = ?"

    val values = paramsToUpdate.values.toIndexedSeq
      .map({
        case m: MultiLanguageValue[_] => m.getJson.toString
        case a => a.toString
      })

    val binds = Json.arr(values: _*).add(serviceId.toString)

    for {
      _ <- name match {
        case Some(n) => checkUniqueName(n)
        case _ => Future.successful(())
      }

      _ <- connection.query(update, binds)
    } yield ()
  }

  private def selectStatement(conditions: Option[String]): String = {
    val where = if (conditions.isDefined) {
      s"WHERE ${conditions.get}"
    } else {
      ""
    }

    s"""SELECT
       |  id,
       |  type,
       |  name,
       |  ordering,
       |  displayname,
       |  description,
       |  active,
       |  config,
       |  scope,
       |  created_at,
       |  updated_at
       |FROM $table $where ORDER BY name""".stripMargin
  }

  def retrieve(id: ServiceId): Future[Service] = {
    for {
      result <- connection.query(selectStatement(Some("id = ?")), Json.arr(id.toString))
      resultArr <- Future(selectNotNull(result))
    } yield {
      convertJsonArrayToService(resultArr.head)
    }
  }

  def delete(id: ServiceId): Future[Unit] = {
    val delete = s"DELETE FROM $table WHERE id = ?"

    for {
      result <- connection.query(delete, Json.arr(id))
      _ <- Future(deleteNotNull(result))
    } yield ()
  }

  def create(
      name: String,
      serviceType: ServiceType,
      ordering: Option[Long],
      displayName: MultiLanguageValue[String],
      description: MultiLanguageValue[String],
      active: Boolean,
      config: Option[JsonObject],
      scope: Option[JsonObject]
  ): Future[ServiceId] = {

    val insert = s"""INSERT INTO $table (
                    |  name,
                    |  type,
                    |  ordering,
                    |  displayname,
                    |  description,
                    |  active,
                    |  config,
                    |  scope)
                    |VALUES
                    |  (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""".stripMargin

    for {
      _ <- checkUniqueName(name)

      result <- connection.query(
        insert,
        Json
          .arr(
            name,
            serviceType.toString,
            ordering.orNull,
            displayName.getJson.toString,
            description.getJson.toString,
            active,
            config.map(_.toString).orNull,
            scope.map(_.toString).orNull
          )
      )

      serviceId = insertNotNull(result).head.get[ServiceId](0)
    } yield serviceId

  }

  def retrieveAll(): Future[Seq[Service]] = {
    for {
      result <- connection.query(selectStatement(None))
      resultArr <- Future(resultObjectToJsonArray(result))
    } yield {
      resultArr.map(convertJsonArrayToService)
    }
  }

  private def convertJsonArrayToService(arr: JsonArray): Service = {
    val config = decode[io.circe.JsonObject](arr.get[String](7)) match {
      case Right(json) => json
      case Left(_) => io.circe.JsonObject.empty
    }

    val scope = decode[io.circe.JsonObject](arr.get[String](8)) match {
      case Right(json) => json
      case Left(_) => io.circe.JsonObject.empty
    }

    Service(
      arr.get[ServiceId](0), // id
      ServiceType(Option(arr.get[String](1))), // type
      arr.get[String](2), // name
      arr.get[Ordering](3), // ordering
      MultiLanguageValue.fromString(arr.get[String](4)), // displayname
      MultiLanguageValue.fromString(arr.get[String](5)), // description
      arr.get[Boolean](6), // active
      config, // config
      scope, // scope
      convertStringToDateTime(arr.get[String](9)), // created_at
      convertStringToDateTime(arr.get[String](10)) // updated_at
    )
  }

  private def checkUniqueName(name: String): Future[Unit] = {

    val sql = s"SELECT COUNT(*) = 0 FROM $table WHERE name = ?"
    connection
      .selectSingleValue[Boolean](sql, Json.arr(name))
      .flatMap({
        case true => Future.successful(())
        case false => Future.failed(ShouldBeUniqueException(s"Name of service should be unique $name.", "service"))
      })
  }
}
