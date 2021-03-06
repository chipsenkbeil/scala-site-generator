package org.senkbeil.grus.structures

import org.senkbeil.grus.Config.CommandGenerateOptions

import scala.util.Try

/**
 * Represents metadata for a page.
 *
 * @param layout The fully-qualified class name for the layout to use
 * @param usingDefaultLayout If true, indicates that the page is using the
 *                           default layout
 * @param weight A weight used for page ordering in menus and other structures
 * @param render Whether or not to render the page
 * @param title If not None, represents the title to associate with the page
 * @param link If not None, represents an alternative link for the page used
 *             in menus and other renderings
 * @param redirect If not None, represents the url the page will
 *                 use as the destination for redirection (ignoring any
 *                 other settings such as layout); does nothing if
 *                 render is false
 * @param fake If true, indicates that the page is fake and should not be
 *             rendered or used anywhere
 * @param other All other metadata properties that were provided that
 *              do not match reserved properties
 */
case class StandardMetadata(
  layout: String,
  usingDefaultLayout: Boolean,
  weight: Double,
  render: Boolean,
  title: Option[String],
  link: Option[String],
  redirect: Option[String],
  fake: Boolean,
  other: Map[String, Seq[String]]
) extends Metadata

object StandardMetadata {
  /**
   * Converts a map of keys and associated values into a metadata construct.
   *
   * @param generateOptions The global configuration used to fill in defaults
   * @param data The data to parse
   * @return The new metadata instance
   */
  def fromMap(
    generateOptions: CommandGenerateOptions,
    data: Map[String, Seq[String]]
  ): StandardMetadata = {
    val title = data.get("title").flatMap(_.headOption)
    val link = data.get("link").flatMap(_.headOption)
    val redirect = data.get("redirect").flatMap(_.headOption)
    val layout = data.get("layout").flatMap(_.headOption)
    val weight = data.get("weight").flatMap(_.headOption)
      .flatMap(w => Try(w.toDouble).toOption)
    val render = data.get("render").flatMap(_.headOption)
      .flatMap(r => Try(r.toBoolean).toOption)
    val fake = data.get("fake").flatMap(_.headOption)
      .flatMap(r => Try(r.toBoolean).toOption)

    StandardMetadata(
      layout = layout.getOrElse(generateOptions.defaultPageLayout()),
      usingDefaultLayout = layout.isEmpty,
      weight = weight.getOrElse(generateOptions.defaultPageWeight()),
      render = render.getOrElse(generateOptions.defaultPageRender()),
      title = title,
      link = link,
      redirect = redirect,
      fake = fake.getOrElse(generateOptions.defaultPageFake()),
      other = data.filterKeys(k => !Metadata.reservedKeys.contains(k))
    )
  }

  /**
   * Converts a Java map of keys and associated values into a
   * metadata construct.
   *
   * @param generateOptions The global configuration used to fill in defaults
   * @param data The data to parse
   * @return The new metadata instance
   */
  def fromJavaMap(
    generateOptions: CommandGenerateOptions,
    data: java.util.Map[String, java.util.List[String]]
  ): StandardMetadata = {
    import scala.collection.JavaConverters._

    val scalaData = data.asScala.mapValues(_.asScala.toSeq).toMap

    fromMap(generateOptions, scalaData)
  }
}
