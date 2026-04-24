package org.libero.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.apps.form.Allocation;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MPayment;
import org.compiere.model.MProduct;
import org.compiere.model.MRole;
import org.compiere.model.MSysConfig;
import org.compiere.model.MUOM;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Util;
import org.eevolution.model.MPPProductBOM;
import org.eevolution.model.MPPProductBOMLine;
import org.zkoss.zul.DefaultTreeNode;

public class MPS {
	protected DecimalFormat format = DisplayType.getNumberFormat(DisplayType.Amount);

	/**	Logger			*/
	protected static final CLogger log = CLogger.getCLogger(Allocation.class);

	private boolean     m_calculating = false;
	protected int       m_C_Currency_ID = 0;
	protected int       m_C_Charge_ID = 0;
	protected int       m_C_DocType_ID = 0;
	protected int       m_C_BPartner_ID = 0;
	private int         m_noInvoices = 0;
	private int         m_noPayments = 0;
	protected BigDecimal	totalInv = Env.ZERO;
	protected BigDecimal 	totalPay = Env.ZERO;
	protected BigDecimal	totalDiff = Env.ZERO;
	
	protected Timestamp allocDate = null;

	//  Index	changed if multi-currency
	private int         i_payment = 7;
	//
	private int         i_open = 6;
	private int         i_discount = 7;
	private int         i_writeOff = 8; 
	private int         i_applied = 9;
	private int 		i_overUnder = 10;
	private Vector<Vector<Object>> dataBOM = new Vector<Vector<Object>>();
	
	protected int         	m_AD_Org_ID = 0;

	private ArrayList<Integer>	m_bpartnerCheck = new ArrayList<Integer>(); 

	protected void dynInit() throws Exception
	{
		m_C_Currency_ID = Env.getContextAsInt(Env.getCtx(), Env.C_CURRENCY_ID);   //  default
		//
		if (log.isLoggable(Level.INFO)) log.info("Currency=" + m_C_Currency_ID);
		
		m_AD_Org_ID = Env.getAD_Org_ID(Env.getCtx());
		m_C_DocType_ID= MDocType.getDocType(MDocType.DOCBASETYPE_PaymentAllocation);
		
	}
	
	/**
	 *  Load Business Partner Info
	 *  - Payments
	 *  - Invoices
	 */
	public void checkBPartner()
	{		
		if (log.isLoggable(Level.CONFIG)) log.config("BPartner=" + m_C_BPartner_ID + ", Cur=" + m_C_Currency_ID);
		//  Need to have both values
		if (m_C_BPartner_ID == 0 || m_C_Currency_ID == 0)
			return;

		//	Async BPartner Test
		Integer key = Integer.valueOf(m_C_BPartner_ID);
		if (!m_bpartnerCheck.contains(key))
		{
			new Thread()
			{
				public void run()
				{
					MPayment.setIsAllocated (Env.getCtx(), m_C_BPartner_ID, null);
					MInvoice.setIsPaid (Env.getCtx(), m_C_BPartner_ID, null);
				}
			}.start();
			m_bpartnerCheck.add(key);
		}
	}
	
	/**
	 * @deprecated
	 * @param isMultiCurrency
	 * @param date
	 * @param paymentTable not used
	 * @return list of payment record
	 */
//	public Vector<Vector<Object>> getPaymentData(boolean isMultiCurrency, Object date, IMiniTable paymentTable)
//	{
//		return getPaymentData(isMultiCurrency, (Timestamp) date, (String)null);
//	}
	
	/**
	 * 
	 * @param isMultiCurrency
	 * @param date
	 * @param trxName optional trx name
	 * @return list of payment record
	 */
	private int periodcolumn(int period) {
		int jmlhari = 0;
		String sql = "select enddate::date-startdate::date as jmlhari  from  C_Period where\n"
				+ " C_Period_ID="+period;                                            //  #7
		
			
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try
			{
				pstmt = DB.prepareStatement(sql.toString(),null);
				
				rs = pstmt.executeQuery();
				while (rs.next())
				{
					jmlhari = rs.getInt(1);
				}
			}
			catch (SQLException e)
			{
				
			}
			finally
			{
				DB.close(rs, pstmt);
			}
			return jmlhari;
	}
	
	
	public Vector<Vector<Object>> getPaymentData(String weekly,int period)
	{	
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		
//			Vector<Object> line = new Vector<Object>();
//			line.add("On Hand : 30 / lead time : 2 minggu");
//			for (int i = 1; i <= 50; i++) {
//				line.add(BigDecimal.ZERO);
//			}			
		 //   data.add(line);
		    Vector<Object> line2 = new Vector<Object>();
			line2.add("Forecast");
			if(Integer.parseInt(weekly)>0) {
			for (int i = 1; i <= Integer.parseInt(weekly); i++) {
				line2.add(BigDecimal.ZERO);
			}
			}else {
				for (int i = 1; i <= periodcolumn(period); i++) {
					line2.add(BigDecimal.ZERO);
				}
			}
			data.add(line2);
			Vector<Object> line3 = new Vector<Object>();
				line3.add("Sales Order");
				if(Integer.parseInt(weekly)>0) {
				for (int i = 1; i <= Integer.parseInt(weekly); i++) {
					line3.add(BigDecimal.ZERO);
				}
				}else {
					for (int i = 1; i <= periodcolumn(period); i++) {
						line3.add(BigDecimal.ZERO);	
				}
				}
				data.add(line3);
				Vector<Object> line4 = new Vector<Object>();
				line4.add("Project OnHand Inventory");
				if(Integer.parseInt(weekly)>0) {
				for (int i = 1; i <= Integer.parseInt(weekly); i++) {
					line4.add(BigDecimal.ZERO);
				}
				}else {
					for (int i = 1; i <= periodcolumn(period); i++) {
						line4.add(BigDecimal.ZERO);
					}
				}
				data.add(line4);
				Vector<Object> line5 = new Vector<Object>();
				line5.add("MPS Quantity");
				if(Integer.parseInt(weekly)>0) {
				for (int i = 1; i <= Integer.parseInt(weekly); i++) {
					line5.add(BigDecimal.ZERO);
				}
				}else {
					for (int i = 1; i <= periodcolumn(period); i++) {
						line5.add(BigDecimal.ZERO);
					}
				}
				data.add(line5);
				Vector<Object> line6 = new Vector<Object>();
				line6.add("MPS Start");
				if(Integer.parseInt(weekly)>0) {
				for (int i = 1; i <= Integer.parseInt(weekly); i++) {
					line6.add(BigDecimal.ZERO);
				}
				}else {
					for (int i = 1; i <= periodcolumn(period); i++) {
						line6.add(BigDecimal.ZERO);
					}	
				}
				data.add(line6);
				Vector<Object> line7 = new Vector<Object>();
				line7.add("ATP");
				if(Integer.parseInt(weekly)>0) {
				for (int i = 1; i <= Integer.parseInt(weekly); i++) {
					line7.add(BigDecimal.ZERO);
				}
				}else {
					for (int i = 1; i <= periodcolumn(period); i++) {
						line7.add(BigDecimal.ZERO);
					}	
				}
				data.add(line7);
				
		return data ;
	}
	
	/**
	 * 
	 * @param isMultiCurrency
	 * @return column name list for payment data
	 */
	public Vector<String> getPaymentColumnNames(String Product, String wekkly,int period)
	{	
		//  Header Info
		Vector<String> columnNames = new Vector<String>();
		columnNames.add(Product);
		if(Integer.parseInt(wekkly)>0) {
		for (int i = 1; i <= Integer.parseInt(wekkly); i++) {
					
			columnNames.add( "Minggu " + i);
			//columnNames.add( "Minggu 2");
			//columnNames.add( "Minggu 3");
		}
		}else {
			for (int i = 1; i <= periodcolumn(period); i++) {
				
				columnNames.add( "Tanggal " + i);
				//columnNames.add( "Minggu 2");
				//columnNames.add( "Minggu 3");
			}
		}
		
		
		return columnNames;
	}
	
	/**
	 * 
	 * @param paymentTable
	 * @param isMultiCurrency
	 */
	public void setPaymentColumnClass(IMiniTable paymentTable)
	{
		int j = 0;
		paymentTable.setColumnClass(j++, String.class, true);    
		for (int i = 1; i <= 50; i++) {
		paymentTable.setColumnClass(i, BigDecimal.class, false);  
		}//  5-ConvAmt
//		paymentTable.setColumnClass(i++, BigDecimal.class, true);       //  6-ConvOpen
//		paymentTable.setColumnClass(i++, BigDecimal.class, false);      //  7-Allocated
		//
		
		

		//  Table UI
		paymentTable.autoSize();
	}
	

	/**
	 * 
	 * @param isMultiCurrency
	 * @param date
	 * @param trxName optional trx name
	 * @return list of unpaid invoice data
	 */
	public Vector<Vector<Object>> getInvoiceData(MProduct product, String weekly, int period)
	{
		DefaultTreeNode parent = new DefaultTreeNode(productSummary(product, false), new ArrayList());
		System.out.println("afafa"+parent);
		dataBOM.clear();
		for (MPPProductBOM bom : MPPProductBOM.getProductBOMs(product))
		{
			parent.getChildren().add(parent(bom,weekly,period));                    
		} 
		return dataBOM;
	}

	/**
	 * 
	 * @param isMultiCurrency
	 * @return list of column name/header
	 */
	public Vector<String> getInvoiceColumnNames(String wekkly,int period)
	{
		//  Header Info
		Vector<String> columnNames = new Vector<String>();
		columnNames.add(Msg.getMsg(Env.getCtx(), "Product"));
		columnNames.add("On Hand");
		columnNames.add("Qty Bom");
		columnNames.add("MasterBOM");
		columnNames.add("Level BOM");
		
		if(Integer.parseInt(wekkly)>0) {
			for (int i = 1; i <= Integer.parseInt(wekkly); i++) {
						
				columnNames.add( "Minggu " + i);
				//columnNames.add( "Minggu 2");
				//columnNames.add( "Minggu 3");
			}
			}else {
				for (int i = 1; i <= periodcolumn(period); i++) {
					
					columnNames.add( "Tanggal " + i);
					//columnNames.add( "Minggu 2");
					//columnNames.add( "Minggu 3");
				}
			}
		
		return columnNames;
	}
	
	/**
	 * set class type for each column
	 * @param invoiceTable
	 * @param isMultiCurrency
	 */
	public void setInvoiceColumnClass(IMiniTable invoiceTable, String wekkly, int period)
	{
		int i = 0;
		invoiceTable.setColumnClass(i++, KeyNamePair.class, true);
		invoiceTable.setColumnClass(i++, BigDecimal.class, true);
		invoiceTable.setColumnClass(i++, BigDecimal.class, true);
		invoiceTable.setColumnClass(i++, String.class, true);
		invoiceTable.setColumnClass(i++, Integer.class, true);
		if(Integer.parseInt(wekkly)>0) {
			for (int j = 4; j <= Integer.parseInt(wekkly)+4; j++) {
						
				invoiceTable.setColumnClass(j, BigDecimal.class, false);
				//columnNames.add( "Minggu 2");
				//columnNames.add( "Minggu 3");
			}
			}else {
				for (int j = 4; j <= periodcolumn(period)+4; j++) {
					
					invoiceTable.setColumnClass(j, BigDecimal.class, false);
					//columnNames.add( "Minggu 2");
					//columnNames.add( "Minggu 3");
				}
			}
		//invoiceTable.setColumnClass(i++, BigDecimal.class, false);  //  0-Selection
		//	10-Conv Applied
		//  Table UI
		invoiceTable.autoSize();
	}
	
	/**
	 * set column index for single or multi currency
	 * @param isMultiCurrency
	 */
	protected void prepareForCalculate(boolean isMultiCurrency)
	{
		i_open = isMultiCurrency ? 6 : 4;
		i_discount = isMultiCurrency ? 7 : 5;
		i_writeOff = isMultiCurrency ? 8 : 6;
		i_applied = isMultiCurrency ? 9 : 7;
		i_overUnder = isMultiCurrency ? 10 : 8;
	}   //  loadBPartner
	
	/**
	 * update payment or invoice applied and write off amount
	 * @param row row to update
	 * @param col change is trigger by selected or applied column
	 * @param isInvoice update invoice or payment applied amount
	 * @param payment
	 * @param invoice
	 * @param isAutoWriteOff true to write off difference, false to use over/under for difference
	 * @return warning message (if any)
	 */
	public void writeOff(int row, int col,IMiniTable payment, int leadtime, BigDecimal qtyhand, IMiniTable invoicetable)
	{
		
		if(col==1) {
			BigDecimal forecast = (BigDecimal)payment.getValueAt(0, col);
			BigDecimal salesorder = (BigDecimal)payment.getValueAt(1, col);
			BigDecimal mpsqty = (BigDecimal)payment.getValueAt(3, col);
			BigDecimal onhandqty = qtyhand;
			if(row==0) {
				if(forecast.compareTo(salesorder)>0) {
					payment.setValueAt(onhandqty.subtract(forecast), 2, col);
				}
			}
			
			if(row==1) {
				if(salesorder.compareTo(forecast)>0) {
					payment.setValueAt(onhandqty.subtract(salesorder), 2, col);
				}
			}
		
			if(row==3) {
				if(forecast.compareTo(salesorder)>0) {
				payment.setValueAt((mpsqty.add(onhandqty)).subtract(forecast), 2, col);
			}else {
				payment.setValueAt((mpsqty.add(onhandqty)).subtract(salesorder), 2, col);
			}
			}
		    

		
		}else if (col>1 ) {
			BigDecimal forecast = (BigDecimal)payment.getValueAt(0, col);
			BigDecimal salesorder = (BigDecimal)payment.getValueAt(1, col);
			BigDecimal mpsqty = (BigDecimal)payment.getValueAt(3, col);
			BigDecimal onhandqty = (BigDecimal)payment.getValueAt(2, col-1);
			BigDecimal Qtylevel0 = BigDecimal.ZERO;
			BigDecimal Qtylevel1 = BigDecimal.ZERO;
		    
			if(row==0) {
			if(forecast.compareTo(salesorder)>0) {
				//if(onhandqty.subtract(forecast).compareTo(BigDecimal.ZERO)<0) {
					payment.setValueAt(onhandqty.subtract(forecast), 2, col);
					//payment.setValueAt(mpsqty, 4, col);
					//payment.setValueAt(mpsqty, 5, col-leadtime);
//				}else {
//				payment.setValueAt(onhandqty.subtract(forecast), 3, col);
//				if(col-2 > 0) {
//				payment.setValueAt(BigDecimal.ZERO, 4, col);
//				payment.setValueAt(BigDecimal.ZERO, 5, col-leadtime);
//				}
//				}
			}
			}
			if(row==1)  {
				if(salesorder.compareTo(forecast)>0) {
					payment.setValueAt(onhandqty.subtract(salesorder), 2, col);
				}
					//payment.setValueAt(mpsqty, 4, col);
				//	payment.setValueAt(mpsqty, 5, col-leadtime);
//				}else {
//				payment.setValueAt(onhandqty.subtract(salesorder), 3, col);
//				if(col-2 > 0) {
//				payment.setValueAt(BigDecimal.ZERO, 4, col);
//				payment.setValueAt(BigDecimal.ZERO, 5, col-leadtime);
//				}
//				}
				//payment.setValueAt(onhandqty.subtract(salesorder), 3, col);
			}
			
			if(row==3) {
				
				if(forecast.compareTo(salesorder)>0) {
				payment.setValueAt((mpsqty.add(onhandqty)).subtract(forecast), 2, col);
				if(col-leadtime>0) {
				payment.setValueAt(mpsqty, 4, col-leadtime);
				for (int i = 0; i < invoicetable.getRowCount(); i++) {
					if(i == 0 && invoicetable.getValueAt(i, 3).toString().equals("Y") ) {
						if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
							 invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
							break;
						}else {
							Qtylevel0 = (mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))).
									  subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
							  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
							  invoicetable.setValueAt(Qtylevel0, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
							  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);
						}
					}else {
						if(i == 1 && invoicetable.getValueAt(i, 3).toString().equals("Y")) {
							if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
							
							invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
							}else {
								Qtylevel1 = Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
								  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
								  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
								  System.out.println("dfafa"+DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
								  System.out.println("col"+(col+4));
								  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
								
							}
							}else {
							if(Integer.parseInt(invoicetable.getValueAt(i, 4).toString())== Integer.parseInt(invoicetable.getValueAt(i-1, 4).toString())) {
								if(invoicetable.getValueAt(i, 3).toString().equals("Y")) {
									if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
										
										invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
										}else {
											Qtylevel1 = Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
											  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
											  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
											  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
											
										}
								}else {
                                       if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
										
										invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
										}else {
											Qtylevel1 = Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
											  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
											  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
											  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
											
										}
								}
							}else {
								if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
									
									invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
									}else {
										Qtylevel1 = Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
										  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
										  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
										  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
										
									}
							}
						}
//						if(Integer.parseInt(invoicetable.getValueAt(i, 4).toString()) == Integer.parseInt(invoicetable.getValueAt(i-1, 4).toString())) {
//							
//						}
						
					}
						
					}
				 // if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
//					  invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
//				  }else {
//					  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
//					  invoicetable.setValueAt((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))).
//							  subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString())), i, (col+3)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
//					  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);
//				  }
				
				}
				
			}else {
//				payment.setValueAt((mpsqty.add(onhandqty)).subtract(salesorder), 2, col);
//				if(col-leadtime>0) {
//				payment.setValueAt(mpsqty, 4, col-leadtime);
//				for (int i = 0; i < invoicetable.getRowCount(); i++) {
//					if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
//						  invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
//					  }else {
//						  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
//						  invoicetable.setValueAt((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString())), i, (col+3)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
//						  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);
//					  }
//					}
//				}
				payment.setValueAt((mpsqty.add(onhandqty)).subtract(forecast), 2, col);
				if(col-leadtime>0) {
				payment.setValueAt(mpsqty, 4, col-leadtime);
				for (int i = 0; i < invoicetable.getRowCount(); i++) {
					if(i == 0 && invoicetable.getValueAt(i, 3).toString().equals("Y") ) {
						if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
							 invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
							break;
						}else {
							Qtylevel0 = (mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))).
									  subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
							  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
							  invoicetable.setValueAt(Qtylevel0, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
							  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);
						}
					}else {
						if(i == 1 && invoicetable.getValueAt(i, 3).toString().equals("Y")) {
							if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
							
							invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
							}else {
								Qtylevel1 = Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
								  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
								  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
								  System.out.println("dfafa"+DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
								  System.out.println("col"+(col+4));
								  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
								
							}
							}else {
							if(Integer.parseInt(invoicetable.getValueAt(i, 4).toString())== Integer.parseInt(invoicetable.getValueAt(i-1, 4).toString())) {
								if(invoicetable.getValueAt(i, 3).toString().equals("Y")) {
									if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
										
										invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
										}else {
											Qtylevel1 = Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
											  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
											  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
											  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
											
										}
								}else {
                                       if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
										
										invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
										}else {
											Qtylevel1 = Qtylevel1.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
											  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
											  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
											  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
											
										}
								}
							}else {
								if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
									
									invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
									}else {
										Qtylevel1 = Qtylevel0.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())).subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString()));
										  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
										  invoicetable.setValueAt(Qtylevel1, i, (col+4)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
										  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);	
										
									}
							}
						}
//						if(Integer.parseInt(invoicetable.getValueAt(i, 4).toString()) == Integer.parseInt(invoicetable.getValueAt(i-1, 4).toString())) {
//							
//						}
						
					}
						
					}
				 // if(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).compareTo((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))))>0) {
//					  invoicetable.setValueAt(new BigDecimal(invoicetable.getValueAt(i, 1).toString()).subtract((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString())))), i, 1);
//				  }else {
//					  KeyNamePair keyNamePair = (KeyNamePair)invoicetable.getValueAt(i, 0);
//					  invoicetable.setValueAt((mpsqty.multiply(new BigDecimal(invoicetable.getValueAt(i, 2).toString()))).
//							  subtract(new BigDecimal(invoicetable.getValueAt(i, 1).toString())), i, (col+3)-DB.getSQLValue(null, "select transferttime from PP_Product_Planning where m_product_id =  "+ keyNamePair.getKey()));
//					  invoicetable.setValueAt(BigDecimal.ZERO, i, 1);
//				  }
				
				}
			}
			}
			
//			if(onhandqty.compareTo(BigDecimal.ZERO)<0) {
//				payment.setValueAt(mpsqty, 4, col);
//				payment.setValueAt(mpsqty.subtract(onhandqty), 5, col-2);
//			}
		}
		
				
	}
	
	/**
	 * perform allocation calculation
	 * @param paymentTable
	 * @param invoiceTable
	 * @param isMultiCurrency
	 */
	public void calculate(IMiniTable paymentTable, IMiniTable invoiceTable, boolean isMultiCurrency)
	{
		allocDate = null;
		prepareForCalculate(isMultiCurrency);
		calculatePayment(paymentTable, isMultiCurrency);
		calculateInvoice(invoiceTable, isMultiCurrency);
		calculateDifference();
	}
	
	/**
	 * Calculate selected payment total
	 * @param payment
	 * @param isMultiCurrency
	 * @return payment summary
	 */
	public String calculatePayment(IMiniTable payment, boolean isMultiCurrency)
	{
		if (log.isLoggable(Level.CONFIG)) log.config("");

		//  Payment
		totalPay = Env.ZERO;
		int rows = payment.getRowCount();
		m_noPayments = 0;
		for (int i = 0; i < rows; i++)
		{
			if (((Boolean)payment.getValueAt(i, 0)).booleanValue())
			{
				Timestamp ts = (Timestamp)payment.getValueAt(i, 1);
				if ( !isMultiCurrency )  // the converted amounts are only valid for the selected date
					allocDate = TimeUtil.max(allocDate, ts);
				BigDecimal bd = (BigDecimal)payment.getValueAt(i, i_payment);
				totalPay = totalPay.add(bd);  //  Applied Pay
				m_noPayments++;
				if (log.isLoggable(Level.FINE)) log.fine("Payment_" + i + " = " + bd + " - Total=" + totalPay);
			}
		}
		return getPaymentInfoText();
	}

	/**
	 * 
	 * @return summary info for payment selected and total applied
	 */
	public String getPaymentInfoText() {
		return String.valueOf(m_noPayments) + " - "
			+ Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(totalPay) + " ";
	}
	
	/**
	 * calculate selected invoice total
	 * @param invoice
	 * @param isMultiCurrency
	 * @return invoice summary
	 */
	public String calculateInvoice(IMiniTable invoice, boolean isMultiCurrency)
	{		
		//  Invoices
		totalInv = Env.ZERO;
		int rows = invoice.getRowCount();
		m_noInvoices = 0;

		for (int i = 0; i < rows; i++)
		{
			if (((Boolean)invoice.getValueAt(i, 0)).booleanValue())
			{
				Timestamp ts = (Timestamp)invoice.getValueAt(i, 1);
				if ( !isMultiCurrency )  // converted amounts only valid for selected date
					allocDate = TimeUtil.max(allocDate, ts);
				BigDecimal bd = (BigDecimal)invoice.getValueAt(i, i_applied);
				totalInv = totalInv.add(bd);  //  Applied Inv
				m_noInvoices++;
				if (log.isLoggable(Level.FINE)) log.fine("Invoice_" + i + " = " + bd + " - Total=" + totalPay);
			}
		}
		return getInvoiceInfoText();
	}

	/**
	 * 
	 * @return summary info for invoice selected and total applied
	 */
	public String getInvoiceInfoText() {
		return String.valueOf(m_noInvoices) + " - "
			+ Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(totalInv) + " ";
	}
	
	/**
	 * Save allocation data
	 * @param m_WindowNo
	 * @param dateTrx
	 * @param payment
	 * @param invoice
	 * @param trxName
	 * @return {@link MAllocationHdr}
	 */
	public MAllocationHdr saveData(int m_WindowNo, Timestamp dateTrx, IMiniTable payment, IMiniTable invoice, String trxName)
	{
		if (m_noInvoices + m_noPayments == 0)
			return null;

		//  fixed fields
		int AD_Client_ID = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "AD_Client_ID");
		int AD_Org_ID = Env.getContextAsInt(Env.getCtx(), m_WindowNo, "AD_Org_ID");
		int C_BPartner_ID = m_C_BPartner_ID;
		int C_Order_ID = 0;
		int C_CashLine_ID = 0;
		int C_Currency_ID = m_C_Currency_ID;	//	the allocation currency
		//
		if (AD_Org_ID == 0)
		{
			//ADialog.error(m_WindowNo, this, "Org0NotAllowed", null);
			throw new AdempiereException("@Org0NotAllowed@");
		}
		//
		if (log.isLoggable(Level.CONFIG)) log.config("Client=" + AD_Client_ID + ", Org=" + AD_Org_ID
			+ ", BPartner=" + C_BPartner_ID + ", Date=" + dateTrx);

		//  Payment - Loop and add them to paymentList/amountList
		int pRows = payment.getRowCount();
		ArrayList<Integer> paymentList = new ArrayList<Integer>(pRows);
		ArrayList<BigDecimal> amountList = new ArrayList<BigDecimal>(pRows);
		BigDecimal paymentAppliedAmt = Env.ZERO;
		for (int i = 0; i < pRows; i++)
		{
			//  Payment line is selected
			if (((Boolean)payment.getValueAt(i, 0)).booleanValue())
			{
				KeyNamePair pp = (KeyNamePair)payment.getValueAt(i, 2);   //  Value
				//  Payment variables
				int C_Payment_ID = pp.getKey();
				paymentList.add(Integer.valueOf(C_Payment_ID));
				//
				BigDecimal PaymentAmt = (BigDecimal)payment.getValueAt(i, i_payment);  //  Applied Payment
				amountList.add(PaymentAmt);
				//
				paymentAppliedAmt = paymentAppliedAmt.add(PaymentAmt);
				//
				if (log.isLoggable(Level.FINE)) log.fine("C_Payment_ID=" + C_Payment_ID 
					+ " - PaymentAmt=" + PaymentAmt); // + " * " + Multiplier + " = " + PaymentAmtAbs);
			}
		}
		if (log.isLoggable(Level.CONFIG)) log.config("Number of Payments=" + paymentList.size() + " - Total=" + paymentAppliedAmt);

		//  Invoices - Loop and generate allocations
		int iRows = invoice.getRowCount();
		
		//	Create Allocation
		MAllocationHdr alloc = new MAllocationHdr (Env.getCtx(), true,	//	manual
			dateTrx, C_Currency_ID, Env.getContext(Env.getCtx(), Env.AD_USER_NAME), trxName);
		alloc.setAD_Org_ID(AD_Org_ID);
		alloc.setC_DocType_ID(m_C_DocType_ID);
		alloc.setDescription(alloc.getDescriptionForManualAllocation(m_C_BPartner_ID, trxName));
		alloc.saveEx();
		//	For all invoices
		BigDecimal unmatchedApplied = Env.ZERO;
		for (int i = 0; i < iRows; i++)
		{
			//  Invoice line is selected
			if (((Boolean)invoice.getValueAt(i, 0)).booleanValue())
			{
				KeyNamePair pp = (KeyNamePair)invoice.getValueAt(i, 2);    //  Value
				//  Invoice variables
				int C_Invoice_ID = pp.getKey();
				BigDecimal AppliedAmt = (BigDecimal)invoice.getValueAt(i, i_applied);
				//  semi-fixed fields (reset after first invoice)
				BigDecimal DiscountAmt = (BigDecimal)invoice.getValueAt(i, i_discount);
				BigDecimal WriteOffAmt = (BigDecimal)invoice.getValueAt(i, i_writeOff);
				//	OverUnderAmt needs to be in Allocation Currency
				BigDecimal OverUnderAmt = ((BigDecimal)invoice.getValueAt(i, i_open))
					.subtract(AppliedAmt).subtract(DiscountAmt).subtract(WriteOffAmt);
				
				if (log.isLoggable(Level.CONFIG)) log.config("Invoice #" + i + " - AppliedAmt=" + AppliedAmt);// + " -> " + AppliedAbs);
				//  loop through all payments until invoice applied
				
				for (int j = 0; j < paymentList.size() && AppliedAmt.signum() != 0; j++)
				{
					int C_Payment_ID = ((Integer)paymentList.get(j)).intValue();
					BigDecimal PaymentAmt = (BigDecimal)amountList.get(j);
					if (PaymentAmt.signum() == AppliedAmt.signum())	// only match same sign (otherwise appliedAmt increases)
					{												// and not zero (appliedAmt was checked earlier)
						if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + PaymentAmt);
						
						BigDecimal amount = AppliedAmt;
						if (amount.abs().compareTo(PaymentAmt.abs()) > 0)  // if there's more open on the invoice
							amount = PaymentAmt;							// than left in the payment
						
						//	Allocation Line
						MAllocationLine aLine = new MAllocationLine (alloc, amount, 
							DiscountAmt, WriteOffAmt, OverUnderAmt);
						aLine.setDocInfo(C_BPartner_ID, C_Order_ID, C_Invoice_ID);
						aLine.setPaymentInfo(C_Payment_ID, C_CashLine_ID);
						aLine.saveEx();

						//  Apply Discounts and WriteOff only first time
						DiscountAmt = Env.ZERO;
						WriteOffAmt = Env.ZERO;
						//  subtract amount from Payment/Invoice
						AppliedAmt = AppliedAmt.subtract(amount);
						PaymentAmt = PaymentAmt.subtract(amount);
						if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + PaymentAmt);
						amountList.set(j, PaymentAmt);  //  update
					}	//	for all applied amounts
				}	//	loop through payments for invoice
				
				if ( AppliedAmt.signum() == 0 && DiscountAmt.signum() == 0 && WriteOffAmt.signum() == 0)
					continue;
				else {			// remainder will need to match against other invoices
					int C_Payment_ID = 0;
					
					//	Allocation Line
					MAllocationLine aLine = new MAllocationLine (alloc, AppliedAmt, 
						DiscountAmt, WriteOffAmt, OverUnderAmt);
					aLine.setDocInfo(C_BPartner_ID, C_Order_ID, C_Invoice_ID);
					aLine.setPaymentInfo(C_Payment_ID, C_CashLine_ID);
					aLine.saveEx();
					if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + AppliedAmt);
					unmatchedApplied = unmatchedApplied.add(AppliedAmt);
				}
			}   //  invoice selected
		}   //  invoice loop

		// check for unapplied payment amounts (eg from payment reversals)
		for (int i = 0; i < paymentList.size(); i++)	{
			BigDecimal payAmt = (BigDecimal) amountList.get(i);
			if ( payAmt.signum() == 0 )
					continue;
			int C_Payment_ID = ((Integer)paymentList.get(i)).intValue();
			if (log.isLoggable(Level.FINE)) log.fine("Payment=" + C_Payment_ID  
					+ ", Amount=" + payAmt);

			//	Allocation Line
			MAllocationLine aLine = new MAllocationLine (alloc, payAmt, 
				Env.ZERO, Env.ZERO, Env.ZERO);
			aLine.setDocInfo(C_BPartner_ID, 0, 0);
			aLine.setPaymentInfo(C_Payment_ID, 0);
			aLine.saveEx();
			unmatchedApplied = unmatchedApplied.subtract(payAmt);
		}		
		
		// check for charge amount
		if ( m_C_Charge_ID > 0 && unmatchedApplied.compareTo(Env.ZERO) != 0 )
		{
			BigDecimal chargeAmt = totalDiff;
	
		//	Allocation Line
			MAllocationLine aLine = new MAllocationLine (alloc, chargeAmt.negate(), 
				Env.ZERO, Env.ZERO, Env.ZERO);
			aLine.setC_Charge_ID(m_C_Charge_ID);
			aLine.setC_BPartner_ID(m_C_BPartner_ID);
			if (!aLine.save(trxName)) {
				StringBuilder msg = new StringBuilder("Allocation Line not saved - Charge=").append(m_C_Charge_ID);
				throw new AdempiereException(msg.toString());
			}
			unmatchedApplied = unmatchedApplied.add(chargeAmt);
		}	
		
		if ( unmatchedApplied.signum() != 0 )
			throw new AdempiereException("Allocation not balanced -- out by " + unmatchedApplied);

		//	Should start WF
		if (alloc.get_ID() != 0)
		{
			if (!alloc.processIt(DocAction.ACTION_Complete))
				throw new AdempiereException("Cannot complete allocation: " + alloc.getProcessMsg());
			alloc.saveEx();
		}
		
		//  Test/Set IsPaid for Invoice - requires that allocation is posted
		for (int i = 0; i < iRows; i++)
		{
			//  Invoice line is selected
			if (((Boolean)invoice.getValueAt(i, 0)).booleanValue())
			{
				KeyNamePair pp = (KeyNamePair)invoice.getValueAt(i, 2);    //  Value
				//  Invoice variables
				int C_Invoice_ID = pp.getKey();
				String sql = "SELECT invoiceOpen(C_Invoice_ID, 0) "
					+ "FROM C_Invoice WHERE C_Invoice_ID=?";
				BigDecimal open = DB.getSQLValueBD(trxName, sql, C_Invoice_ID);
				if (open != null && open.signum() == 0)	 {
					sql = "UPDATE C_Invoice SET IsPaid='Y' "
						+ "WHERE C_Invoice_ID=" + C_Invoice_ID;
					int no = DB.executeUpdate(sql, trxName);
					if (log.isLoggable(Level.CONFIG)) log.config("Invoice #" + i + " is paid - updated=" + no);
				} else {
					if (log.isLoggable(Level.CONFIG)) log.config("Invoice #" + i + " is not paid - " + open);
				}
			}
		}
		//  Test/Set Payment is fully allocated
		for (int i = 0; i < paymentList.size(); i++)
		{
			int C_Payment_ID = ((Integer)paymentList.get(i)).intValue();
			MPayment pay = new MPayment (Env.getCtx(), C_Payment_ID, trxName);
			if (pay.testAllocation())
				pay.saveEx();
			if (log.isLoggable(Level.CONFIG)) log.config("Payment #" + i + (pay.isAllocated() ? " not" : " is") 
					+ " fully allocated");
		}
		paymentList.clear();
		amountList.clear();
		
		return alloc;
	}   //  saveData

	/**
	 * 
	 * @return C_BPartner_ID
	 */
	public int getC_BPartner_ID() {
		return m_C_BPartner_ID;
	}

	/**
	 * 
	 * @param C_BPartner_ID
	 */
	public void setC_BPartner_ID(int C_BPartner_ID) {
		this.m_C_BPartner_ID = C_BPartner_ID;
	}

	/**
	 * 
	 * @return C_Currency_ID
	 */
	public int getC_Currency_ID() {
		return m_C_Currency_ID;
	}

	/**
	 * 
	 * @param C_Currency_ID
	 */
	public void setC_Currency_ID(int C_Currency_ID) {
		this.m_C_Currency_ID = C_Currency_ID;
	}

	/**
	 * 
	 * @return C_DocType_ID
	 */
	public int getC_DocType_ID() {
		return m_C_DocType_ID;
	}

	/**
	 * 
	 * @param C_DocType_ID
	 */
	public void setC_DocType_ID(int C_DocType_ID) {
		this.m_C_DocType_ID = C_DocType_ID;
	}

	/**
	 * 
	 * @return C_Charge_ID
	 */
	public int getC_Charge_ID() {
		return m_C_Charge_ID;
	}

	/**
	 * 
	 * @param C_Charge_ID
	 */
	public void setC_Charge_ID(int C_Charge_ID) {
		this.m_C_Charge_ID = C_Charge_ID;
	}

	/**
	 * 
	 * @return AD_Org_ID
	 */
	public int getAD_Org_ID() {
		return m_AD_Org_ID;
	}

	/**
	 * 
	 * @param AD_Org_ID
	 */
	public void setAD_Org_ID(int AD_Org_ID) {
		this.m_AD_Org_ID = AD_Org_ID;
	}

	/**
	 * 
	 * @return number of selected invoice
	 */
	public int getSelectedInvoiceCount() {
		return m_noInvoices;
	}

	/**
	 * 
	 * @return number of selected payment
	 */
	public int getSelectedPaymentCount() {
		return m_noPayments;
	}

	/**
	 * 
	 * @return total of invoice applied amount
	 */
	public BigDecimal getInvoiceAppliedTotal() {
		return totalInv;
	}

	/**
	 * 
	 * @return total of payment applied amount
	 */
	public BigDecimal getPaymentAppliedTotal() {
		return totalPay;
	}

	/**
	 * 
	 * @return true if all condition is meet to proceed with allocation
	 */
	public boolean isOkToAllocate() {
		return totalDiff.signum() == 0 || getC_Charge_ID() > 0;
	}

	/**
	 * 
	 * @return difference between invoice and payment applied amount
	 */
	public BigDecimal getTotalDifference() {
		return totalDiff;
	}

	/**
	 * calculate difference between invoice and payment applied amount
	 */
	public void calculateDifference() {
		totalDiff = totalPay.subtract(totalInv);
	}
	
	public DefaultTreeNode parent(MPPProductBOMLine bomline, String weekly, int period) 
	{

		
		MProduct M_Product = MProduct.get(bomline.getCtx(), bomline.getM_Product_ID());
		MPPProductBOM bomproduct = new MPPProductBOM(bomline.getCtx(), bomline.getPP_Product_BOM_ID(), null);
		DefaultTreeNode parent = new DefaultTreeNode(productSummary(M_Product, false), new ArrayList());

		Vector<Object> line = new Vector<Object>(17);
//  4 ValidTo
		KeyNamePair pp = new KeyNamePair(M_Product.getM_Product_ID(),M_Product.getName());
		line.add(pp); //  5 M_Product_ID
	
		dataBOM.add(line);
		System.out.println("ddsdsd"+dataBOM);

		for (MPPProductBOM bom : MPPProductBOM.getProductBOMs((MProduct) bomproduct.getM_Product()))
		{
			MProduct component = MProduct.get(bomline.getCtx(), bom.getM_Product_ID());
			return component(component,weekly,period);
		}
		return parent;
	}
	
	public String productSummary(MPPProductBOM bom) {
		String value = bom.getValue();
		String name = bom.get_Translation(MPPProductBOM.COLUMNNAME_Name);
		//
		StringBuffer sb = new StringBuffer(value);
		if (name != null && !name.equals(value))
			sb.append("_").append(name);
		//
		return sb.toString();
	}
	
	public DefaultTreeNode parent(MPPProductBOM bom,String weekly, int period) 
	{
		DefaultTreeNode parent = new DefaultTreeNode(productSummary(bom), new ArrayList()); 

		for (MPPProductBOMLine bomline : bom.getLines())
		{
			MProduct component = MProduct.get(bomline.getCtx(), bomline.getM_Product_ID());
			Vector<Object> line = new Vector<Object>(17);
		
		    KeyNamePair namePair = new KeyNamePair(component.getM_Product_ID(), component.getName());
			line.add(namePair);
			
			line.add(DB.getSQLValueBD(null, "select coalesce(sum(qtyonhand),0) from m_storage where m_product_id = ?", component.getM_Product_ID()));
			line.add(bomline.getQtyBOM());
			line.add(DB.getSQLValueString(null, "select isbom from m_product where m_product_id = ?", component.getM_Product_ID()));
			line.add(bomline.getLine());
			if(Integer.parseInt(weekly)>0) {
			for (int i = 5; i <= Integer.parseInt(weekly)+6; i++) {
				line.add(BigDecimal.ZERO);
			}
			}else {
				for (int i = 5; i <= periodcolumn(period)+6; i++) {
					line.add(BigDecimal.ZERO);
				}
			}
			
			//  5 M_Product_ID
			
			dataBOM.add(line);
			parent.getChildren().add(component(component,weekly,period));

		}
		return parent;
	}
	
	public String productSummary(MProduct product, boolean isLeaf) {
		MUOM uom = MUOM.get(product.getCtx(), product.getC_UOM_ID());
		String value = product.getValue();
		String name = product.get_Translation(MProduct.COLUMNNAME_Name);
		//
		StringBuffer sb = new StringBuffer(value);
		if (name != null && !value.equals(name))
			sb.append("_").append(product.getName());
		sb.append(" [").append(uom.get_Translation(MUOM.COLUMNNAME_UOMSymbol)).append("]");
		//
		return sb.toString();
	}
	
	public DefaultTreeNode component(MProduct product, String weekly, int period) 
	{   

	
			for (MPPProductBOM bom : MPPProductBOM.getProductBOMs(product))
			{
				return parent(bom,weekly,period);
			}  
			return new DefaultTreeNode(productSummary(product, true), new ArrayList());
		
	}
}
