package org.libero.process;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MDocType;
import org.compiere.model.MLocator;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MProduct;
import org.compiere.model.MStorageOnHand;
import org.compiere.model.MWarehouse;
import org.compiere.model.Query;


import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.eevolution.model.MDDOrder;
import org.eevolution.model.MDDOrderLine;

public class InterWHCreateOutbound extends SvrProcess {

	private int p_Locator = 0;
	private int p_C_DocType_ID = 0;
	private int p_DD_Order_ID = 0;
	private Timestamp p_MovementDate = null;


	
	protected void prepare() {
		
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("M_Locator_ID"))
				p_Locator = para[i].getParameterAsInt();
			
			else if (name.equals("C_DocType_ID"))
				p_C_DocType_ID = para[i].getParameterAsInt();
			else if (name.equals("DD_Order_ID"))
				p_DD_Order_ID = para[i].getParameterAsInt();
			else if (name.equals("MovementDate"))
				p_MovementDate = para[i].getParameterAsTimestamp();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	}

	protected String doIt() throws Exception {
		
		//Validate DD_Order_ID
		if (p_DD_Order_ID <= 0) {
			return "Error: No Selected Inter-warehouse Document";
		}
		
		if (p_MovementDate == null) {
			return "Error: No Movement Date";
		}
		
		//Validate status of InterWarehouse Movement= CO
		MDDOrder interWH = new MDDOrder(getCtx(), p_DD_Order_ID, get_TrxName());
		if (!interWH.getDocStatus().equals(DocAction.ACTION_Complete)) {
			return "Error: Only Completed Inter-warehouse Document Can be Processed";
		}
		
		/* Custom from FBI?
		 
		//Validate Transit Warehouse Is Set
		String sqlCheck = "SELECT AD_OrgInv_ID FROM AD_InventoryOrg WHERE AD_InventoryOrg_ID=?";
		int OrgTo = DB.getSQLValueEx(get_TrxName(), sqlCheck, new Object[]{interWH.get_ValueAsInt("AD_InventoryOrg_ID")});

		MOrgInfo orgInfo = MOrgInfo.get(getCtx(), OrgTo, get_TrxName());
		
		if (orgInfo.get_ValueAsInt("Transit_Warehouse_ID") <= 0) {
			MOrg org = new MOrg(getCtx(), interWH.getAD_Org_ID(), get_TrxName());
			return "Missing Setup: Transit Warehouse for Org "+ org.getName();
		}
		*/
		
		//only allow running process for role with access to warehouse destination
//		boolean match = new Query(getCtx(), X_AD_Role_WHAccess.Table_Name, "AD_Role_ID=? AND M_Warehouse_ID=?", get_TrxName())
//						.setParameters(Env.getContextAsInt(getCtx(), Env.AD_ROLE_ID), interWH.getM_Warehouse_ID())
//						.setOnlyActiveRecords(true)
//						.match();
//		
//		if (!match) {
//			return "Error: User Role Does Not Access to Warehouse Source";
//		}
			
		/*Bug #2990 Create multiple Outbound and Inbound		
		//Check whether an existing inbound is already exists
		if (interWH.get_ValueAsInt("M_MovementTo_ID") > 0) {
			return "Outbound Movement Has Been Created";
		}
		*/

		//Validate DocType = Material Movement
		if (p_C_DocType_ID <= 0) {
			return "Error: No Document Type Selected for Inbound Movement";
		} else {
			MDocType docType = new MDocType(getCtx(), p_C_DocType_ID, get_TrxName());
			if (!docType.getDocBaseType().equals(MDocType.DOCBASETYPE_MaterialMovement)) {
				return "Error: Selected Document Type Is Not Material Movement";
			}
		}
		
		//Validate Locator
		if (p_Locator <= 0) {
			return "Error: No Source Locator Selected";
		} else {
			MLocator locator = new MLocator(getCtx(), p_Locator, get_TrxName());

			if (locator.getM_Warehouse_ID()!= interWH.getM_Warehouse_ID()) {
				return "Error: Selected Locator Is Not in The Source Warehouse";
			}
			//@David Commented because warehouse zone not implemented yet
			/*else if (locator.getM_WarehouseZone_ID()!=interWH.getM_WarehouseZone_ID()) {
				return "Error: Selected Locator Is Not in The Source Warehouse Zone";
			}*/
		}
		if(DB.getSQLValue(get_TrxName(), "select count(*) from m_movement where dd_order_id = " + p_DD_Order_ID +" and docstatus in ('DR','IN') and IsOutbound = 'Y'") > 0) {
			return "Error: Movement Created Draft ";
		}
		
		
 
		MWarehouse whFrom = new MWarehouse(getCtx(), interWH.getM_Warehouse_ID(), get_TrxName());
		MWarehouse whTo = new MWarehouse(getCtx(), interWH.get_ValueAsInt("M_WarehouseTo_ID"), get_TrxName());
		MWarehouse whTransit;
//		if (whFrom.getAD_Org_ID() == whTo.getAD_Org_ID()) {
//			whTransit = whFrom;
//		}
//		else {
//			String sqlWHTransit = "SELECT M_Warehouse_ID FROM M_Warehouse WHERE IsIntransit='Y' AND IsActive='Y' AND AD_Org_ID="+whFrom.getAD_Org_ID();
			String sqlWHTransit = "SELECT M_Warehouse_ID FROM M_Warehouse WHERE IsIntransit='Y' AND IsActive='Y' AND AD_Org_ID="+whTo.getAD_Org_ID();
			int M_WareHouse_InTransit_ID = DB.getSQLValue(get_TrxName(), sqlWHTransit);
			if (M_WareHouse_InTransit_ID<=0){
				MOrg org = new MOrg(getCtx(), whTo.getAD_Org_ID(), get_TrxName());
				throw new AdempiereException("Warehouse.InTransit='Y' of organization "+org.getName()+" not exist");
			}
			whTransit = new MWarehouse(getCtx(), M_WareHouse_InTransit_ID, get_TrxName());	
//		}
		String sqlLocatorTransit = "SELECT M_Locator_ID FROM M_Locator WHERE  IsActive='Y' AND M_Warehouse_ID="+whTransit.getM_Warehouse_ID();
		int locator_InTransit_ID = DB.getSQLValue(get_TrxName(), sqlLocatorTransit);
		if (locator_InTransit_ID<=0)
			throw new AdempiereException("Locator.IsIntransit='Y' of warehouse "+whTransit.getName()+" not exist");

		
		//int M_WarehouseTo_ID = interWH.get_ValueAsInt("M_WarehouseTo_ID");
		int M_WarehouseTo_ID = whTransit.getM_Warehouse_ID();
		
		//Create outbound movement
		MMovement outbound = new MMovement(getCtx(), 0, get_TrxName());		
		outbound.setAD_Org_ID(interWH.getAD_Org_ID());
		if (interWH.getAD_OrgTrx_ID() > 0) {
			outbound.setAD_OrgTrx_ID(interWH.getAD_OrgTrx_ID());
		}
		outbound.setMovementDate(p_MovementDate);
		outbound.setC_Project_ID(interWH.getC_Project_ID());
		outbound.setC_BPartner_ID(interWH.getC_BPartner_ID());
		outbound.setC_BPartner_Location_ID(interWH.getC_BPartner_Location_ID());
		outbound.setM_Shipper_ID(interWH.getM_Shipper_ID());
		outbound.setDocAction(DocAction.ACTION_Complete);
		outbound.setDocStatus(DocAction.STATUS_Drafted);
		outbound.setC_DocType_ID(p_C_DocType_ID);
		outbound.set_ValueOfColumn("M_Warehouse_ID", interWH.getM_Warehouse_ID());
		//outbound.setM_WarehouseZone_ID(interWH.getM_WarehouseZone_ID());
		//outbound.setM_WarehouseTo_ID(orgInfo.getTransit_Warehouse_ID());
		//outbound.set_ValueOfColumn("M_WarehouseTo_ID", orgInfo.get_ValueAsInt("Transit_Warehouse_ID"));
		outbound.set_ValueOfColumn("M_WarehouseTo_ID", M_WarehouseTo_ID);
		outbound.setDD_Order_ID(interWH.getDD_Order_ID());
		outbound.set_ValueOfColumn("IsOutbound", "Y");
		outbound.saveEx();
		
		//Create outbound movement lines
		MDDOrderLine[] lines = interWH.getLines();
		//MWarehouse whTransit = new MWarehouse(getCtx(), orgInfo.getTransit_Warehouse_ID(), get_TrxName());
		//MWarehouse whTransit = new MWarehouse(getCtx(), orgInfo.get_ValueAsInt("Transit_Warehouse_ID"), get_TrxName());
		
		//@David
		//Warehouse In-Transit
		/*Case 1
		 * M_WarehouseFrom.AD_Org_ID == M_WarehouseTo.AD_Org_ID
		 *
		 *Case 2
		 * M_WarehouseFrom.AD_Org_ID != M_WarehouseTo.AD_Org_ID
		 */

		//MLocator locatorTransit = whTransit.getDefaultLocator();
		//MWarehouse whSource = new MWarehouse(getCtx(), outbound.getM_Warehouse_ID(), get_TrxName());
		MWarehouse whSource = new MWarehouse(getCtx(), outbound.get_ValueAsInt("M_Warehouse_ID"), get_TrxName());
		
		for (MDDOrderLine line : lines) {
			
			//moveLine.setQtyEntered(line.getQtyEntered());
			
			String sqlQtyEntWhere = "DD_OrderLine_ID="+line.getDD_OrderLine_ID()+" AND IsOutbound='Y' AND DocStatus IN ('CO','CL')";
//			BigDecimal qtyEntOutbound = new Query(getCtx(), MMovementLine.Table_Name, sqlQtyEntWhere, get_TrxName())
//									.addJoinClause("JOIN M_Movement ON M_Movement.M_Movement_ID = M_MovementLine.M_Movement_ID")
//									.sum("MovementQty");
			
			String jml = line.get_ValueAsString("QtyOutbound");
			BigDecimal qtyEntOutbound =  new BigDecimal(jml);
			
			if(line.getQtyEntered().subtract(qtyEntOutbound).compareTo(BigDecimal.ZERO)>0) {
			MMovementLine moveLine = new MMovementLine(outbound);
			moveLine.setLine(line.getLine());
			moveLine.setM_Product_ID(line.getM_Product_ID());
			
			//moveLine.set_CustomColumn("QtyEntered", line.getQtyEntered().subtract(qtyEntOutbound));
			
	
			
			//@David
			//Set Qty = DD_OrderLine.Qty - SUM(M_MovementLine.Qty, CO & CL only
			//moveLine.setMovementQty(line.getQtyEntered());
//			String sqlQtyWhere = "DD_OrderLine_ID="+line.getDD_OrderLine_ID()+" AND IsOutbound='Y' AND DocStatus IN ('CO','CL')";
//			BigDecimal qtyOutbound = new Query(getCtx(), MMovementLine.Table_Name, sqlQtyWhere, get_TrxName())
//									.addJoinClause("JOIN M_Movement ON M_Movement.M_Movement_ID = M_MovementLine.M_Movement_ID")
//									.sum("MovementQty");
//			BigDecimal qtyMove = line.getQtyOrdered().subtract(qtyOutbound);
//			System.out.println(qtyMove);
//			if (qtyMove.signum()<1) {
//				continue;
//			}
			moveLine.setMovementQty(line.getQtyEntered().subtract(qtyEntOutbound));
			
			//Comment check negative inventory because process leave outbound as drafted
			
//			if (moveLine.getM_Product_ID() > 0 && isDisallowNegativeInv(whSource)) {
//				//@David Commented because warehouse zone not implemented yet
//				/*BigDecimal qtyOnHand = MStorageOnHand.getQtyOnHandForWarehouseZone(moveLine.getM_Product_ID(),
//						outbound.getM_WarehouseZone_ID(), 0, get_TrxName());
//				*/
//				BigDecimal qtyOnHand = MStorageOnHand.getQtyOnHandForLocator(moveLine.getM_Product_ID(),
//						outbound.get_ValueAsInt("M_Locator_ID"), 0, get_TrxName());
//				MProduct product = MProduct.get(getCtx(), moveLine.getM_Product_ID());
//				
//				if (qtyOnHand.compareTo((BigDecimal)moveLine.get_Value("QtyEntered")) < 0)	
//					return product.getName() + " Out Of Stock";
//			}
			if(line.getM_AttributeSetInstance_ID() > 0) {
			moveLine.setM_AttributeSetInstance_ID(line.getM_AttributeSetInstance_ID());
			}
			moveLine.setM_Locator_ID(p_Locator);
//			moveLine.setM_LocatorTo_ID(locatorTransit.get_ID());
			moveLine.setM_LocatorTo_ID(locator_InTransit_ID);
			moveLine.set_ValueOfColumn("C_UOM_ID", line.getC_UOM_ID());
			moveLine.setDD_OrderLine_ID(line.getDD_OrderLine_ID());
			moveLine.saveEx();
		}
		}
		/*leave as drafted to allow partial outbound
		//Complete movement
		//outbound.processIt(DocAction.ACTION_Complete);
		 */
		outbound.saveEx();

		//Bug #2990 Create multiple Outbound and Inbound
		//interWH.set_ValueOfColumn("M_MovementTo_ID", outbound.get_ID());
		interWH.saveEx();
		
		String message = Msg.parseTranslation(getCtx(), "@GeneratedOutbound@"+ outbound.getDocumentNo());
		addBufferLog(0, null, null, message, outbound.get_Table_ID(),outbound.getM_Movement_ID());

		return "Successfully Created Outbound Movement #" + outbound.getDocumentNo();
		
	}

	public boolean isDisallowNegativeInv(MWarehouse warehouse){
		if(warehouse.isDisallowNegativeInv())
			return true;
		
		return false;
	}
	
}
