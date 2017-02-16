import com.typesafe.sbt.SbtNativePackager.autoImport.maintainer
import com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.debianPackageDependencies
import sbt.Keys.organization

lazy val commonSettings = Seq(
  scalaVersion in ThisBuild := "2.11.8",
  organization in ThisBuild := "com.gu",

  resolvers += "Guardian Bintray" at "https://dl.bintray.com/guardian/editorial-tools",

  // silly SBT command to work-around lack of support for root projects that are not in the "root" folder
  // https://github.com/sbt/sbt/issues/2405
  onLoad in Global := (onLoad in Global).value andThen (Command.process("project root", _))
)

lazy val common = (project in file("common"))
  .settings(commonSettings,
    name := "media-atom-common",
    libraryDependencies ++= Dependencies.commonDependencies
  )

lazy val app = (project in file("."))
  .dependsOn(common)
  .enablePlugins(PlayScala, SbtWeb, BuildInfoPlugin, JDebPackaging)
  .settings(commonSettings,
    name := "media-atom-maker",
    libraryDependencies ++= Dependencies.appDependencies,

    aggregate in run := false,

    javaOptions in Universal ++= Seq(
      "-Dpidfile.path=/dev/null"
    ),

    buildInfoKeys := Seq[BuildInfoKey](
      name,
      BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
        "git rev-parse HEAD".!!.trim
      } catch {
        case e: Exception => "unknown"
      }))
    ),

    buildInfoPackage := "app",
    serverLoading in Debian := Systemd,

    debianPackageDependencies := Seq("openjdk-8-jre-headless"),
    maintainer := "Digital CMS <digitalcms.dev@guardian.co.uk>",
    packageSummary := "media-atom-maker",
    packageDescription := """making media atoms"""
  )

lazy val uploader = (project in file("uploader"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings,
    name := "media-atom-uploader",
    libraryDependencies ++= Dependencies.uploaderDependencies,

    topLevelDirectory in Universal := None,
    packageName in Universal := normalizedName.value
  )

lazy val root = (project in file("root"))
  .aggregate(common, app, uploader)
  .enablePlugins(RiffRaffArtifact)
  .settings(
    riffRaffBuildIdentifier := Option(System.getenv("CIRCLE_BUILD_NUM")).getOrElse("dev"),
    riffRaffManifestBranch := Option(System.getenv("CIRCLE_BRANCH")).getOrElse("dev"),
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := "media-service:media-atom-maker",
    riffRaffArtifactResources := Seq(
      (packageBin in Universal in app).value -> s"${(name in app).value}/${(packageBin in Universal in app).value.getName}",
      (packageBin in Universal in uploader).value -> s"${(name in uploader).value}/${(packageBin in Universal in uploader).value.getName}",
      (baseDirectory in Global in app).value / "conf/riff-raff.yaml" -> "riff-raff.yaml"
    )
  )
