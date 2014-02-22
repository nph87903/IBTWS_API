package Ademo;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.logging.Logger;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.TagValue;
import com.ib.client.UnderComp;
import com.ib.controller.ApiConnection;
import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.NewContract;
import com.ib.controller.NewOrder;
import com.ib.controller.OrderType;
import com.ib.controller.Types.Action;
import com.ib.controller.Types.SecType;

public class PlaceOrderHelper implements EWrapper{
	
	private final JTextArea m_inLog = new JTextArea();
	private final JTextArea m_outLog = new JTextArea();
	private final Logger m_inLogger = new Logger( m_inLog);
	private final Logger m_outLogger = new Logger( m_outLog);
	
	
	private final static int sum = 10000;
	int tickerId = 10000;
	int orderId = 0;
	double price = 0;
	ApiConnection client;
	
	double askPrice = 0;
	
	public PlaceOrderHelper(){
		client = new ApiConnection(this, m_inLogger, m_outLogger);
	}
	
	
	public boolean placeOrder(String symbol, double percentage){
//		EClientSocket e = new EClientSocket(this);
//		e.eConnect("127.0.0.1", 7496, 0);
//		try {
//			client.eConnect(new Socket("127.0.0.1",7469), 0);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
			client.eConnect("127.0.0.1", 7496, 0);
			System.out.println(client.isConnected());
			NewContract contract = new NewContract();
			contract.symbol("700"); 
			contract.secType(SecType.STK); 
			contract.expiry( ); 
			contract.strike(  ); 
			contract.right(  ); 
			contract.multiplier( ); 
			contract.exchange("SEHK");
//			contract.primaryExch("ISLAND");
			contract.currency("USD"); 
			contract.localSymbol();
			contract.tradingClass();
			System.out.println(client.isConnected());
			
			client.reqMktData(++tickerId, contract.getContract(), "", false, Collections.<TagValue>emptyList() );
			NewOrder order = new NewOrder();
			order.orderId(15);
			order.action(Action.BUY);
			order.totalQuantity(100);
			order.orderType(OrderType.MKT);
			order.account("DU128546");
			client.placeOrder(contract, order);
		return false;
		
	}
	public static void main(String[] args){
		PlaceOrderHelper po = new PlaceOrderHelper();
		
		po.placeOrder("", 0.1);
	}


	@Override
	public void error(Exception e) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void error(String str) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void error(int id, int errorCode, String errorMsg) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void connectionClosed() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickPrice(int tickerId, int field, double price,
			int canAutoExecute) {
		System.out.println(tickerId);
		System.out.println(field);
		System.out.println(price);
		this.price = price;
		System.out.println(canAutoExecute);
		
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickSize(int tickerId, int field, int size) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double optPrice,
			double pvDividend, double gamma, double vega, double theta,
			double undPrice) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickGeneric(int tickerId, int tickType, double value) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickString(int tickerId, int tickType, String value) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints,
			String formattedBasisPoints, double impliedFuture, int holdDays,
			String futureExpiry, double dividendImpact, double dividendsToExpiry) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void orderStatus(int orderId, String status, int filled,
			int remaining, double avgFillPrice, int permId, int parentId,
			double lastFillPrice, int clientId, String whyHeld) {
		// TODO Auto-generated method stub
		System.out.println(status);
	}


	@Override
	public void openOrder(int orderId, Contract contract, Order order,
			OrderState orderState) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void openOrderEnd() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void updateAccountValue(String key, String value, String currency,
			String accountName) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void updatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void updateAccountTime(String timeStamp) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void accountDownloadEnd(String accountName) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void nextValidId(int orderId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void contractDetailsEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void execDetails(int reqId, Contract contract, Execution execution) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void execDetailsEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void updateMktDepth(int tickerId, int position, int operation,
			int side, double price, int size) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void updateMktDepthL2(int tickerId, int position,
			String marketMaker, int operation, int side, double price, int size) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message,
			String origExchange) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void managedAccounts(String accountsList) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void receiveFA(int faDataType, String xml) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void historicalData(int reqId, String date, double open,
			double high, double low, double close, int volume, int count,
			double WAP, boolean hasGaps) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void scannerParameters(String xml) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void scannerData(int reqId, int rank,
			ContractDetails contractDetails, String distance, String benchmark,
			String projection, String legsStr) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void scannerDataEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void realtimeBar(int reqId, long time, double open, double high,
			double low, double close, long volume, double wap, int count) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void currentTime(long time) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void fundamentalData(int reqId, String data) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void deltaNeutralValidation(int reqId, UnderComp underComp) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tickSnapshotEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void marketDataType(int reqId, int marketDataType) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void commissionReport(CommissionReport commissionReport) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void position(String account, Contract contract, int pos,
			double avgCost) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void positionEnd() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void accountSummary(int reqId, String account, String tag,
			String value, String currency) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void accountSummaryEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void verifyMessageAPI(String apiData) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void verifyCompleted(boolean isSuccessful, String errorText) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void displayGroupList(int reqId, String groups) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void displayGroupUpdated(int reqId, String contractInfo) {
		// TODO Auto-generated method stub
		
	}

	private static class Logger implements ILogger {
		final private JTextArea m_area;

		Logger( JTextArea area) {
			m_area = area;
		}

		@Override public void log(final String str) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override public void run() {
//					m_area.append(str);
//					
//					Dimension d = m_area.getSize();
//					m_area.scrollRectToVisible( new Rectangle( 0, d.height, 1, 1) );
				}
			});
		}
	}
}
