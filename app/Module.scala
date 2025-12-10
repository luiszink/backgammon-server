import de.htwg.se.backgammon.controller.IController
import de.htwg.se.backgammon.controller.base.Controller
import com.google.inject.AbstractModule
import de.htwg.se.backgammon.model.base.Model
import de.htwg.se.backgammon.model.base.Game
import de.htwg.se.backgammon.model.base.DefaultSetup
import de.htwg.se.backgammon.model.base.Dice
import play.api.Logger
import com.google.inject.{AbstractModule, Provides}
import org.apache.pekko.actor._
import javax.inject._
import actors.LobbyManager
import actors.MatchmakerActor
import org.apache.pekko.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext

class Module extends AbstractModule {

  private val logger = Logger(this.getClass)

  override def configure(): Unit = {
    bind(classOf[IController]).toInstance {
      val NUMBER_OF_FIELDS = 24
      val NUMBER_OF_FIGURES = 15
      val model = new Model(
        new Game(DefaultSetup(NUMBER_OF_FIELDS, NUMBER_OF_FIGURES)),
        new Dice()
      )
      Controller(model)
    }
  }

  @Provides
  @Singleton
  @Named("lobby-manager")
  def provideLobbyManager(system: ActorSystem): ActorRef = {
    system.actorOf(Props[LobbyManager](), "lobbyManager")
  }

  @Provides
  @Singleton
  @Named("matchmaker")
  def provideMatchmaker(
      system: ActorSystem,
      @Named("lobby-manager") lobbyManager: ActorRef
  )(implicit ec: ExecutionContext): ActorRef = {
    system.actorOf(
      Props(new MatchmakerActor(lobbyManager)(using provideTimeout(), ec)),
      "matchmaker"
    )
  }

  @Provides
  @Singleton
  def provideTimeout(): Timeout = Timeout(2.seconds)
}
