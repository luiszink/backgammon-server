package firebase.model

import de.htwg.se.backgammon.model.Player

case class GamePlayer(
  uid: String,
  name: String,
  color: Player
)
