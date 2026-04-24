package org.libero.process;

import java.math.BigDecimal;

import org.compiere.model.MAcctSchema;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MCost;
import org.compiere.model.MCostElement;
import org.compiere.model.MCostQueue;
import org.compiere.model.MLot;
import org.compiere.model.MLotCtl;
import org.compiere.model.MProduct;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.libero.model.MPPOrder;

public class GenerateAttributeMO extends SvrProcess {

	@Override
	protected void prepare() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected String doIt() throws Exception {
		MPPOrder mppOrder = new MPPOrder(getCtx(), getRecord_ID(), get_TrxName());
		GenerateCodeAttributepporder(mppOrder);
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public void costPPorder (MPPOrder pporder){
		MAcctSchema as = new MAcctSchema(getCtx(),1000000 , get_TrxName());	
		MProduct product = new MProduct(getCtx(), pporder.getM_Product_ID(), get_TrxName());
		 String costingMethod = product.getCostingMethod(as);
		MCostElement element = MCostElement.getByCostingMethod(getCtx(), costingMethod).get(0);
				if(pporder.getM_AttributeSetInstance_ID()>0) {					
						  
						  MCost cost = new MCost(product, pporder.getM_AttributeSetInstance_ID(),as, 0, element.getM_CostElement_ID());
						  cost.setCurrentCostPrice(BigDecimal.ZERO);
						  cost.save();
				}	 
		  
	}
	
	public  void costQueuePPorder (MPPOrder mppOrder){
		MAcctSchema acctSchema = new MAcctSchema(getCtx(),1000000, get_TrxName());
		MProduct product = new MProduct(getCtx(), mppOrder.getM_Product_ID(), get_TrxName());
		 String costingMethod = product.getCostingMethod(acctSchema);
		MCostElement element = MCostElement.getByCostingMethod(getCtx(), costingMethod).get(0);
				if(mppOrder.getM_AttributeSetInstance_ID()>0) {					
					     
						  MCostQueue costQueue = new MCostQueue(product, mppOrder.getM_AttributeSetInstance_ID(), acctSchema, 0, element.getM_CostElement_ID(), get_TrxName());
						  costQueue.setCurrentCostPrice(BigDecimal.ZERO);
						  costQueue.save();						
					
				}
			
			
		  
		
	}
	
	private void GenerateCodeAttributepporder(MPPOrder mppOrder) {
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
				
				MPPOrder Line = new MPPOrder(getCtx(), mppOrder.getPP_Order_ID(), get_TrxName());
				Line.setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
				if(Line.save()) {
					costPPorder(Line);
					costQueuePPorder(Line);
				}
			}
		}
		
	
}

}
