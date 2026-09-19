package me.investcompany.domain

interface InvestmentRepository : AutoCloseable {
    fun employees(): List<Employee>
    fun clients(query: String = ""): List<Client>
    fun accounts(): List<Account>
    fun instrumentTypes(): List<InstrumentType>
    fun instruments(): List<Instrument>
    fun trades(filter: TradeFilter = TradeFilter()): List<Trade>
    fun dividends(): List<Dividend>
    fun positions(): List<PortfolioPosition>
    fun portfolioSummaries(): List<PortfolioSummary>
    fun monthlyTurnover(): List<MonthlyTurnover>
    fun assetAllocation(): List<AssetAllocation>
    fun dashboard(): Dashboard
    fun addEmployee(employee: EmployeeInput)
    fun updateEmployee(id: Long, employee: EmployeeInput)
    fun archiveEmployee(id: Long)
    fun restoreEmployee(id: Long)
    fun addClient(client: NewClient)
    fun updateClient(id: Long, client: ClientUpdate)
    fun updateClientStatus(id: Long, status: ClientStatus)
    fun addAccount(clientId: Long, accountNumber: String)
    fun updateAccount(id: Long, clientId: Long, accountNumber: String)
    fun updateAccountStatus(id: Long, status: AccountStatus)
    fun addInstrument(typeId: Long, ticker: String, name: String, issuer: String, price: Double)
    fun updateInstrument(id: Long, instrument: InstrumentInput)
    fun archiveInstrument(id: Long)
    fun restoreInstrument(id: Long)
    fun addPrice(instrumentId: Long, price: Double)
    fun deleteLatestPrice(instrumentId: Long)
    fun addTrade(trade: NewTrade)
    fun updateTrade(id: Long, trade: NewTrade)
    fun deleteTrade(id: Long)
    fun addDividend(accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double)
    fun updateDividend(id: Long, accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double)
    fun deleteDividend(id: Long)
    fun coupons(): List<Coupon>
    fun addCoupon(accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double)
    fun updateCoupon(id: Long, accountId: Long, instrumentId: Long, amount: Double, taxAmount: Double)
    fun deleteCoupon(id: Long)
}
