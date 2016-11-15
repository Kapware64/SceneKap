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
    def summary = column[String]("summary")
    def last_summary_mod = column[String]("last_summary_mod")
    def extra = column[String]("extra")

    def * = (pid, rComments, tComments, website, photo_uri, summary, last_summary_mod, extra) <>
            ((Place.apply _).tupled, Place.unapply)
  }

  private val places = TableQuery[PlaceTable]

  def upsertSummary(pid:String, sum: String): Future[Int] = db.run {
    val md = (System.currentTimeMillis / 1000).toString

    for {
      rowsAffected <- places.filter(p => p.pid === pid).map(p => (p.summary, p.last_summary_mod)).update(sum, md)
      result <- rowsAffected match {
        case 0 => places += Place(pid, "", "", "", "", sum, md, "")
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(
          s"Expected 0 or 1 change, not $n for $pid"))
      }
    } yield result
  }

  def upsertWebsite(pid:String, url: String): Future[Int] = db.run {
    for {
      rowsAffected <- places.filter(p => p.pid === pid).map(p => (p.website, p.last_summary_mod)).update(url, "0")
      result <- rowsAffected match {
        case 0 => places += Place(pid, "", "", url, "", "", "", "")
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(
          s"Expected 0 or 1 change, not $n for $pid"))
      }
    } yield result
  }

  def upsertPhoto(pid:String, url: String): Future[Int] = db.run {
    for {
      rowsAffected <- places.filter(p => p.pid === pid).map(p => p.photo_uri).update(url)
      result <- rowsAffected match {
        case 0 => places += Place(pid, "", "", "", url, "", "", "")
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(
          s"Expected 0 or 1 change, not $n for $pid"))
      }
    } yield result
  }

//  def create(pid:String, rC:String, tC:String, w:String, pUri: String, sum: String, lSumMod: String, e:String): Future[Int] = {
//    val q = places += Place(pid, rC, tC, w, pUri, sum, lSumMod, e)
//    db.run(q)
//  }
//
//  def updateSummary(pid: String, newSummary: String): Future[Int] = db.run {
//    val q = for { p <- places if p.pid === pid} yield p.summary
//    q.update(newSummary)
//  }
//
//  def updatePhotoUri(pid: String, uri: String): Future[Int] = db.run {
//    val q = for { p <- places if p.pid === pid} yield p.photo_uri
//    q.update(uri)
//  }
//
//  def updateWebsite(pid: String, url: String): Future[Int] = db.run {
//    val q = for { p <- places if p.pid === pid} yield p.website
//    q.update(url)
//  }

  def getMult(pids: List[String]): Future[Seq[Place]] = db.run {
    places.filter(_.pid inSet(pids)).result
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