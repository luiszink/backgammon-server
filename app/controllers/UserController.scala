package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import com.google.firebase.auth.FirebaseAuth
import models.UserProfile
import models.GameSummary
import firebase.FirestoreGameRepository

@Singleton
class UserController @Inject() (
    cc: ControllerComponents,
    firebaseAuth: FirebaseAuth,
    @Named("gameRepository") gameRepository: FirestoreGameRepository
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def fetchUser(uid: String): Action[AnyContent] = Action.async {

    Future {
      firebaseAuth.getUser(uid)
    }.map { user =>
      val profile = UserProfile(
        uid = user.getUid,
        username = Option(user.getDisplayName).getOrElse("Anonymous"),
        creationTime = user.getUserMetadata.getCreationTimestamp,
        lastSignInTime = user.getUserMetadata.getLastSignInTimestamp
      )

      Ok(Json.toJson(profile))
    }.recover {
      case _: com.google.firebase.auth.FirebaseAuthException =>
        NotFound(Json.obj("error" -> "User not found"))

      case ex =>
        InternalServerError(Json.obj("error" -> ex.getMessage))
    }
  }

  def fetchUserGames(
      uid: String,
      page: Int,
      pageSize: Int
  ): Action[AnyContent] =
    Action {
      val games: Seq[GameSummary] = gameRepository.getGamesByUser(uid, page, pageSize)
      Ok(Json.toJson(games))
    }
}
