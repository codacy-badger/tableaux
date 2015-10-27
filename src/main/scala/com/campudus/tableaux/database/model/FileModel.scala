package com.campudus.tableaux.database.model

import java.util.UUID

import com.campudus.tableaux.database.domain.{File, MultiLanguageValue}
import com.campudus.tableaux.database.model.FolderModel.FolderId
import com.campudus.tableaux.database.{DatabaseConnection, DatabaseHandler}
import com.campudus.tableaux.helper.ResultChecker._
import org.joda.time.DateTime
import org.vertx.scala.core.json.{Json, JsonArray, JsonObject}

import scala.concurrent.Future
import scala.util.Try

object FileModel {
  def apply(connection: DatabaseConnection): FileModel = {
    new FileModel(connection)
  }
}

class FileModel(override protected[this] val connection: DatabaseConnection) extends DatabaseHandler[File, UUID] {
  val table: String = "file"

  /**
   * Will add a new entity marked as temporary!
   */
  override def add(o: File): Future[File] = {
    //if a UUID is already defined use this one
    val uuid = o.uuid.getOrElse(UUID.randomUUID())

    val insert =
      s"""INSERT INTO $table (
          |uuid,
          |idfolder,
          |tmp) VALUES (?,?,true) RETURNING created_at""".stripMargin

    val b = MultiLanguageValue.merge(Map(
      "title" -> o.title.values,
      "description" -> o.description.values,
      "internal_name" -> o.internalName.values,
      "external_name" -> o.externalName.values,
      "mime_type" -> o.mimeType.values
    ))

    for {
      resultJson <- connection.query(insert, Json.arr(uuid.toString, o.folder.orNull))
      resultRow = insertNotNull(resultJson).head

      _ <- addTranslations(uuid, b)

    } yield {
      val createdAt = DateTime.parse(resultRow.get[String](0))
      o.copy(uuid = Some(uuid), createdAt = Some(createdAt))
    }
  }

  private def addTranslations(uuid: UUID, map: Map[String, Map[String, _]]): Future[Seq[JsonObject]] = {
    // TODO add in order
    Future.sequence(map.foldLeft(Seq.empty[Future[JsonObject]]) {
      case (result, (langtag, columnsValueMap)) =>
        val columns = columnsValueMap.keySet.toSeq
        val values = columnsValueMap.values.toSeq

        val placeholder = columns.map(_ => "?").mkString(", ")

        val select = s"SELECT COUNT(uuid) FROM file_lang WHERE uuid = ? AND langtag = ?"
        val insert = s"INSERT INTO file_lang(${columns.mkString(",")}, uuid, langtag) VALUES($placeholder, ?, ?)"

        val update = s"UPDATE file_lang SET ${columns.map(column => s"$column = ?").mkString(", ")} WHERE uuid = ? AND langtag = ?"

        val binds = Json.arr(values: _*).add(uuid.toString).add(langtag)

        val future = connection.transactional({ case t =>
          for {
            (t, count) <- t.query(select, Json.arr(uuid.toString, langtag)).map({
              case (t, json) =>
                (t, selectNotNull(json).head.get[Long](0))
            })
            (t, result) <- if (count > 0) {
              t.query(update, binds)
            } else {
              t.query(insert, binds)
            }
          } yield (t, result)
        })

        result :+ future
    })
  }

  private def json_agg(tableAlias: String, column: String, languageColumn: String = "langtag"): String = {
    s"json_object_agg(DISTINCT COALESCE($tableAlias.$languageColumn,'de_DE'), $tableAlias.$column) as $column"
  }

  private def select(where: String): String = {
    s"""SELECT
        |	f.uuid,
        |	f.idfolder,
        |	f.created_at,
        |	f.updated_at,
        |
        |	${json_agg("fl", "title")},
        |	${json_agg("fl", "description")},
        |	${json_agg("fl", "internal_name")},
        |	${json_agg("fl", "external_name")},
        |	${json_agg("fl", "mime_type")}
        |
        |FROM file f LEFT JOIN file_lang fl ON (f.uuid = fl.uuid)
        |WHERE $where
        |GROUP BY f.uuid""".stripMargin
  }

  private def selectOrdered(where: String, langtag: String): String = {
    s"""
       |SELECT
       |	s.*
       |FROM (
       | ${select(where)}
       |) s
       |ORDER BY s.title->>'${langtag}' ASC NULLS FIRST
     """.stripMargin
  }

  override def retrieve(id: UUID): Future[File] = retrieve(id, withTmp = false)

  def retrieve(id: UUID, withTmp: Boolean): Future[File] = {
    for {
      resultJson <- if (withTmp) {
        connection.query(select("f.uuid = ?"), Json.arr(id.toString))
      } else {
        connection.query(select("f.uuid = ? AND tmp = FALSE"), Json.arr(id.toString))
      }
      resultRow = selectNotNull(resultJson).head
    } yield {
      convertRowToFile(resultRow)
    }
  }

  override def retrieveAll(): Future[Seq[File]] = {
    for {
      resultJson <- connection.query(select("f.tmp = FALSE"))
      resultRows = selectNotNull(resultJson)
    } yield {
      resultRows.map(convertRowToFile)
    }
  }

  def retrieveFromFolder(folder: Option[FolderId]): Future[Seq[File]] = {
    for {
      resultJson <- if (folder.isEmpty) {
        connection.query(selectOrdered("f.idfolder IS NULL AND f.tmp = FALSE", "de_DE"))
      } else {
        connection.query(selectOrdered("f.idfolder = ? AND f.tmp = FALSE", "de_DE"), Json.arr(folder.get))
      }

      resultRows = getSeqOfJsonArray(resultJson)
    } yield {
      resultRows.map(convertRowToFile)
    }
  }

  def convertRowToFile(row: JsonArray): File = {
    File(
      row.get[String](0), //uuid
      row.get[Long](1), //idfolder

      row.get[String](4), //title
      row.get[String](5), //description
      row.get[String](6), //internal_name
      row.get[String](7), //external_name
      row.get[String](8), //mime_type

      row.get[String](2), //created_at
      row.get[String](3) //updated_at
    )
  }

  implicit def convertStringToMultiLanguage[A](str: String): MultiLanguageValue[A] = {
    MultiLanguageValue[A](Try(Json.fromObjectString(str)).toOption)
  }

  implicit def convertStringToUUID(str: String): Option[UUID] = {
    Some(UUID.fromString(str))
  }

  override def update(o: File): Future[File] = {
    val update =
      s"""UPDATE $table SET
          |idfolder = ?,
          |updated_at = CURRENT_TIMESTAMP,
          |tmp = FALSE
          |WHERE uuid = ?""".stripMargin

    val b = MultiLanguageValue.merge(Map(
      "title" -> o.title.values,
      "description" -> o.description.values,
      "internal_name" -> o.internalName.values,
      "external_name" -> o.externalName.values,
      "mime_type" -> o.mimeType.values
    ))

    for {
      resultJson <- connection.query(update, Json.arr(o.folder.orNull, o.uuid.get.toString))
      _ <- Future(updateNotNull(resultJson))

      _ <- addTranslations(o.uuid.get, b)

      file <- retrieve(o.uuid.get)
    } yield file
  }

  override def size(): Future[Long] = {
    val select = s"SELECT COUNT(*) FROM $table WHERE tmp = FALSE"

    connection.selectSingleValue(select)
  }

  override def delete(o: File): Future[Unit] = {
    deleteById(o.uuid.get)
  }

  override def deleteById(id: UUID): Future[Unit] = {
    val delete = s"DELETE FROM $table WHERE uuid = ?"

    for {
      resultJson <- connection.query(delete, Json.arr(id.toString))
      _ <- Future(deleteNotNull(resultJson))
    } yield ()
  }

  def deleteByIdAndLangtag(id: UUID, langtag: String): Future[Unit] = {
    val delete = s"DELETE FROM file_lang WHERE uuid = ? AND langtag = ?"

    for {
      resultJson <- connection.query(delete, Json.arr(id.toString, langtag))
      _ <- Future(deleteNotNull(resultJson))
    } yield ()
  }
}
