package actors

import org.apache.pekko.actor._
import scala.collection.mutable
import models.LobbyOptions

class LobbyManager extends Actor {
  private val lobbies = mutable.Map.empty[String, ActorRef]

  def receive: Receive = 
    case ("create", lobbyId: String, options: LobbyOptions) => {
      val lobby = context.actorOf(Props(new LobbyActor(lobbyId, options)), lobbyId)
      lobbies(lobbyId) = lobby
      sender() ! lobby
    }

    case ("get", lobbyId: String) =>
      sender() ! lobbies.get(lobbyId)
  
}
  