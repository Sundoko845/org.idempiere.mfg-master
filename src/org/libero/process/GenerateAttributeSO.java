package org.libero.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MLot;
import org.compiere.model.MLotCtl;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;






public class GenerateAttributeSO extends SvrProcess  {
	
	int jml = 0;

	@Override
	protected void prepare() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected String doIt() throws Exception {
		MOrder order = new MOrder(getCtx(), getRecord_ID(), get_TrxName());
		for (MOrderLine line : order.getLines()) {
		
	    GenerateCodeAttributepporder(line);
		}
		
		return null;
		
	}
	private void GenerateCodeAttributepporder(MOrderLine mppOrder) {
		MLot lot = new MLot(getCtx(), 0, get_TrxName());
		MLotCtl lotCtl = new MLotCtl(getCtx(), DB.getSQLValue(get_TrxName(), "select M_LotCtl_ID from M_AttributeSet ma left join m_product mp on mp.M_AttributeSet_ID = ma.M_AttributeSet_ID where mp.m_product_id = "+mppOrder.getM_Product_ID()), get_TrxName());
		lot.setM_Product_ID(mppOrder.getM_Product_ID());
		lot.setM_LotCtl_ID(lotCtl.getM_LotCtl_ID());
		lot.setName(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
		if(lot.save()) {
			MAttributeSetInstance attributeSetInstance = new MAttributeSetInstance(getCtx(), 0, get_TrxName());
			attributeSetInstance.setM_AttributeSet_ID(mppOrder.getM_Product().getM_AttributeSet_ID());
			attributeSetInstance.setM_Lot_ID(lot.getM_Lot_ID());
			attributeSetInstance.setAD_Org_ID(mppOrder.getAD_Org_ID());
			attributeSetInstance.setDescription(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
			attributeSetInstance.setLot(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
			if(attributeSetInstance.save(get_TrxName())) {
				
				lotCtl.setCurrentNext(lotCtl.getCurrentNext()+lotCtl.getIncrementNo());
				lotCtl.save();
				
				MOrderLine Line = new MOrderLine(getCtx(), mppOrder.getC_OrderLine_ID(), get_TrxName());
				Line.setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
				Line.save();
				
			}
		}
		
	
}


	

}
