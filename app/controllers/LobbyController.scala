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

import play.api.mvc._
import play.api.mvc.Results._
import firebase.FirebaseAuthService

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

  def joinLobby(lobbyId: String): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] { request =>

      request.getQueryString("token") match {

        case Some(token) =>
          try {
            val authUser = FirebaseAuthService.verify(token)
            val lobby = getOrCreateLobby(lobbyId)

            // Source: server → client
            val (outActor, source) =
              Source
                .actorRef[OutgoingMessage](
                  completionMatcher = PartialFunction.empty,
                  failureMatcher = PartialFunction.empty,
                  bufferSize = 16,
                  overflowStrategy = OverflowStrategy.dropHead
                )
                .map(msg => Json.toJson(msg): JsValue)
                .preMaterialize()

            // Client actor
            val clientActor =
              system.actorOf(
                Props(new ClientActor(lobby, authUser, outActor))
              )

            // Sink: client → server
            val sink: Sink[JsValue, NotUsed] =
              Flow[JsValue]
                .map(ClientMessage.fromJson)
                .collect { case JsSuccess(msg, _) => msg }
                .to(
                  Sink.actorRef(
                    clientActor,
                    onCompleteMessage = StopActor,
                    onFailureMessage = _ => StopActor
                  )
                )

            Future.successful(
              Right(Flow.fromSinkAndSource(sink, source))
            )

          } catch {
            case _: Exception =>
              Future.successful(Left(Forbidden("Invalid Firebase token")))
          }

        case None =>
          Future.successful(Left(Unauthorized("Missing token")))
      }
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
