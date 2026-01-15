package firebase.model

import firebase.model.GamePlayer

case class GameRecord(
  lobbyId: String,
  startedAt: Long,
  endedAt: Option[Long],
  players: Seq[GamePlayer],
  winner: Option[GamePlayer],
  moves: Seq[StoredMove],
  cancelled: Boolean
)
