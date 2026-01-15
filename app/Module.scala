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
import firebase.FirebaseInit
import com.google.firebase.cloud.FirestoreClient
import com.google.cloud.firestore.Firestore
import firebase.FirestoreGameRepository
import com.google.cloud.firestore.FirestoreOptions
import java.io.FileInputStream
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

class Module extends AbstractModule {

  private val logger = Logger(this.getClass)

  override def configure(): Unit = {
    FirebaseInit.init()
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
  def provideLobbyManager(
      system: ActorSystem,
      @Named("gameRepository") gameRepository: FirestoreGameRepository
  ): ActorRef = {
    system.actorOf(Props(new LobbyManager(gameRepository)), "lobbyManager")
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


  @Provides
  @Singleton
  def provideCredentials(): GoogleCredentials = {
    val stream =
      getClass.getClassLoader.getResourceAsStream(
        "backgammon-firebase-adminsdk.json"
      )

    if (stream == null)
      throw new RuntimeException("Service Account Datei nicht gefunden")

    GoogleCredentials.fromStream(stream)
  }

  @Provides
  @Singleton
  def provideFirebaseApp(credentials: GoogleCredentials): FirebaseApp = {
    val options = FirebaseOptions.builder()
      .setCredentials(credentials)
      .build()

    if (FirebaseApp.getApps.isEmpty) {
      FirebaseApp.initializeApp(options)
    } else {
      FirebaseApp.getInstance()
    }
  }

  @Provides
  @Singleton
  def provideFirebaseAuth(app: FirebaseApp): FirebaseAuth =
    FirebaseAuth.getInstance(app)

  @Provides
  @Singleton
  def provideFirestore(credentials: GoogleCredentials): Firestore = {
    val options = FirestoreOptions.newBuilder()
      .setCredentials(credentials)
      .build()

    options.getService
  }
  
  @Provides
  @Singleton
  @Named("gameRepository")
  def provideGameRepository(
      firestore: Firestore,
      ec: ExecutionContext
  ): FirestoreGameRepository = 
    new FirestoreGameRepository(firestore)
}