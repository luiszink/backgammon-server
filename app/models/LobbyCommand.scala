package models

import org.apache.pekko.actor.ActorRef
import firebase.AuthUser

sealed trait LobbyCommand
case class Join(user: AuthUser, ref: ActorRef) extends LobbyCommand
case class Leave(user: AuthUser) extends LobbyCommand
case class BroadcastMessage(user: AuthUser, text: String) extends LobbyCommand
case class Move(user: AuthUser, from: String, to: String) extends LobbyCommand

