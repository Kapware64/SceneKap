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

  private def voteComment(pid: String, cid: String, voteVal: Int): Future[Int] = db.run {
    for {
      rowsAffected <- places.filter(p => p.pid === pid).map(p => p.rComments).update(voteVal.toString + " Comment")
      result <- rowsAffected match {
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(
          s"Expected 1 change, not $n for $pid"))
      }
    } yield result
  }

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

  def addComment(pid: String, text: String): Future[Int] = db.run {
    for {
      rowsAffected <- places.filter(p => p.pid === pid).map(p => p.rComments).update(text)
      result <- rowsAffected match {
        case 0 => places += Place(pid, "0 " + text, "", "", "", "", "", "")
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(
          s"Expected 0 or 1 change, not $n for $pid"))
      }
    } yield result
  }

  def upvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, 1)
  def downvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, -1)

  def getMult(pids: List[String]): Future[Seq[Place]] = db.run {
    places.filter(_.pid inSet(pids)).result
  }

  def get(pid: String): Future[Option[Place]] = db.run {
    places.filter(_.pid === pid).result.headOption
  }

  def deleteAll(): Future[Int] = db.run {
    places.delete
  }

  def list(): Future[Seq[Place]] = db.run {
    places.result
  }
}