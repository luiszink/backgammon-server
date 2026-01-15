package firebase

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._

case class CacheEntry[A](value: A, expiresAt: Long)

class TTLCache[K, V](ttl: FiniteDuration) {

  private val store = new ConcurrentHashMap[K, CacheEntry[V]]()

  def get(key: K): Option[V] = {
    val now = System.currentTimeMillis()
    val entry = store.get(key)

    if (entry == null) None
    else if (entry.expiresAt < now) {
      store.remove(key)
      None
    } else {
      Some(entry.value)
    }
  }

  def put(key: K, value: V): Unit = {
    val expiresAt = System.currentTimeMillis() + ttl.toMillis
    store.put(key, CacheEntry(value, expiresAt))
  }

  def invalidate(key: K): Unit =
    store.remove(key)

  def invalidateAll(predicate: K => Boolean): Unit = {
    store.keySet().forEach { k =>
      if (predicate(k)) store.remove(k)
    }
  }

  def clear(): Unit =
    store.clear()
}
    