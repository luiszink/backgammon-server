package models

import play.api.libs.json._
import de.htwg.se.backgammon.controller.IController
import de.htwg.se.backgammon.model.base.Game
import de.htwg.se.backgammon.controller.base.Controller
import de.htwg.se.backgammon.model.base.Model
import de.htwg.se.backgammon.model.base.Dice

case class LobbyOptions(boardSize: BoardSize = BoardSize.Classic) {
  def apply(): IController = {
    val game: Game = boardSize match {
      case BoardSize.Small    => new Game(12, 10)
      case BoardSize.Medium   => new Game(16, 12)
      case BoardSize.Classic  => new Game(24, 15)
    }
    return Controller(new Model(game, Dice()))
  }
}

object LobbyOptions {
  implicit val format: OFormat[LobbyOptions] = Json.format[LobbyOptions]
}

sealed trait BoardSize
object BoardSize {
  case object Small extends BoardSize
  case object Medium extends BoardSize
  case object Classic extends BoardSize

  // Play JSON format
  implicit val format: Format[BoardSize] = new Format[BoardSize] {
    def writes(size: BoardSize): JsValue = JsString(size.toString.toLowerCase)
    def reads(json: JsValue): JsResult[BoardSize] = json match {
      case JsString("small")   => JsSuccess(Small)
      case JsString("medium")  => JsSuccess(Medium)
      case JsString("classic") => JsSuccess(Classic)
      case other               => JsError(s"Unknown board size: $other")
    }
  }
}