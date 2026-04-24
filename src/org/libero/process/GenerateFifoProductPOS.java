package org.libero.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProductPrice;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfo;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;

public class GenerateFifoProductPOS extends SvrProcess{
    
	MOrder order;
	@Override
	protected void prepare() {
		
		
	}

	@Override
	protected String doIt() throws Exception {
		order = new MOrder(getCtx(), getRecord_ID(), get_TrxName());
		for (MOrderLine orderLine : order.getLines()) {
			if(orderLine.getM_Product().getM_AttributeSet_ID()>0) {
				 getlinefifo(orderLine.getM_Product_ID(), order, orderLine.getQtyEntered(), orderLine.getC_OrderLine_ID(), 
						   DB.getSQLValue(null, "select m_locator_id from m_locator where m_warehouse_id = "+order.getM_Warehouse_ID()+" and isdefault = 'Y'"));					
			   orderLine.delete(false);
			}
			
		}
		
		order.setDocAction(DocAction.ACTION_Complete);
		if (order.processIt(DocAction.ACTION_Complete)) {
			if (log.isLoggable(Level.FINE))
				log.fine("Order: " + order.getDocumentNo() + " completed fine");
			  
		} else
			throw new IllegalStateException("Order: " + order.getDocumentNo() + " Did not complete");

		order.saveEx();
		
		return null;
	}


	
	private void getlinefifo(int m_product_id,MOrder order,BigDecimal qty, int lineid, int m_locator_id){
		String sql = "select ms.qtyonhand,mas.m_attributesetinstance_id from m_storage ms " + 
				" inner join m_attributesetinstance mas on mas.m_attributesetinstance_id = ms.m_attributesetinstance_id" + 
				" where m_product_id = ? and ms.m_locator_id = ? and ms.datematerialpolicy is not null and ms.qtyonhand > 0" + 
				" order by ms.datematerialpolicy,mas.serno";
		PreparedStatement preparedStatement = null;
		ResultSet rs =null;
		BigDecimal totalqty = qty;
		try {
			preparedStatement = DB.prepareStatement(sql, get_TrxName());
			preparedStatement.setInt(1, m_product_id);
			preparedStatement.setInt(2, m_locator_id);
			rs = preparedStatement.executeQuery();
			MProductPrice price = MProductPrice.get(order.getCtx(), DB.getSQLValue(get_TrxName(), "select M_PriceList_Version_ID from M_PriceList_Version where M_PriceList_ID = ?",order.getM_PriceList_ID()), m_product_id, get_TrxName());
			while (rs.next()) {
				if(totalqty.compareTo(BigDecimal.ZERO)> 0) {
				MOrderLine line = new MOrderLine(order);
				line.setAD_Org_ID(order.getAD_Org_ID());
				line.setM_Product_ID(m_product_id);
				line.setPriceEntered(price.getPriceList());
				line.setPriceActual(price.getPriceList());
				line.setPriceList(price.getPriceList());
				if(totalqty.compareTo(rs.getBigDecimal(1))>0) {
				line.setQtyEntered(rs.getBigDecimal(1));
				line.setQtyOrdered(rs.getBigDecimal(1));
				line.setLineNetAmt(rs.getBigDecimal(1).multiply(price.getPriceList()));
				}else {
				line.setQtyEntered(totalqty);
				line.setQtyOrdered(totalqty);
				line.setLineNetAmt(totalqty.multiply(price.getPriceList()));
				}
				line.setM_AttributeSetInstance_ID(rs.getInt(2));
				line.setC_Tax_ID(DB.getSQLValue(get_TrxName(), "select c_tax_id from c_orderline where c_orderline_id = ?", lineid));
				line.save(get_TrxName());
				totalqty = totalqty.subtract(rs.getBigDecimal(1));
				}
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			try {
				rs.close();
				preparedStatement.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}


}
