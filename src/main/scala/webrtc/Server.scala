package webrtc

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.StdIn
import spray.json._
import DefaultJsonProtocol._
import webrtc.User.{IncomingMessage, OutgoingMessage} // if you don't supply your own Protocol (see below)



object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val chatRoom = system.actorOf(Props(new Signaling), "singaling")

    implicit val IncomingMessageFormat = jsonFormat2(IncomingMessage)
    implicit val OutgoingMessageMessageFormat = jsonFormat1(OutgoingMessage)


    def newUser(): Flow[Message, Message, NotUsed] = {
      // new connection - new user actor
      val userActor = system.actorOf(Props(new User(chatRoom)))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          // transform websocket message to domain message
          case TextMessage.Strict(text) => {
            val msg = text.parseJson.convertTo[IncomingMessage]
            msg
          }
        }.to(Sink.actorRef[User.IncomingMessage](userActor, PoisonPill))

      val outgoingMessages: Source[Message, NotUsed] =
        Source.actorRef[User.OutgoingMessage](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            userActor ! User.Connected(outActor)
            NotUsed
          }.map {
          // transform domain message to web socket message
          (outMsg: User.OutgoingMessage) => TextMessage(outMsg.message)
        }

      // then combine both to a flow
      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    val route =
      path("get") {
        get {
          handleWebSocketMessages(newUser())
        }
      }

    val binding = Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)


    // the rest of the sample code will go here
    println("Started server at 127.0.0.1:8080, press enter to kill server")
    StdIn.readLine()
    system.terminate()
  }
}