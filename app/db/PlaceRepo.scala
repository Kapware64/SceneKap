package db

import javax.inject.{Inject, Singleton}

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import models.Place

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlaceRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import driver.api._

  private class PlaceTable(tag: Tag) extends Table[Place](tag, "places") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def pid = column[String]("pid")
    def rComments = column[String]("rComments")
    def tComments = column[String]("tComments")
    def website = column[String]("website")
    def photo_uri = column[String]("photo_uri")
    def extra = column[String]("extra")

    def * = (pid, rComments, tComments, website, photo_uri, extra) <> ((Place.apply _).tupled, Place.unapply)
  }

  private val places = TableQuery[PlaceTable]

  def create(pid:String, rC:String, tC:String, w:String, pUri: String, e:String): Future[Int] = {
    val q = places += Place(pid, rC, tC, w, pUri, e)
    db.run(q)
  }

  def deleteAll(): Future[Int] = db.run {
    places.delete
  }

  def get(pid: String): Future[Option[Place]] = db.run {
    places.filter(_.pid === pid).result.headOption
  }

  def list(): Future[Seq[Place]] = db.run {
    places.result
  }
}