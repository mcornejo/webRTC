package webrtc

import akka.actor._
import scala.collection.mutable
import spray.json._
import DefaultJsonProtocol._

object Signaling {
  /* Classes and Messages to exchange between actors */
  case object Join
  case object AllUsers
  case class Message(id: String, message: String)
  case class User(id: String)
  case class ListUsers(users: List[String])
  case class OutMessage(message: String)
}

class Signaling extends Actor {
  import Signaling._
  /* the users Map represents the storage of users. For this example it is used a Map,
  in production this must be changed for a Distributed Hash Table */
  var users: mutable.Map[String, ActorRef] = mutable.Map[String, ActorRef]()

  /* Some marchellers to make the json parsing easier */
  implicit val OutMessageFormat = jsonFormat1(OutMessage)
  implicit val ListUsersFormat = jsonFormat1(ListUsers)
  implicit val UserFormat = jsonFormat1(User)

  def receive = {
    /* Join Message: is the first message sent by each connection*/
    case Join =>
      val newId = sender().hashCode().toHexString
      users = users + (newId -> sender())
      self ! Signaling.Message(newId, User(newId).toJson.toString)
      self ! AllUsers
      context.watch(sender())

      /* If the connection is terminated, the user is removed from the storage */
    case Terminated(user) =>
      users = users - user.hashCode().toHexString
      users.foreach(_._2 ! Message(sender().hashCode().toHexString, ListUsers(users.keys.toList).toJson.toString))

      /* This method handles a message send/received by each connection */
    case message: Message =>
      val maybeUser = users.get(message.id)
      maybeUser match {
        case Some(act) => act ! Message(sender().hashCode().toHexString, message.message) //act ! message
        case None => users(sender().hashCode().toHexString) ! Message(sender().hashCode().toHexString, "{\"message\": \"User not found\"}")
      }

    /* This message is to get the full list of users. An auxiliary method for the example */
    case AllUsers =>
      users.foreach(_._2 ! Message("", ListUsers(users.keys.toList).toJson.toString))
  }
}
