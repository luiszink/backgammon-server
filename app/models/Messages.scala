
package models

import play.api.libs.json._
import de.htwg.se.backgammon.model.Player
import de.htwg.se.backgammon.model.IGame

sealed trait ClientMessage
case class JoinLobby(user: String) extends ClientMessage
case class ChatMessage(user: String, text: String) extends ClientMessage

object ClientMessage {
  implicit val joinFormat: OFormat[JoinLobby] = Json.format[JoinLobby]
  implicit val chatFormat: OFormat[ChatMessage] = Json.format[ChatMessage]

  implicit val clientMessageFormat: Format[ClientMessage] = new Format[ClientMessage] {
    def writes(msg: ClientMessage): JsValue = msg match {
      case j: JoinLobby    => Json.obj("type" -> "JoinLobby") ++ Json.toJsObject(j)
      case c: ChatMessage  => Json.obj("type" -> "ChatMessage") ++ Json.toJsObject(c)
    }

    def reads(json: JsValue): JsResult[ClientMessage] = (json \ "type").validate[String].flatMap {
      case "JoinLobby" =>
        val withoutType = json.as[JsObject] - "type"
        withoutType.validate[JoinLobby]
      case "ChatMessage" =>
        val withoutType = json.as[JsObject] - "type"
        withoutType.validate[ChatMessage]
      case other =>
        JsError(s"Unknown type: $other")
    }
  }

  def fromJson(js: JsValue): JsResult[ClientMessage] = js.validate[ClientMessage]

}

sealed trait OutgoingMessage {
  def messageType: String
  def timestamp: Long = System.currentTimeMillis()
}

object OutgoingMessage {

  val playerAssignedFormat = Json.format[PlayerAssigned]
  val chatBroadcastFormat = Json.format[ChatBroadcast]
  val serverInfoFormat    = Json.format[ServerInfo]
  val gameUpdateFormat    = Json.format[GameUpdate]

  implicit val writes: Writes[OutgoingMessage] = Writes {
    case m: PlayerAssigned =>
      Json.obj(
        "type"      -> m.messageType,
        "timestamp" -> m.timestamp,
        "data"      -> Json.toJson(m)(using playerAssignedFormat)
      )

    case m: ChatBroadcast =>
      Json.obj(
        "type"      -> m.messageType,
        "timestamp" -> m.timestamp,
        "data"      -> Json.toJson(m)(using chatBroadcastFormat)
      )

    case m: ServerInfo =>
      Json.obj(
        "type"      -> m.messageType,
        "timestamp" -> m.timestamp,
        "data"      -> Json.toJson(m)(using serverInfoFormat)
      )
    case m: GameUpdate => Json.obj(
        "type"      -> m.messageType,
        "timestamp" -> m.timestamp,
        "data"      -> Json.toJson(m)(using gameUpdateFormat)
      )
  }
}

case class ServerInfo(text: String) extends OutgoingMessage {
  val messageType = "ServerInfo"
}

case class ChatBroadcast(user: String, text: String) extends OutgoingMessage {
  val messageType = "ChatBroadcast"
}

case class PlayerAssigned(color: Player) extends OutgoingMessage {
  val messageType = "PlayerAssigned"
}

case class GameUpdate(game: IGame, currentPlayer: Player, dice: List[Int]) extends OutgoingMessage {
    val messageType = "GameUpdate"
}

