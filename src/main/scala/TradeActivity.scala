case class TradeActivity(Date: String,
                         Time: String,
                         StartPrice: String,
                         EndPrice: Double,
                         TradedVolume: Int)

object TradeActivity {
  def apply(mTA: MergedTradeActivity, date: String): TradeActivity = TradeActivity(
    Date = date,
    Time = mTA.startPrice,
    StartPrice = mTA.startPrice,
    EndPrice = mTA.endPrice,
    TradedVolume = mTA.tradedVolume
  )
}
