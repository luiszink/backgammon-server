package actors

import org.apache.pekko.actor.{Actor, ActorRef, Props}
import org.apache.pekko.pattern.{ask, pipe}
import org.apache.pekko.util.Timeout
import play.api.Logging

import java.util.UUID
import scala.concurrent.ExecutionContext
import models.LobbyOptions

case class JoinQueue(playerId: String, options: LobbyOptions, socket: ActorRef)
case class MatchFound(lobbyId: String, players: Seq[String])
case object Queued

case class LobbyCreated(lobbyId: String, players: Seq[(String, ActorRef)])

class MatchmakerActor(lobbyManager: ActorRef)
                     (implicit val timeout: Timeout, ec: ExecutionContext)
  extends Actor with Logging {

  private var sockets = Map.empty[String, ActorRef]
  private var queues = Map.empty[LobbyOptions, Seq[String]]

  case class LobbyCreated(lobbyId: String, players: Seq[String])

  def receive: Receive = {
    case JoinQueue(playerId, options, socket) =>
      logger.info(s"Options object for $playerId: ${options.hashCode()} -> $options")
      if (!sockets.contains(playerId)) {
        sockets += playerId -> socket
        logger.info(s"[SocketAssigned] $playerId -> $socket")
      }
      
      val updatedQueue = queues.getOrElse(options, Seq.empty)
      if (!updatedQueue.contains(playerId)) queues += options -> (updatedQueue :+ playerId)

      val queueNow = queues(options)
      logger.info(s"[QueueUpdated] $options queue=$queueNow")

      if (queueNow.size >= 2) {
        val Seq(p1, p2, rest @ _*) = queueNow
        queues += options -> rest

        val lobbyId = UUID.randomUUID().toString
        logger.info(s"[MatchFormed] lobbyId=$lobbyId players=$p1,$p2")

        // Ask lobby manager and pipe result back
        (lobbyManager ? ("create", lobbyId, options))
          .mapTo[ActorRef]
          .map(_ => LobbyCreated(lobbyId, Seq(p1, p2)))
          .recover {
            case e =>
              logger.error(s"[LobbyCreationFailed] lobbyId=$lobbyId", e)
              LobbyCreated(lobbyId, Seq(p1, p2))
          }
          .pipeTo(self)
      }

    case LobbyCreated(lobbyId, players) =>
      logger.info(s"[LobbyCreated] lobbyId=$lobbyId players=$players")
      players.foreach { playerId =>
        sockets.get(playerId).foreach { socketRef =>
          logger.info(s"[NotifyPlayer] player=$playerId sending MatchFound lobbyId=$lobbyId")
          socketRef ! MatchFound(lobbyId, players)
        }
      }
  }
}
