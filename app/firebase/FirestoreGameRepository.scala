package firebase

import com.google.cloud.firestore.Firestore
import scala.concurrent.ExecutionContext
import com.google.cloud.firestore.FieldValue
import javax.inject.Inject
import firebase.model.*
import scala.jdk.CollectionConverters._
import com.google.cloud.firestore.QueryDocumentSnapshot
import models.GameSummary
import models.LobbyOptions
import scala.concurrent.duration.DurationInt

class FirestoreGameRepository(
    db: Firestore
) {
  private val gamesByUserCache =
    new TTLCache[(String, Int, Int), Seq[GameSummary]](5.minutes)

  def createGame(record: GameRecord): Unit = {
    val docData: java.util.Map[String, Object] = Map(
      "lobbyId" -> record.lobbyId,
      "startedAt" -> record.startedAt.asInstanceOf[Object],
      "endedAt" -> null,
      "cancelled" -> java.lang.Boolean.valueOf(false),
      "players" -> record.players.map(gamePlayerToMap).asJava,
      "moves" -> Seq.empty.asJava
    ).asJava

    db.collection("games")
      .document(record.lobbyId)
      .set(docData)
  }

  private def gamePlayerToMap(
      player: GamePlayer
  ): java.util.Map[String, Object] =
    Map(
      "uid" -> player.uid,
      "name" -> player.name,
      "color" -> player.color.toString
    ).map { case (k, v) => k -> v.asInstanceOf[Object] }.asJava

  def appendMove(lobbyId: String, move: StoredMove): Unit = {
    val moveMap = Map(
      "player" -> move.player,
      "from" -> move.from,
      "to" -> move.to,
      "timestamp" -> move.timestamp
    ).asJava

    db.collection("games")
      .document(lobbyId)
      .update("moves", FieldValue.arrayUnion(moveMap))
  }

  def finishGame(
      lobbyId: String,
      players: Seq[GamePlayer],
      winner: Option[GamePlayer],
      options: LobbyOptions,
      cancelled: Boolean
  ): Unit = {
    val endedAt = System.currentTimeMillis()

    db.collection("games")
      .document(lobbyId)
      .update(
        "endedAt",
        endedAt,
        "winner",
        winner.map(gamePlayerToMap).orNull,
        "cancelled",
        cancelled
      )

    players.foreach { player =>
      db.collection("users")
        .document(player.uid)
        .collection("games")
        .document(lobbyId)
        .set(
          Map(
            "lobbyId" -> lobbyId,
            "endedAt" -> endedAt,
            "won" -> winner.exists(_.uid == player.uid),
            "color" -> player.color.toString(),
            "scope" -> options.scope.toString(),
            "boardSize" -> options.boardSize.toString(),
            "opponentUid" -> players
              .find(_.uid != player.uid)
              .map(_.uid)
              .orNull,
            "opponentName" -> players
              .find(_.uid != player.uid)
              .map(_.name)
              .orNull
          ).asJava
        )
      gamesByUserCache.invalidateAll { case (uid, _, _) =>
        uid == player.uid
      }
    }
  }

  def getGamesByUser(
      uid: String,
      page: Int,
      pageSize: Int
  ): Seq[GameSummary] = {

    val cacheKey = (uid, page, pageSize)

    gamesByUserCache.get(cacheKey).getOrElse {
      val start = (page - 1) * pageSize

      val docs =
        db.collection("users")
          .document(uid)
          .collection("games")
          .orderBy("endedAt")
          .get()
          .get()
          .getDocuments
          .asScala
          .toSeq
          .sortBy(doc =>
            -Option(doc.getLong("endedAt")).map(_.longValue).getOrElse(0L)
          )
          .slice(start, start + pageSize)

      val result = docs.map { doc =>
        GameSummary(
          lobbyId = doc.getString("lobbyId"),
          endedAt = Option(doc.getLong("endedAt")),
          won = Option(doc.getBoolean("won")),
          color = Option(doc.getString("color")),
          scope = Option(doc.getString("scope")),
          boardSize = Option(doc.getString("boardSize")),
          opponentUid = Option(doc.getString("opponentUid")),
          opponentName = Option(doc.getString("opponentName"))
        )
      }

      gamesByUserCache.put(cacheKey, result)
      result
    }
  }

}
