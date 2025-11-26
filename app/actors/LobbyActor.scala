package actors

import org.apache.pekko.actor._
import models._
import scala.collection.mutable
import de.htwg.se.backgammon.controller.IController
import de.htwg.se.backgammon.model.Player

class LobbyActor(lobbyId: String, options: LobbyOptions) extends Actor {
  private val controller: IController = options.apply()

  private val users = mutable.Map.empty[String, ActorRef]

  def receive: Receive = {
    case Join(user, ref) =>
      if (users.size >= 2) {
        ref ! ServerInfo("Lobby is full. Cannot join.")
        context.stop(ref)
      } else {
        users += user -> ref
        val color = if (users.size == 1) Player.White else Player.Black
        ref ! PlayerAssigned(color)
        ref ! GameUpdate(controller.game, controller.currentPlayer, controller.dice)
        println(s"$user ($color) joined lobby $lobbyId / ${users.size} players")
        broadcast(s"$user joined the lobby")
      }
    case BroadcastMessage(user, text) =>
      broadcast(ChatBroadcast(user, text))

    case Leave(user) =>
      users -= user
      broadcast(s"$user left the lobby")
  }

  private def broadcast(msg: OutgoingMessage): Unit = {
    println(s"send broadcast $msg to ${users}")
    users.values.foreach(_ ! msg)
  }

  private def broadcast(msg: String): Unit = {
    println(s"send broadcast to ${users}")
    users.values.foreach(_ ! ServerInfo(msg))
  }
}
