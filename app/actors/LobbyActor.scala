package actors

import org.apache.pekko.actor._
import models._
import scala.collection.mutable
import de.htwg.se.backgammon.controller.IController
import de.htwg.se.backgammon.model.Player
import scala.concurrent.duration.DurationInt
import firebase.AuthUser

case class CheckDisconnectTimeout(user: AuthUser)
case object GetLobbyState
case class LobbyState(gameStarted: Boolean, scope: LobbyScope)


case class PlayerSlot(
  authUser: AuthUser,
  color: Player,
  actor: Option[ActorRef],
  disconnectTime: Option[Long]
)

class LobbyActor(lobbyId: String, options: LobbyOptions) extends Actor {
  private val controller: IController = options.apply()
  private val users = mutable.Map.empty[String, PlayerSlot]
  private var gameStarted = false;

  def receive: Receive = {
    case Join(user, ref) =>
      users.get(user.uid) match {

        // Reconnect
        case Some(slot @ PlayerSlot(_, color, None, _)) =>
          users(user.uid) = slot.copy(actor = Some(ref), disconnectTime = None)
          ref ! PlayerAssigned(color)
          ref ! GameUpdate(controller.game, controller.currentPlayer, controller.dice)
          broadcastLobbyUpdate()
          broadcast(s"${user.name.getOrElse(color)} reconnected")

        // Already connected
        case Some(PlayerSlot(_, _, Some(_), _)) =>
          ref ! ServerInfo("You are already connected.")
          context.stop(ref)

        // New player
        case None if users.size < 2 =>
          val color = if (users.isEmpty) Player.White else Player.Black
          val slot = PlayerSlot(user, color, Some(ref), None)
          users += user.uid -> slot

          ref ! PlayerAssigned(color)
          ref ! GameUpdate(controller.game, controller.currentPlayer, controller.dice)
          if (users.size == 2) {
            gameStarted = true
          }
          broadcastLobbyUpdate()
          broadcast(s"${user.name.getOrElse(color)} joined the lobby")

        // Lobby full for new usernames
        case None =>
          ref ! ServerInfo("Lobby is full")
          context.stop(ref)
    }
    case BroadcastMessage(user, text) =>
      broadcast(ChatBroadcast(user.name.getOrElse("Guest"), text))
    case Leave(user) =>
      users.get(user.uid) match {
        case Some(slot) =>
          val now = System.currentTimeMillis()
          users(user.uid) = slot.copy(actor = None, disconnectTime = Some(now))
          broadcastLobbyUpdate()
          broadcast(s"${user.name.getOrElse(slot.color)} disconnected")

          context.system.scheduler.scheduleOnce(1.minute)(
            self ! CheckDisconnectTimeout(user)
          )(using context.dispatcher)

        case None => // ignore
      }
    case CheckDisconnectTimeout(user) =>
      users.get(user.uid) match {
        case Some(PlayerSlot(_, _, _, Some(disconnectTime))) =>
          val now = System.currentTimeMillis()
          val elapsed = now - disconnectTime

          if (elapsed >= 60000) {
            broadcast(s"Game cancelled: $user disconnected for too long.")
           // cancelGame()           // implement logic
          }

        // Player has reconnected → do nothing
        case _ =>
      }
    case Move(user, from, to) => 
      users.get(user.uid) match {
        case Some(PlayerSlot(user, color, Some(actor), disconnectTime)) => 
          if (color != controller.currentPlayer) {
            actor ! ServerInfo("It's not your turn!")
          } else {
            val move = de.htwg.se.backgammon.model.base.Move.create(
              controller.game, controller.currentPlayer, from.toInt, to.toInt
            ) 
            controller.doAndPublish(controller.put, move)
            broadcast(GameUpdate(controller.game, controller.currentPlayer, controller.dice))

          }
        case _ => 
      }
    case GetLobbyState =>
      sender() ! LobbyState(gameStarted, options.scope)

  }

  private def broadcastLobbyUpdate(): Unit = {
    def getUser(color: Player): Option[User] =
      users
        .find(_._2.color == color)
        .map { case (uid, slot) =>
          User(
            name = slot.authUser.name.getOrElse("Guest"),
            connected = slot.actor.isDefined
          )
        }

    val update = LobbyUpdate(white= getUser(Player.White), black= getUser(Player.Black), gameStarted)
    users.values.flatMap(_.actor).foreach(_ ! update)
  }

  private def broadcast(msg: OutgoingMessage): Unit = {
    println(s"send broadcast (${lobbyId}) $msg to ${users}")
    users.values.flatMap(_.actor).foreach(_ ! msg)
  }

  private def broadcast(msg: String): Unit = {
    println(s"send broadcast (${lobbyId}) " +
      s"to ${users}")
    users.values.flatMap(_.actor).foreach(_ ! ServerInfo(msg))
  }
}
