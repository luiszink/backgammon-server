package models

import play.api.libs.json._

case class GameSummary(
  lobbyId: String,
  endedAt: Option[Long],
  won: Option[Boolean],
  color: Option[String],
  scope: Option[String],
  boardSize: Option[String],
  opponentUid: Option[String],
  opponentName: Option[String]
)

object GameSummary {
  implicit val writes: Writes[GameSummary] = Json.writes[GameSummary]
}
