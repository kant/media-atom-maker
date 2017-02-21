package controllers

import play.api.cache._
import com.gu.pandahmac.HMACAuthActions
import model.{YouTubeChannel, YouTubeVideoCategory}
import play.api.libs.json.Json
import play.api.mvc.Controller
import util.{YouTubeChannelsApi, YouTubeConfig, YouTubeVideoCategoryApi}

import scala.concurrent.duration._

class Youtube (val authActions: HMACAuthActions, youtubeConfig: YouTubeConfig, cache: CacheApi) extends Controller {
  import authActions.AuthAction

  def listCategories() = AuthAction {
    val categories = cache.getOrElse[List[YouTubeVideoCategory]]("categories", 1.hours) {
      YouTubeVideoCategoryApi(youtubeConfig).list}
    Ok(Json.toJson(categories))
  }

  def listChannels() = AuthAction {
    val channels = cache.getOrElse[List[YouTubeChannel]]("channels", 1.hours) {
      YouTubeChannelsApi(youtubeConfig).fetchMyChannels()}
    Ok(Json.toJson(channels))
  }
}
