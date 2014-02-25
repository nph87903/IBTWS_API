package Ademo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Map.Entry;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.ExecutionFilter;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.ScannerSubscription;
import com.ib.client.TagValue;
import com.ib.client.UnderComp;
import com.ib.controller.AccountSummaryTag;
import com.ib.controller.AdvisorUtil;
import com.ib.controller.Alias;
import com.ib.controller.ApiConnection;
import com.ib.controller.ApiController.IAccountSummaryHandler;
import com.ib.controller.ApiController.IMarketValueSummaryHandler;
import com.ib.controller.Bar;
import com.ib.controller.ConcurrentHashSet;
import com.ib.controller.Group;
import com.ib.controller.MarketValueTag;
import com.ib.controller.NewContract;
import com.ib.controller.NewContractDetails;
import com.ib.controller.NewOrder;
import com.ib.controller.NewOrderState;
import com.ib.controller.NewTickType;
import com.ib.controller.OrderStatus;
import com.ib.controller.Position;
import com.ib.controller.Profile;
import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.Types.BarSize;
import com.ib.controller.Types.DeepSide;
import com.ib.controller.Types.DeepType;
import com.ib.controller.Types.DurationUnit;
import com.ib.controller.Types.ExerciseType;
import com.ib.controller.Types.FADataType;
import com.ib.controller.Types.FundamentalType;
import com.ib.controller.Types.MktDataType;
import com.ib.controller.Types.NewsType;
import com.ib.controller.Types.WhatToShow;

public class ApiOrderPlacer  implements EWrapper{
	private ApiConnection m_client;
	private final ILogger m_outLogger;
	private final ILogger m_inLogger;
	private int m_reqId;	// used for all requests except orders; designed not to conflict with m_orderId
	private int m_orderId;

	private final IConnectionHandler m_connectionHandler;
	private ITradeReportHandler m_tradeReportHandler;
	private IAdvisorHandler m_advisorHandler;
	private final HashMap<Integer,IInternalHandler> m_contractDetailsMap = new HashMap<Integer,IInternalHandler>();
	private final HashMap<Integer,IEfpHandler> m_efpMap = new HashMap<Integer,IEfpHandler>();
	private final HashMap<Integer,ITopMktDataHandler> m_topMktDataMap = new HashMap<Integer,ITopMktDataHandler>();
	private final HashMap<Integer, IOrderHandler> m_orderHandlers = new HashMap<Integer, IOrderHandler>();
	private final HashMap<Integer,IAccountSummaryHandler> m_acctSummaryHandlers = new HashMap<Integer,IAccountSummaryHandler>();
	private final HashMap<Integer,IMarketValueSummaryHandler> m_mktValSummaryHandlers = new HashMap<Integer,IMarketValueSummaryHandler>();
	private final ConcurrentHashSet<IPositionHandler> m_positionHandlers = new ConcurrentHashSet<IPositionHandler>();
	private final ConcurrentHashSet<ILiveOrderHandler> m_liveOrderHandlers = new ConcurrentHashSet<ILiveOrderHandler>();

	// ---------------------------------------- Constructor and Connection handling ----------------------------------------
	public interface IConnectionHandler {
		void connected();
		void disconnected();
		void accountList(ArrayList<String> list);
		void error(Exception e);
		void message(int id, int errorCode, String errorMsg);
		void show(String string);
	}

	public ApiOrderPlacer( IConnectionHandler handler, ILogger inLogger, ILogger outLogger) {
		m_connectionHandler = handler;
		m_client = new ApiConnection( this, inLogger, outLogger);
		m_inLogger = inLogger;
		m_outLogger = outLogger;
	}

	public void connect( String host, int port, int clientId) {
		m_client.eConnect(host, port, clientId);
		sendEOM();
	}

	public void disconnect() {
		m_client.eDisconnect();
		m_connectionHandler.disconnected();
		sendEOM();
	}

	@Override public void managedAccounts(String accounts) {
		ArrayList<String> list = new ArrayList<String>();
		for( StringTokenizer st = new StringTokenizer( accounts, ","); st.hasMoreTokens(); ) {
			list.add( st.nextToken() );
		}
		m_connectionHandler.accountList( list);
		recEOM();
	}

	@Override public void nextValidId(int orderId) {
		m_orderId = orderId;
		m_reqId = m_orderId + 10000000; // let order id's not collide with other request id's
		if (m_connectionHandler != null) {
			m_connectionHandler.connected();
		}
		recEOM();
	}

	@Override public void error(Exception e) {
		m_connectionHandler.error( e);
	}

	@Override public void error(int id, int errorCode, String errorMsg) {
		IOrderHandler handler = m_orderHandlers.get( id);
		if (handler != null) {
			handler.handle( errorCode, errorMsg);
		}

		for (ILiveOrderHandler liveHandler : m_liveOrderHandlers) {
			liveHandler.handle( id, errorCode, errorMsg);
		}

		// "no sec def found" response?
		if (errorCode == 200) {
			IInternalHandler hand = m_contractDetailsMap.remove( id);
			if (hand != null) {
				hand.contractDetailsEnd();
			}
		}

		m_connectionHandler.message( id, errorCode, errorMsg);
		recEOM();
	}

	

	

	@Override public void accountSummary( int reqId, String account, String tag, String value, String currency) {

		recEOM();
	}

	@Override public void accountSummaryEnd( int reqId) {

		recEOM();
	}

	// ---------------------------------------- Position handling ----------------------------------------
	public interface IPositionHandler {
		void position( String account, NewContract contract, int position, double avgCost);
		void positionEnd();
	}

	public void reqPositions( IPositionHandler handler) {
		m_positionHandlers.add( handler);
		m_client.reqPositions();
		sendEOM();
	}

	public void cancelPositions( IPositionHandler handler) {
		m_positionHandlers.add( handler);
		m_client.cancelPositions();
		sendEOM();
	}

	@Override public void position(String account, Contract contractIn, int pos, double avgCost) {
		NewContract contract = new NewContract( contractIn);
		for (IPositionHandler handler : m_positionHandlers) {
			handler.position( account, contract, pos, avgCost);
		}
		recEOM();
	}

	@Override public void positionEnd() {
		for (IPositionHandler handler : m_positionHandlers) {
			handler.positionEnd();
		}
		recEOM();
	}

	// ---------------------------------------- Contract Details ----------------------------------------
	public interface IContractDetailsHandler {
		void contractDetails(ArrayList<NewContractDetails> list);
	}

	public void reqContractDetails( NewContract contract, final IContractDetailsHandler processor) {
		final ArrayList<NewContractDetails> list = new ArrayList<NewContractDetails>();
		internalReqContractDetails( contract, new IInternalHandler() {
			@Override public void contractDetails(NewContractDetails data) {
				list.add( data);
			}
			@Override public void contractDetailsEnd() {
				processor.contractDetails( list);
			}
		});
		sendEOM();
	}

	private interface IInternalHandler {
		void contractDetails(NewContractDetails data);
		void contractDetailsEnd();
	}

	private void internalReqContractDetails( NewContract contract, IInternalHandler processor) {
		int reqId = m_reqId++;
		m_contractDetailsMap.put( reqId, processor);
		m_client.reqContractDetails(reqId, contract.getContract() );
		sendEOM();
	}

	@Override public void contractDetails(int reqId, ContractDetails contractDetails) {
		IInternalHandler handler = m_contractDetailsMap.get( reqId);
		if (handler != null) {
			handler.contractDetails( new NewContractDetails( contractDetails) );
		}
		else {
			show( "Error: no contract details handler for reqId " + reqId);
		}
		recEOM();
	}

	@Override public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		IInternalHandler handler = m_contractDetailsMap.get( reqId);
		if (handler != null) {
			handler.contractDetails(new NewContractDetails( contractDetails) );
		}
		else {
			show( "Error: no bond contract details handler for reqId " + reqId);
		}
		recEOM();
	}

	@Override public void contractDetailsEnd(int reqId) {
		IInternalHandler handler = m_contractDetailsMap.remove( reqId);
		if (handler != null) {
			handler.contractDetailsEnd();
		}
		else {
			show( "Error: no contract details handler for reqId " + reqId);
		}
		recEOM();
	}

	// ---------------------------------------- Top Market Data handling ----------------------------------------
	public interface ITopMktDataHandler {
		void tickPrice(NewTickType tickType, double price, int canAutoExecute);
		void tickSize(NewTickType tickType, int size);
		void tickString(NewTickType tickType, String value);
		void tickSnapshotEnd();
		void marketDataType(MktDataType marketDataType);
	}

	public interface IEfpHandler extends ITopMktDataHandler {
		void tickEFP(int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry);
	}

	public interface IOptHandler extends ITopMktDataHandler {
		void tickOptionComputation( NewTickType tickType, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice);
	}

	public static class TopMktDataAdapter implements ITopMktDataHandler {
		@Override public void tickPrice(NewTickType tickType, double price, int canAutoExecute) {
		}
		@Override public void tickSize(NewTickType tickType, int size) {
		}
		@Override public void tickString(NewTickType tickType, String value) {
		}
		@Override public void tickSnapshotEnd() {
		}
		@Override public void marketDataType(MktDataType marketDataType) {
		}
	}

    public void reqTopMktData(NewContract contract, String genericTickList, boolean snapshot, ITopMktDataHandler handler) {
    	int reqId = m_reqId++;
    	m_topMktDataMap.put( reqId, handler);
    	m_client.reqMktData( reqId, contract.getContract(), genericTickList, snapshot, Collections.<TagValue>emptyList() );
		sendEOM();
    }

    

    public void reqEfpMktData(NewContract contract, String genericTickList, boolean snapshot, IEfpHandler handler) {
    	int reqId = m_reqId++;
    	m_topMktDataMap.put( reqId, handler);
    	m_efpMap.put( reqId, handler);
    	m_client.reqMktData( reqId, contract.getContract(), genericTickList, snapshot, Collections.<TagValue>emptyList() );
		sendEOM();
    }

    public void cancelTopMktData( ITopMktDataHandler handler) {
    	Integer reqId = getAndRemoveKey( m_topMktDataMap, handler);
    	if (reqId != null) {
    		m_client.cancelMktData( reqId);
    	}
    	else {
    		show( "Error: could not cancel top market data");
    	}
		sendEOM();
    }


    public void cancelEfpMktData( IEfpHandler handler) {
    	cancelTopMktData( handler);
    	getAndRemoveKey( m_efpMap, handler);
    }

	public void reqMktDataType( MktDataType type) {
		m_client.reqMarketDataType( type.ordinal() );
		sendEOM();
	}

	@Override public void tickPrice(int reqId, int tickType, double price, int canAutoExecute) {
		ITopMktDataHandler handler = m_topMktDataMap.get( reqId);
		if (handler != null) {
			handler.tickPrice( NewTickType.get( tickType), price, canAutoExecute);
		}
		System.out.println(price);
		recEOM();
	}

	@Override public void tickGeneric(int reqId, int tickType, double value) {
		ITopMktDataHandler handler = m_topMktDataMap.get( reqId);
		if (handler != null) {
			handler.tickPrice( NewTickType.get( tickType), value, 0);
		}
		recEOM();
	}

	@Override public void tickSize(int reqId, int tickType, int size) {
		ITopMktDataHandler handler = m_topMktDataMap.get( reqId);
		if (handler != null) {
			handler.tickSize( NewTickType.get( tickType), size);
		}
		recEOM();
	}

	@Override public void tickString(int reqId, int tickType, String value) {
		ITopMktDataHandler handler = m_topMktDataMap.get( reqId);
		if (handler != null) {
			handler.tickString( NewTickType.get( tickType), value);
		}
		recEOM();
	}

	@Override public void tickEFP(int reqId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
		IEfpHandler handler = m_efpMap.get( reqId);
		if (handler != null) {
			handler.tickEFP( tickType, basisPoints, formattedBasisPoints, impliedFuture, holdDays, futureExpiry, dividendImpact, dividendsToExpiry);
		}
		recEOM();
	}

	@Override public void tickSnapshotEnd(int reqId) {
		ITopMktDataHandler handler = m_topMktDataMap.get( reqId);
		if (handler != null) {
			handler.tickSnapshotEnd();
		}
		recEOM();
	}

	@Override public void marketDataType(int reqId, int marketDataType) {
		ITopMktDataHandler handler = m_topMktDataMap.get( reqId);
		if (handler != null) {
			handler.marketDataType( MktDataType.get( marketDataType) );
		}
		recEOM();
	}






	@Override public void tickOptionComputation(int reqId, int tickType, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
			System.out.println( String.format( "not handled %s %s %s %s %s %s %s %s %s", tickType, impliedVol, delta, optPrice, pvDividend, gamma, vega, theta, undPrice) );
		recEOM();
	}


	// ---------------------------------------- Trade reports ----------------------------------------
	public interface ITradeReportHandler {
		void tradeReport(String tradeKey, NewContract contract, Execution execution);
		void tradeReportEnd();
		void commissionReport(String tradeKey, CommissionReport commissionReport);
	}

    public void reqExecutions( ExecutionFilter filter, ITradeReportHandler handler) {
    	m_tradeReportHandler = handler;
    	m_client.reqExecutions( m_reqId++, filter);
		sendEOM();
    }

	@Override public void execDetails(int reqId, Contract contract, Execution execution) {
		if (m_tradeReportHandler != null) {
			int i = execution.m_execId.lastIndexOf( '.');
			String tradeKey = execution.m_execId.substring( 0, i);
			m_tradeReportHandler.tradeReport( tradeKey, new NewContract( contract), execution);
		}
		recEOM();
	}

	@Override public void execDetailsEnd(int reqId) {
		if (m_tradeReportHandler != null) {
			m_tradeReportHandler.tradeReportEnd();
		}
		recEOM();
	}

	@Override public void commissionReport(CommissionReport commissionReport) {
		if (m_tradeReportHandler != null) {
			int i = commissionReport.m_execId.lastIndexOf( '.');
			String tradeKey = commissionReport.m_execId.substring( 0, i);
			m_tradeReportHandler.commissionReport( tradeKey, commissionReport);
		}
		recEOM();
	}

	// ---------------------------------------- Advisor info ----------------------------------------
	public interface IAdvisorHandler {
		void groups(ArrayList<Group> groups);
		void profiles(ArrayList<Profile> profiles);
		void aliases(ArrayList<Alias> aliases);
	}

	public void reqAdvisorData( FADataType type, IAdvisorHandler handler) {
		m_advisorHandler = handler;
		m_client.requestFA( type.ordinal() );
		sendEOM();
	}

	public void updateGroups( ArrayList<Group> groups) {
		m_client.replaceFA( FADataType.GROUPS.ordinal(), AdvisorUtil.getGroupsXml( groups) );
		sendEOM();
	}

	public void updateProfiles(ArrayList<Profile> profiles) {
		m_client.replaceFA( FADataType.PROFILES.ordinal(), AdvisorUtil.getProfilesXml( profiles) );
		sendEOM();
	}

	@Override public final void receiveFA(int faDataType, String xml) {
		if (m_advisorHandler == null) {
			return;
		}

		FADataType type = FADataType.get( faDataType);

		switch( type) {
			case GROUPS:
				ArrayList<Group> groups = AdvisorUtil.getGroups( xml);
				m_advisorHandler.groups(groups);
				break;

			case PROFILES:
				ArrayList<Profile> profiles = AdvisorUtil.getProfiles( xml);
				m_advisorHandler.profiles(profiles);
				break;

			case ALIASES:
				ArrayList<Alias> aliases = AdvisorUtil.getAliases( xml);
				m_advisorHandler.aliases(aliases);
				break;
		}
		recEOM();
	}

	// ---------------------------------------- Trading and Option Exercise ----------------------------------------
	public interface IOrderHandler {
		void orderState(NewOrderState orderState);
		void handle(int errorCode, String errorMsg);
	}

	public void placeOrModifyOrder(NewContract contract, final NewOrder order, final IOrderHandler handler) {
		// when placing new order, assign new order id
		if (order.orderId() == 0) {
			order.orderId( m_orderId++);
			if (handler != null) {
				m_orderHandlers.put( order.orderId(), handler);
			}
		}

		m_client.placeOrder( contract, order);
		sendEOM();
	}

	public void cancelOrder(int orderId) {
		m_client.cancelOrder( orderId);
		sendEOM();
	}

	public void cancelAllOrders() {
		m_client.reqGlobalCancel();
		sendEOM();
	}

	public void exerciseOption( String account, NewContract contract, ExerciseType type, int quantity, boolean override) {
		m_client.exerciseOptions( m_reqId++, contract.getContract(), type.ordinal(), quantity, account, override ? 1 : 0);
		sendEOM();
	}

	public void removeOrderHandler( IOrderHandler handler) {
		getAndRemoveKey(m_orderHandlers, handler);
	}


	// ---------------------------------------- Live order handling ----------------------------------------
	public interface ILiveOrderHandler {
		void openOrder(NewContract contract, NewOrder order, NewOrderState orderState);
		void openOrderEnd();
		void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld);
		void handle(int orderId, int errorCode, String errorMsg);  // add permId?
	}

	public void reqLiveOrders( ILiveOrderHandler handler) {
		m_liveOrderHandlers.add( handler);
		m_client.reqAllOpenOrders();
		sendEOM();
	}

	public void takeTwsOrders( ILiveOrderHandler handler) {
		m_liveOrderHandlers.add( handler);
		m_client.reqOpenOrders();
		sendEOM();
	}

	public void takeFutureTwsOrders( ILiveOrderHandler handler) {
		m_liveOrderHandlers.add( handler);
		m_client.reqAutoOpenOrders( true);
		sendEOM();
	}

	public void removeLiveOrderHandler(ILiveOrderHandler handler) {
		m_liveOrderHandlers.remove( handler);
	}

	@Override public void openOrder(int orderId, Contract contract, Order orderIn, OrderState orderState) {
		NewOrder order = new NewOrder( orderIn);

		IOrderHandler handler = m_orderHandlers.get( orderId);
		if (handler != null) {
			handler.orderState( new NewOrderState( orderState) );
		}

		if (!order.whatIf() ) {
			for (ILiveOrderHandler liveHandler : m_liveOrderHandlers) {
				liveHandler.openOrder( new NewContract( contract), order, new NewOrderState( orderState) );
			}
		}
		recEOM();
	}

	@Override public void openOrderEnd() {
		for (ILiveOrderHandler handler : m_liveOrderHandlers) {
			handler.openOrderEnd();
		}
		recEOM();
	}

	@Override public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
		for (ILiveOrderHandler handler : m_liveOrderHandlers) {
			handler.orderStatus(orderId, OrderStatus.valueOf( status), filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
		}
		recEOM();
	}


	// ---------------------------------------- Market Scanners ----------------------------------------
	

	

	@Override public void scannerParameters(String xml) {
		recEOM();
	}

	@Override public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
		recEOM();
	}

	@Override public void scannerDataEnd(int reqId) {
		recEOM();
	}


	// ----------------------------------------- Historical data handling ----------------------------------------



	@Override public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double wap, boolean hasGaps) {
		recEOM();
	}



    @Override public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
		recEOM();
	}


    // ----------------------------------------- Fundamentals handling ----------------------------------------

    @Override public void fundamentalData(int reqId, String data) {
		recEOM();
	}




	@Override public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
		recEOM();
	}

	@Override public void verifyMessageAPI( String apiData) {}
	@Override public void verifyCompleted( boolean isSuccessful, String errorText) {}
	@Override public void displayGroupList(int reqId, String groups) {}
	@Override public void displayGroupUpdated(int reqId, String contractInfo) {}

	// ---------------------------------------- other methods ----------------------------------------
	/** Not supported in ApiController. */
	@Override public void deltaNeutralValidation(int reqId, UnderComp underComp) {
		show( "RECEIVED DN VALIDATION");
		recEOM();
	}

	private void sendEOM() {
		m_outLogger.log( "\n");
	}

	private void recEOM() {
		m_inLogger.log( "\n");
	}

	public void show(String string) {
		m_connectionHandler.show( string);
	}

    private static <K,V> K getAndRemoveKey( HashMap<K,V> map, V value) {
    	for (Entry<K,V> entry : map.entrySet() ) {
    		if (entry.getValue() == value) {
    			map.remove( entry.getKey() );
    			return entry.getKey();
    		}
    	}
		return null;
    }

	/** Obsolete, never called. */
	@Override public void error(String str) {
		throw new RuntimeException();
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
	public void currentTime(long time) {
		// TODO Auto-generated method stub
		
	}
	@Override public void connectionClosed() {
		m_connectionHandler.disconnected();
	}


	// ---------------------------------------- Account and portfolio updates ----------------------------------------
	
	@Override public void updateAccountValue(String tag, String value, String currency, String account) {
		recEOM();
	}

	@Override public void updateAccountTime(String timeStamp) {
		recEOM();
	}

	@Override public void accountDownloadEnd(String account) {
		recEOM();
	}

	@Override public void updatePortfolio(Contract contractIn, int positionIn, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String account) {
		recEOM();
	}
}
