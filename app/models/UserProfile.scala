package models

import play.api.libs.json._

case class UserProfile(
  uid: String,
  username: String,
  creationTime: Long,
  lastSignInTime: Long
)

object UserProfile {
  implicit val writes: Writes[UserProfile] = Json.writes[UserProfile]
}
