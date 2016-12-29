package db

import javax.inject.{Inject, Singleton}

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import models.Place
import org.mongodb.scala.bson._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.{Completed, MongoClient, MongoCollection, MongoDatabase}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlaceRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val mongoClient: MongoClient = MongoClient("mongodb://localhost/?connectTimeoutMS=5000&socketTimeoutMS=5000")
  val database: MongoDatabase = mongoClient.getDatabase("SK")
  val collection: MongoCollection[Document] = database.getCollection("places")

  private def voteComment(pid: String, cid: String, voteVal: Int): Future[Int] = Future {
    -1
  }

  private def convDecToPlace(doc: Document): Option[Place] = {
    val rComments = getArrFieldAsJson(doc, "rComments")
    val tComments = getArrFieldAsJson(doc, "tComments")
    val pid = doc.getString("pid")
    val websiteRaw = doc.getString("website")
    val website = if(websiteRaw == null) "" else websiteRaw
    val photoRaw = doc.getString("photo_uri")
    val photo_uri = if(photoRaw == null) "" else photoRaw
    val summaryRaw = doc.getString("summary")
    val summary = if(summaryRaw == null) "" else summaryRaw
    val sumModRaw = doc.getString("last_summary_mod")
    val last_summary_mod = if(sumModRaw == null) "" else sumModRaw
    val extra = ""
    Some(Place(pid, rComments, tComments, website, photo_uri, summary, last_summary_mod, extra))
  }

  private def convDecToPlaces(docs: Seq[Document]): List[Place] = {
    def helper(docs: Seq[Document], acc: List[Place]): List[Place] = {
      if(docs.isEmpty) acc
      else {
        val plOpt = convDecToPlace(docs.head)
        plOpt match {
          case Some(pl) => helper(docs.tail, pl :: acc)
          case None => helper(docs.tail, acc)
        }
      }
    }

    helper(docs.reverse, List[Place]())
  }

  def upsertSummary(pid: String, sum: String): Future[Int] = {
    val md = (System.currentTimeMillis / 1000).toString

    for {
      updateRes <- collection.updateOne(equal("pid", pid), combine(set("summary", sum), set("last_summary_mod", md))).toFuture
      addRes <- {
        if(updateRes.head.getMatchedCount == 0) {
          val doc: Document = Document("pid" -> pid, "summary" -> sum, "last_summary_mod" -> md)
          collection.insertOne(doc).toFuture
        }
        else Future{Seq[Completed](Completed())}
      }
      finalRes <- Future{if(addRes.isEmpty) 0 else 1}
    } yield finalRes
  }

  def upsertWebsite(pid:String, url: String): Future[Int] = {
    for {
      updateRes <- collection.updateOne(equal("pid", pid), combine(set("website", url), set("last_summary_mod", "0"))).toFuture
      addRes <- {
        if(updateRes.head.getMatchedCount == 0) {
          val doc: Document = Document("pid" -> pid, "website" -> url, "last_summary_mod" -> "0")
          collection.insertOne(doc).toFuture
        }
        else Future{Seq[Completed](Completed())}
      }
      finalRes <- Future{if(addRes.isEmpty) 0 else 1}
    } yield finalRes
  }

  def upsertPhoto(pid:String, url: String): Future[Int] = {
    for {
      updateRes <- collection.updateOne(equal("pid", pid), set("photo_uri", url)).toFuture
      addRes <- {
        if(updateRes.head.getMatchedCount == 0) {
          val doc: Document = Document("pid" -> pid, "photo_uri" -> url)
          collection.insertOne(doc).toFuture
        }
        else Future{Seq[Completed](Completed())}
      }
      finalRes <- Future{if(addRes.isEmpty) 0 else 1}
    } yield finalRes
  }

  def getArrFieldAsJson(doc: Document, keyword: String): String = {
    def jsonizeArr(arr: BsonArray): String = {
      val size = arr.size

      def helper(ind: Int, acc: String): String = {
        if(ind >= size) acc + "]"
        else helper(ind + 1, acc + "," + arr.get(ind).asDocument.toJson)
      }

      helper(0, "").replaceFirst(",", "[")
    }

    doc.get(keyword) match {
      case Some(res) => jsonizeArr(res.asArray)
      case _ => ""
    }
  }

  def get(pid: String): Future[Option[Place]] = {
    for {
      res <- collection.find(equal("pid", pid)).first.toFuture
      place <- Future{convDecToPlace(res.head)}
    } yield place
  }

  def getMult(pids: List[String]): Future[List[Place]] = {
    for {
      res <- collection.find(in("pid", pids: _*)).toFuture
      place <- Future{convDecToPlaces(res)}
    } yield place
  }

  def list(): Future[List[Place]] = {
    for {
      res <- collection.find().toFuture
      place <- Future{convDecToPlaces(res)}
    } yield place
  }

  def addComment(pid: String, text: String): Future[Int] = Future {
    -1
  }

  def upvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, 1)
  def downvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, -1)
}

//TODO: GetMult and Nearby should work so that photo entries in db replace google photo entries
//TODO: Add measures for failing gracefully