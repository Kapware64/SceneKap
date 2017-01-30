package db

import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.typesafe.config.ConfigFactory
import models.{Place, User}
import org.mongodb.scala.bson.{BsonObjectId, _}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.{InsertOneOptions, PushOptions, ReturnDocument, UpdateOptions}
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.mongodb.scala.model.Indexes._

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._
import async.calcUrlSumAndScore
import async.detCommentProfanity
import com.mongodb.client.result.UpdateResult

@Singleton
class MongoRepo @Inject()(implicit ec: ExecutionContext) {
  val appConf = ConfigFactory.load

  val MONGO_CONNECT_TIMEOUT: Int = appConf.getInt("mongoConnections.connectTimeoutMS")
  val MONGO_SOCKET_TIMEOUT: Int = appConf.getInt("mongoConnections.socketTimeoutMS")
  val MONGO_SERVER_SELECTION_TIMEOUT: Int = appConf.getInt("mongoConnections.serverSelectionTimeoutMS")

  val MAX_COMMENTS: Int = appConf.getInt("comments.maxComments")
  val COMMENT_DELETE_CUTOFF: Int = appConf.getInt("comments.deleteCutoff")
  val DEFAULT_COMMENT: Document = Document("id" -> "", "votes" -> -10000, "date" -> "", "text" -> "")

  val MAX_PHOTOS: Int = appConf.getInt("photo.maxPhotos")
  val PHOTO_DELETE_CUTOFF: Int = appConf.getInt("photo.deleteCutoff")
  val MIN_MAIN_PHOTO_VOTES: Int = appConf.getInt("photo.minMainVotes")

  val MIN_SUM_SCORE: Int = appConf.getInt("website.minSumScore")

  val ADD_COMMENT_SCORE: Int = appConf.getInt("scoring.addComment")
  val ADD_PHOTO_SCORE: Int = appConf.getInt("scoring.addPhoto")
  val VOTE_SCORE_MULT: Int = appConf.getInt("scoring.voteMult")

  val strConf: String = "mongodb://localhost/?connectTimeoutMS=" + MONGO_CONNECT_TIMEOUT + "&socketTimeoutMS=" + MONGO_SOCKET_TIMEOUT + "&serverSelectionTimeoutMS=" + MONGO_SERVER_SELECTION_TIMEOUT
  val mongoClient: MongoClient = MongoClient(strConf)
  val database: MongoDatabase = mongoClient.getDatabase("SK")
  val usersCollection: MongoCollection[Document] = database.getCollection("users")
  val placesCollection: MongoCollection[Document] = database.getCollection("places")

  val upst: UpdateOptions = new UpdateOptions
  val after: FindOneAndUpdateOptions = new FindOneAndUpdateOptions
  val elemFilter: FindOneAndUpdateOptions = new FindOneAndUpdateOptions
  val rPushOpt: PushOptions = new PushOptions
  val pPushOpt: PushOptions = new PushOptions
  val tPushOpt: PushOptions = new PushOptions
  val insOpt: InsertOneOptions = new InsertOneOptions

  upst.upsert(true)
  after.returnDocument(ReturnDocument.AFTER)
  rPushOpt.slice(-1 * MAX_COMMENTS)
  pPushOpt.slice(-1 * MAX_PHOTOS)
  tPushOpt.sortDocument(descending("votes"))

  placesCollection.createIndex(ascending("pid"))
  placesCollection.createIndex(compoundIndex(ascending("pid"), ascending("rComments.id")))
  placesCollection.createIndex(compoundIndex(ascending("pid"), ascending("rPhotoUris.id")))
  usersCollection.createIndex(ascending("username"))
  usersCollection.createIndex(ascending("score"))
  usersCollection.createIndex(compoundIndex(ascending("username"), ascending("email")))
  usersCollection.createIndex(compoundIndex(ascending("username"), ascending("password")))

  private def convDocToUser(doc: Document): Option[User] = {
    val usernameRaw = doc.getString("username")
    val username = if(usernameRaw == null) "" else usernameRaw
    val passwordRaw = doc.getString("password")
    val password = if(passwordRaw == null) "" else passwordRaw
    val emailRaw = doc.getString("email")
    val email = if(emailRaw == null) "" else emailRaw
    val deviceIDRaw = doc.getString("deviceID")
    val deviceID = if(deviceIDRaw == null) "" else deviceIDRaw
    val scoreRaw = doc.getInteger("score")
    val score = if(scoreRaw == null) 0 else scoreRaw.intValue

    Some(User(username, password, email, deviceID, score))
  }

  private def convDocToPlace(doc: Document): Option[Place] = {
    val rComments = getArrFieldAsJson(doc, "rComments")
    val tComments = getArrFieldAsJson(doc, "tComments")
    val pid = doc.getString("pid")
    val websiteRaw = doc.getString("website")
    val website = if(websiteRaw == null) "" else websiteRaw
    val rPhotoUris = getArrFieldAsJson(doc, "rPhotoUris")
    val tPhotoUris = getArrFieldAsJson(doc, "tPhotoUris")
    val summaryRaw = doc.getString("summary")
    val summary = if(summaryRaw == null) "" else summaryRaw
    val (topPhotoVotesRaw, topPhotoUriRaw): (BsonNumber, BsonString) = doc.get("tPhotoUris") match {
      case Some(res) => {
        val arr = res.asArray
        if(arr.isEmpty) (null, null)
        else {
          val doc = arr.get(0).asDocument
          (doc.getNumber("votes"), doc.getString("link"))
        }
      }
      case None => (null, null)
    }
    val topPhotoVotes = if(topPhotoVotesRaw == null) 0 else topPhotoVotesRaw.intValue
    val topPhotoUri = if(topPhotoUriRaw == null || topPhotoVotes < MIN_MAIN_PHOTO_VOTES) "" else topPhotoUriRaw.getValue
    val sumModRaw = doc.getString("last_summary_mod")
    val last_summary_mod = if(sumModRaw == null) "" else sumModRaw
    val extra = ""
    Some(Place(pid, rComments, tComments, website, rPhotoUris, tPhotoUris, topPhotoUri, summary, last_summary_mod, extra))
  }

  private def convDocToPlaces(docs: Seq[Document]): List[Place] = {
    def helper(docs: Seq[Document], acc: List[Place]): List[Place] = {
      if(docs.isEmpty) acc
      else {
        val plOpt = convDocToPlace(docs.head)
        plOpt match {
          case Some(pl) => helper(docs.tail, pl :: acc)
          case None => helper(docs.tail, acc)
        }
      }
    }

    helper(docs.reverse, List[Place]())
  }

  private def convDocToUsers(docs: Seq[Document]): List[User] = {
    def helper(docs: Seq[Document], acc: List[User]): List[User] = {
      if(docs.isEmpty) acc
      else {
        val usOpt = convDocToUser(docs.head)
        usOpt match {
          case Some(user) => helper(docs.tail, user :: acc)
          case None => helper(docs.tail, acc)
        }
      }
    }

    helper(docs.reverse, List[User]())
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

  private def getCurDateSeconds: String = (System.currentTimeMillis / 1000).toString

  private def getDay: String = {
    val format: SimpleDateFormat = new SimpleDateFormat("MMM d y, h:mm a")
    format.format(Calendar.getInstance.getTime)
  }

  private def extractScoreFromUser(user: Document): Int = {
    val scoreRaw = user.getInteger("score")
    if(scoreRaw == null) 0 else scoreRaw
  }

  private def extractUserFromDoc(doc: Document): String = {
    doc.get("rComments") match {
      case Some(res) => {
        val arr = res.asArray
        if(arr.isEmpty) ""
        else {
          val doc = arr.get(0).asDocument
          val usernameRaw = doc.getString("username")
          if(usernameRaw == null) "" else usernameRaw.getValue
        }
      }
      case None => ""
    }
  }

  def upsertSummary(pid: String, sum: String): Future[Int] = {
    val md = if(sum.isEmpty) "0" else getCurDateSeconds

    for {
      updateRes <- placesCollection.updateOne(equal("pid", pid), combine(set("summary", sum), set("last_summary_mod", md)), upst).toFuture
      finalRes <- if(updateRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def upsertWebsite(placeKeywords: String, pid: String, url: String): Future[Int] = {
    for {
      baseUrl <- {
        try {
          val urlObj: URL = new URL(url)
          Future{urlObj.getProtocol + "://" + urlObj.getHost}
        } catch { case e: java.net.MalformedURLException => Future{""}}
      }
      (_, score) <- if(baseUrl.isEmpty) Future{("", 0)} else calcUrlSumAndScore(placeKeywords, baseUrl, false, false)
      upsertRes <- {
        if(score < MIN_SUM_SCORE) Future{Seq[UpdateResult](UpdateResult.unacknowledged())}
        else placesCollection.updateOne(equal("pid", pid), combine(set("website", url), set("last_summary_mod", "0")), upst).toFuture
      }
      finalRes <- if(score < MIN_SUM_SCORE) Future{-1} else if(upsertRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def get(pid: String): Future[Option[Place]] = {
    for {
      res <- placesCollection.find(equal("pid", pid)).first.toFuture
      sRes <- {
        if(res.isEmpty) Future{res}
        else {
          val tCommentsSortedRaw = res.head.getBoolean("tCommentsSorted")
          val tPhotosSortedRaw = res.head.getBoolean("tPhotosSorted")
          val tCommentsSorted = tCommentsSortedRaw != null && tCommentsSortedRaw
          val tPhotosSorted = tPhotosSortedRaw != null && tPhotosSortedRaw

          if(tCommentsSorted && tPhotosSorted) Future{res}
          else for {
            clear1 <-
              if(!tCommentsSorted) placesCollection.updateOne(equal("pid", pid), set("tComments", List[Document]())).toFuture
              else Future{UpdateResult.acknowledged(0.toLong, 0.toLong, BsonString(""))}
            sorted1 <-
              if(!tCommentsSorted) placesCollection.findOneAndUpdate(equal("pid", pid), combine(pushEach("tComments", tPushOpt, getArrField(res.head, "rComments"): _*), set("tCommentsSorted", true)), after).toFuture
              else Future{res}
            clear2 <- {
              if(!tPhotosSorted) placesCollection.updateOne(equal("pid", pid), set("tPhotoUris", List[Document]())).toFuture
              else Future{UpdateResult.acknowledged(0.toLong, 0.toLong, BsonString(""))}
            }
            sorted2 <- {
              if(!tPhotosSorted) placesCollection.findOneAndUpdate(equal("pid", pid), combine(pushEach("tPhotoUris", tPushOpt, getArrField(res.head, "rPhotoUris"): _*), set("tPhotosSorted", true)), after).toFuture
              else Future{sorted1}
            }
          } yield sorted2
        }
      }
      finalRes <- if(sRes.isEmpty) Future{None} else Future{convDocToPlace(sRes.head)}
    } yield finalRes
  }

  def getMult(pids: List[String]): Future[List[Place]] = {
    for {
      res <- placesCollection.find(in("pid", pids: _*)).toFuture
      place <- Future{convDocToPlaces(res)}
    } yield place
  }

  def list(): Future[List[Place]] = {
    for {
      res <- placesCollection.find().toFuture
      place <- Future{convDocToPlaces(res)}
    } yield place
  }

  def addPhoto(pid:String, url: String, username: String): Future[Int] = {
    val photo = Document("id" -> BsonObjectId.apply.getValue.toString, "votes" -> 0, "date" -> getDay, "link" -> url)

    for {
      aRes <- placesCollection.updateOne(equal("pid", pid), combine(pushEach("rPhotoUris", pPushOpt, photo), set("tPhotosSorted", false)), upst).toFuture
      scoreRes <- changeScore(username, ADD_PHOTO_SCORE)
      finalRes <- if(aRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def addComment(pid: String, text: String, username: String): Future[Int] = {
    val comment = Document("id" -> BsonObjectId.apply.getValue.toString, "votes" -> 0, "date" -> getDay, "text" -> text, "username" -> username)
    val isProfane = detCommentProfanity(text)

    for {
      rRes <-
        if(isProfane) Future{Seq[UpdateResult](UpdateResult.unacknowledged())}
        else placesCollection.updateOne(equal("pid", pid), combine(pushEach("rComments", rPushOpt, comment), set("tCommentsSorted", false)), upst).toFuture
      scoreRes <- changeScore(username, ADD_COMMENT_SCORE)
      finalRes <- if(isProfane) Future{-1} else if(rRes.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def voteComment(pid: String, cid: String, voteVal: Int, username: String): Future[Int] = {
    elemFilter.projection(elemMatch("rComments", equal("id", cid)))

    for {
      rRes <- placesCollection.findOneAndUpdate(and(equal("pid", pid), equal("rComments.id", cid)), combine(inc("rComments.$.votes", voteVal), set("tCommentsSorted", false)), elemFilter).toFuture
      delRes <- if(voteVal < 0) placesCollection.updateOne(and(equal("pid", pid), equal("rComments.id", cid)), pull("rComments", lte("votes", COMMENT_DELETE_CUTOFF))).toFuture else Future{rRes}
      scoreRes1 <- changeScore(username, VOTE_SCORE_MULT)
      scoreRes2 <- {
        val user = extractUserFromDoc(rRes.head)
        val cScoreVal = if(user == username) -1 else voteVal
        if(voteVal > 0 && user.nonEmpty) changeScore(user, cScoreVal * VOTE_SCORE_MULT) else Future{0}
      }
      finalRes <- if(rRes.isEmpty) Future{0} else Future{1}
    } yield finalRes
  }

  def votePhoto(pid: String, cid: String, voteVal: Int, username: String): Future[Int] = {
    elemFilter.projection(elemMatch("rPhotoUris", equal("id", cid)))

    for {
      rRes <- placesCollection.findOneAndUpdate(and(equal("pid", pid), equal("rPhotoUris.id", cid)), combine(inc("rPhotoUris.$.votes", voteVal), set("tPhotosSorted", false)), elemFilter).toFuture
      delRes <- if(voteVal < 0) placesCollection.updateOne(and(equal("pid", pid), equal("rPhotoUris.id", cid)), pull("rPhotoUris", lte("votes", PHOTO_DELETE_CUTOFF))).toFuture else Future{rRes}
      scoreRes1 <- changeScore(username, VOTE_SCORE_MULT)
      scoreRes2 <- {
        val user = extractUserFromDoc(rRes.head)
        val cScoreVal = if(user == username) -1 else voteVal
        if(voteVal > 0 && user.nonEmpty) changeScore(user, cScoreVal * VOTE_SCORE_MULT) else Future{0}
      }
      finalRes <- if(rRes.isEmpty) Future{0} else Future{1}
    } yield finalRes
  }

  def addUser(username: String, password: String, email: String, deviceID: String): Future[Int] = {
    for {
      res <-
        usersCollection.updateOne(or(equal("username", username), equal("email", email)),
        combine(setOnInsert("username", username), setOnInsert("password", password), setOnInsert("email", email), setOnInsert("deviceID", deviceID), setOnInsert("score", 0)), upst).toFuture
      finalRes <- if(!res.head.wasAcknowledged) Future{0} else if(res.head.getMatchedCount > 0) Future{-1} else Future{1}
    } yield finalRes
  }

  def firstLogin(username: String, password: String, deviceID: String): Future[Int] = {
    for {
      res <- usersCollection.find(and(equal("username", username), equal("password", password), equal("deviceID", deviceID))).first.toFuture
      finalRes <- if(res.isEmpty) Future{-1} else Future{extractScoreFromUser(res.head)}
    } yield finalRes
  }

  def regLogin(username: String, password: String, deviceID: String): Future[Int] = {
    for {
      res <- usersCollection.findOneAndUpdate(and(equal("username", username), equal("password", password)), set("deviceID", deviceID)).toFuture
      finalRes <- if(res.isEmpty) Future{-1} else Future{extractScoreFromUser(res.head)}
    } yield finalRes
  }

  def changeScore(username: String, voteVal: Int): Future[Int] = {
    for {
      res <- usersCollection.updateOne(equal("username", username), inc("score", voteVal)).toFuture
      finalRes <- if(res.head.wasAcknowledged) Future{1} else Future{0}
    } yield finalRes
  }

  def getAllUsers: Future[List[User]] = {
    for {
      res <- usersCollection.find().toFuture
      place <- Future{convDocToUsers(res)}
    } yield place
  }
}