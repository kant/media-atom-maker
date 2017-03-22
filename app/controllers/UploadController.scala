package controllers

import java.util.UUID

import com.gu.media.logging.Logging
import com.gu.media.upload._
import com.gu.media.upload.actions.{CopyParts, DeleteParts, UploadActionSender, UploadPartToYouTube}
import com.gu.media.youtube.{YouTubeAccess, YouTubeUploader}
import com.gu.pandahmac.HMACAuthActions
import com.gu.pandomainauth.action.UserRequest
import com.gu.pandomainauth.model.User
import controllers.UploadController.CreateRequest
import data.{DataStores, UnpackedDataStores}
import model.MediaAtom
import model.commands.CommandExceptions.AtomMissingYouTubeChannel
import org.cvogt.play.json.Jsonx
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Controller, Result}
import util.AWSConfig

class UploadController(val authActions: HMACAuthActions, awsConfig: AWSConfig, youTube: YouTubeAccess,
                       uploadActions: UploadActionSender, override val stores: DataStores)

  extends Controller with Logging with JsonRequestParsing with UnpackedDataStores {

  import authActions.APIHMACAuthAction

  private val UPLOAD_KEY_HEADER = "X-Upload-Key"
  private val table = stores.uploadStore
  private val credsGenerator = new CredentialsGenerator(awsConfig)
  private val uploader = new YouTubeUploader(awsConfig, youTube)

  def list(atomId: String) = APIHMACAuthAction {
    val uploads = table.list(atomId)
    Ok(Json.toJson(uploads))
  }

  def create = APIHMACAuthAction { implicit raw =>
    parse(raw) { req: CreateRequest =>
      log.info(s"Request for upload under atom ${req.atomId}. filename=${req.filename}. size=${req.size}")

      val atom = MediaAtom.fromThrift(getPreviewAtom(req.atomId))
      val upload = buildUpload(atom, raw.user, req.size)
      table.put(upload)

      Ok(Json.toJson(upload))
    }
  }

  def delete(id: String) = APIHMACAuthAction {
    table.delete(id)
    NoContent
  }

  def credentials(id: String) = APIHMACAuthAction { implicit req =>
    partRequest(id, req) { (upload, part) =>
      val credentials = credsGenerator.forKey(upload.id, part.key)
      Ok(Json.toJson(credentials))
    }
  }

  def complete(id: String) = APIHMACAuthAction { implicit req =>
    partRequest(id, req) { (upload, part) =>
      partComplete(upload, part)
      NoContent
    }
  }

  private def buildUpload(atom: MediaAtom, user: User, size: Long) = {
    val id = UUID.randomUUID().toString

    val plutoData = PlutoSyncMetadata(
      projectId = atom.plutoProjectId,
      key = CompleteUploadKey(awsConfig.userUploadFolder, id).toString,
      assetVersion = -1
    )

    val metadata = UploadMetadata(
      atomId = atom.id,
      user = user.email,
      bucket = awsConfig.userUploadBucket,
      region = awsConfig.region.getName,
      title = atom.title,
      pluto = plutoData
    )

    val youTube = YouTubeMetadata(
      channel = atom.channelId.getOrElse { AtomMissingYouTubeChannel },
      upload = None
    )

    val parts = chunk(id, size)

    Upload(id, parts, metadata, youTube)
  }

  private def partRequest(id: String, request: UserRequest[_])(fn: (Upload, UploadPart) => Result): Result = {
    table.get(id) match {
      case Some(upload) =>
        request.headers.get(UPLOAD_KEY_HEADER) match {
          case Some(key) =>
            upload.parts.find(_.key == key) match {
              case Some(part) =>
                fn(upload, part)

              case None =>
                BadRequest(s"Unknown part key $key")
            }

          case None =>
            BadRequest(s"Missing header $UPLOAD_KEY_HEADER")
        }

      case None =>
        BadRequest(s"Unknown upload id $id")
    }
  }

  private def partComplete(upload: Upload, part: UploadPart): Upload = {
    val uploadUri = upload.youTube.upload.getOrElse {
      uploader.startUpload(upload.metadata.title, upload.youTube.channel, upload.id, upload.parts.last.end)
    }

    val complete = upload
      .withPart(part.key)(_.copy(uploadedToS3 = true))
      .copy(youTube = upload.youTube.copy(upload = Some(uploadUri)))

    table.put(complete)
    uploadActions.send(UploadPartToYouTube(upload.id, part.key))

    if(complete.parts.forall(_.uploadedToS3)) {
      val completeKey = CompleteUploadKey(awsConfig.userUploadFolder, complete.id).toString

      uploadActions.send(CopyParts(upload.id, completeKey))
      uploadActions.send(DeleteParts(upload.id))
    }

    complete
  }

  private def chunk(uploadId: String, size: Long): List[UploadPart] = {
    val boundaries = Upload.calculateChunks(size)

    boundaries.zipWithIndex.map { case ((start, end), id) =>
      UploadPart(UploadPartKey(awsConfig.userUploadFolder, uploadId, id).toString, start, end)
    }
  }
}

object UploadController {
  case class CreateRequest(atomId: String, filename: String, size: Long)
  case class CreateResponse(id: String, region: String, bucket: String, parts: List[UploadPart])

  implicit val createRequestFormat: Format[CreateRequest] = Jsonx.formatCaseClass[CreateRequest]
  implicit val createResponseFormat: Format[CreateResponse] = Jsonx.formatCaseClass[CreateResponse]
}
