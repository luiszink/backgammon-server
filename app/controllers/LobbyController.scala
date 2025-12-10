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
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.pekko.NotUsed
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import play.api.libs.streams.ActorFlow

case object StopActor

@Singleton
class LobbyController @Inject() (
    cc: ControllerComponents,
    @Named("lobby-manager") lobbyManager: ActorRef,
    @Named("matchmaker") matchmaker: ActorRef,
    implicit val timeout: Timeout
)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
    extends AbstractController(cc) {

  def countPublicWaiting(): Action[AnyContent] = Action.async {
    (lobbyManager ? CountPublicWaitingLobbies)
      .mapTo[Int]
      .map(count => Ok(count.toString))
  }

  def socket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      QueueingActor.props("", matchmaker, out)
    }
  }

  // Create a new lobby with auto-generated ID
  def createLobby: Action[JsValue] = Action(parse.json) { request =>
    val options = request.body.validate[LobbyOptions] match {
      case JsSuccess(value, _) => value
      case JsError(errors)     =>
        println(s"Validation failed: $errors")
        LobbyOptions() // fallback
    }
    println(s"Create Lobby with options ${options}")

    val lobbyId = UUID.randomUUID().toString
    lobbyManager ! ("create", lobbyId, options)
    Ok(Json.obj("lobbyId" -> lobbyId))
  }

  def joinLobby(lobbyId: String, user: String) =
    WebSocket.accept[JsValue, JsValue] { _ =>
      val lobby = getOrCreateLobby(lobbyId)

      // Source for messages from the server to this client
      val (outActor, source) = Source
        .actorRef[OutgoingMessage](
          completionMatcher = PartialFunction.empty,
          failureMatcher = PartialFunction.empty,
          bufferSize = 16,
          overflowStrategy = OverflowStrategy.dropHead
        )
        .map(msg => Json.toJson(msg): JsValue)
        .preMaterialize()

      // Sink for messages from client to lobby
      val clientActor =
        system.actorOf(Props(new ClientActor(lobby, user, outActor)))

      val sink: Sink[JsValue, NotUsed] = Flow[JsValue]
        .map(js => ClientMessage.fromJson(js))
        .collect { case JsSuccess(msg, _) => msg }
        .to(
          Sink.actorRef(
            clientActor,
            onCompleteMessage = StopActor,
            onFailureMessage = ex => "failure"
          )
        )

      Flow.fromSinkAndSource(sink, source)
    }

  private def getOrCreateLobby(lobbyId: String): ActorRef = {
    implicit val timeout: Timeout = 5.seconds
    val future = (lobbyManager ? ("get", lobbyId)).mapTo[Option[ActorRef]]
    Await.result(future, timeout.duration).getOrElse {
      val createdFuture =
        (lobbyManager ? ("create", lobbyId, LobbyOptions())).mapTo[ActorRef]
      Await.result(createdFuture, timeout.duration)
    }
  }
}
