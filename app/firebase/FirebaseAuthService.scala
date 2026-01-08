package firebase

import com.google.firebase.auth.{FirebaseAuth, FirebaseToken}

case class AuthUser(
  uid: String,
  isAnonymous: Boolean,
  email: Option[String],
  name: Option[String]
)

object FirebaseAuthService {

  def verify(token: String): AuthUser = {
    val decoded: FirebaseToken =
      FirebaseAuth.getInstance().verifyIdToken(token)

    val claims = decoded.getClaims

    val signInProvider =
      Option(claims.get("firebase"))
        .map(_.asInstanceOf[java.util.Map[String, Any]])
        .flatMap(m => Option(m.get("sign_in_provider")))
        .map(_.toString)
        .getOrElse("unknown")

    val isAnonymous = signInProvider == "anonymous"

    AuthUser(
      uid = decoded.getUid,
      isAnonymous = isAnonymous,
      email = Option(decoded.getEmail),
      name = Option(decoded.getName)
    )
  }
}