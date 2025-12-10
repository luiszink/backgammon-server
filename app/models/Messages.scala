
package models

import play.api.libs.json._
import de.htwg.se.backgammon.model.Player
import de.htwg.se.backgammon.model.IGame

sealed trait ClientMessage
case class JoinLobby(user: String) extends ClientMessage
case class ChatMessage(text: String) extends ClientMessage
case class MoveMessage(from: String, to: String) extends ClientMessage

object ClientMessage {
  implicit val joinFormat: OFormat[JoinLobby] = Json.format[JoinLobby]
  implicit val chatFormat: OFormat[ChatMessage] = Json.format[ChatMessage]
  implicit val moveFormat: OFormat[MoveMessage] = Json.format[MoveMessage]


  implicit val clientMessageFormat: Format[ClientMessage] = new Format[ClientMessage] {
    def writes(msg: ClientMessage): JsValue = msg match {
      case j: JoinLobby     => Json.obj("type" -> "JoinLobby") ++ Json.toJsObject(j)
      case c: ChatMessage   => Json.obj("type" -> "ChatMessage") ++ Json.toJsObject(c)
      case m: MoveMessage   => Json.obj("type" -> "MoveMessage") ++ Json.toJsObject(m)
    }

    def reads(json: JsValue): JsResult[ClientMessage] = (json \ "type").validate[String].flatMap {
      case "JoinLobby" =>
        val withoutType = json.as[JsObject] - "type"
        withoutType.validate[JoinLobby]
      case "ChatMessage" =>
        val withoutType = json.as[JsObject] - "type"
        withoutType.validate[ChatMessage]
      case "MoveMessage" =>
        val withoutType = json.as[JsObject] - "type"
        withoutType.validate[MoveMessage]
      case other =>
        JsError(s"Unknown type: $other")
    }
  }

  def fromJson(js: JsValue): JsResult[ClientMessage] =  {
    val test = js.validate[ClientMessage]
    println(test)
    return test
  }

}

sealed trait OutgoingMessage {
  def messageType: String
  def timestamp: Long = System.currentTimeMillis()
}

object OutgoingMessage {

  val playerAssignedFormat = Json.format[PlayerAssigned]
  val lobbyUpdateFormat = Json.format[LobbyUpdate]
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
    case m: LobbyUpdate => Json.obj(
        "type"      -> m.messageType,
        "timestamp" -> m.timestamp,
        "data"      -> Json.toJson(m)(using lobbyUpdateFormat)
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

case class User (name: String, connected: Boolean)

object User {
  implicit val format: OFormat[User] = Json.format[User]
}

case class LobbyUpdate(white: Option[User] , black: Option[User], gameStarted: Boolean) extends OutgoingMessage {
    val messageType = "LobbyUpdate"
}