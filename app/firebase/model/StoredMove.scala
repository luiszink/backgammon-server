package firebase.model

case class StoredMove(
  player: String,
  from: Int,
  to: Int,
  timestamp: Long
)
