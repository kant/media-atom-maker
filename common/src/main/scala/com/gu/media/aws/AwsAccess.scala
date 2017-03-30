package com.gu.media.aws

import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentialsProviderChain}
import com.amazonaws.regions.{Region, Regions}
import com.gu.media.Settings

trait AwsAccess { this: Settings =>
  def regionName: Option[String]
  def readTag(tag: String): Option[String]

  def instanceCredentials: AWSCredentialsProvider
  def localDevCredentials: Option[AWSCredentialsProvider]

  val credsProvider = new AWSCredentialsProviderChain(List(Some(instanceCredentials), localDevCredentials).flatten: _*)

  final def defaultRegion: Region = Region.getRegion(Regions.EU_WEST_1)
  final def region: Region = regionName
    .map { name => Region.getRegion(Regions.fromName(name)) }
    .getOrElse(defaultRegion)

  // These are injected as environment variables when running in a Lambda (unfortunately they cannot be tagged)
  final val stack: Option[String] = readTag("Stack")
  final val app: String = readTag("App").getOrElse("media-atom-maker")
  final val stage: String = readTag("Stage").getOrElse("DEV")
}
