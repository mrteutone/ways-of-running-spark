case class MergedTradeActivity(startTime: String,
                               endTime: String,
                               startPrice: String,
                               endPrice: Double,
                               tradedVolume: Int) {

  def mergeWith(tA: TradeActivity): MergedTradeActivity = {
    val (newStartTime, newStartPrice) =
      if (tA.Time < startTime) (tA.Time, tA.StartPrice)
      else (startTime, startPrice)

    val (newEndTime, newEndPrice) =
      if (tA.Time > endTime) (tA.Time, tA.EndPrice)
      else (endTime, endPrice)

    MergedTradeActivity(
      startTime = newStartTime,
      endTime = newEndTime,
      startPrice = newStartPrice,
      endPrice = newEndPrice,
      tradedVolume = tradedVolume + tA.TradedVolume
    )
  }
}

object MergedTradeActivity {

  def apply(tA: TradeActivity): MergedTradeActivity =
    MergedTradeActivity(
      startTime = tA.Time,
      endTime = tA.Time,
      startPrice = tA.StartPrice,
      endPrice = tA.EndPrice,
      tradedVolume = tA.TradedVolume
    )
}
