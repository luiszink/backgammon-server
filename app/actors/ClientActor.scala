package actors

import org.apache.pekko.actor._
import models._
import play.api.libs.json._
import play.libs.Json
import de.htwg.se.backgammon.model.Player
import controllers.StopActor

class ClientActor(lobby: ActorRef, user: String, out: ActorRef) extends Actor {
  private var player: Option[Player] = None

  override def preStart(): Unit = lobby ! Join(user, self)
  override def postStop(): Unit = lobby ! Leave(user)

  def receive: Receive = {
    case msg: ChatMessage =>
      lobby ! BroadcastMessage(user, msg.text)
    case msg: MoveMessage => 
      println("Received move")
      lobby ! Move(user, msg.from, msg.to)
    case player: Player =>
      this.player = Some(player)
      out ! PlayerAssigned(player)
    case msg: ServerInfo =>
      println(s"[$user] Sending ServerInfo: ${msg.text}")
      out ! msg
    case msg: OutgoingMessage =>
      println(s"[$user] Sending OutgoingMessage (${msg.messageType})")
      out ! msg
    case StopActor => 
      context.stop(self) 
  }
}

object ClientActor {
  def props(lobby: ActorRef, user: String, out: ActorRef): Props =
    Props(new ClientActor(lobby, user, out))
}

