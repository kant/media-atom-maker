package com.gu.media.upload

import org.cvogt.play.json.Jsonx
import play.api.libs.json.Format

case class UploadProgress(uploadedToS3: Long, uploadedToYouTube: Long)

object UploadProgress {
  implicit val format: Format[UploadProgress] = Jsonx.formatCaseClass[UploadProgress]
}
