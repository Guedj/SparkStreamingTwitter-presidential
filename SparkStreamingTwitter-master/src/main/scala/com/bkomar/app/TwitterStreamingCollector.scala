package com.bkomar.app

import com.bkomar.utils.Utils
import org.apache.spark.SparkConf
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}

object TwitterStreamingCollector {
  private var numTweetsCollected = 0L //count for the tweet collected
  private var numTweetsToCollect = 10 // num of tweet to collect

  def main(args: Array[String]) {
    //number of args except filters
    val baseParamsCount = 3
    if (args.length < 4) {
      System.err.println("Run streaming with the following parameters: <outputPath> <batchIntervalSeconds> " +
        "<partitionsNum> <filtering keywords>")
      System.exit(1)
    }

    //TODO bk move to utils
    val outputPath: String = args(0)
    val batchInterval: Int = args(1).toInt
    val partitionNum: Int = args(2).toInt
    val keyWordsFilters: Seq[String] = args.takeRight(args.length - baseParamsCount)

    println("Collector is executed with the filters: " + keyWordsFilters.mkString(Utils.hashTagSeparator))

    val sparkConf = new SparkConf().setAppName("TwitterStreamingCollector")
    val ssc = new StreamingContext(sparkConf, Seconds(batchInterval))

    Utils.setUpTwitterOAuth

    val twitterStream = TwitterUtils.createStream(ssc, None, keyWordsFilters)
    twitterStream.foreachRDD((rdd, time) => {
      val count = rdd.count()
      if (rdd.count() > 0) {
        if (numTweetsCollected > numTweetsToCollect) {
          System.exit(1)
          }
        else {
          numTweetsCollected += count
          println("Numbmer of tweets received: " + count)
          rdd
            .map(t => (
              t.getUser.getName,
              t.getCreatedAt.toString,
              t.getText,
              t.getHashtagEntities.map(_.getText).mkString(Utils.hashTagSeparator)
            //            t.getRetweetCount  issue in twitter api, always returns 0
            ))
            .repartition(partitionNum)
            //.coalesce(1, shuffle = true).saveAsTextFile(outputPath + "tweets" + time.milliseconds.toString + ".txt")
            .coalesce(1, shuffle = true).saveAsTextFile(outputPath + "tweetsmerged"+".json")


            }


        //TODO bk add checkpointing
        //test for hive and sql context
      }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
