package org.libero.process;

import java.sql.Timestamp;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MDocType;
import org.compiere.model.MLocator;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
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

public class InterWHCreateInbound extends SvrProcess {

	private int p_M_Warehouse_ID = 0;
	private int p_M_WarehouseZone_ID = 0;
	private int p_Locator = 0;
	private int p_C_DocType_ID = 0;
	private int p_DD_Order_ID = 0;
	private int p_M_MovementOutBound_ID = 0;
	private Timestamp p_MovementDate = null;
	
	protected void prepare() {
		
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("DD_Order_ID"))
				p_DD_Order_ID = para[i].getParameterAsInt();
			else if (name.equals("M_Warehouse_ID"))
				p_M_Warehouse_ID = para[i].getParameterAsInt();
			else if (name.equals("M_WarehouseZone_ID"))
				p_M_WarehouseZone_ID = para[i].getParameterAsInt();
			
			else if (name.equals("M_Locator_ID"))
				p_Locator = para[i].getParameterAsInt();
			else if (name.equals("C_DocType_ID"))
				p_C_DocType_ID = para[i].getParameterAsInt();
			else if (name.equals("M_Movement_ID"))
				p_M_MovementOutBound_ID = para[i].getParameterAsInt();
			else if (name.equals("MovementDate"))
				p_MovementDate = para[i].getParameterAsTimestamp();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
		//p_DD_Order_ID = getRecord_ID();
	}

	protected String doIt() throws Exception {
		
		//Validate DD_Order_ID
		if (p_DD_Order_ID <= 0) {
			return "Error: No Selected Inter-warehouse Document";
		}
		
		if (p_M_MovementOutBound_ID <= 0) {
			return "Error: No Outbound Document";
		}

		if (p_MovementDate == null) {
			return "Error: No Movement Date";
		}
		
		//Validate status of InterWarehouse Movement= CO
		MDDOrder interWH = new MDDOrder(getCtx(), p_DD_Order_ID, get_TrxName());
		if (!interWH.getDocStatus().equals(DocAction.ACTION_Complete)) {
			return "Error: Only Completed Inter-warehouse Document Can be Processed";
		}
		
		/*
		//Validate Transit Warehouse Is Set
		String sqlCheck = "SELECT AD_OrgInv_ID FROM AD_InventoryOrg WHERE AD_InventoryOrg_ID=?";
		int OrgTo = DB.getSQLValueEx(get_TrxName(), sqlCheck, new Object[]{interWH.get_ValueAsInt("AD_InventoryOrg_ID")});
		 
		MOrgInfo orgInfo = MOrgInfo.get(getCtx(), OrgTo, get_TrxName());

		//MOrgInfo orgInfo = MOrgInfo.get(getCtx(), interWH.getAD_Org_ID(), get_TrxName());
		if (orgInfo.get_ValueAsInt("Transit_Warehouse_ID") <= 0) {
			MOrg org = new MOrg(getCtx(), interWH.getAD_Org_ID(), get_TrxName());
			return "Missing Setup: Transit Warehouse for Org "+ org.getName();
		}
		*/
		
		//only allow running process for role with access to warehouse destination
//		boolean match = new Query(getCtx(), X_AD_Role_WHAccess.Table_Name, "AD_Role_ID=? AND M_Warehouse_ID=?", get_TrxName())
//						.setParameters(Env.getContextAsInt(getCtx(), Env.AD_ROLE_ID), p_M_Warehouse_ID)
//						.setOnlyActiveRecords(true)
//						.match();
//		
//		if (!match) {
//			return "Error: User Role Does Not Access to Warehouse Destination";
//		}
		
		/*Bug #2990 Create multiple Outbound and Inbound
		//Check whether an existing inbound is already exists
		if (interWH.get_ValueAsInt("M_MovementTo_ID") <= 0) {
			return "Error: Must Create Outbound Movement Before Create Inbound";
		}
		*/
		
		if (interWH.get_ValueAsInt("M_MovementIn_ID") > 0) {
			return "Error: Inbound Movement Has Been Created";
		}
		
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
			return "Error: No Destination Locator Selected";
		} else {
			MLocator locator = new MLocator(getCtx(), p_Locator, get_TrxName());
			if (locator.getM_Warehouse_ID()!= p_M_Warehouse_ID) {
				return "Error: Selected Locator Is Not in The Destination Warehouse";
			}
		}
		
		boolean alreadyInbounded = new Query(getCtx(), MMovement.Table_Name, "M_OutBoundFrom_ID="+p_M_MovementOutBound_ID+" AND DocStatus NOT IN ('VO','RE')", get_TrxName())
							.match();
		
		if (alreadyInbounded) 
			return "Error: This OutBound is already inbounded";
		//@David
		//Warehouse In-Transit
		/*Case 1
		 * M_WarehouseFrom.AD_Org_ID == M_WarehouseTo.AD_Org_ID
		 *
		 *Case 2
		 * M_WarehouseFrom.AD_Org_ID != M_WarehouseTo.AD_Org_ID
		 */
		MWarehouse whFrom = new MWarehouse(getCtx(), interWH.getM_Warehouse_ID(), get_TrxName());
		MWarehouse whTo = new MWarehouse(getCtx(), interWH.get_ValueAsInt("M_WarehouseTo_ID"), get_TrxName());
//		MWarehouse whTransit;
//		if (whFrom.getAD_Org_ID() == whTo.getAD_Org_ID()) {
//			whTransit = whFrom;
//		}
//		else {
//			String sqlWHTransit = "SELECT M_Warehouse_ID FROM M_Warehouse WHERE IsIntransit='Y' AND IsActive='Y' AND AD_Org_ID="+whFrom.getAD_Org_ID();
//			int M_WareHouse_InTransit_ID = DB.getSQLValue(get_TrxName(), sqlWHTransit);
//			if (M_WareHouse_InTransit_ID<=0)
//				throw new AdempiereException("Warehouse.InTransit='Y' not exist");
//			whTransit = new MWarehouse(getCtx(), M_WareHouse_InTransit_ID, get_TrxName());	
//		}

//		String sqlLocatorTransit = "SELECT M_Locator_ID FROM M_Locator WHERE IsIntransit='Y' AND IsActive='Y' AND M_Warehouse_ID="+whTransit.getM_Warehouse_ID();
//		int locator_InTransit_ID = DB.getSQLValue(get_TrxName(), sqlLocatorTransit);
//		if (locator_InTransit_ID<=0)
//			throw new AdempiereException("Locator.IsIntransit='Y' not exist");

		MMovement outbound = new MMovement(getCtx(), p_M_MovementOutBound_ID, get_TrxName());
		MMovementLine [] lines = outbound.getLines(true);
		
		//Create inbound movement
		MMovement inbound = new MMovement(getCtx(), 0, get_TrxName());
		inbound.setAD_Org_ID(interWH.getAD_Org_ID());
		inbound.setMovementDate(p_MovementDate);
		inbound.setC_DocType_ID(p_C_DocType_ID);
		//inbound.setM_Warehouse_ID(orgInfo.getTransit_Warehouse_ID());
		
		//inbound.set_ValueOfColumn("M_Warehouse_ID", orgInfo.get_ValueAsInt("Transit_Warehouse_ID"));
		//inbound.set_ValueOfColumn("M_Warehouse_ID", outbound.get_Value("M_WarehouseTo_ID"));
		//inbound.setM_WarehouseTo_ID(p_M_Warehouse_ID);
		//inbound.set_ValueOfColumn("M_WarehouseTo_ID", p_M_Warehouse_ID);
		/*
		if (interWH.getAD_OrgTrx_ID() > 0) {
			inbound.setAD_OrgTrx_ID(OrgTo);
		}
		*/
		inbound.setDocStatus(DocAction.STATUS_Drafted);
		inbound.setDocAction(DocAction.ACTION_Complete);
		inbound.setDD_Order_ID(interWH.getDD_Order_ID());
		inbound.setC_Project_ID(interWH.getC_Project_ID());
		inbound.setC_BPartner_ID(outbound.getC_BPartner_ID());
		inbound.setC_BPartner_Location_ID(outbound.getC_BPartner_Location_ID());
		inbound.setM_Shipper_ID(outbound.getM_Shipper_ID());
		inbound.setAD_User_ID(outbound.getAD_User_ID());
		inbound.set_ValueOfColumn("IsInbound", "Y");
		inbound.set_ValueOfColumn("M_OutBoundFrom_ID", p_M_MovementOutBound_ID);
		inbound.set_ValueOfColumn("kendaraan", outbound.get_Value("kendaraan"));
		inbound.set_ValueOfColumn("Pengirim", outbound.get_Value("Pengirim"));
		inbound.set_ValueOfColumn("driver", outbound.get_Value("driver"));
		inbound.set_ValueOfColumn("datesendmovement", outbound.get_Value("datesendmovement"));
		inbound.set_ValueOfColumn("datereceivedmovement", outbound.get_Value("datereceivedmovement"));
		inbound.set_ValueOfColumn("salesrep_id", outbound.get_Value("salesrep_id"));
		inbound.set_ValueOfColumn("c_order_id", outbound.get_Value("c_order_id"));
		inbound.set_ValueOfColumn("salesrep_id", outbound.get_Value("salesrep_id"));
		inbound.set_ValueOfColumn("salesrep_id", outbound.get_Value("salesrep_id"));
		inbound.saveEx();
		
		//Create inbound movement lines
		/*2994 - Set Link antara Inbound dan Outbound
		Change create lines method to get from outbound (M_Movement) provided by parameter
		MDDOrderLine[] lines = interWH.getLines();
		*/
		//MWarehouse whTransit = new MWarehouse(getCtx(), orgInfo.get_ValueAsInt("Transit_Warehouse_ID"), get_TrxName());
		//MLocator locatorTransit = whTransit.getDefaultLocator();
		
		/*2994 - Set Link antara Inbound dan Outbound
			Change create lines method to get from outbound (M_Movement) provided by parameter
		for (MDDOrderLine line : lines) {
			MMovementLine moveLine = new MMovementLine(inbound);
			moveLine.setLine(line.getLine());
			moveLine.setAD_Org_ID(interWH.getAD_Org_ID());
			moveLine.setM_Product_ID(line.getM_Product_ID());
			//moveLine.setQtyEntered(line.getQtyEntered());
			moveLine.set_ValueOfColumn("QtyEntered", line.getQtyEntered());
			moveLine.setMovementQty(line.getQtyEntered());
			moveLine.setM_Locator_ID(locator_InTransit_ID);
			moveLine.setM_LocatorTo_ID(p_Locator);
			//moveLine.setC_UOM_ID(line.getC_UOM_ID());
			moveLine.set_ValueOfColumn("C_UOM_ID", line.getC_UOM_ID());
			moveLine.setDD_OrderLine_ID(line.getDD_OrderLine_ID());
			moveLine.saveEx();
		}
		*/
		for (MMovementLine line : lines) {
			MMovementLine moveLine = new MMovementLine(inbound);
			moveLine.setLine(line.getLine());
			moveLine.setAD_Org_ID(interWH.getAD_Org_ID());
			moveLine.setM_Product_ID(line.getM_Product_ID());
			//moveLine.setQtyEntered(line.getQtyEntered());
			moveLine.set_ValueOfColumn("QtyEntered", line.get_Value("QtyEntered"));
			moveLine.setMovementQty(line.getMovementQty());
			moveLine.setM_Locator_ID(line.getM_LocatorTo_ID());
			moveLine.setM_LocatorTo_ID(p_Locator);
			//moveLine.setC_UOM_ID(line.getC_UOM_ID());
			moveLine.set_ValueOfColumn("C_UOM_ID", line.get_Value("C_UOM_ID"));
			moveLine.setDD_OrderLine_ID(line.getDD_OrderLine_ID());
			moveLine.set_ValueOfColumn("M_OutBoundLineFrom_ID", line.getM_MovementLine_ID());
			moveLine.setM_AttributeSetInstance_ID(line.getM_AttributeSetInstance_ID());
			moveLine.saveEx();
		}
		
		
		//Complete movement
//		if(!inbound.processIt(DocAction.ACTION_Complete)){
//			throw new AdempiereException("Failed to complete inbound");
//		}
//		inbound.saveEx();
		
		/*Bug #2990 Create multiple Outbound and Inbound
		//Set Inbound Movement Link to Inter-warehouse
		//interWH.setM_MovementIn_ID(inbound.get_ID());
		interWH.set_ValueOfColumn("M_MovementIn_ID", inbound.get_ID());
		*/
		interWH.saveEx();
		
		String message = Msg.parseTranslation(getCtx(), "@GeneratedInbound@"+ inbound.getDocumentNo());
		addBufferLog(0, null, null, message, inbound.get_Table_ID(),inbound.getM_Movement_ID());

		return "";
	}

}
