package io.vamp.core.persistence

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ActorDescription
import io.vamp.common.http.RestClient
import io.vamp.core.model.artifact.{Artifact, DefaultBlueprint}
import io.vamp.core.model.reader._
import io.vamp.core.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._

import scala.concurrent.Future

object ElasticsearchPersistenceActor extends ActorDescription {

  def props(args: Any*): Props = Props(classOf[ElasticsearchPersistenceActor], args: _*)

  lazy val index = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.index")

  lazy val elasticsearchUrl: String = ConfigFactory.load().getString("vamp.core.persistence.elasticsearch.url")
}

case class ElasticsearchArtifact(name: String, artifact: String)

case class ElasticsearchSearchResponse(hits: ElasticsearchSearchHits)

case class ElasticsearchSearchHits(total: Long, hits: List[Map[String, _]])

class ElasticsearchPersistenceActor extends PersistenceActor with TypeOfArtifact with PaginationSupport {

  import ElasticsearchPersistenceActor._

  private val store = new InMemoryStore(log)

  private val types: Map[String, YamlReader[_ <: Artifact]] = Map(
    "deployments" -> DeploymentReader,
    "breeds" -> BreedReader,
    "blueprints" -> BlueprintReader,
    "slas" -> SlaReader,
    "scales" -> ScaleReader,
    "escalations" -> EscalationReader,
    "routings" -> RoutingReader,
    "filters" -> FilterReader,
    "workflows" -> WorkflowReader,
    "scheduled-workflows" -> ScheduledWorkflowReader
  )

  protected def info(): Future[Any] = RestClient.get[Any](s"$elasticsearchUrl") map {
    case info => Map[String, Any]("type" -> "elasticsearch", "elasticsearch" -> info)
  }

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] = Future {
    log.debug(s"${getClass.getSimpleName}: all [${`type`.getSimpleName}] of $page per $perPage")
    store.all(`type`, page, perPage)
  }

  protected def create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = false): Future[Artifact] = {
    implicit val formats = CoreSerializationFormat.full
    log.debug(s"${getClass.getSimpleName}: create [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    val storeArtifact = store.create(artifact, source, ignoreIfExists)
    storeArtifact match {
      case blueprint: DefaultBlueprint => blueprint.clusters.flatMap(_.services).map(_.breed).foreach(breed => create(breed, ignoreIfExists = true))
      case _ =>
    }

    val artifacts = typeOf(storeArtifact.getClass)
    findHitBy(storeArtifact.name, storeArtifact.getClass) map {
      case None =>
        // TODO validate response
        RestClient.post[Any](s"$elasticsearchUrl/$index/$artifacts", ElasticsearchArtifact(storeArtifact.name, write(storeArtifact)))
      case Some(hit) =>
        // TODO validate response
        if (ignoreIfExists)
          hit.get("_id").foreach(id => RestClient.post[Any](s"$elasticsearchUrl/$index/$artifacts/$id", ElasticsearchArtifact(storeArtifact.name, write(storeArtifact))))
    } map {
      case _ => storeArtifact
    }
  }

  protected def read(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] = Future {
    log.debug(s"${getClass.getSimpleName}: read [${`type`.getSimpleName}] - $name}")
    store.read(name, `type`)
  }

  protected def update(artifact: Artifact, source: Option[String] = None, create: Boolean = false): Future[Artifact] = {
    implicit val formats = CoreSerializationFormat.full
    log.debug(s"${getClass.getSimpleName}: update [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    store.update(artifact, source, create)
    findHitBy(artifact.name, artifact.getClass) map {
      case None => if (create) {
        // TODO validate response
        RestClient.post[Any](s"$elasticsearchUrl/$index/${typeOf(artifact.getClass)}", ElasticsearchArtifact(artifact.name, write(artifact)))
      }
      case Some(hit) =>
        // TODO validate response
        hit.get("_id").foreach(id => RestClient.post[Any](s"$elasticsearchUrl/$index/${typeOf(artifact.getClass)}/$id", ElasticsearchArtifact(artifact.name, write(artifact))))
    } map {
      case _ => artifact
    }
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Artifact] = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    val artifact = store.delete(name, `type`)
    findHitBy(name, `type`) map {
      case None =>
      case Some(hit) => hit.get("_id").foreach(id => RestClient.delete(s"$elasticsearchUrl/$index/${typeOf(`type`)}/$id"))
    } map {
      case _ => artifact
    }
  }

  override protected def start() = types.foreach {
    case (group, _) =>
      allPages[Artifact](findAllArtifactsBy(group)) map {
        case artifacts => artifacts.foreach(artifact => store.create(artifact, None, ignoreIfExists = true))
      }
  }

  private def findAllArtifactsBy(`type`: String)(from: Int, size: Int): Future[ArtifactResponseEnvelope] = {
    RestClient.post[ElasticsearchSearchResponse](s"$elasticsearchUrl/$index/${`type`}/_search", Map("from" -> from, "size" -> size)) map {
      case response: ElasticsearchSearchResponse =>
        val list = response.hits.hits.flatMap { hit =>
          hit.get("_source").flatMap(_.asInstanceOf[Map[String, _]].get("artifact")).flatMap { artifact =>
            types.get(`type`).flatMap(reader => Some(reader.read(artifact.toString)))
          }
        }
        ArtifactResponseEnvelope(list, response.hits.total, from, size)
      case other =>
        log.debug(s"unexpected: ${other.toString}")
        ArtifactResponseEnvelope(Nil, 0L, from, size)
    }
  }

  private def findHitBy(name: String, `type`: Class[_ <: Artifact]): Future[Option[Map[String, _]]] = {
    RestClient.post[ElasticsearchSearchResponse](s"$elasticsearchUrl/$index/${typeOf(`type`)}/_search", Map("from" -> 0, "size" -> 1, "query" -> ("term" -> ("name" -> name)))) map {
      case response: ElasticsearchSearchResponse => if (response.hits.total == 1) Some(response.hits.hits.head) else None
      case other =>
        log.error(s"unexpected: ${other.toString}")
        None
    }
  }
}
