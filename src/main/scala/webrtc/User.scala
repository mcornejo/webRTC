package webrtc

import akka.actor.{Actor, ActorRef}

/* It represent a user with their respective actions */
object User {
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(id: String, message: String)
  case class OutgoingMessage(message: String)
}


class User(signaling: ActorRef) extends Actor {
  import User._

  def receive = {
    case Connected(outgoing) =>
      context.become(connected(outgoing))
  }

  /*At connecting time, it send a Join method to the Signaling protocol */
  def connected(outgoing: ActorRef): Receive = {
    signaling ! Signaling.Join

    {
      case IncomingMessage(id, message) =>
        signaling ! Signaling.Message(id, message)

      case Signaling.Message(id, message) =>
        outgoing ! OutgoingMessage(message)

    }

  }

}
