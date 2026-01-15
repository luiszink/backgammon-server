package firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}

import java.io.FileInputStream

object FirebaseInit {

  def init(): Unit = {
    if (FirebaseApp.getApps.isEmpty) {
      val serviceAccount =
        new FileInputStream("conf/backgammon-firebase-adminsdk.json")

      val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .build()

      FirebaseApp.initializeApp(options)

      println(f"Firebase initialized with options : $options")
    }
  }
}
