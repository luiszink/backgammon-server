package actors

import org.apache.pekko.actor.{Actor, ActorRef, Props}
import play.api.Logging
import models.JoinQueueRequest
import play.api.libs.json.Json
import models.LobbyOptions
case class WSMessage(text: String)

class QueueingActor(var player: String, matchmaker: ActorRef, out: ActorRef) extends Actor with Logging {

  def receive: Receive = {
    case msg: String =>
      try {
        val js = Json.parse(msg)
        val playerId = (js \ "playerId").as[String]
        val options = (js \ "options").as[LobbyOptions]

        matchmaker ! JoinQueue(playerId, options, self)
      } catch {
        case e: Throwable =>
          logger.error(s"[QueueingActor] Failed to parse JSON: $msg", e)
      }

    case matchFound: MatchFound =>
      logger.info(s"[QueueingActor] player=$player received MatchFound $matchFound")
      out ! s"""{"type":"matchFound","lobbyId":"${matchFound.lobbyId}","players":[${matchFound.players.map(p => s""""$p"""").mkString(",")}]}"""

    case msg: WSMessage =>
      logger.info(s"[QueueingActor] player=$player received WSMessage: $msg")
  }
}

object QueueingActor {
  def props(playerId: String, matchmaker: ActorRef, out: ActorRef): Props =
    Props(new QueueingActor(playerId, matchmaker, out))
}
