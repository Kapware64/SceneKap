package db

import java.text.SimpleDateFormat
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.result.UpdateResult
import play.api.db.slick.DatabaseConfigProvider
import models.Place
import org.mongodb.scala.bson._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.{ReturnDocument, UpdateOptions}
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.UpdateResult
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlaceRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val strConf: String = "mongodb://localhost/?connectTimeoutMS=7500&socketTimeoutMS=7500&serverSelectionTimeoutMS=10000"
  val mongoClient: MongoClient = MongoClient(strConf)
  val database: MongoDatabase = mongoClient.getDatabase("SK")
  val collection: MongoCollection[Document] = database.getCollection("places")

  val MAX_RECENT_COMMENTS: Int = 10

  val upst: UpdateOptions = new UpdateOptions
  val upstF: FindOneAndUpdateOptions = new FindOneAndUpdateOptions

  upst.upsert(true)
  upstF.returnDocument(ReturnDocument.AFTER)
  upstF.upsert(true)

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

  private def getCurDateSeconds: String = (System.currentTimeMillis / 1000).toString
  private def getDay: String = {
    val format = new SimpleDateFormat("M/d/y")
    format.format(Calendar.getInstance().getTime())
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
    val comment = Document("id" -> pid, "votes" -> 0, "date" -> getDay, "text" -> text)

    for {
      pushRes <- collection.findOneAndUpdate(equal("pid", pid), push("rComments", comment), upstF).toFuture
      numComments <- if(pushRes == null) Future{0} else {
        pushRes.head.get("rComments") match {
          case Some(rComments) => if(rComments.isArray) Future{rComments.asArray.size} else Future{0}
          case _ => Future{0}
        }
      }
      action <-
        if(numComments <= 0) Future{CommentAddAction(false, true)}
        else if(numComments <= MAX_RECENT_COMMENTS) Future{CommentAddAction(false, false)}
        else Future{CommentAddAction(true, false)}
      addCommentRes <-
        if(action.failed) Future{Seq[UpdateResult](UpdateResult.unacknowledged)}
        else if(action.needAdjust) collection.updateOne(equal("pid", pid), popFirst("rComments")).toFuture
        else Future{Seq[UpdateResult](UpdateResult.acknowledged(0, 0.toLong, BsonNumber(0)))}
      finalRes <- if(addCommentRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def upvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, 1)
  def downvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, -1)
}

case class CommentAddAction(needAdjust: Boolean, failed: Boolean)