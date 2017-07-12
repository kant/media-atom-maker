package com.gu.media

import com.google.api.services.youtube.model.{Channel, Video, VideoCategory}
import com.google.api.services.youtubePartner.model.VideoAdvertisingOption
import com.gu.media.util.ISO8601Duration
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.JavaConverters._
import org.joda.time.DateTime
import com.gu.media.util.JsonDate._

package object youtube {
  case class YouTubeVideoCategory(id: Int, title: String)

  object YouTubeVideoCategory {
    implicit val reads: Reads[YouTubeVideoCategory] = Json.reads[YouTubeVideoCategory]
    implicit val writes: Writes[YouTubeVideoCategory] = Json.writes[YouTubeVideoCategory]

    def build(category: VideoCategory): YouTubeVideoCategory = {
      YouTubeVideoCategory(category.getId.toInt, category.getSnippet.getTitle)
    }
  }

  case class YouTubeAdFormat(format: String, description: String)

  object YouTubeAdFormat {
    implicit val reads: Reads[YouTubeAdFormat] = Json.reads[YouTubeAdFormat]
    implicit val writes: Writes[YouTubeAdFormat] = Json.writes[YouTubeAdFormat]

    def build(format: String): YouTubeAdFormat = {
      // definitions lifted from https://developers.google.com/youtube/partner/docs/v1/videoAdvertisingOptions#adFormats[]
      val description = format match {
        case "display"              => """Appears to the right of the feature video and above the video suggestions list
                                      |on www.youtube.com. Not shown on O&O platforms."""
        case "long"                 => """Long video ads which would be enabled to run for videos that can show standard
                                      |in-stream ads. Requires standard_instream ads to be enabled."""
        case "overlay"              => """An ad that is displayed in the video player during video playback.
                                      |Overlay settings also govern YouTube's ability to display ads in a
                                      |companion ad slot on the page."""
        case "product_listing"      => """Ads that feature products related to or featured in the video content.
                                      |These ads are sponsored cards that display during the video.
                                      |The cards are added automatically by the ad system.
                                      |Viewers see a teaser for the card for a few seconds and can also click the icon
                                      |in the top right corner of the video to browse the video's cards."""
        case "standard_instream"    => """Non-skippable video ads that play before during, or after the video content.
                                      |While playing, in-stream ads are the only content displayed in the player."""
        case "third_party"          => "Advertisements are served by a third-party ad server."
        case "trueview_inslate"     => """The user has an option to choose one of several ads to run.
                                      |Requires standard_instream ads to be enabled."""
        case "trueview_instream"    => """Skippable video ads that run before video playback begins.
                                      |Advertisers are charged only when a viewer has watched the ad for an agreed
                                      |length of time or until it ends whichever comes first."""
        case _                      => "Unknown... Make. It. Rain."
      }

      YouTubeAdFormat(format = format, description = description.stripMargin.replaceAll("\n", " "))
    }
  }

  case class YouTubeChannel(id: String, title: String)

  object YouTubeChannel {
    implicit val reads: Reads[YouTubeChannel] = Json.reads[YouTubeChannel]
    implicit val writes: Writes[YouTubeChannel] = Json.writes[YouTubeChannel]

    def build(channel: Channel): YouTubeChannel = {
      YouTubeChannel(
        id = channel.getId,
        title = channel.getSnippet.getTitle
      )
    }
  }

  case class YouTubeVideo (
     id: String,
     title: String,
     duration: Long,
     publishedAt: DateTime,
     privacyStatus: String,
     tags: Seq[String],
     contentBundleTags: Seq[String],
     channel: YouTubeChannel
  )

  object YouTubeVideo {
    implicit val reads: Reads[YouTubeVideo] = Json.reads[YouTubeVideo]
    implicit val writes: Writes[YouTubeVideo] = Json.writes[YouTubeVideo]

    def build(video: Video): YouTubeVideo = {
      val tags: Seq[String] = video.getSnippet.getTags match {
        case null => List()
        case t => t.asScala
      }

      YouTubeVideo(
        id = video.getId,
        title = video.getSnippet.getTitle,
        duration = ISO8601Duration.toSeconds(video.getContentDetails.getDuration),
        publishedAt = new DateTime(video.getSnippet.getPublishedAt.toString),
        privacyStatus = video.getStatus.getPrivacyStatus,
        tags = tags,
        contentBundleTags = tags.filter(t => t.startsWith("gdnpfp")),
        channel = YouTubeChannel(id = video.getSnippet.getChannelId, title = video.getSnippet.getChannelTitle)
      )
    }
  }

  case class YouTubeAdvertising(id: String, adFormats: Seq[YouTubeAdFormat], adBreaks: Seq[String])

  object YouTubeAdvertising {
    implicit val reads: Reads[YouTubeAdvertising] = Json.reads[YouTubeAdvertising]
    implicit val writes: Writes[YouTubeAdvertising] = Json.writes[YouTubeAdvertising]

    def build(videoAdvertisingOption: VideoAdvertisingOption): YouTubeAdvertising = {
      YouTubeAdvertising(
        id = videoAdvertisingOption.getId,
        adFormats = videoAdvertisingOption.getAdFormats.asScala.toList.map(YouTubeAdFormat.build),
        adBreaks = videoAdvertisingOption.getAdBreaks.asScala.toList.map(_.getPosition)
      )
    }
  }

  case class YouTubeVideoCommercialInfo (video: YouTubeVideo, advertising: YouTubeAdvertising)

  object YouTubeVideoCommercialInfo {
    implicit val reads: Reads[YouTubeVideoCommercialInfo] = Json.reads[YouTubeVideoCommercialInfo]
    implicit val writes: Writes[YouTubeVideoCommercialInfo] = Json.writes[YouTubeVideoCommercialInfo]

    def build(video: Video, videoAdvertisingOption: VideoAdvertisingOption): YouTubeVideoCommercialInfo = {
      YouTubeVideoCommercialInfo (
        video = YouTubeVideo.build(video),
        advertising = YouTubeAdvertising.build(videoAdvertisingOption)
      )
    }
  }

  case class YouTubeMetadataUpdate(
    title: Option[String],
    categoryId: Option[String],
    description: Option[String],
    tags: List[String],
    license: Option[String],
    privacyStatus: Option[String]
  ) {
    def withSaneTitle(): YouTubeMetadataUpdate = {
      // Editorial add "- video" for on platform SEO, but it isn't needed on a YouTube video title as its a video platform
      val cleanTitle = this.title.map(_.replaceAll(" (-|–) video( .*)?$", ""))
      this.copy(title = cleanTitle)
    }

    def withContentBundleTags(): YouTubeMetadataUpdate = {
      val contentBundledTags = getContentBundlingTags()
      this.copy(tags = contentBundledTags)
    }

    private def getContentBundlingTags(): List[String] = {
      val contentBundlingMap: Map[String, String] = Map (
        "uk" -> "gdnpfpnewsuk",
        "us" -> "gdnpfpnewsus",
        "au" -> "gdnpfpnewsau",
        "world" -> "gdnpfpnewsworld",
        "politics" -> "gdnpfpnewspolitics",
        "opinion" -> "gdnpfpnewsopinion",
        "football" -> "gdnpfpsportfootball",
        "cricket" -> "gdnpfpsportcricket",
        "rugby union" -> "gdnpfpsportrugbyunion",
        "rugby league" -> "gdnpfpsportrugbyleague",
        "f1" -> "gdnpfpsportf1",
        "tennis" -> "gdnpfpsporttennis",
        "golf" -> "gdnpfpsportgolf",
        "cycling" -> "gdnpfpsportcycling",
        "boxing" -> "gdnpfpsportboxing",
        "racing" -> "gdnpfpsportracing",
        "us sports" -> "gdnpfpsportus",
        "other sport" -> "gdnpfpsportother",
        "other sports" -> "gdnpfpsportother",
        "culture" -> "gdnpfpculture",
        "film" -> "gdnpfpculturefilm",
        "music" -> "gdnpfpculturemusic",
        "lifestyle" -> "gdnpfplifestyle",
        "food" -> "gdnpfplifestylefood",
        "health & fitness" -> "gdnpfplifestylehealthfitness",
        "business" -> "gdnpfpbusiness",
        "money" -> "gdnpfpmoney",
        "fashion" -> "gdnpfpfashion",
        "environment" -> "gdnpfpenvironment",
        "technology" -> "gdnpfptechnology",
        "travel" -> "gdnpfptravel",
        "science" -> "gdnpfpscience",
        "athletics" -> "gdnpfpsportother",
        "basketball" -> "gdnpfpsportus",
        "sport 2.0" -> "gdnpfpsport20"
      )

      this.tags.flatMap { tag =>
        contentBundlingMap.get(tag.toLowerCase()) match {
          case Some(contentBundleTag) => List(tag, contentBundleTag)
          case None => List(tag)
        }
      }
    }
  }

  object YouTubeMetadataUpdate {
    def prettyToString(metadata: YouTubeMetadataUpdate): String = {
      Map(
        "title" -> metadata.title,
        "description" -> metadata.description,
        "tags" -> metadata.tags,
        "categoryId" -> metadata.categoryId,
        "license" -> metadata.license,
        "privacyStatus" -> metadata.privacyStatus.map(_.toString)
      ).collect {
        case (key, Some(value)) =>
          s"\t$key=$value"
      }.mkString("\n")
    }
  }
}
