import java.io.File

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.{FlatSpec, MustMatchers, OptionValues}

import scala.reflect.io.Directory

class StockAggregatorTest extends FlatSpec with MustMatchers with OptionValues {

  val className = getClass.getName

  implicit val sparkSession: SparkSession = {
    val configFileProperties = ConfigFileProperties()

    val sparkConf = new SparkConf()
      .setAppName(className)
      .setAll(configFileProperties.sparkProperties)

    SparkSession.builder.config(sparkConf).getOrCreate()
  }

  behavior of className.stripSuffix("Test")

  it should "work correctly e2e with local test data" in {

    val outputPath = "/tmp/deutsche-boerse-xetra-pds/test/processed/"
    val directory = new Directory(new File(outputPath))
    directory.deleteRecursively()
    StockAggregator.main(Array("--ISIN", "DE0005772206", "--output", outputPath))

    val result = sparkSession
      .read
      .option("treatEmptyValuesAsNulls", false)
      .option("header", true)
      .csv(outputPath)

    // TODO: test against expected rows calculated by hand.
    result.show()
    result.printSchema()
  }
}
