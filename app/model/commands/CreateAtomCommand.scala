package model.commands

import java.util.Date
import java.util.UUID._

import com.gu.atom.data.IDConflictError
import com.gu.contentatom.thrift.atom.media.{Category => ThriftCategory, MediaAtom => ThriftMediaAtom, Metadata => ThriftMetadata}
import com.gu.contentatom.thrift.{Atom => ThriftAtom, _}
import com.gu.media.logging.Logging
import com.gu.pandomainauth.model.{User => PandaUser}
import data.DataStores
import model.commands.CommandExceptions._
import model.{ChangeRecord, Image, MediaAtom, PrivacyStatus, MediaAtomBeforeCreation, ContentChangeDetails}
import org.cvogt.play.json.Jsonx
import play.api.libs.json.Format

import scala.util.{Failure, Success}

case class CreateAtomCommand(data: MediaAtomBeforeCreation, override val stores: DataStores, user: PandaUser)

  extends Command with Logging {

  type T = MediaAtom

  def process() = {
    val atomId = randomUUID().toString
    val createdChangeRecord = Some(ChangeRecord.now(user))

    val details = ContentChangeDetails(
      lastModified = createdChangeRecord,
      created = createdChangeRecord,
      published = None,
      revision = 1L
    )

    log.info(s"Request to create new atom $atomId [${data.title}]")

    val atom = data.asThrift(atomId, details)

    auditDataStore.auditCreate(atom.id, getUsername(user))

    log.info(s"Creating new atom $atomId [${data.title}]")

    previewDataStore.createAtom(atom).fold({
        case IDConflictError =>
          log.error(s"Cannot create new atom $atomId. The id is already in use")
          AtomIdConflict

        case other =>
          log.error(s"Cannot create new atom $atomId. $other")
          UnknownFailure
      },
      _ => {
        log.info(s"Successfully created new atom $atomId [${data.title}]")

        val event = ContentAtomEvent(atom, EventType.Update, new Date().getTime)

        previewPublisher.publishAtomEvent(event) match {
          case Success(_)  =>
            log.info(s"New atom published to preview $atomId [${data.title}]")
            MediaAtom.fromThrift(atom)

          case Failure(err) =>
            log.error(s"Unable to published new atom to preview $atomId [${data.title}]", err)
            AtomPublishFailed(err.toString)
        }
      }
    )
  }
}
