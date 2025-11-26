package controllers

import javax.inject._
import play.api.mvc._
import org.apache.pekko.actor._
import org.apache.pekko.stream.Materializer
import scala.concurrent.ExecutionContext
import org.apache.pekko.stream.scaladsl._
import play.api.libs.json._
import actors._
import models._
import java.util.UUID
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.util.Timeout
import scala.concurrent.Await

import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.pekko.NotUsed

@Singleton
class LobbyController @Inject()(cc: ControllerComponents)
                               (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends AbstractController(cc) {

  private val lobbyManager = system.actorOf(Props[LobbyManager](), "lobbyManager")

  // Create a new lobby with auto-generated ID
  def createLobby: Action[JsValue] = Action(parse.json) { request =>
    val options = request.body.validate[LobbyOptions].asOpt.getOrElse(LobbyOptions())
    val lobbyId = UUID.randomUUID().toString
    lobbyManager ! ("create", lobbyId, options)
    Ok(Json.obj("lobbyId" -> lobbyId))
  }

  def joinLobby(lobbyId: String, user: String) = WebSocket.accept[JsValue, JsValue] { _ =>
    val lobby = getOrCreateLobby(lobbyId)
    
    // Source for messages from the server to this client
    val (outActor, source) = Source.actorRef[OutgoingMessage](
      completionMatcher = PartialFunction.empty,
      failureMatcher = PartialFunction.empty,
      bufferSize = 16,
      overflowStrategy = OverflowStrategy.dropHead
    ).map(msg => Json.toJson(msg): JsValue).preMaterialize()

    // Sink for messages from client to lobby
    val clientActor = system.actorOf(Props(new ClientActor(lobby, user, outActor)))

    val sink: Sink[JsValue, NotUsed] = Flow[JsValue]
      .map(js => ClientMessage.fromJson(js))
      .collect { case JsSuccess(msg, _) => msg }
      .to(Sink.actorRef(clientActor, onCompleteMessage = "leave", onFailureMessage = ex => "failure"))

    Flow.fromSinkAndSource(sink, source)
  }


  import scala.concurrent.duration.DurationInt

  // Helper to get or create a lobby actor
  private def getOrCreateLobby(lobbyId: String): ActorRef = {
    implicit val timeout: Timeout = 5.seconds
    val future = (lobbyManager ? ("get", lobbyId)).mapTo[Option[ActorRef]]
    Await.result(future, timeout.duration).getOrElse {
      val createdFuture = (lobbyManager ? ("create", lobbyId, LobbyOptions())).mapTo[ActorRef]
      Await.result(createdFuture, timeout.duration)
    }
  }
}
