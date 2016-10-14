package dal

import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile

import models.User

import scala.concurrent.{ Future, ExecutionContext }

/**
 * A repository for people.
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class UserRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import driver.api._

  /**
   * Here we define the table. It will have a name of users
   */
  private class UserTable(tag: Tag) extends Table[User](tag, "users") {

    /** The ID column, which is the primary key, and auto incremented */
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    /** The name column */
    def username = column[String]("username")

    /** The age column */
    def settings = column[String]("settings")

    def lastlogged = column[String]("lastlogged")

    def password = column[String]("password")

    /**
     * This is the tables default "projection".
     *
     * It defines how the columns are converted to and from the User object.
     *
     * In this case, we are simply passing the id, name and page parameters to the User case classes
     * apply and unapply methods.
     */
    def * = (id, username, settings, lastlogged, password) <> ((User.apply _).tupled, User.unapply)
  }

  /**
   * The starting point for all queries on the user table.
   */
  private val users = TableQuery[UserTable]

  /**
   * Create a person with the given name and age.
   *
   * This is an asynchronous operation, it will return a future of the created person, which can be used to obtain the
   * id for that person.
   */
  def create(username: String, settings: String, lastlogged: String, password: String): Future[Int] = {
    val q = users += User(0, username, settings, lastlogged, password)
    db.run(q)
  }

  def createmult(toAdd: List[User]) = {
    val q = users ++= toAdd
    db.run(q)
  }

  def deleteAll(): Future[Int] = db.run {
    users.delete
  }

  /**
   * List all the people in the database.
   */
  def list(): Future[Seq[User]] = db.run {
    users.result
  }
}
