import java.time.LocalDate
import java.util.stream.Collectors

import ColumnNames.Input._
import ColumnNames.diffPrevClosing
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.SparkConf
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DoubleType, IntegerType}
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.rogach.scallop.ScallopConf
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object StockAggregator {

  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {

    object CommandLineArgs extends ScallopConf(args) {
      val isin = opt[String](name = "ISIN", required = true)
      val outputPath = opt[String](name = "output", default = Some("deutsche-boerse-xetra-pds/processed/"))
      val replace = opt[Boolean](name = "replace")
      val sparkProperties = propsLong[String]("spark")
      verify()
    }

    logger.debug(CommandLineArgs.summary)

    implicit val sparkSession: SparkSession = {
      val sparkConf = new SparkConf().setAll(CommandLineArgs.sparkProperties)
      SparkSession.builder.config(sparkConf).getOrCreate()
    }

    import sparkSession.implicits._

    val isin = CommandLineArgs.isin()
    val outputPath = CommandLineArgs.outputPath()
    val configFileProperties = ConfigFileProperties()

    val inputPaths = buildInputPaths(
      prefix = configFileProperties.inputPath,
      startDate = LocalDate.parse(configFileProperties.startDate)
    )

    val dailyWindow = Window.orderBy(Date)

    sparkSession
      .read
      .option("header", true)
      .option("inferSchema", false) // Speeds up reading
      .csv(inputPaths: _*)
      .filter(col(ISIN) === isin)
      .select(
        col(Date),
        col(Time),
        col(StartPrice),
        col(EndPrice).cast(DoubleType),
        col(TradedVolume).cast(IntegerType)
      )
      .as[TradeActivity]
      .groupByKey(_.Date)
      .flatMapGroups { (date, it) =>
        val merged = it.foldLeft(Option.empty[MergedTradeActivity]) { (agg, next) =>
          agg match {
            case None => Some(MergedTradeActivity(next))
            case Some(mTA) => Some(mTA.mergeWith(next))
          }
        }
        merged.map(mTA => TradeActivity(mTA, date))
      }
      .withColumn(diffPrevClosing, col(EndPrice) / lag(EndPrice, 1).over(dailyWindow) - 1)
      .select(
        lit(isin).as(ISIN),
        col(Date),
        col(StartPrice),
        col(EndPrice),
        col(TradedVolume),
        concat(
          format_number(col(diffPrevClosing) * 100, 2), lit("%")
        ).as(diffPrevClosing)
      )
      .orderBy(Date)
      .coalesce(1)
      .write
      .option("header", true)
      .option("nullValue", null)
      .mode(if (CommandLineArgs.replace()) SaveMode.Overwrite else SaveMode.ErrorIfExists)
      .csv(outputPath)

    logger.info(s"CSV file successfully written to $outputPath")
  }

  def buildInputPaths(prefix: String, startDate: LocalDate)
                     (implicit sparkSession: SparkSession): Seq[String] = {

    val fs = FileSystem.get(new Path(prefix).toUri, sparkSession.sparkContext.hadoopConfiguration)

    val dates: Seq[LocalDate] = startDate
      .datesUntil(LocalDate.now())  // requires Java 11
      .collect(Collectors.toList[LocalDate])
      .asScala

    dates
      .map(date => s"$prefix/$date/")
      .filter(path => fs.exists(new Path(path)))
  }
}
