package model

import com.gu.media.model.{SelfHostedAsset, VideoAsset, VideoSource, YouTubeAsset}
import com.gu.media.upload.model.Upload
import com.gu.media.youtube.YouTubeProcessingStatus
import org.cvogt.play.json.Jsonx
import play.api.libs.json.Format

case class ClientAsset(id: String, asset: Option[VideoAsset], processing: Option[ClientAssetProcessing])
case class ClientAssetProcessing(status: String, failed: Boolean, current: Option[Long], total: Option[Long])

object ClientAsset {
  implicit val format: Format[ClientAsset] = Jsonx.formatCaseClass[ClientAsset]

  def byVersion(assets: List[Asset], youTube: String => Option[YouTubeProcessingStatus]): List[ClientAsset] = {
    val versions = assets.map(_.version).distinct.sorted.reverse

    versions.flatMap { version =>
      ClientAsset(assets.filter(_.version == version), youTube)
    }
  }

  def apply(assets: List[Asset], getProcessingStatus: String => Option[YouTubeProcessingStatus]): Option[ClientAsset] = {
    assets.headOption.map { asset =>
      asset.platform match {
        case Platform.Url => selfHostedAsset(asset.version, assets)
        case Platform.Youtube => youTubeAsset(asset.version, asset.id, getProcessingStatus)
        case other => throw new IllegalArgumentException(s"Unsupported platform ${other.name}")
      }
    }
  }

  def apply(state: String, upload: Upload, error: Option[String]): ClientAsset = {
    if(upload.metadata.selfHost) {
      selfHostedUpload(upload.id, state, error)
    } else {
      youTubeUpload(upload, error)
    }
  }

  private def selfHostedAsset(version: Long, assets: List[Asset]): ClientAsset = {
    val sources = assets.collect {
      case Asset(_, _, id, _, Some(mimeType)) => VideoSource(id, mimeType)
    }

    ClientAsset(version.toString, asset = Some(SelfHostedAsset(sources)), processing = None)
  }

  private def selfHostedUpload(id: String, state: String, error: Option[String]): ClientAsset = {
    ClientAsset(id, asset = None, processing = Some(ClientAssetProcessing(
      status = error.getOrElse(state),
      failed = error.isDefined,
      current = None,
      total = None
    )))
  }

  private def youTubeAsset(version: Long, id: String, getProcessingStatus: String => Option[YouTubeProcessingStatus]): ClientAsset = {
    val processing = getProcessingStatus(id)
      .filterNot(_.status == "succeeded")
      .map(ClientAssetProcessing(_))

    val asset = processing match {
      case Some(_) => None
      case None => Some(YouTubeAsset(id))
    }

    ClientAsset(id = version.toString, asset, processing)
  }

  private def youTubeUpload(upload: Upload, error: Option[String]): ClientAsset = {
    val processing = error match {
      case Some(msg) =>
        ClientAssetProcessing(
          status = msg,
          failed = true,
          current = None,
          total = None
        )

      case None =>
        val fullyUploaded = upload.progress.fullyUploaded
        val current = upload.progress.chunksInYouTube
        val total = upload.parts.length

        ClientAssetProcessing(
          status = if(fullyUploaded) { "Uploading" } else { "Uploading to YouTube" },
          failed = false,
          current = if(fullyUploaded) { None } else { Some(current) },
          total = if(fullyUploaded) { None } else { Some(total) }
        )
    }

    ClientAsset(id = upload.id, asset = None, Some(processing))
  }
}

object ClientAssetProcessing {
  implicit val format: Format[ClientAssetProcessing] = Jsonx.formatCaseClass[ClientAssetProcessing]

  def apply(status: YouTubeProcessingStatus): ClientAssetProcessing = {
    ClientAssetProcessing(
      status = getStatusText(status),
      failed = status.failure.nonEmpty,
      current = if(status.processed == 0) { None } else { Some(status.processed) },
      total = if(status.total == 0) { None } else { Some(status.total) }
    )
  }

  private def getStatusText(status: YouTubeProcessingStatus): String = status match {
    case YouTubeProcessingStatus(_, "processing", _, _, 0, _) =>
      "YouTube Processing"

    case YouTubeProcessingStatus(_, "processing", _, _, timeLeftMs, _) =>
      s"YouTube Processing (${timeLeftMs / 1000}s left)"

    case _ =>
      status.failure.getOrElse(status.status)
  }
}
