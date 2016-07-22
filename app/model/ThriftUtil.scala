package model

import java.net.URI
import java.util.UUID.randomUUID

import com.gu.contentatom.thrift._
import com.gu.contentatom.thrift.atom.media._
import play.api.mvc.{BodyParser, BodyParsers}
import util.atom.MediaAtomImplicits._

import scala.concurrent.ExecutionContext
import scala.util.Try

object ThriftUtil {
  type ThriftResult[A] = Either[String, A]

  val youtube = "https?://www.youtube.com/watch\\?v=([^&]+)".r

  def getSingleParam(params: Map[String, Seq[String]], name: String): Option[String] =
    params.get(name).flatMap(_.headOption)

  def parsePlatform(uri: String): ThriftResult[Platform] =
    uri match {
      case youtube(_) => Right(Platform.Youtube)
      case _ => Left(s"Unrecognised platform in uri ($uri)")
    }

  def parseId(uri: String): ThriftResult[String] =
    uri match {
      case youtube(id) => Right(id)
      case Url(url) => Right(url)
      case _ => Left(s"couldn't extract id from uri ($uri)")
    }

  def parseAsset(uri: String, version: Long): ThriftResult[Asset] =
    for {
      id <- parseId(uri).right
      platform <- parsePlatform(uri).right
    } yield Asset(
      id = id,
      assetType = AssetType.Video,
      version = version,
      platform = platform
    )

  def parseAssets(uris: Seq[String], version: Long): ThriftResult[List[Asset]] =
    uris.foldLeft(Right(Nil): ThriftResult[List[Asset]]) { (assetsEither, uri) =>
      for {
        assets <- assetsEither.right
        asset <- parseAsset(uri, version).right
      } yield {
        asset :: assets
      }
    }

  def parseMediaAtom(params: Map[String, Seq[String]]): ThriftResult[MediaAtom] = {
    val version = params.get("version").map(_.head.toLong).getOrElse(1L)
    val title = params.get("title").map(_.head) getOrElse "unknown"
    val category = params.get("category").map(_.head) match {
      case Some("documentary") => Category.Documentary
      case Some("explainer") => Category.Explainer
      case Some("feature") => Category.Feature
      case Some("hosted") => Category.Hosted
      case _ => Category.News
    }
    val duration = params.get("duration").map(_.head.toLong)
    val source = params.get("source").map(_.head)
    for {
      assets <- parseAssets(
        params.get("uri").getOrElse(Nil),
        version
      ).right
    } yield MediaAtom(
      assets = assets,
      activeVersion = Some(version),
      title,
      category,
      plutoProjectId = None,
      duration,
      source
    )
  }

  def parseRequest(params: Map[String, Seq[String]]): ThriftResult[Atom] = {
    val id = getSingleParam(params,"id").getOrElse(randomUUID().toString)

    for(mediaAtom <- parseMediaAtom(params).right) yield {
      Atom(
        id = id,
        atomType = AtomType.Media,
        labels = Nil,
        defaultHtml = "",
        data = AtomData.Media(mediaAtom),
        contentChangeDetails = ContentChangeDetails(
          None, None, None, 1L
        )
      ).updateDefaultHtml
    }
  }

  def getSingleRequiredParam(params: Map[String, Seq[String]], name: String): ThriftResult[String] =
    getSingleParam(params, name).toRight(s"Missing param ${name}")

  def atomBodyParser(implicit ec: ExecutionContext): BodyParser[ThriftResult[Atom]] =
    BodyParsers.parse.urlFormEncoded map { urlParams =>
      parseRequest(urlParams)
    }

  def assetBodyParser(implicit ec: ExecutionContext): BodyParser[ThriftResult[Asset]] =
    BodyParsers.parse.urlFormEncoded map { urlParams =>
      for {
        uri <- getSingleRequiredParam(urlParams, "uri").right
        version <- getSingleRequiredParam(urlParams, "version").right
        asset <- parseAsset(uri, version.toLong).right
      } yield asset
    }
}

object Url {

  def unapply(s: String): Option[String] = {
    Try(new URI(s)).filter { uri =>
      uri.isAbsolute && uri.getScheme == "https"
    }.map(_.toASCIIString).toOption
  }
}
