package org.senkbeil.grus

import java.io.File
import java.net.URL
import java.nio.file.Paths

import coursier.MavenRepository
import org.rogach.scallop._
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version}
import org.senkbeil.grus.layouts.Page
import org.senkbeil.grus.Config.PreventConfigExit

object Config {
  private val DefaultLogLevel: Logger.Level.Level = Logger.defaultLevel
  private val DefaultStackTraceDepth: Int = 10

  /** Aids in converting to log level from string. */
  private implicit val LogLevelConverter: ValueConverter[Logger.Level.Level] =
    singleArgConverter[Logger.Level.Level](n =>
      Logger.Level.withName(n.trim.toLowerCase.capitalize), {
      case _: Throwable => Left(
        "Choices are " +
          Logger.Level.values.mkString(", ")
      )
    })

  /** Aids in converting to Maven theme from string. */
  private implicit val MavenThemeConverter: ValueConverter[List[MavenTheme]] =
    listArgConverter[MavenTheme](s => {
      val tokens = s.split(",")
      if (tokens.length != 3)
        throw new Exception("Theme format is <ORG>,<ARTIFACT>,<VERSION>")

      MavenTheme(tokens(0), tokens(1), tokens(2))
    })

  /** Aids in converting to jar theme from string. */
  private implicit val LocalJarThemeConverter: ValueConverter[List[LocalJarTheme]] =
    listArgConverter[LocalJarTheme](s => LocalJarTheme(new File(s)))

  /** Aids in converting to class dir theme from string. */
  private implicit val LocalClassDirThemeConverter: ValueConverter[List[LocalClassDirTheme]] =
    listArgConverter[LocalClassDirTheme](s => LocalClassDirTheme(new File(s)))

  /** Aids in converting to sbt project theme from string. */
  private implicit val LocalSbtProjectThemeConverter: ValueConverter[List[LocalSbtProjectTheme]] =
    listArgConverter[LocalSbtProjectTheme](s => LocalSbtProjectTheme(new File(s)))

  /** Aids in converting to Maven repository from string. */
  private implicit val MavenRepositoryConverter: ValueConverter[List[MavenRepository]] =
    listArgConverter[MavenRepository](s => MavenRepository(s))

  // ===========================================================================
  // = IMPLICITS
  // ===========================================================================

  object Implicits {
    /** Represents an implicit wrapper of a Config instance. */
    implicit class ConfigWrapper(config: Config) {
      /**
       * Represents the consolidated log level of the configuration.
       *
       * @return The log level associated with the active subcommand
       */
      def logLevel(): Logger.Level.Level = {
        if (config.usingGenerateCommand) config.generate.logLevel()
        else if (config.usingServeCommand) config.serve.logLevel()
        else if (config.usingPublishCommand) config.publish.logLevel()
        else if (config.usingSkeletonCommand) config.skeleton.logLevel()
        else DefaultLogLevel
      }

      /**
       * Represents the consolidated stack trace depth of the configuration.
       *
       * @return The depth of the stack trace associated with the active subcommand
       */
      def stackTraceDepth(): Int = {
        if (config.usingGenerateCommand) config.generate.stackTraceDepth()
        else if (config.usingServeCommand) config.serve.stackTraceDepth()
        else if (config.usingPublishCommand) config.publish.stackTraceDepth()
        else if (config.usingSkeletonCommand) config.skeleton.stackTraceDepth()
        else DefaultStackTraceDepth
      }
    }
  }

  // ===========================================================================
  // = COMMON SETTINGS
  // ===========================================================================

  /** Represents common options across commands. */
  trait CommandCommonOptions extends ScallopConfBase with PreventConfigExit {
    /** Represents the fully-qualified class name of the default layout. */
    val logLevel: ScallopOption[Logger.Level.Level] =
      opt[Logger.Level.Level](
        descr = Seq(
          "The lowest level of logging to print:",
          Logger.Level.values.mkString(", ")
        ).mkString(" "),
        default = Some(DefaultLogLevel),
        argName = "level"
      )

    /** Represents the maximum stack trace to print out when errors occur. */
    val stackTraceDepth: ScallopOption[Int] = opt[Int](
      descr = Seq(
        "The maximum depth of the stack trace to print out for errors",
        "(less than zero uses full stack)"
      ).mkString(" "),
      default = Some(DefaultStackTraceDepth),
      argName = "depth"
    )

    appendDefaultToDescription = true
    shortSubcommandsHelp(true)
  }

  /** Mixin to prevent sys.exit(0) when printing help/version info. */
  trait PreventConfigExit extends ScallopConfBase {
    private var quickExit: Boolean = false
    private var errorExit: Boolean = false

    // Override message handler to not system exit on error
    errorMessageHandler = { message =>
      if (overrideColorOutput.value.getOrElse(System.console() != null)) {
        Console.err.println(
          "[\u001b[31m%s\u001b[0m] Error: %s".format(printedName, message)
        )
      } else {
        // no colors on output
        Console.err.println("[%s] Error: %s" format (printedName, message))
      }
    }

    /** Returns whether or not an exit should occur. */
    def shouldExit: Boolean = {
      quickExit || errorExit || (isRootConfig && subcommands.collect {
        case c: PreventConfigExit => c
      }.exists(_.shouldExit))
    }

    override protected def onError(e: Throwable): Unit = e match {
      case r: ScallopResult if !throwError.value => r match {
        case Help("") =>
          builder.printHelp
          quickExit = true
        case Help(subname) =>
          builder.findSubbuilder(subname).get.printHelp
          quickExit = true
        case Version =>
          builder.vers.foreach(println)
          quickExit = true
        case ScallopException(message) =>
          errorMessageHandler(message)
          errorExit = true
      }
      case e: Throwable => throw e
    }
  }

  // ===========================================================================
  // = SKELETON SETTINGS
  // ===========================================================================

  trait CommandSkeletonOptions extends CommandCommonOptions {
    /** Represents the project directory of skeleton content. */
    val projectDir: ScallopOption[String] = opt[String](
      descr = "The directory where skeleton content is created",
      argName = "dir",
      default = Some(".")
    )

    /** Represents whether or not a skeleton is generated for a theme. */
    val forTheme: ScallopOption[Boolean] = opt[Boolean](
      descr = "If provided, indicates generating theme instead of website",
      default = Some(false)
    )

    /** Represents a flag to NOT clear the project directory. */
    val doNotClearProjectDir: ScallopOption[Boolean] = opt[Boolean](
      descr = "If provided, will not clear the project directory",
      default = Some(false)
    )
  }

  // ===========================================================================
  // = GENERATE SETTINGS
  // ===========================================================================

  trait CommandGenerateOptions extends CommandCommonOptions {
    /** Represents the fully-qualified class name of the default layout. */
    val defaultPageLayout: ScallopOption[String] = opt[String](
      descr = "The class representing the default layout if one is not specified",
      argName = "layout",
      default = Some(classOf[Page].getName)
    )

    /** Represents the weight for a page if not specified. */
    val defaultPageWeight: ScallopOption[Double] = opt[Double](
      descr = "The weight for a page if one is not specified",
      argName = "weight",
      default = Some(0)
    )

    /** Represents whether or not to render a page if not specified. */
    val defaultPageRender: ScallopOption[Boolean] = opt[Boolean](
      name = "ignore-implicit-page-render",
      descr = Seq(
        "If provided, the page will not be rendered if not specified in metadata,",
        "but the page will still appear in menus"
      ).mkString(" "),
      default = Some(true)
    )

    /** Represents whether or not a page is fake if not specified. */
    val defaultPageFake: ScallopOption[Boolean] = opt[Boolean](
      name = "implicit-page-fake",
      descr = "If true, the page will be fake if not specified in metadata",
      default = Some(false)
    )

    /** Represents a flag to NOT generate a .nojekyll file. */
    val doNotGenerateNoJekyllFile: ScallopOption[Boolean] = opt[Boolean](
      descr = "If provided, will not generate a .nojekyll file in the output",
      default = Some(false)
    )

    /** Represents a flag to NOT generate a sitemap.xml file. */
    val doNotGenerateSitemapFile: ScallopOption[Boolean] = opt[Boolean](
      descr = "If provided, will not generate a sitemap.xml file in the output",
      default = Some(false)
    )

    /** Represents the site's hostname. */
    val siteHost: ScallopOption[URL] = opt[URL](
      descr = Seq(
        "Represents the host used when generating content such as",
        "http://www.example.com"
      ).mkString(" "),
      argName = "url",
      default = Some(new URL("http://localhost/"))
    )

    /** Represents the output directory of generated content. */
    val outputDir: ScallopOption[String] = opt[String](
      descr = "The output directory where content is generated and served",
      argName = "dir",
      default = Some("out")
    )

    /** Represents the input directory of static and source content. */
    val inputDir: ScallopOption[String] = opt[String](
      descr = "The root input directory where source and static content is found",
      argName = "dir",
      default = Some("site")
    )

    /** Represents the directory of source content. */
    val srcDir: ScallopOption[String] = opt[String](
      descr = "The source directory (relative to input directory) where source content is found",
      argName = "dir",
      default = Some("src")
    )

    /** Represents the directory of static content. */
    val staticDir: ScallopOption[String] = opt[String](
      descr = "The static directory (relative to input directory) where static content is found",
      argName = "dir",
      default = Some("static")
    )

    /** Represents custom theme(s) to load from Maven into the generator. */
    val mavenThemes: ScallopOption[List[MavenTheme]] = opt[List[MavenTheme]](
      name = "maven-theme",
      descr = "The theme(s) to load from Maven-oriented repos in the form of 'ORG,ARTIFACT,VERSION'",
      argName = "theme",
      default = Some(Nil)
    )

    /** Represents custom theme(s) to load from jars into the generator. */
    val jarThemes: ScallopOption[List[LocalJarTheme]] = opt[List[LocalJarTheme]](
      name = "jar-theme",
      descr = "The theme(s) to load from local jars",
      argName = "jar",
      default = Some(Nil)
    )

    /** Represents custom theme(s) to load from class dirs into the generator. */
    val classDirThemes: ScallopOption[List[LocalClassDirTheme]] =
      opt[List[LocalClassDirTheme]](
        name = "class-dir-theme",
        descr = "The theme(s) to load from local directories of class files",
        argName = "dir",
        default = Some(Nil)
      )

    /** Represents custom theme(s) to load from sbt projects into the generator. */
    val sbtProjectThemes: ScallopOption[List[LocalSbtProjectTheme]] =
      opt[List[LocalSbtProjectTheme]](
        name = "sbt-project-theme",
        descr = "The theme(s) to load from class dirs of sbt projects",
        argName = "dir",
        default = Some(Nil)
      )

    /** Represents custom theme(s) to load from Maven into the generator. */
    val mavenThemeRepos: ScallopOption[List[MavenRepository]] =
      opt[List[MavenRepository]](
        name = "maven-theme-repository",
        descr = "The repositories to use when looking for Maven themes",
        argName = "url",
        default = Some(List(MavenRepository("https://repo1.maven.org/maven2")))
      )
  }

  // ===========================================================================
  // = SERVE SETTINGS
  // ===========================================================================

  trait CommandServeOptions extends CommandGenerateOptions {
    /** Represents the port used when serving content. */
    val port: ScallopOption[Int] = opt[Int](
      descr = "The port to use when serving files",
      argName = "port",
      default = Some(8080)
    )

    /** Represents whether or not to live regen site when changed. */
    val liveReload: ScallopOption[Boolean] = opt[Boolean](
      descr = "If specified, re-generates files while being served",
      default = Some(true)
    )

    /** Represents the time in milliseconds to wait after first change. */
    val liveReloadWaitTime: ScallopOption[Long] = opt[Long](
      descr = Seq(
        "The number of milliseconds to wait after detecting a change",
        "before performing a live reload"
      ).mkString(" "),
      argName = "millis",
      default = Some(500)
    )

    /** Represents whether or not to allow unknown MIME types. */
    val allowUnsupportedMediaTypes: ScallopOption[Boolean] = opt[Boolean](
      descr = Seq(
        "If specified, files with unknown MIME types will be served,",
        "but without a Content-Type header"
      ).mkString(" "),
      default = Some(false)
    )

    /**
      * Represents files that serve as defaults when accessing a directory.
      *
      * E.g. '/my/path/' becomes '/my/path/index.html'
      */
    val indexFiles: ScallopOption[List[String]] = opt[List[String]](
      descr = "Files that serve as defaults when accessing a directory",
      argName = "file",
      default = Some(List("index.html", "index.htm"))
    )

    /** Represents whether or not to generate site on server start. */
    val generateOnStart: ScallopOption[Boolean] = opt[Boolean](
      descr = "If specified, regenerates the site when the server starts",
      default = Some(false)
    )
  }

  // ===========================================================================
  // = PUBLISH SETTINGS
  // ===========================================================================

  trait CommandPublishOptions extends CommandCommonOptions {
    val remoteName: ScallopOption[String] = opt[String](
      descr = "The remote name used as the destination of the publish",
      argName = "name",
      default = Some("origin")
    )

    val remoteBranch: ScallopOption[String] = opt[String](
      descr = "The branch to publish the content to",
      argName = "branch",
      default = Some("gh-pages")
    )

    val cacheDir: ScallopOption[String] = opt[String](
      descr = "The directory where a copy of the repository lives for publishing",
      default = Some(Paths.get(
        System.getProperty(
          "user.home",
          System.getProperty("java.io.tmpdir")
        ),
        ".scala-site-gen"
      ).toAbsolutePath.toString),
      argName = "dir"
    )

    val outputDir: ScallopOption[String] = opt[String](
      descr = "The output directory containing the site to publish",
      argName = "dir",
      default = Some("out")
    )

    val forceCopy: ScallopOption[Boolean] = opt[Boolean](
      descr = "If provided, forces the copying of the repository to the cache",
      default = Some(false)
    )

    val authorName: ScallopOption[String] = opt[String](
      descr = "The name to use for both author and committer when publishing",
      default = None,
      argName = "name"
    )

    val authorEmail: ScallopOption[String] = opt[String](
      descr = "The email to use for both author and committer when publishing",
      default = None,
      argName = "email"
    )
  }
}

/**
 * Represents the CLI configuration for the site generator tool.
 *
 * @param arguments The list of arguments fed into the CLI (same
 *                  arguments that are fed into the main method)
 */
class Config(arguments: Seq[String])
  extends ScallopConf(arguments) with PreventConfigExit {
  // ===========================================================================
  // = COMMON OPTIONS AND SETTINGS
  // ===========================================================================
  import Config._

  // ===========================================================================
  // = SKELETON SETTINGS
  // ===========================================================================

  /** Represents the command to generate a new theme or website. */
  val skeleton = new Subcommand("skeleton") with CommandSkeletonOptions {
    descr("Produces an initial theme or website to flesh out")
  }
  addSubcommand(skeleton)

  def usingSkeletonCommand: Boolean = subcommand.exists(_ == skeleton)

  // ===========================================================================
  // = GENERATE SETTINGS
  // ===========================================================================

  /** Represents the command to regenerate the site. */
  val generate = new Subcommand("generate") with CommandGenerateOptions {
    descr("Generates the site")
  }
  addSubcommand(generate)

  def usingGenerateCommand: Boolean = subcommand.exists(_ == generate)

  // ===========================================================================
  // = SERVE SETTINGS
  // ===========================================================================

  /** Represents the command to serve the site using a local server. */
  val serve = new Subcommand("serve") with CommandServeOptions {
    descr("Serves the site using a local server")
  }
  addSubcommand(serve)

  def usingServeCommand: Boolean = subcommand.exists(_ == serve)

  // ===========================================================================
  // = PUBLISH SETTINGS
  // ===========================================================================

  /** Represents the command to publish the built site. */
  val publish = new Subcommand("publish") with CommandPublishOptions {
    descr("Publishes the site to Github Pages")
  }
  addSubcommand(publish)

  def usingPublishCommand: Boolean = subcommand.exists(_ == publish)

  // ===========================================================================
  // = INITIALIZATION
  // ===========================================================================

  // Display our default values in our help menu
  appendDefaultToDescription = true

  // Enable displaying subcommands in help
  shortSubcommandsHelp(true)

  // Mark version of tool
  version(BuildInfo.version)

  // Process arguments
  verify()
}

