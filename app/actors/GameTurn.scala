package actors

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt
import de.htwg.se.backgammon.model.Player


import java.time.Instant
import scala.concurrent.duration._
import org.apache.pekko.actor.{Scheduler, Cancellable}
import scala.concurrent.ExecutionContext

case class GameTurn(
  var currentPlayer: Player,
  var turnStartedAt: Instant,
  turnDuration: FiniteDuration = 30.seconds,
  onTick: (Player, Long) => Unit, 
  onTimeOut: Player => Unit
)(implicit scheduler: Scheduler, ec: ExecutionContext) {

  private var timeoutTask: Option[Cancellable] = None
  private var tickTask: Option[Cancellable] = None

  def timeLeftSeconds: Long = {
    val elapsed = Instant.now().getEpochSecond - turnStartedAt.getEpochSecond
    math.max(0, turnDuration.toSeconds - elapsed)
  }

  def start(player: Player, force: Boolean = false): Unit = {
    if (!force && player == currentPlayer ) {
      return
    }

    cancelAll()

    currentPlayer = player
    turnStartedAt = Instant.now()

    onTick(currentPlayer, turnDuration.toSeconds)

    tickTask = Some(
      scheduler.scheduleAtFixedRate(0.seconds, 1.second) { () =>
        val left = timeLeftSeconds
        onTick(currentPlayer, left)

        if (left <= 0) {
          handleTimeout()
        }
      }
    )

    timeoutTask = Some(
      scheduler.scheduleOnce(turnDuration) {
        handleTimeout()
      }
    )
  }

  def isExpired: Boolean =
    timeLeftSeconds == 0

  private def handleTimeout(): Unit = synchronized {
    if (timeoutTask.isDefined) {
      cancelAll()
      onTimeOut(currentPlayer)
    }
  }

  private def cancelAll(): Unit = {
    timeoutTask.foreach(_.cancel())
    tickTask.foreach(_.cancel())
    timeoutTask = None
    tickTask = None
  }
}
