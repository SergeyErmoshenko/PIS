package me.investcompany.domain

interface InvestmentRepository : AutoCloseable {
    fun employees(): List<Employee>
    fun clients(query: String = ""): List<Client>
    fun accounts(): List<Account>
    fun instruments(): List<Instrument>
    fun trades(filter: TradeFilter = TradeFilter()): List<Trade>
    fun dividends(): List<Dividend>
    fun positions(): List<PortfolioPosition>
    fun portfolioSummaries(): List<PortfolioSummary>
    fun monthlyTurnover(): List<MonthlyTurnover>
    fun assetAllocation(): List<AssetAllocation>
    fun dashboard(): Dashboard
    fun addClient(client: NewClient)
    fun updateClientStatus(id: Long, status: ClientStatus)
    fun addAccount(clientId: Long, accountNumber: String)
    fun updateAccountStatus(id: Long, status: AccountStatus)
    fun addInstrument(typeId: Long, ticker: String, name: String, issuer: String, price: Double)
    fun addPrice(instrumentId: Long, price: Double)
    fun addTrade(trade: NewTrade)
    fun deleteTrade(id: Long)
    fun addDividend(accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double)
}
