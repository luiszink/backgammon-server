package actors

import org.apache.pekko.actor._
import scala.collection.mutable
import models.LobbyOptions
import models.LobbyScope
import scala.concurrent.Future
import org.apache.pekko.pattern.ask
import scala.concurrent.Future
import scala.concurrent.duration._
import org.apache.pekko.util.Timeout
import org.apache.pekko.pattern.pipe
import javax.inject.Inject
import javax.inject.Named
import firebase.* 
import com.google.cloud.firestore.FirestoreOptions
import com.google.auth.oauth2.GoogleCredentials
import java.io.FileInputStream


implicit val timeout: Timeout = 2.seconds

case object CountPublicWaitingLobbies

class LobbyManager @Inject() (
    @Named("gameRepository") gameRepository: FirestoreGameRepository
) extends Actor {
private val lobbies = mutable.Map.empty[String, ActorRef]

  def receive: Receive = 
    case ("create", lobbyId: String, options: LobbyOptions) => {
      val lobby = context.actorOf(Props(new LobbyActor(lobbyId, options, gameRepository)), lobbyId)
      lobbies(lobbyId) = lobby
      sender() ! lobby
    }

    case ("get", lobbyId: String) =>
      sender() ! lobbies.get(lobbyId)
      
    
    case CountPublicWaitingLobbies =>
      import context.dispatcher

      val futures: Iterable[Future[LobbyState]] =
        lobbies.values.map(ref => (ref ? GetLobbyState).mapTo[LobbyState])

      val aggregated: Future[Int] = Future.sequence(futures).map { states =>
        states.count(s => !s.gameStarted && s.scope == LobbyScope.Public)
      }

      aggregated pipeTo sender()
  
}
  