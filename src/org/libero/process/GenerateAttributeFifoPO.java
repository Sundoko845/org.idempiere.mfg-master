package org.libero.process;

import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MSerNoCtl;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;

public class GenerateAttributeFifoPO extends SvrProcess {

	@Override
	protected void prepare() {
		MOrder order = new MOrder(getCtx(), getRecord_ID(), get_TrxName());
		for (MOrderLine orderLine : order.getLines()) {
			if(orderLine.getM_Product().getM_AttributeSet_ID()>0 && orderLine.getM_AttributeSetInstance_ID()==0) {
			  GenerateCodeAttribute(orderLine);		
			}
			
		}
		
	}

	@Override
	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
	
	private void GenerateCodeAttribute(MOrderLine line) {
		MAttributeSetInstance attributeSetInstance = new MAttributeSetInstance(getCtx(), 0, get_TrxName());
		attributeSetInstance.setM_AttributeSet_ID(line.getM_Product().getM_AttributeSet_ID());
		attributeSetInstance.setSerNo(DB.getSQLValueString(null,"select mser.prefix||mser.currentnext as noprefix from m_product mp \n" + 
				" left join M_AttributeSet mat on mat.M_AttributeSet_ID = mp.M_AttributeSet_ID\n" + 
				" left join M_SerNoCtl mser on mser.M_SerNoCtl_ID = mat.M_SerNoCtl_ID \n" + 
				" where mp.m_product_id = ?" , line.getM_Product_ID())); 
		attributeSetInstance.setDescription(DB.getSQLValueString(null,"select '#'||mser.prefix||mser.currentnext as noprefix from m_product mp \n" + 
				" left join M_AttributeSet mat on mat.M_AttributeSet_ID = mp.M_AttributeSet_ID\n" + 
				" left join M_SerNoCtl mser on mser.M_SerNoCtl_ID = mat.M_SerNoCtl_ID \n" + 
				" where mp.m_product_id = ?" , line.getM_Product_ID()));
		attributeSetInstance.setAD_Org_ID(line.getAD_Org_ID());
		if(attributeSetInstance.save(get_TrxName())) {
			MSerNoCtl noCtl = new MSerNoCtl(getCtx(), line.getM_Product().getM_AttributeSet().getM_SerNoCtl_ID(), get_TrxName());
			noCtl.setCurrentNext(noCtl.getCurrentNext()+1);
			noCtl.setName(noCtl.getName());
			noCtl.save();
			MOrderLine Line = new MOrderLine(getCtx(), line.getC_OrderLine_ID(), get_TrxName());
			Line.setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
			Line.saveEx();
		}
	
}

}
