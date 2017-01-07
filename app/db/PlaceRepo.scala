package db

import java.text.SimpleDateFormat
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import com.mongodb.client.model.FindOneAndUpdateOptions
import play.api.db.slick.DatabaseConfigProvider
import models.Place
import org.mongodb.scala.bson.{BsonObjectId, _}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.{PushOptions, ReturnDocument, UpdateOptions}
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.mongodb.scala.model.Indexes._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlaceRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val strConf: String = "mongodb://localhost/?connectTimeoutMS=7500&socketTimeoutMS=7500&serverSelectionTimeoutMS=10000"
  val mongoClient: MongoClient = MongoClient(strConf)
  val database: MongoDatabase = mongoClient.getDatabase("SK")
  val collection: MongoCollection[Document] = database.getCollection("places")

  val MAX_RECENT_COMMENTS: Int = 5
  val MAX_TOP_COMMENTS: Int = 3
  val DEFAULT_COMMENT: Document = Document("id" -> "", "votes" -> -10000, "date" -> "", "text" -> "")

  val upst: UpdateOptions = new UpdateOptions
  val after: FindOneAndUpdateOptions = new FindOneAndUpdateOptions
  val rPushOpt: PushOptions = new PushOptions
  val tPushOpt: PushOptions = new PushOptions

  upst.upsert(true)
  after.returnDocument(ReturnDocument.AFTER)
  after.upsert(true)
  rPushOpt.slice(-1 * MAX_RECENT_COMMENTS)
  tPushOpt.sortDocument(descending("votes"))
  tPushOpt.slice(MAX_TOP_COMMENTS)

  collection.createIndex(ascending("pid"))
  collection.createIndex(compoundIndex(ascending("pid"), ascending("rComments.id")))
  collection.createIndex(compoundIndex(ascending("pid"), ascending("tComments.id")))

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

  private def getArrFieldAsList(doc: Document, field: String): List[Document] = {
    def proc(arr: BsonArray): List[Document] = {
      val size = arr.size

      def helper(ind: Int, acc: List[Document]): List[Document] = {
        if(ind >= size) acc
        else helper(ind + 1, arr.get(ind).asDocument :: acc)
      }

      helper(0, List[Document]())
    }

    doc.get(field) match {
      case Some(res) => proc(res.asArray)
      case _ => List[Document]()
    }
  }

  private def voteComment(pid: String, cid: String, voteVal: Int): Future[Int] = {
    if(voteVal > 0) after.projection(elemMatch("rComments", equal("id", cid))) else after.projection(null)

    for {
      toPush <- collection.findOneAndUpdate(and(equal("pid", pid), equal("rComments.id", cid)), inc("rComments.$.votes", voteVal), after).toFuture
      tRes <- collection.updateOne(and(equal("pid", pid), equal("tComments.id", cid)), inc("tComments.$.votes", voteVal)).toFuture
      finalRes <-
      if((tRes.head.getMatchedCount.toInt > 0 && voteVal > 0) || (tRes.head.getMatchedCount.toInt == 0 && voteVal < 0)) Future{1}
      else for {
        addRes <- collection.updateOne(equal("pid", pid), addEachToSet("tComments", getArrFieldAsList(toPush.head, "rComments"): _*)).toFuture
        sortRes <- collection.updateOne(equal("pid", pid), pushEach("tComments", tPushOpt, List[Document](): _*)).toFuture
        composite <- if(addRes.head.wasAcknowledged && sortRes.head.wasAcknowledged) Future{1} else Future{0}
      } yield composite
    } yield finalRes
  }

  private def getCurDateSeconds: String = (System.currentTimeMillis / 1000).toString
  private def getDay: String = {
    val format = new SimpleDateFormat("M/d/y")
    format.format(Calendar.getInstance.getTime)
  }

  def upsertSummary(pid: String, sum: String): Future[Int] = {
    val md = if(sum.isEmpty) "0" else getCurDateSeconds

    for {
      updateRes <- collection.updateOne(equal("pid", pid), combine(set("summary", sum), set("last_summary_mod", md)), upst).toFuture
      finalRes <- if(updateRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def upsertWebsite(pid:String, url: String): Future[Int] = {
    for {
      updateRes <- collection.updateOne(equal("pid", pid), combine(set("website", url), set("last_summary_mod", "0")), upst).toFuture
      finalRes <- if(updateRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def upsertPhoto(pid:String, url: String): Future[Int] = {
    for {
      updateRes <- collection.updateOne(equal("pid", pid), set("photo_uri", url), upst).toFuture
      finalRes <- if(updateRes.head.wasAcknowledged) Future{1} else Future{0}
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
      case _ => "[]"
    }
  }

  def get(pid: String): Future[Option[Place]] = {
    for {
      res <- collection.find(equal("pid", pid)).first.toFuture
      place <- if(res.isEmpty) Future{None} else Future{convDecToPlace(res.head)}
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

  def addComment(pid: String, text: String): Future[Int] = {
    val comment = Document("id" -> BsonObjectId.apply.getValue.toString, "votes" -> 0, "date" -> getDay, "text" -> text)

    for {
      pushRes <- collection.updateOne(equal("pid", pid), pushEach("rComments", rPushOpt, comment), upst).toFuture
      finalRes <- if(pushRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def upvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, 1)
  def downvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, -1)
}

case class CommentAddAction(needAdjust: Boolean, failed: Boolean)