package org.libero.process;




import java.util.logging.Level;

import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.eevolution.model.MPPProductBOM;
import org.eevolution.model.MPPProductBOMLine;
import org.eevolution.model.MPPProductPlanning;




public class CalculateProductPlanningFromSalesOrder extends SvrProcess  {
	
	    private int  p_AD_Workflow_ID = 0;
	// Statistics 
		private int count_created = 0;
		private int count_updated = 0;
		private int count_error = 0;
		MOrder order = null;

	@Override
	protected void prepare() {
		for (ProcessInfoParameter para : getParameter())
		{
			String name = para.getParameterName();
			if (para.getParameter() == null);				
			else if (name.equals(MPPProductPlanning.COLUMNNAME_AD_Workflow_ID))
			{    
				p_AD_Workflow_ID =  para.getParameterAsInt();
			}		
			
			else
			{
				log.log(Level.SEVERE, "prepare - Unknown Parameter: " + name);
			}
		}
		order =  new MOrder(getCtx(), getRecord_ID(), get_TrxName());
	}

	@Override
	protected String doIt() throws Exception {
		//MOrder order =  new MOrder(getCtx(), getRecord_ID(), get_TrxName());
		for (MOrderLine orderLine : order.getLines() ) {
			MProduct M_Product = MProduct.get(getCtx(), orderLine.getM_Product_ID());
			for (MPPProductBOM bom : MPPProductBOM.getProductBOMs(M_Product))
			{
				createPlanning(M_Product.getM_Product_ID(), order, bom);
				parent(bom);
			}
		}
		
		
		return "@Created@ #"+count_created
				+" @Updated@ #"+count_updated
				+" @Error@ #"+count_error;
	}
	
	
	
	public void parent(MPPProductBOM bom) 
	{
		for (MPPProductBOMLine bomline : bom.getLines())
		{
			MProduct component = MProduct.get(getCtx(), bomline.getM_Product_ID());
			createPlanning(component.getM_Product_ID(), order, bom);
	         component(component);
		}
	}
	
	public void component(MProduct product) 
	{   
	    for (MPPProductBOM bom : MPPProductBOM.getProductBOMs(product))
			{
			 parent(bom);
			}  
			
		
	}
	private void createPlanning(int M_Product_ID,MOrder order,MPPProductBOM bom)
	{
		MPPProductPlanning pp = MPPProductPlanning.get(getCtx(),getAD_Client_ID() , order.getAD_Org_ID() , order.getM_Warehouse_ID(), 1000000,M_Product_ID, get_TrxName());
		boolean isNew = pp == null;
		// Create Product Data Planning
		if (pp == null)
		{
			pp = new MPPProductPlanning(getCtx(), 0, get_TrxName());                  
			pp.setAD_Org_ID(order.getAD_Org_ID());
			pp.setM_Warehouse_ID(order.getM_Warehouse_ID());
			pp.setS_Resource_ID(1000000);
			pp.setM_Product_ID(M_Product_ID);
		}
		pp.setDD_NetworkDistribution_ID (0);		
		pp.setAD_Workflow_ID(p_AD_Workflow_ID);
		pp.setPP_Product_BOM_ID(bom.getPP_Product_BOM_ID());
		pp.setIsCreatePlan(true);                                                                         
		pp.setIsMPS(false);                                    
		pp.setIsRequiredMRP(true);
		pp.setIsRequiredDRP(true);; 
		pp.setPlanner_ID(getAD_User_ID());
		pp.setOrder_Policy("LFL");
		pp.setIsPhantom(false);                                                                                      
		//
		if (!pp.save())
			count_error++;
		if (isNew)
			count_created++;
		else
			count_updated++;
	}
	
	

}
