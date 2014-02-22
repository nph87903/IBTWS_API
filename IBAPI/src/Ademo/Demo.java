package Ademo;

import java.util.ArrayList;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.ib.controller.NewContract;
import com.ib.controller.NewOrder;
import com.ib.controller.OrderType;
import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.Types.Action;
import com.ib.controller.Types.SecType;

import Ademo.ApiOrderPlacer.IConnectionHandler;

public class Demo implements IConnectionHandler {
	private final JTextArea m_inLog = new JTextArea();
	private final JTextArea m_outLog = new JTextArea();
	private final Logger m_inLogger = new Logger( m_inLog);
	private final Logger m_outLogger = new Logger( m_outLog);
	private final ApiOrderPlacer m_controller = new ApiOrderPlacer( this, m_inLogger, m_outLogger);
	
	static Demo INSTANCE = new Demo();
	
	
	public static void main(String[] args) {
		INSTANCE.run();
		NewContract contract = new NewContract();
		contract.symbol("700"); 
		contract.secType(SecType.STK); 
		contract.expiry( ); 
		contract.strike(  ); 
		contract.right(  ); 
		contract.multiplier( ); 
		contract.exchange("SEHK");
//		contract.primaryExch("ISLAND");
		contract.currency("USD"); 
		contract.localSymbol();
		contract.tradingClass();
		
		INSTANCE.m_controller.reqTopMktData(contract, "", false, null);
		NewOrder order = new NewOrder();
		order.orderId(150);
		order.action(Action.BUY);
		order.totalQuantity(100);
		order.orderType(OrderType.MKT);
		order.account("DU128546");
		INSTANCE.m_controller.placeOrModifyOrder(contract, order, null);
	}
	private void run() {
        // make initial connection to local host, port 7496, client id 0
		m_controller.connect( "127.0.0.1", 7496, 0);
    }
	@Override
	public void connected() {
		System.out.println("connected");
		
	}

	@Override
	public void disconnected() {
		// TODO Auto-generated method stub

		System.out.println("disconnected");
	}

	@Override
	public void accountList(ArrayList<String> list) {
		// TODO Auto-generated method stub
		System.out.println("accont list");
		System.out.println(list);
	}

	@Override
	public void error(Exception e) {
		// TODO Auto-generated method stub
		System.out.println(e.getMessage());
	}

	@Override
	public void message(int id, int errorCode, String errorMsg) {
		// TODO Auto-generated method stub
		System.out.println(errorMsg);
	}

	@Override
	public void show(String string) {
		System.out.println(string);
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
