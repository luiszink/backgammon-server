package controllers

import javax.inject._
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Source, BroadcastHub, Keep, SourceQueueWithComplete}
import play.api.libs.EventSource
import play.api.mvc._
import play.api.http.ContentTypes
import play.api.data._
import play.api.data.Forms._
import de.htwg.se.backgammon.controller.IController
import de.htwg.se.backgammon.view.TUI
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.apache.pekko.stream.OverflowStrategy
import de.htwg.se.backgammon.util.PrettyPrint
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import de.htwg.se.backgammon.util.Event
import de.htwg.se.backgammon.view.GUI
import de.htwg.se.backgammon.model.base.Game
import de.htwg.se.backgammon.model.base.Move
import de.htwg.se.backgammon.controller.strategy.ValidateMoveStrategy
import de.htwg.se.backgammon.model.IGame
import de.htwg.se.backgammon.model.base.BearInMove
import play.api.libs.json._
import scala.util.{Try, Success, Failure}
import play.filters.csrf._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents, val gameController: IController)(implicit mat: Materializer, ec: ExecutionContext) extends BaseController {

  def cors(result: Result): Result = {
      result.withHeaders(
        "Access-Control-Allow-Origin" -> "http://localhost:5173",
        "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers" -> "Content-Type"
      )
  }

  def index = Action {implicit request: Request[AnyContent] =>
    cors(Ok(""))
  }

  def undo = Action {implicit request: Request[AnyContent] =>
    gameController.undoAndPublish(gameController.undo)
    Redirect(routes.HomeController.index())
  }

  def rules = Action {
    Ok(views.html.rules())
  }

  def boardHtml = views.html.components.board(gameController.game)

  def setBoardSize(size: String) = Action { implicit request: Request[AnyContent] =>
    val game: Game = size match {
      case "small"  => new Game(12, 10)
      case "medium" => new Game(16, 12)
      case "classic"=> new Game(24, 15)
      case _        => new Game(24, 15)
    }
    gameController.init(game)

    Ok(boardHtml) 
  }

  def move(from: String, to: String) = Action { implicit request: Request[AnyContent] => 
    gameController.doAndPublish(
        gameController.put,
        Move.create(gameController.game, gameController.currentPlayer, from.toInt, to.toInt)
      )
    Ok(Json.obj(
      "success" -> true,
      "boardHtml" -> boardHtml.toString(),
      "currentPlayer" -> gameController.currentPlayer.toString,
      "dice" -> Json.toJson(gameController.dice)
    ))
  }

  def hints(from: Int): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val fields   = gameController.game.fields
    val maxIndex = fields.length - 1
    val current  = gameController.currentPlayer

    val destinations: Seq[Int] = {
      if (from < 0 || from > maxIndex) Seq.empty
      else {
        val fromField = fields(from)

        if (fromField.isEmpty() || !fromField.isOccupiedBy(current)) {
          Seq.empty
        } else {
          (0 to maxIndex).flatMap { to =>
            if (to == from) None
            else {
              val targetField = fields(to)

              if (!targetField.isEmpty() && !targetField.isOccupiedBy(current)) {
                None
              } else {
                val isValid = Try {
                  val move = Move.create(gameController.game, current, from, to)
                  val validator = ValidateMoveStrategy(gameController, move)
                  validator.validate()
                }.isSuccess

                if (isValid) Some(to) else None
              }
            }
          }
        }
      }
    }

    Ok(Json.obj(
      "ok"          -> true,
      "from"        -> from,
      "destinations"-> destinations
    ))
  }

  def bearIn(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>    
    val move = BearInMove(gameController.currentPlayer, gameController.die)
    val result: Try[IGame] = gameController.put(move)

    result match {
      case Success(_) =>
        Ok(Json.obj(
          "ok"            -> true,
          "boardHtml"     -> boardHtml.toString(),
          "currentPlayer" -> gameController.currentPlayer.toString,
          "dice"          -> Json.toJson(gameController.dice)
        ))

      case Failure(ex) =>
        BadRequest(Json.obj(
          "ok"    -> false,
          "error" -> ex.getMessage
        ))
    }
  }
}
