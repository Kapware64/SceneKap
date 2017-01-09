package db

import java.text.SimpleDateFormat
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.typesafe.config.ConfigFactory
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
import scala.collection.JavaConversions._

@Singleton
class PlaceRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  val appConf = ConfigFactory.load

  val MONGO_CONNECT_TIMEOUT: Int = appConf.getInt("mongoConnections.connectTimeoutMS")
  val MONGO_SOCKET_TIMEOUT: Int = appConf.getInt("mongoConnections.socketTimeoutMS")
  val MONGO_SERVER_SELECTION_TIMEOUT: Int = appConf.getInt("mongoConnections.serverSelectionTimeoutMS")

  val MAX_COMMENTS: Int = appConf.getInt("comments.maxComments")
  val COMMENT_DELETE_CUTOFF: Int = appConf.getInt("comments.deleteCutoff")
  val DEFAULT_COMMENT: Document = Document("id" -> "", "votes" -> -10000, "date" -> "", "text" -> "")

  val strConf: String = "mongodb://localhost/?connectTimeoutMS=" + MONGO_CONNECT_TIMEOUT + "&socketTimeoutMS=" + MONGO_SOCKET_TIMEOUT + "&serverSelectionTimeoutMS=" + MONGO_SERVER_SELECTION_TIMEOUT
  val mongoClient: MongoClient = MongoClient(strConf)
  val database: MongoDatabase = mongoClient.getDatabase("SK")
  val collection: MongoCollection[Document] = database.getCollection("places")

  val upst: UpdateOptions = new UpdateOptions
  val after: FindOneAndUpdateOptions = new FindOneAndUpdateOptions
  val rPushOpt: PushOptions = new PushOptions
  val tPushOpt: PushOptions = new PushOptions

  upst.upsert(true)
  after.returnDocument(ReturnDocument.AFTER)
  rPushOpt.slice(-1 * MAX_COMMENTS)
  tPushOpt.sortDocument(descending("votes"))

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

  private def getArrFieldAsJson(doc: Document, keyword: String): String = {
    def jsonizeArr(arr: BsonArray): String = {
      val size = arr.size

      def helper(ind: Int, acc: String): String = {
        if(ind >= size) acc + "]"
        else helper(ind + 1, acc + "," + arr.get(ind).asDocument.toJson)
      }

      if(size == 0) "[]" else helper(0, "").replaceFirst(",", "[")
    }

    doc.get(keyword) match {
      case Some(res) => jsonizeArr(res.asArray)
      case _ => "[]"
    }
  }

  private def getArrField(doc: Document, keyword: String): Seq[BsonValue] = {
    doc.get(keyword) match {
      case Some(res) => res.asArray.getValues
      case _ => Seq[BsonValue]()
    }
  }

  private def voteComment(pid: String, cid: String, voteVal: Int): Future[Int] = {
    for {
      rRes <- collection.updateOne(and(equal("pid", pid), equal("rComments.id", cid)), combine(inc("rComments.$.votes", voteVal), set("tSorted", false))).toFuture
      delRes <- if(voteVal < 0) collection.updateOne(and(equal("pid", pid), equal("rComments.id", cid)), pull("rComments", lte("votes", -5))).toFuture else Future{rRes}
      finalRes <- if(rRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  private def getCurDateSeconds: String = (System.currentTimeMillis / 1000).toString

  private def getDay: String = {
    val format: SimpleDateFormat = new SimpleDateFormat("MMM d y, h:mm a")
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

  def get(pid: String): Future[Option[Place]] = {
    for {
      res <- collection.find(equal("pid", pid)).first.toFuture
      sRes <- {
        if(res.isEmpty) Future{res}
        else {
          val tSorted = res.head.getBoolean("tSorted")
          if(tSorted != null && tSorted) Future{res}
          else for {
            clear <- collection.updateOne(equal("pid", pid), set("tComments", List[Document]())).toFuture
            sorted <- collection.findOneAndUpdate(equal("pid", pid), combine(pushEach("tComments", tPushOpt, getArrField(res.head, "rComments"): _*), set("tSorted", true)), after).toFuture
          } yield sorted
        }
      }
      finalRes <- if(sRes.isEmpty) Future{None} else Future{convDecToPlace(sRes.head)}
    } yield finalRes
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
      rRes <- collection.updateOne(equal("pid", pid), combine(pushEach("rComments", rPushOpt, comment), set("tSorted", false)), upst).toFuture
      finalRes <- if(rRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def upvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, 1)
  def downvoteComment(pid: String, cid: String): Future[Int] = voteComment(pid, cid, -1)
}

case class CommentAddAction(needAdjust: Boolean, failed: Boolean)