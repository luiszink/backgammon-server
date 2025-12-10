package models
import play.api.libs.json._

case class JoinQueueRequest(
  playerId: String,
  options: LobbyOptions
)

object JoinQueueRequest {
  implicit val format: OFormat[JoinQueueRequest] = Json.format[JoinQueueRequest]
}