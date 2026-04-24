package org.libero.process;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.libero.model.MPPCostCollector;
import org.libero.model.MPPOrder;

public class ProsesVoidManufacturing extends SvrProcess {

	private int p_PP_Cost_Collector_ID = 0;
	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();

		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("PP_Cost_Collector_ID"))
			{
				p_PP_Cost_Collector_ID = ((BigDecimal) para[i].getParameter()).intValue();
			}
			else
				log.log(Level.SEVERE, "prepare - Unknown Parameter: " + name);
		}
		
	}

	@Override
	protected String doIt() throws Exception {
		
		
		if(p_PP_Cost_Collector_ID>0) {
			List<MPPCostCollector> list = new Query(getCtx(), "PP_Cost_Collector"," docstatus = 'CO' and pp_order_id = "+getRecord_ID()+" and description = '"+DB.getSQLValueString(null, "select description from pp_cost_collector where pp_cost_collector_id = ?", p_PP_Cost_Collector_ID)+"'",get_TrxName()).list();
			for (MPPCostCollector mppCostCollector : list) {
				mppCostCollector.voidIt();
				mppCostCollector.setDocStatus(MPPCostCollector.DOCSTATUS_Voided);
				mppCostCollector.setDocAction(MPPCostCollector.DOCACTION_Void);
				mppCostCollector.saveEx(get_TrxName());
			}
		}else {
		//MPPCostCollector collector = new MPPCostCollector(mppOrder);
			MPPOrder mppOrder = new MPPOrder(getCtx(), getRecord_ID(), get_TrxName());
			List<MPPCostCollector> list = new Query(getCtx(), "PP_Cost_Collector"," Docstatus = 'CO' and PP_Order_ID = " + mppOrder.getPP_Order_ID(),get_TrxName()).list();
			for (MPPCostCollector mppCostCollector : list) {
			mppCostCollector.voidIt();
			mppCostCollector.setDocStatus(MPPCostCollector.DOCSTATUS_Voided);
			mppCostCollector.setDocAction(MPPCostCollector.DOCACTION_Void);
			mppCostCollector.saveEx(get_TrxName());
		}
		}
		return null;
	}

}
