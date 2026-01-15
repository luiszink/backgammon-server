package firebase

import com.google.cloud.firestore.Firestore
import javax.inject._
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

@Singleton
class FirestoreLifecycle @Inject() (
    firestore: Firestore,
    lifecycle: ApplicationLifecycle
) {
  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  lifecycle.addStopHook { () =>
    Future {
      firestore.close()
    }
  }
}
