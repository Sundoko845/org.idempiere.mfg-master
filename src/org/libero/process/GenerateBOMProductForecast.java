package org.libero.process;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DBException;
import org.compiere.model.MBPartner;
import org.compiere.model.MCostElement;
import org.compiere.model.MDocType;
import org.compiere.model.MForecast;
import org.compiere.model.MForecastLine;
import org.compiere.model.MLocator;
import org.compiere.model.MMessage;
import org.compiere.model.MNote;
import org.compiere.model.MOrg;
import org.compiere.model.MProduct;
import org.compiere.model.MProductPO;
import org.compiere.model.MRequisition;
import org.compiere.model.MRequisitionLine;
import org.compiere.model.MStorageOnHand;
import org.compiere.model.MUOMConversion;
import org.compiere.model.MWarehouse;
import org.compiere.model.PO;
import org.compiere.model.POResultSet;
import org.compiere.model.Query;
import org.compiere.model.X_C_BP_Group;
import org.compiere.process.SvrProcess;
import org.compiere.util.CCache;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Util;
import org.compiere.wf.MWorkflow;
import org.eevolution.model.I_PP_Product_Planning;
import org.eevolution.model.MDDOrder;
import org.eevolution.model.MDDOrderLine;
import org.eevolution.model.MPPProductBOM;
import org.eevolution.model.MPPProductBOMLine;
import org.eevolution.model.MPPProductPlanning;
import org.eevolution.model.X_PP_Product_Planning;
import org.libero.form.TreeBOM;
import org.libero.model.MDDNetworkDistribution;
import org.libero.model.MDDNetworkDistributionLine;
import org.libero.model.MPPMRP;
import org.libero.model.MPPOrder;
import org.libero.model.TampungDataBom;
import org.libero.model.TampungDataDeleteMRP;

public class GenerateBOMProductForecast extends SvrProcess {

	TreeBOM treebom = new TreeBOM();
	private int     p_AD_Org_ID     = 0;
	private int     p_S_Resource_ID = 0;
	private int     p_M_Warehouse_ID= 0;
	private boolean p_IsRequiredDRP = false;
	private int     p_Planner_ID = 0;
	@SuppressWarnings("unused")
	private String  p_Version = "1";
	/** Product ID - for testing purposes */
	protected int	p_M_Product_ID = 0;
	private int p_C_Order_ID  = 0;
	private int p_M_Order_ID = 0;
	private int generatebom = 0;

	// Global Variables
	private MPPProductPlanning m_product_planning = null;
	private BigDecimal QtyProjectOnHand = Env.ZERO;
	private BigDecimal QtyGrossReqs = Env.ZERO;
	private BigDecimal QtyScheduledReceipts = Env.ZERO;
	private Timestamp DatePromisedFrom = null;
	private Timestamp DatePromisedTo = null;
	private Timestamp Today = new Timestamp (System.currentTimeMillis());  
	private Timestamp TimeFence = null;
	private Timestamp Planning_Horizon = null;
	// Document Types
	private int docTypeReq_ID = 0;
	private int docTypeMO_ID = 0; 
	private int docTypeMF_ID = 0; 
	private int docTypeDO_ID = 0;
	// Statistics
	private int count_MO = 0;
	private int count_MR = 0;
	private int count_DO = 0;
	private int count_Msg = 0;
	private boolean p_DeleteMRP;
	
	private String  msg_debug;
	// Cache
	private static CCache<String ,Integer>   dd_order_id_cache 	= new CCache<String,Integer>(MDDOrder.COLUMNNAME_DD_Order_ID, 50);
	private static CCache<Integer,MBPartner>   partner_cache 	= new CCache<Integer,MBPartner>(MBPartner.Table_Name, 50);
	
	@Override
	protected void prepare() {
		//StringBuffer resultMsg = new StringBuffer();
//		final String whereClause = " M_Forecast_ID = "+getRecord_ID();
//		Collection<MForecastLine> forline = new Query(getCtx(), MForecastLine.Table_Name, whereClause, get_TrxName())											
//									.list();
//		for (MForecastLine mForecastLine : forline) {
//			
//			MPPOrder mppOrder = new MPPOrder(getCtx(), DB.getSQLValue(null, "select pp_order_id from pp_order where m_forecastline_id = "+mForecastLine.getM_ForecastLine_ID()), get_TrxName());
//			List<MPPMRP> listmrp = new Query(getCtx(), MPPMRP.Table_Name, "pp_orderref_id = '"+mppOrder.getPP_Order_ID()+"' and coalesce(pp_order_id,0) != "+mppOrder.getPP_Order_ID()+" and typemrp = 'S'", msg_debug).list();
//			listtaTampungDataDeleteMRPs = new ArrayList<>();
//			for(MPPMRP mppmrp  : listmrp) {
//				TampungDataDeleteMRP dataDeleteMRP = new TampungDataDeleteMRP();
//				dataDeleteMRP.setRequisitionid(mppmrp.getM_Requisition_ID());
//				dataDeleteMRP.setOrderid(mppmrp.getPP_Order_ID());
//				listtaTampungDataDeleteMRPs.add(dataDeleteMRP);
//			}
//			try {
//			deleteMRP(mppOrder.getAD_Client_ID(), mppOrder.getAD_Org_ID(), mppOrder.getS_Resource_ID(), mppOrder.getM_Warehouse_ID(),mppOrder.getPP_Order_ID());
//		} catch (SQLException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		}
		
	}
	
	public boolean isRequiredDRP()
	{
		return p_IsRequiredDRP;
	}
	
	public int getPlanner_ID()
	{
		if (this.p_Planner_ID <= 0)
		{
			this.p_Planner_ID = Env.getAD_User_ID(getCtx());
		}
		return this.p_Planner_ID;
	}

	private List<TampungDataDeleteMRP> listtaTampungDataDeleteMRPs  = null;
	@Override
	protected String doIt() throws Exception {

         
		
	
	
		StringBuffer resultMsg = new StringBuffer();
		final String whereClause = " M_Forecast_ID = "+getRecord_ID();
		Collection<MForecastLine> forline = new Query(getCtx(), MForecastLine.Table_Name, whereClause, get_TrxName())											
									.list();
		for (MForecastLine mForecastLine : forline) {
			System.out.println("ffffff"+mForecastLine.getQty());
			
			MPPOrder mppOrder = new MPPOrder(getCtx(), DB.getSQLValue(null, "select pp_order_id from pp_order where m_forecastline_id = "+mForecastLine.getM_ForecastLine_ID()), get_TrxName());
			this.Planning_Horizon = TimeUtil.addDays(getToday(), 120);
			MProduct product = MProduct.get(getCtx(), mForecastLine.getM_Product_ID());
	        DefaultMutableTreeNode parent = new DefaultMutableTreeNode(treebom.productSummary(product, false));
			MPPProductBOM bom;
			
	        for(Iterator<MPPProductBOM> iterator1 = MPPProductBOM.getProductBOMs(product).iterator(); iterator1.hasNext(); parent.add(parent(bom,mppOrder.getM_Product_ID(),mppOrder))) {
	            bom = (MPPProductBOM)iterator1.next();
	        }
	        generatebom = 0;
		}
		
        resultMsg.append("<br> " +Msg.translate(getCtx(), "Created"));
		resultMsg.append("<br> ");
		resultMsg.append("<br> " +Msg.translate(getCtx(), "PP_Order_ID")+":"+count_MO);
		resultMsg.append("<br> " +Msg.translate(getCtx(), "DD_Order_ID")+":"+count_DO);
		resultMsg.append("<br> " +Msg.translate(getCtx(), "M_Requisition_ID")+":"+count_MR);
		resultMsg.append("<br> " +Msg.translate(getCtx(), "AD_Note_ID")+":"+count_Msg);
		//resultMsg.append("<br>finish MRP to Plant " +plant.getName());
		return msg_debug+"|"+resultMsg.toString();
	}
	
	protected String runMRP(int AD_Client_ID , int AD_Org_ID, int S_Resource_ID , int M_Warehouse_ID,int C_Order_ID, int PP_Order_ID, int productid, int ProductMPO, BigDecimal qtybom,int productmasterbom,MPPOrder order) throws SQLException
	{
		System.out.println(productid +"-" + productmasterbom +" - "+ ProductMPO);
		MProduct product = null;                                                                       

		int BeforePP_MRP_ID = 0;						
		Timestamp  BeforeDateStartSchedule = null;
		Timestamp  POQDateStartSchedule = null;
		BigDecimal Qty = BigDecimal.ZERO;           
	
		docTypeReq_ID = getDocType(MDocType.DOCBASETYPE_PurchaseRequisition, AD_Org_ID);
		docTypeMO_ID = getDocType(MDocType.DOCBASETYPE_ManufacturingOrder, AD_Org_ID);
		docTypeMF_ID = getDocType(MDocType.DOCBASETYPE_MaintenanceOrder, AD_Org_ID);
		docTypeDO_ID = getDocType(MDocType.DOCBASETYPE_DistributionOrder, AD_Org_ID);
		
		if(generatebom==0) {
          	TampungDataBom tampungDataBom = new TampungDataBom();
         
          	tampungDataBom.setProductparent(DB.getSQLValue(get_TrxName(), "select m_product_id from pp_order where pp_order_id = " + PP_Order_ID));
          	tampungDataBom.setQtyrequired(DB.getSQLValueBD(get_TrxName(), "select qtyentered from pp_order where pp_order_id = " + PP_Order_ID));
          	//tampungDataBom.setQtyparentorder(DB.getSQLValueBD(get_TrxName(), "select qtyplan from pp_order where pp_order_id = " + PP_Order_ID));
          	list.add(tampungDataBom);
          	generatebom = 1;
          }
		
					final int PP_MRP_ID = DB.getSQLValue(null, "select pp_mrp_id from pp_mrp where PP_Order_ID =  " + PP_Order_ID);
					//final String OrderType = rs.getString(MPPMRP.COLUMNNAME_OrderType);
					final Timestamp DatePromised = DB.getSQLValueTS(null, "select datepromised from PP_Order where PP_Order_ID =  " + PP_Order_ID);
					//final Timestamp DateStartSchedule = DB.getSQLValueTS(null, "select datestartschedule from PP_Order where PP_Order_ID =  " + PP_Order_ID);
					for (int i = 0; i < list.size(); i++) {
						System.out.println("sfsfss"+list.get(i).getQtyrequired());
						if(productmasterbom == list.get(i).getProductparent()) {
							//Qty = (list.get(i).getQtyrequired().multiply(qtybom));
							if(DB.getSQLValueString(get_TrxName(), "select isbom from m_product where m_product_id = " + productid).equals("Y"))
							{
//								try {
//									Qty = (list.get(i).getQtyrequired().multiply(qtybom)).subtract(DB.getSQLValueBD(get_TrxName(), "select coalesce(sum(qtyonhand),0) from m_storage where m_product_id = "+productid+" having coalesce(sum(qtyonhand),0) > 0"));
//								} catch (Exception e) {
//									Qty = (list.get(i).getQtyrequired().multiply(qtybom));
//								}
								Qty = (list.get(i).getQtyrequired().multiply(qtybom));
								if(DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp pp inner join  m_product mp on mp.m_product_id = pp.m_product_id where mp.isbom = 'Y' and DocStatus = 'DR' and pp.ad_org_id = "+AD_Org_ID+" and pp.m_warehouse_id = "+M_Warehouse_ID+" and pp.m_product_id = " +productid+" and pp.s_resource_id = "+S_Resource_ID+" and pp.TypeMRP = '"+MPPMRP.TYPEMRP_Supply+"'").compareTo(BigDecimal.ZERO)>0) {
									Qty = ( DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp where DocStatus = 'DR' and ad_org_id = "+AD_Org_ID+" and m_warehouse_id = "+M_Warehouse_ID+" and m_product_id = " +productid+" and s_resource_id = "+S_Resource_ID+" and TypeMRP = '"+MPPMRP.TYPEMRP_Demand+"'")
											.subtract(DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp pp inner join  m_product mp on mp.m_product_id = pp.m_product_id where mp.isbom = 'Y' and DocStatus = 'DR' and pp.ad_org_id = "+AD_Org_ID+" and pp.m_warehouse_id = "+M_Warehouse_ID+" and pp.m_product_id = " +productid+" and pp.s_resource_id = "+S_Resource_ID+" and pp.TypeMRP = '"+MPPMRP.TYPEMRP_Supply+"'")));
									System.out.println("tes "+productid + DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp where DocStatus = 'DR' and ad_org_id = "+AD_Org_ID+" and m_warehouse_id = "+M_Warehouse_ID+" and m_product_id = " +productid+" and s_resource_id = "+S_Resource_ID+" and TypeMRP = '"+MPPMRP.TYPEMRP_Demand+"'"));
									System.out.println("tes "+productid +  DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp pp inner join  m_product mp on mp.m_product_id = pp.m_product_id where mp.isbom = 'Y' and DocStatus = 'DR' and pp.ad_org_id = "+AD_Org_ID+" and pp.m_warehouse_id = "+M_Warehouse_ID+" and pp.m_product_id = " +productid+" and pp.s_resource_id = "+S_Resource_ID+" and pp.TypeMRP = '"+MPPMRP.TYPEMRP_Supply+"'"));
									
									System.out.println("QTY Y "+productid+" "+ Qty);
								}
								
								//Qty = DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp where DocStatus = 'DR' and ad_org_id = "+AD_Org_ID+" and m_warehouse_id = "+M_Warehouse_ID+" and m_product_id = " +productid+" and s_resource_id = "+S_Resource_ID+" and TypeMRP = '"+MPPMRP.TYPEMRP_Demand+"'")
										//.subtract(DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp pp inner join  m_product mp on mp.m_product_id = pp.m_product_id where mp.isbom = 'Y' and DocStatus = 'DR' and pp.ad_org_id = "+AD_Org_ID+" and pp.m_warehouse_id = "+M_Warehouse_ID+" and pp.m_product_id = " +productid+" and pp.s_resource_id = "+S_Resource_ID+" and pp.TypeMRP = '"+MPPMRP.TYPEMRP_Supply+"'"));
							}else {
								int conv =DB.getSQLValue(get_TrxName(), "select count(*) from C_UOM_Conversion where m_product_id = "+list.get(i).getProductparent());
								BigDecimal qtyconv = BigDecimal.ONE;
								//System.out.println("adfafaf"+list.get(i).getProductparent());
								//System.out.println("sdsff"+conv);
								if(conv>0) {
									int idconv =DB.getSQLValue(get_TrxName(), "select  C_UOM_Conversion_ID from C_UOM_Conversion where m_product_id = "+list.get(i).getProductparent());
									MUOMConversion conversion = new MUOMConversion(Env.getCtx(),idconv, get_TrxName());
									qtyconv = conversion.getDivideRate();
								//	System.out.println("yyyyy"+qtyconv+" "+qtybom);
									if(qtyconv.compareTo(qtybom)==0) {
										 Qty = list.get(i).getQtyrequired();	
									}else {
										Qty = list.get(i).getQtyparentorder().multiply(qtybom);
									}
								
								}else {
								Qty = (list.get(i).getQtyrequired().multiply(qtybom));
								
								}
							Qty = DB.getSQLValueBD(null, "select sum(qty) from pp_mrp where DocStatus = 'DR' and ad_org_id = "+AD_Org_ID+" and m_warehouse_id = "+M_Warehouse_ID+" and m_product_id = " +productid+" and s_resource_id = "+S_Resource_ID+" and TypeMRP = '"+MPPMRP.TYPEMRP_Demand+"'")
									.subtract(DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp pp inner join  m_product mp on mp.m_product_id = pp.m_product_id where mp.isbom = 'N' and DocStatus = 'DR' and pp.ad_org_id = "+AD_Org_ID+" and pp.m_warehouse_id = "+M_Warehouse_ID+" and pp.m_product_id = " +productid+" and pp.s_resource_id = "+S_Resource_ID+" and pp.TypeMRP = '"+MPPMRP.TYPEMRP_Supply+"'"));
							//System.out.println(DB.getSQLValueBD(null, "select sum(qty) from pp_mrp where DocStatus = 'DR' and ad_org_id = "+AD_Org_ID+" and m_warehouse_id = "+M_Warehouse_ID+" and m_product_id = " +productid+" and s_resource_id = "+S_Resource_ID+" and TypeMRP = '"+MPPMRP.TYPEMRP_Demand+"'"));
							//System.out.println(DB.getSQLValueBD(null, "select coalesce(sum(qty),0) from pp_mrp pp inner join  m_product mp on mp.m_product_id = pp.m_product_id where mp.isbom = 'N' and DocStatus = 'DR' and pp.ad_org_id = "+AD_Org_ID+" and pp.m_warehouse_id = "+M_Warehouse_ID+" and pp.m_product_id = " +productid+" and pp.s_resource_id = "+S_Resource_ID+" and pp.TypeMRP = '"+MPPMRP.TYPEMRP_Supply+"'"));
						    System.out.println("QTY"+Qty);
							}
							
						
						}
					}
					
					final int M_Product_ID = productid ; 
			

				
						

						// Load Product & define Product Data Planning
						product = MProduct.get(getCtx(), M_Product_ID);
						log.info("Calculte Plan to this Product:" + product);
						setProduct(AD_Client_ID,AD_Org_ID ,S_Resource_ID , M_Warehouse_ID,  product);
						
					  
							
						if (X_PP_Product_Planning.ORDER_POLICY_PeriodOrderQuantity.equals(m_product_planning.getOrder_Policy()))
						{
							POQDateStartSchedule =null;
						}
					
					
			
					
					int daysPOQ = m_product_planning.getOrder_Period().intValueExact() - 1;
					//first DatePromised.compareTo for ORDER_POLICY_PeriodOrderQuantity
					if (X_PP_Product_Planning.ORDER_POLICY_PeriodOrderQuantity.equals(m_product_planning.getOrder_Policy()) 
							&& (DatePromisedTo !=null && DatePromised.compareTo(DatePromisedTo) > 0))
					{
						calculatePlan(AD_Client_ID,AD_Org_ID,M_Warehouse_ID,PP_MRP_ID,product ,DatePromisedFrom,C_Order_ID,ProductMPO,productmasterbom,order);						
						DatePromisedFrom = DatePromised;
						DatePromisedTo = TimeUtil.addDays(DatePromised, daysPOQ<0 ? 0 : daysPOQ);                                     
						POQDateStartSchedule = DatePromised;
						
					}
					else if(POQDateStartSchedule==null)
					{
						DatePromisedFrom = DatePromised;
						DatePromisedTo = TimeUtil.addDays(DatePromised, daysPOQ<0 ? 0 : daysPOQ);                                     
						POQDateStartSchedule = DatePromised;
					}
									
					//MRP-150
					//Past Due Demand
					//Indicates that a demand order is past due.
					if(DatePromised.compareTo(getToday()) < 0)
					{
						String comment = Msg.translate(getCtx(), MPPOrder.COLUMNNAME_DatePromised)
										 + ": " + DatePromised;
						createMRPNote("MRP-150", AD_Org_ID, PP_MRP_ID, product, MPPMRP.getDocumentNo(PP_MRP_ID), 
								Qty, comment);
					}

					BeforePP_MRP_ID = PP_MRP_ID;

					if (X_PP_Product_Planning.ORDER_POLICY_PeriodOrderQuantity.equals(m_product_planning.getOrder_Policy()))
					{
						// Verify if is DatePromised < DatePromisedTo then Accumulation QtyGrossReqs 
						if (DatePromisedTo != null && DatePromised.compareTo(DatePromisedTo) <= 0)
						{
							QtyGrossReqs = QtyGrossReqs.add(Qty);
							log.info("Accumulation   QtyGrossReqs:" + QtyGrossReqs);
							log.info("DatePromised:" + DatePromised);
							log.info("DatePromisedTo:" + DatePromisedTo);
							
						}						
					}
					// If  Order_Policy = LoteForLote then always create new range for next period and put QtyGrossReqs          
					else if (X_PP_Product_Planning.ORDER_POLICY_Lot_For_Lot.equals(m_product_planning.getOrder_Policy()))
					{       
					
						QtyGrossReqs = QtyGrossReqs.add(Qty);
						BeforeDateStartSchedule = DatePromised; 		
						calculatePlan(AD_Client_ID, AD_Org_ID,M_Warehouse_ID,PP_MRP_ID,product,BeforeDateStartSchedule,C_Order_ID,ProductMPO,productmasterbom,order ); 		
						
					}                                                                        
			

				// If exist QtyGrossReq of last Demand after finish while verify plan
				if (QtyGrossReqs.signum() != 0 && product != null)
				{   
					System.out.println(POQDateStartSchedule);
					if (X_PP_Product_Planning.ORDER_POLICY_PeriodOrderQuantity.equals(m_product_planning.getOrder_Policy())
							&&  POQDateStartSchedule.compareTo(Planning_Horizon) < 0) 
					{
						BeforeDateStartSchedule =  POQDateStartSchedule; 
						calculatePlan(AD_Client_ID,AD_Org_ID,M_Warehouse_ID,BeforePP_MRP_ID , product ,BeforeDateStartSchedule,C_Order_ID,ProductMPO,productmasterbom,order );
					}
					else if (X_PP_Product_Planning.ORDER_POLICY_Lot_For_Lot.equals(m_product_planning.getOrder_Policy())
							&& BeforeDateStartSchedule.compareTo(Planning_Horizon) <= 0 )
					{
                       calculatePlan(AD_Client_ID,AD_Org_ID,M_Warehouse_ID,BeforePP_MRP_ID , product ,BeforeDateStartSchedule,C_Order_ID,ProductMPO,productmasterbom,order );
					}	
					
				}
				else if (product != null)
				{
					//Create Action Notice if exist supply
					getNetRequirements(
							AD_Client_ID, 
							AD_Org_ID, 
							M_Warehouse_ID, 
							product, 
							null);					
				}

				
			// end for
	

		return "ok";
	}
	
	protected void deleteMRP(int AD_Client_ID, int AD_Org_ID,int S_Resource_ID, int M_Warehouse_ID, int pporderid) throws SQLException
	{
		
		 //Delete Requisition with Status Close from MRP Table
		String sql = "DELETE FROM PP_MRP WHERE OrderType = 'POR' AND DocStatus NOT IN ('CL', 'CO') AND AD_Client_ID = " + AD_Client_ID +  " AND AD_Org_ID=" + AD_Org_ID+ " AND M_Warehouse_ID="+M_Warehouse_ID+" AND pp_orderref_id = '"+pporderid+"'";				
				DB.executeUpdateEx(sql, get_TrxName());
				commitEx();		
				//Delete Requisition with Draft Status
				String whereClause = "DocStatus IN ('DR') AND AD_Client_ID=? AND AD_Org_ID=? AND M_Warehouse_ID=?";
				//deletePO(MRequisition.Table_Name, whereClause, new Object[]{AD_Client_ID, AD_Org_ID, M_Warehouse_ID});

				// Delete Action Notice
				 sql = "DELETE FROM AD_Note WHERE AD_Table_ID=? AND AD_Client_ID=? AND AD_Org_ID=?";
				DB.executeUpdateEx(sql, new Object[]{MPPMRP.Table_ID, AD_Client_ID, AD_Org_ID}, get_TrxName());
				commitEx();

				if (isRequiredDRP())
				{
					//Delete Distribution Order with Draft Status
					whereClause = "DocStatus='DR' AND AD_Client_ID=? AND AD_Org_ID=?"
								+" AND EXISTS (SELECT 1 FROM PP_MRP mrp WHERE  mrp.DD_Order_ID=DD_Order.DD_Order_ID AND mrp.S_Resource_ID=? )"
								+" AND EXISTS (SELECT 1 FROM DD_OrderLine ol INNER JOIN  M_Locator l ON (l.M_Locator_ID=ol.M_LocatorTo_ID) "
								+" WHERE ol.DD_Order_ID=DD_Order.DD_Order_ID AND l.M_Warehouse_ID=?)";
					deletePO(MDDOrder.Table_Name, whereClause, new Object[]{AD_Client_ID, AD_Org_ID, S_Resource_ID, M_Warehouse_ID});
				}
		// Delete Manufacturing Order with Close Status from MRP Table
		 sql = "DELETE FROM PP_MRP WHERE OrderType = 'MOP' AND DocStatus NOT IN ('CL', 'CO')AND AD_Client_ID=" + AD_Client_ID  + " AND AD_Org_ID=" + AD_Org_ID + " AND M_Warehouse_ID="+M_Warehouse_ID +  " AND S_Resource_ID="+S_Resource_ID+" AND PP_Order_ID != "+pporderid ;					
		DB.executeUpdateEx(sql, get_TrxName());
		commitEx();
		//Delete Manufacturing Order with Draft Status 
		whereClause = "DocStatus='DR' AND AD_Client_ID=? AND AD_Org_ID=? AND M_Warehouse_ID=? AND S_Resource_ID=? AND PP_Order_ID!=?";
		//deletePO(MPPOrder.Table_Name, whereClause, new Object[]{AD_Client_ID, AD_Org_ID, M_Warehouse_ID, S_Resource_ID,pporderid});
		for (int i = 0; i < listtaTampungDataDeleteMRPs.size(); i++) {

			if(listtaTampungDataDeleteMRPs.get(i).getRequisitionid()>0) {
				deletePO(MRequisition.Table_Name, "M_Requisition_ID = ?", new Object[]{listtaTampungDataDeleteMRPs.get(i).getRequisitionid()});
			}else if(listtaTampungDataDeleteMRPs.get(i).getOrderid()>0) {
				deletePO(MPPOrder.Table_Name, "PP_Order_ID = ?", new Object[]{listtaTampungDataDeleteMRPs.get(i).getOrderid()});
			}
		}

		
		
		// Mark all supply MRP records as available
		DB.executeUpdateEx("UPDATE PP_MRP SET IsAvailable ='Y' WHERE TypeMRP = 'S' AND AD_Client_ID = ? AND AD_Org_ID=? AND M_Warehouse_ID=?", new Object[]{AD_Client_ID,AD_Org_ID,M_Warehouse_ID} ,get_TrxName());
		commitEx();
	}
	
	public DefaultMutableTreeNode parent(MPPProductBOM bom, int product,MPPOrder mppOrder)
    {   
//		try {
//			deleteMRP(mppOrder.getAD_Client_ID(), mppOrder.getAD_Org_ID(), mppOrder.getS_Resource_ID(), mppOrder.getM_Warehouse_ID(),mppOrder.getPP_Order_ID());
//		} catch (SQLException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		 DefaultMutableTreeNode parent = new DefaultMutableTreeNode(treebom.productSummary(bom));
	        MPPProductBOMLine amppproductbomline[];
	        int j = (amppproductbomline = bom.getLines()).length;
	        for(int i = 0; i < j; i++)
	        {
	        
	            MPPProductBOMLine bomline = amppproductbomline[i];
	            MProduct component = MProduct.get(getCtx(), bomline.getM_Product_ID());            
	     
	            try {
					runMRP(mppOrder.getAD_Client_ID(), mppOrder.getAD_Org_ID(), mppOrder.getS_Resource_ID(), mppOrder.getM_Warehouse_ID(), 0, mppOrder.getPP_Order_ID(), bomline.getM_Product_ID(), mppOrder.getPP_Order_ID(),bomline.getQtyBOM(),bomline.getPP_Product_BOM().getM_Product_ID(),mppOrder);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	           
               if(DB.getSQLValue(get_TrxName(), "select count(*) from PP_Product_BOM where m_product_id = " + bomline.getM_Product().getM_Product_ID()) > 0) {
            	   
               }
	           parent.add(component(component,product,mppOrder));
	        }

	        return parent;
    }
	
	
	
	public DefaultMutableTreeNode component(MProduct product, int productfp, MPPOrder mppOrder)
    {
       
        Iterator<MPPProductBOM> iterator = MPPProductBOM.getProductBOMs(product).iterator();
        if(iterator.hasNext())
        {
            MPPProductBOM bom = (MPPProductBOM)iterator.next();
            return parent(bom,productfp,mppOrder);
        } else
        {
        	
            return new DefaultMutableTreeNode(treebom.productSummary(product, true));
        }
    }
	
	/**************************************************************************
	 * 	Define the product to calculate plan
	 *  @param AD_Client_ID Client ID
	 *  @param AD_Org_ID Organization ID
	 *  @param M_Warehuse_ID Warehouse ID
	 *	@param MProduct
	 * @throws SQLException 
	 */
	private void setProduct(int AD_Client_ID , int AD_Org_ID, int S_Resource_ID , int M_Warehouse_ID, MProduct product) throws SQLException
	{
		DatePromisedTo = null;
		DatePromisedFrom = null;
		//
		// Find data product planning demand 
		m_product_planning = getProductPlanning(AD_Client_ID, AD_Org_ID, S_Resource_ID, M_Warehouse_ID, product);
		
		log.info("PP:"+AD_Client_ID+"|"+ AD_Org_ID+"|"+ S_Resource_ID+"|"+ M_Warehouse_ID+"|"+ product);
		
		if (m_product_planning == null)
		{
			createMRPNote("MRP-120", AD_Org_ID, 0, product, (String)null,  null , null);
			return;
		}
		
		if(m_product_planning.getTimeFence().signum() > 0)
		{
			TimeFence = TimeUtil.addDays(getToday(), m_product_planning.getTimeFence().intValueExact());
		}

		QtyProjectOnHand = getQtyOnHand(m_product_planning);
		if(QtyProjectOnHand.signum() < 0)
		{
			String comment = Msg.translate(getCtx(), MStorageOnHand.COLUMNNAME_QtyOnHand) 
							+ ": " + QtyProjectOnHand;
			//MRP-140
			//Beginning Quantity Less Than Zero
			//Indicates that the quantity on hand is negative.
			createMRPNote("MRP-140", AD_Org_ID, 0, product , null , QtyProjectOnHand , comment);
		}
		
		// Quantity Project On hand 100 
		// Safety Stock 150
		// 150 > 100 The Quantity Project On hand is now 50
		if(m_product_planning.getSafetyStock().signum() > 0
				&& m_product_planning.getSafetyStock().compareTo(QtyProjectOnHand) > 0)
		{
			String comment = Msg.translate(getCtx(), MStorageOnHand.COLUMNNAME_QtyOnHand) 
							+ ": " + QtyProjectOnHand
							+ " "  +  Msg.translate(getCtx(), I_PP_Product_Planning.COLUMNNAME_SafetyStock)
							+ ": " + m_product_planning.getSafetyStock();
			createMRPNote("MRP-001", AD_Org_ID, 0, product , null , QtyProjectOnHand , comment);
		}
		log.info("QtyOnHand :" + QtyProjectOnHand);
	}
	
	protected MPPProductPlanning getProductPlanning(int AD_Client_ID , int AD_Org_ID, int S_Resource_ID , int M_Warehouse_ID, MProduct product) throws SQLException
	{
		// Find data product planning demand 
		MPPProductPlanning pp = MPPProductPlanning.find(getCtx() ,AD_Org_ID , M_Warehouse_ID, S_Resource_ID , product.getM_Product_ID(), get_TrxName());
		if (pp == null)
		{
			return null;
		}
		MPPProductPlanning pp2 = new MPPProductPlanning(getCtx(), 0 , null);                                                       
		MPPProductPlanning.copyValues(pp, pp2);
		pp2.setIsRequiredDRP(isRequiredDRP());
		//
		if (pp2.getPP_Product_BOM_ID() <= 0 && product.isBOM())
		{
			pp2.setPP_Product_BOM_ID(MPPProductBOM.getBOMSearchKey(product));  
		}      
		if (pp2.getAD_Workflow_ID() <= 0 && product.isBOM())
		{
			pp2.setAD_Workflow_ID(MWorkflow.getWorkflowSearchKey(product));  
		} 
		if (pp2.getPlanner_ID() <= 0)
		{
			pp2.setPlanner_ID(getPlanner_ID());
		}
		if(pp2.getM_Warehouse_ID() <= 0)
		{
			pp2.setM_Warehouse_ID(M_Warehouse_ID);
		}
		if (pp2.getS_Resource_ID() <= 0)
		{
			pp2.setS_Resource_ID(S_Resource_ID);
		}
		if (pp2.getOrder_Policy() == null)
		{
			pp2.setOrder_Policy(X_PP_Product_Planning.ORDER_POLICY_Lot_For_Lot);
		}
		
		//Find Vendor
		if (!isRequiredDRP())
		{	
			if(product.isPurchased())
			{    
				int C_BPartner_ID = 0;
				MProductPO[] ppos = MProductPO.getOfProduct(getCtx(), product.getM_Product_ID(), get_TrxName());
				for (int i = 0; i < ppos.length; i++)
				{
					if (ppos[i].isCurrentVendor() && ppos[i].getC_BPartner_ID() != 0)
					{
						C_BPartner_ID = ppos[i].getC_BPartner_ID();
						pp2.setDeliveryTime_Promised(BigDecimal.valueOf(ppos[i].getDeliveryTime_Promised()));    	                	            
						pp2.setOrder_Min(ppos[i].getOrder_Min());
						pp2.setOrder_Max(Env.ZERO);
						pp2.setOrder_Pack(ppos[i].getOrder_Pack());
						pp2.setC_BPartner_ID(C_BPartner_ID);
						break;
					}
				}
				if(C_BPartner_ID <= 0)
				{
					createMRPNote("MRP-130", AD_Org_ID, 0, product, (String)null, null , null);
					pp2.setIsCreatePlan(false);
				}
			}
			if (product.isBOM())
			{	
				if (pp2.getAD_Workflow_ID() <= 0)
					log.info("Error: Do not exist workflow ("+product.getValue()+")");
			}
		}
		//
		return pp2;
	}
	
	protected BigDecimal getQtyOnHand(I_PP_Product_Planning pp)
	{
		return MPPMRP.getQtyOnHand(getCtx(), pp.getM_Warehouse_ID() , pp.getM_Product_ID(), get_TrxName());
	}
	
	protected Timestamp getToday()
	{
		return this.Today;
	}

	/**************************************************************************
	 * 	Calculate Plan this product
	 *	@param PP_MRP_ID MRP ID
	 *  @param M_Warehouse_ID Warehoue ID
	 *  @param product Product
	 *  @param DemandDateStartSchedule Demand Date Start Schedule
	 * @throws SQLException 
	 */
	private void calculatePlan(int AD_Client_ID, int AD_Org_ID, int M_Warehouse_ID, int PP_MRP_ID,
								MProduct product, Timestamp DemandDateStartSchedule,int order_id, int ProductMO, int productmasterbom, MPPOrder order) throws SQLException
	{
		//Set Yield o QtyGrossReqs
		// Note : the variables  DemandDateStartSchedule , DemandDateFinishSchedule are same DatePromised to Demands Sales Order Type

		log.info("Create Plan ...");
		System.out.println("asfafagag"+ product.getM_Product_ID());
		
		// Check Internal Error: product from data planning should be the same with the product given as argument 
		if (m_product_planning.getM_Product_ID() != product.get_ID())
		{
			throw new IllegalStateException("MRP Internal Error:"
						+" DataPlanningProduct("+m_product_planning.getM_Product_ID()+")"
						+" <> Product("+product+")");
		}

		final BigDecimal yield = BigDecimal.valueOf(m_product_planning.getYield());
		if (yield.signum() != 0)
		{
			QtyGrossReqs = QtyGrossReqs.multiply(Env.ONEHUNDRED).divide(yield, 4, RoundingMode.HALF_UP);
		}
		
		BigDecimal QtyNetReqs = getNetRequirements(
				AD_Client_ID, 
				AD_Org_ID, 
				M_Warehouse_ID, 
				product, 
				DemandDateStartSchedule);
		
		System.out.println("ffggg"+QtyNetReqs);
		
		
		BigDecimal QtyPlanned = Env.ZERO;

		((PO)m_product_planning).dump();
		log.info("                    Product:" + product);
		log.info(" Demand Date Start Schedule:" + DemandDateStartSchedule);
		log.info("           DatePromisedFrom:" + DatePromisedFrom + " DatePromisedTo:" +   DatePromisedTo);    
		log.info("                Qty Planned:" + QtyPlanned);
		log.info("     Qty Scheduled Receipts:" + QtyScheduledReceipts);
		log.info("           QtyProjectOnHand:" + QtyProjectOnHand);
		log.info("               QtyGrossReqs:" + QtyGrossReqs);
		log.info("                     Supply:" + (QtyScheduledReceipts).add(QtyProjectOnHand));
		log.info("                 QtyNetReqs:" + QtyNetReqs);    

		if (QtyNetReqs.signum() > 0)
		{ // entire qty is available or scheduled to receipt
			QtyProjectOnHand = QtyNetReqs;                                                
			QtyNetReqs = Env.ZERO;
			QtyScheduledReceipts = Env.ZERO;
			QtyPlanned = Env.ZERO;
			QtyGrossReqs = Env.ZERO;
			System.out.println("gfggg");
			return;
		}
		else
		{
			QtyPlanned = QtyNetReqs.negate();                          
			QtyGrossReqs = Env.ZERO;
			QtyScheduledReceipts = Env.ZERO;
			System.out.println("ryryry");
		}
		// ***** Order Modifier ********
		// Check Order Min 
		if(QtyPlanned.signum() > 0 && m_product_planning.getOrder_Min().signum() > 0)
		{    
			if (m_product_planning.getOrder_Min().compareTo(QtyPlanned) > 0)
			{
				String comment = Msg.translate(getCtx(), I_PP_Product_Planning.COLUMNNAME_Order_Min) 
								+ ":" + m_product_planning.getOrder_Min();
				createMRPNote("MRP-080", AD_Org_ID, PP_MRP_ID, product , null, QtyPlanned, comment);
			}
			QtyPlanned = QtyPlanned.max(m_product_planning.getOrder_Min());
		}
		// Check Order Pack
		if (m_product_planning.getOrder_Pack().signum() > 0 && QtyPlanned.signum() > 0)
		{
			QtyPlanned = m_product_planning.getOrder_Pack().multiply(QtyPlanned.divide(m_product_planning.getOrder_Pack(), 0 , BigDecimal.ROUND_UP));
		}
		// Check Order Max                                                
		if(QtyPlanned.compareTo(m_product_planning.getOrder_Max()) > 0 && m_product_planning.getOrder_Max().signum() > 0)
		{   
			String comment = Msg.translate(getCtx(), I_PP_Product_Planning.COLUMNNAME_Order_Max) 
								+ ":" + m_product_planning.getOrder_Max();
			createMRPNote("MRP-090", AD_Org_ID, PP_MRP_ID, product  , null , QtyPlanned , comment); 
		}                        

		QtyProjectOnHand = QtyPlanned.add(QtyNetReqs);

		log.info("QtyNetReqs:" +  QtyNetReqs);
		log.info("QtyPlanned:" +  QtyPlanned);
		log.info("QtyProjectOnHand:" +  QtyProjectOnHand);     
	
		// MRP-100 Time Fence Conflict  Action Notice
		// Indicates that there is an unsatisfied material requirement inside the planning time fence for this product.
		// You should either manually schedule and expedite orders to fill this demand or delay fulfillment
		// of the requirement that created the demand.
		if(TimeFence != null && DemandDateStartSchedule.compareTo(TimeFence) < 0)
		{
			String comment =  Msg.translate(getCtx(), I_PP_Product_Planning.COLUMNNAME_TimeFence) 
							+ ":" + m_product_planning.getTimeFence()
							+ "-"
							+ Msg.getMsg(getCtx(), "Date")
							+ ":" + TimeFence + " "
							+ Msg.translate(getCtx(), MPPOrder.COLUMNNAME_DatePromised)
							+ ":" + DemandDateStartSchedule;
			createMRPNote("MRP-100", AD_Org_ID, PP_MRP_ID, product , null , QtyPlanned , comment);
		}
		
		// MRP-020 Create
		// Indicates that a supply order should be created to satisfy a negative projected on hand.
		// This message is created if the flag 'Create Plan' is No.
		if (m_product_planning.isCreatePlan() == false && QtyPlanned.signum() > 0)
		{	
			createMRPNote("MRP-020", AD_Org_ID, PP_MRP_ID, product , null , QtyPlanned , null); 
			return;
		}
		System.out.println("sfsfsg"+QtyPlanned.signum());
		
		if (QtyPlanned.signum() > 0)    
		{
			int loops = 1;
			if (m_product_planning.getOrder_Policy().equals(X_PP_Product_Planning.ORDER_POLICY_FixedOrderQuantity))
			{    
				if (m_product_planning.getOrder_Qty().signum() != 0)
					loops = (QtyPlanned.divide(m_product_planning.getOrder_Qty() , 0 , BigDecimal.ROUND_UP)).intValueExact();
				QtyPlanned = m_product_planning.getOrder_Qty();
			}

			for (int ofq = 1 ; ofq <= loops ; ofq ++ )
			{
				log.info("Is Purchased: "+ product.isPurchased()+ " Is BOM: " +  product.isBOM());
				try
				{
					createSupply(AD_Org_ID, PP_MRP_ID, product, QtyPlanned, DemandDateStartSchedule,order_id,ProductMO,productmasterbom,order.getQtyOrdered());
				}
				catch (Exception e)
				{
					// MRP-160 - Cannot Create Document
					// Indicates that there was an error durring document creation
					createMRPNote("MRP-160", AD_Org_ID, PP_MRP_ID, product, QtyPlanned, DemandDateStartSchedule, e);
				}
			} // end for oqf
		}       
		else
		{
			log.info("No Create Plan");
		}
	}
	
	/**
	 * Create supply document to balance QtyPlnned 
	 * @param AD_Org_ID
	 * @param PP_MRP_ID
	 * @param product
	 * @param QtyPlanned
	 * @param DemandDateStartSchedule
	 * @throws AdempiereException if there is any error
	 * @throws SQLException 
	 */
	private List<TampungDataBom> list = new ArrayList<>();
	protected void createSupply(int AD_Org_ID, int PP_MRP_ID, MProduct product, BigDecimal QtyPlanned ,Timestamp DemandDateStartSchedule, int orderid, int productMO, int productmasterbom, BigDecimal qtyorder)
	throws AdempiereException, SQLException
	{
		System.out.println("sfsfsgfsg"+product.getM_Product_ID()); 
		// Distribution Order
		if(isRequiredDRP() && m_product_planning.getDD_NetworkDistribution_ID() > 0)
		{
			//createDDOrder(AD_Org_ID, PP_MRP_ID, product, QtyPlanned, DemandDateStartSchedule);
		}
		// Requisition
		else if (product.isPurchased()&& !product.isBOM()) // then create M_Requisition
		{
			
			System.out.println("yoyo"+product.getName()+" "+QtyPlanned);
 			createRequisition(AD_Org_ID, PP_MRP_ID, product, QtyPlanned ,DemandDateStartSchedule,orderid,productMO,productmasterbom);
		}
		// Manufacturing Order
		else if (product.isBOM())
		{
			createPPOrder(AD_Org_ID, PP_MRP_ID, product,QtyPlanned, DemandDateStartSchedule,productMO,qtyorder);
		}
		else
		{
			throw new IllegalStateException("MRP Internal Error: Don't know what document to "
											+"create for "+product+"("+m_product_planning+")");
		}
	}
	
	protected void createDDOrder(int AD_Org_ID, int PP_MRP_ID, MProduct product,BigDecimal QtyPlanned ,Timestamp DemandDateStartSchedule)
	throws AdempiereException, SQLException
	{		
		//TODO vpj-cd I need to create logic for DRP-040 Shipment Due  Action Notice
		//Indicates that a shipment for a Order Distribution is due. 
		// Action should be taken at the source warehouse to ensure that the order is received on time.
		
		//TODO vpj-cd I need to create logic for DRP-050 Shipment Pas Due  Action Notice
		//Indicates that a shipment for a Order Distribution is past due. You should either delay the orders created the requirement for the product 
		//or expedite them when the product does arrive.
		
		if(m_product_planning.getDD_NetworkDistribution_ID() == 0)
		{
			//Indicates that the Product Planning Data for this product does not specify a valid network distribution.
			createMRPNote("DRP-060", AD_Org_ID, PP_MRP_ID, product , (String)null , null , null);
		}
		MDDNetworkDistribution network = MDDNetworkDistribution.get(getCtx(),m_product_planning.getDD_NetworkDistribution_ID());
		MDDNetworkDistributionLine[] network_lines = network.getLines(m_product_planning.getM_Warehouse_ID());
		int M_Shipper_ID = 0;
		MDDOrder order = null;
		Integer DD_Order_ID = 0;

		for (MDDNetworkDistributionLine network_line : network_lines)
		{	
			//get supply source warehouse and locator
			MWarehouse source = MWarehouse.get(getCtx(), network_line.getM_WarehouseSource_ID());	
			MLocator locator = source.getDefaultLocator();
			
			//get supply target warehouse and locator
			MWarehouse target = MWarehouse.get(getCtx(), network_line.getM_Warehouse_ID());
			MLocator locator_to =target.getDefaultLocator(); 
			//get the transfer time
			BigDecimal transfertTime = network_line.getTransfertTime(); 
			if(transfertTime.compareTo(Env.ZERO) <= 0)
			{
				transfertTime = m_product_planning.getTransfertTime();
			}

			if (locator == null || locator_to == null)
			{
				String comment = Msg.translate(getCtx(), MDDNetworkDistributionLine.COLUMNNAME_M_WarehouseSource_ID)
								 + ":" + source.getName();
				createMRPNote("DRP-001", AD_Org_ID, PP_MRP_ID, product , null , null , comment);
				continue;
			}
			//get the warehouse in transit
			MWarehouse[] wsts = MWarehouse.getInTransitForOrg(getCtx(), source.getAD_Org_ID());

			if (wsts == null || wsts.length == 0)
			{					
				String comment = Msg.translate(getCtx(), MOrg.COLUMNNAME_Name)
				 + ":" + MOrg.get(getCtx(), AD_Org_ID).getName();
				createMRPNote("DRP-010", AD_Org_ID, PP_MRP_ID, product , null , null , comment);
				continue;
			}

			if(network_line.getM_Shipper_ID()==0)
			{
				String comment = Msg.translate(getCtx(), MDDNetworkDistribution.COLUMNNAME_Name) 
				+ ":" + network.getName();
				createMRPNote("DRP-030", AD_Org_ID, PP_MRP_ID, product , null , null , comment);
				continue;
			}
			
			if(M_Shipper_ID != network_line.getM_Shipper_ID())
			{	

				//Org Must be linked to BPartner
				MOrg org = MOrg.get(getCtx(), locator_to.getAD_Org_ID());
				int C_BPartner_ID = org.getLinkedC_BPartner_ID(get_TrxName()); 
				if (C_BPartner_ID == 0)
				{
					String comment = Msg.translate(getCtx(), MOrg.COLUMNNAME_Name)
					 + ":" + MOrg.get(getCtx(), AD_Org_ID).getName();
					createMRPNote("DRP-020", AD_Org_ID, PP_MRP_ID, product, null , null , comment);
					continue;
				}
				
				MBPartner bp = getBPartner(C_BPartner_ID);
				// Try found some order with Shipper , Business Partner and Doc Status = Draft 
				// Consolidate the demand in a single order for each Shipper , Business Partner , DemandDateStartSchedule
				DD_Order_ID = getDDOrder_ID(AD_Org_ID,wsts[0].get_ID(),network_line.getM_Shipper_ID(), bp.getC_BPartner_ID(),DemandDateStartSchedule);
				if (DD_Order_ID <= 0)
				{	
					order = new MDDOrder(getCtx() , 0 , get_TrxName());
					order.setAD_Org_ID(target.getAD_Org_ID());
					order.setC_BPartner_ID(C_BPartner_ID);
					order.setAD_User_ID(bp.getPrimaryAD_User_ID());
					order.setC_DocType_ID(docTypeDO_ID);  
					order.setM_Warehouse_ID(wsts[0].get_ID());
					order.setDocAction(MDDOrder.DOCACTION_Complete);
					order.setDateOrdered(getToday());                       
					order.setDatePromised(DemandDateStartSchedule);
					order.setM_Shipper_ID(network_line.getM_Shipper_ID());	    	                
					order.setIsInDispute(false);
					order.setIsInTransit(false);
					order.setSalesRep_ID(m_product_planning.getPlanner_ID());
					order.saveEx();
					DD_Order_ID = order.get_ID();				
					String key = order.getAD_Org_ID()+"#"+order.getM_Warehouse_ID()+"#"+network_line.getM_Shipper_ID()+"#"+C_BPartner_ID+"#"+DemandDateStartSchedule+"DR";
					dd_order_id_cache.put(key,DD_Order_ID);
				}
				else
				{
					order = new MDDOrder(getCtx(), DD_Order_ID ,get_TrxName());
				}
				
				M_Shipper_ID = network_line.getM_Shipper_ID();
				
			}   

			BigDecimal QtyOrdered = QtyPlanned.multiply(network_line.getPercent()).divide(Env.ONEHUNDRED);

			MDDOrderLine oline = new MDDOrderLine(getCtx(), 0 , get_TrxName());
			oline.setDD_Order_ID(order.getDD_Order_ID());
			oline.setAD_Org_ID(target.getAD_Org_ID());
			oline.setM_Locator_ID(locator.getM_Locator_ID());
			oline.setM_LocatorTo_ID(locator_to.getM_Locator_ID());
			oline.setM_Product_ID(m_product_planning.getM_Product_ID()); 
			oline.setDateOrdered(getToday());                       
			oline.setDatePromised(DemandDateStartSchedule);
			oline.setQtyEntered(QtyOrdered);
			oline.setQtyOrdered(QtyOrdered);
			oline.setTargetQty(MPPMRP.getQtyReserved(getCtx(), target.getM_Warehouse_ID(), m_product_planning.getM_Product_ID(), DemandDateStartSchedule, get_TrxName()));
			oline.setIsInvoiced(false);
			oline.saveEx();



			// Set Correct Dates for Plan
			final String whereClause = MPPMRP.COLUMNNAME_DD_OrderLine_ID+"=?";
			List<MPPMRP> mrpList = new Query(getCtx(), MPPMRP.Table_Name, whereClause, get_TrxName())
										.setParameters(new Object[]{oline.getDD_OrderLine_ID()})
										.list();
			for (MPPMRP mrp : mrpList)
			{
				mrp.setDateOrdered(getToday());               
				mrp.setS_Resource_ID(m_product_planning.getS_Resource_ID());
				mrp.setDatePromised(TimeUtil.addDays(DemandDateStartSchedule , (m_product_planning.getDeliveryTime_Promised().add(transfertTime)).negate().intValueExact()));                                                            
				mrp.setDateFinishSchedule(DemandDateStartSchedule);
				mrp.saveEx();
			}
			
			count_DO += 1;
		}
		
		commitEx();
	}
	
	protected void createRequisition(int AD_Org_ID, int PP_MRP_ID, MProduct product, BigDecimal QtyPlanned, Timestamp DemandDateStartSchedule, int order_id, int ProductMO,int productmasterbom)
	throws AdempiereException, SQLException
	{
		log.info("Create Requisition");
		
		
				int duration = MPPMRP.getDurationDays(null,QtyPlanned, m_product_planning);
				// Get PriceList from BPartner/Group - teo_sarca, FR [ 2829476 ]
				int M_PriceList_ID = -1;
				if (m_product_planning.getC_BPartner_ID() > 0)
				{
					final String sql = "SELECT COALESCE(bp."+MBPartner.COLUMNNAME_PO_PriceList_ID
					+",bpg."+X_C_BP_Group.COLUMNNAME_PO_PriceList_ID+")"
					+" FROM C_BPartner bp"
					+" INNER JOIN C_BP_Group bpg ON (bpg.C_BP_Group_ID=bp.C_BP_Group_ID)"
					+" WHERE bp.C_BPartner_ID=?";
					M_PriceList_ID = DB.getSQLValueEx(get_TrxName(), sql, m_product_planning.getC_BPartner_ID());
				}

				MRequisition req = new  MRequisition(getCtx(),0, get_TrxName()); 
				req.setAD_Org_ID(AD_Org_ID);
				req.setAD_User_ID(m_product_planning.getPlanner_ID());                                                        
				req.setDateRequired(TimeUtil.addDays(DemandDateStartSchedule, 0 - duration));
				req.setDescription("Requisition generated from MRP"); // TODO: add translation
				req.setM_Warehouse_ID(m_product_planning.getM_Warehouse_ID());
				req.setC_DocType_ID(docTypeReq_ID);
				if (M_PriceList_ID > 0)
					req.setM_PriceList_ID(M_PriceList_ID);
				req.saveEx();

				MRequisitionLine reqline = new  MRequisitionLine(req);
				reqline.setLine(10);
				reqline.setAD_Org_ID(AD_Org_ID);
				reqline.setC_BPartner_ID(m_product_planning.getC_BPartner_ID());
				reqline.setM_Product_ID(m_product_planning.getM_Product_ID());
				reqline.setPrice();
				reqline.setPriceActual(Env.ZERO);
				reqline.setQty(QtyPlanned);
				reqline.saveEx();

				// Set Correct Dates for Plan
				final String whereClause = MPPMRP.COLUMNNAME_M_Requisition_ID+"=?";
				List<MPPMRP> mrpList = new Query(getCtx(), MPPMRP.Table_Name, whereClause, get_TrxName())
											.setParameters(new Object[]{req.getM_Requisition_ID()})
											.list();
				for (MPPMRP mrp : mrpList)
				{
					mrp.setDateOrdered(getToday());
					mrp.setS_Resource_ID(m_product_planning.getS_Resource_ID());
					mrp.setDatePromised(req.getDateRequired());                                                            
					mrp.setDateStartSchedule(req.getDateRequired());                                                            
					mrp.setDateFinishSchedule(DemandDateStartSchedule);
					mrp.set_CustomColumn("PP_OrderRef_ID", ProductMO);
					mrp.setIsAvailable(true);
					if(order_id > 0) {
					mrp.setC_Order_ID(order_id);
					}
					mrp.saveEx();

				}
				commitEx();	
				count_MR += 1;
			
		
		
		
	}
	
	protected void createPPOrder(int AD_Org_ID, int PP_MRP_ID, MProduct product,BigDecimal QtyPlanned,Timestamp DemandDateStartSchedule, int ProductMO, BigDecimal QtyOrder)
	throws AdempiereException, SQLException
	{
		log.info("PP_Product_BOM_ID:" + m_product_planning.getPP_Product_BOM_ID() + ", AD_Workflow_ID:" + m_product_planning.getAD_Workflow_ID()
		+ ", product_planning:" + m_product_planning);
		if (m_product_planning.getPP_Product_BOM_ID() == 0 || m_product_planning.getAD_Workflow_ID() == 0)
		{
			throw new AdempiereException("@FillMandatory@ @PP_Product_BOM_ID@, @AD_Workflow_ID@ ( @M_Product_ID@="+product.getValue()+")");
		}
		
		MPPOrder order = new MPPOrder(getCtx(), 0, get_TrxName());
		order.addDescription("MO generated from MRP");
		order.setAD_Org_ID(AD_Org_ID);
		order.setLine(10);
		if(MPPProductBOM.BOMTYPE_Maintenance.equals(getBOMType()))
		{
			log.info("Maintenance Order Created");
			order.setC_DocTypeTarget_ID(docTypeMF_ID);
			order.setC_DocType_ID(docTypeMF_ID); 
		}
		else 
		{	
			log.info("Manufacturing Order Created");
			order.setC_DocTypeTarget_ID(docTypeMO_ID);
			order.setC_DocType_ID(docTypeMO_ID);  
		}
		
		order.setS_Resource_ID(m_product_planning.getS_Resource_ID());
		order.setM_Warehouse_ID(m_product_planning.getM_Warehouse_ID());
		order.setM_Product_ID(m_product_planning.getM_Product_ID());
		order.setM_AttributeSetInstance_ID(0);
		order.setPP_Product_BOM_ID(m_product_planning.getPP_Product_BOM_ID());
		order.setAD_Workflow_ID(m_product_planning.getAD_Workflow_ID());
		order.setPlanner_ID(m_product_planning.getPlanner_ID());
		order.setDateOrdered(getToday());                       
		order.setDatePromised(DemandDateStartSchedule);
		order.set_CustomColumn("QtyPlan", QtyOrder);
		
		//TODO red1-- phepetko commented 
		int duration =  0;//MPPMRP.getDurationDays(null,QtyPlanned, m_product_planning);
		
		order.setDateStartSchedule(TimeUtil.addDays(DemandDateStartSchedule, 0 - duration));
		order.setDateFinishSchedule(DemandDateStartSchedule);
		order.setQty(QtyPlanned);
		// QtyBatchSize : do not set it, let the MO to take it from workflow
		order.setC_UOM_ID(product.getC_UOM_ID());
		order.setYield(Env.ZERO);
		order.setScheduleType(MPPMRP.TYPEMRP_Demand);
		order.setPriorityRule(MPPOrder.PRIORITYRULE_Medium);
		order.setDocAction(MPPOrder.DOCACTION_Complete);
		order.saveEx();
		
		// Set Correct Dates for Plan
				final String whereClause = MPPMRP.COLUMNNAME_PP_Order_ID + "=?";
				List<MPPMRP> mrpList = new Query(getCtx(), MPPMRP.Table_Name, whereClause, get_TrxName())
											.setParameters(new Object[]{order.getPP_Order_ID()})
											.list();
				for (MPPMRP mrp : mrpList)
				{
					
					mrp.set_CustomColumn("PP_OrderRef_ID", ProductMO);
					mrp.saveEx();

				}
				commitEx();

				 if(generatebom>0) {
			          	TampungDataBom tampungDataBom = new TampungDataBom();
			          	tampungDataBom.setProductparent(m_product_planning.getM_Product_ID());
			          	tampungDataBom.setQtyrequired(QtyPlanned);
			          	tampungDataBom.setQtyparentorder(DB.getSQLValueBD(get_TrxName(), "select qtyplan from pp_order where pp_order_id = " + order.getPP_Order_ID()));
			          	list.add(tampungDataBom);
			          	generatebom = 1;
			          }
		//commitEx();

		count_MO += 1;
	}
	
	private void deletePO(String tableName, String whereClause, Object[] params) throws SQLException
	{
		// TODO: refactor this method and move it to org.compiere.model.Query class
		POResultSet<PO> rs = new Query(getCtx(), tableName, whereClause, get_TrxName())
									.setParameters(params)
									.scroll();
		try {
			while(rs.hasNext()) {
				rs.next().deleteEx(true);
			}
		}
		finally {
			rs.close();
		}
		commitEx();
	}

	/**
	 * Create MRP Notice
	 * @param code MRP/DRP Code (see MRP-xxx and DRP-xxx messages)
	 * @param AD_Org_ID organization
	 * @param PP_MRP_ID MRP record id 
	 * @param product product (optional)
	 * @param documentNo Document# (optional)
	 * @param qty quantity (optional)
	 * @param comment comment (optional)
	 * @throws SQLException 
	 */
	protected void createMRPNote(String code, int AD_Org_ID, int PP_MRP_ID, MProduct product, String documentNo, BigDecimal qty, String comment) throws SQLException
	{
		documentNo = documentNo != null ? documentNo : "";
		comment = comment != null ? comment : "";
		qty = qty != null ? qty : Env.ZERO;
		
		MMessage msg = MMessage.get(getCtx(), code);
		// If MRP code not found, use MRP-999 - unknown error 
		if (msg == null)
		{
			msg = MMessage.get(getCtx(), "MRP-999");
		}
		String message = Msg.getMsg(getCtx(), msg.getValue());
		
		int user_id = 0;
		if (m_product_planning != null)
		{
			user_id = m_product_planning.getPlanner_ID();
		}
		
		String reference = "";
		if (product != null)
		{
			reference = product.getValue() + " " + product.getName();
		}
		
		if (!Util.isEmpty(documentNo, true))
		{
			message += " " + Msg.translate(getCtx(), MPPOrder.COLUMNNAME_DocumentNo) +":" + documentNo;
		}
		if (qty != null)
		{
			message += " " + Msg.translate(getCtx(), "QtyPlan") + ":" + qty;
		}
		if (!Util.isEmpty(comment, true))
		{
	        message +=  " " + comment;
		}
	
		MNote note = new MNote(getCtx(),
							msg.getAD_Message_ID(),
							user_id,
							MPPMRP.Table_ID, PP_MRP_ID,
							reference,
							message,
							get_TrxName());
		note.setAD_Org_ID(AD_Org_ID);
		note.saveEx();
		commitEx(); 
		log.info(code+": "+note.getTextMsg());  
		count_Msg += 1;
	}
	
	private void createMRPNote(String code, MPPMRP mrp, MProduct product, String comment) throws SQLException
	{
//		String comment = Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DateStartSchedule)
//		 + ":" + mrp.getDateStartSchedule()
//		 + " " + Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
//		 + ":" + DemandDateStartSchedule;
		createMRPNote(code,  mrp.getAD_Org_ID(), mrp.get_ID(), product,
				MPPMRP.getDocumentNo(mrp.get_ID()), mrp.getQty(), comment);
	}
	
	protected void createMRPNote(String code, int AD_Org_ID, int PP_MRP_ID,
			MProduct product, BigDecimal qty,
			Timestamp DemandDateStartSchedule,
			Exception e) throws SQLException
	{
		String documentNo = null;
		String comment = e.getLocalizedMessage();
		createMRPNote(code, AD_Org_ID, PP_MRP_ID, product, documentNo, qty, comment);
	}
	
	private int getDDOrder_ID(int AD_Org_ID,int M_Warehouse_ID, int M_Shipper_ID,int C_BPartner_ID, Timestamp DatePromised)
	{
		String key = AD_Org_ID+"#"+M_Warehouse_ID+"#"+M_Shipper_ID+"#"+C_BPartner_ID+"#"+DatePromised+"DR";
		Integer order_id = dd_order_id_cache.get(key.toString());
		if ( order_id == null)
		{
			String sql = "SELECT DD_Order_ID FROM DD_Order WHERE AD_Org_ID=? AND M_Warehouse_ID=? AND M_Shipper_ID = ? AND C_BPartner_ID=? AND DatePromised=? AND DocStatus=?";
			order_id = DB.getSQLValueEx(get_TrxName(), sql, 
				new Object[]{	AD_Org_ID,
								M_Warehouse_ID,
								M_Shipper_ID,
								C_BPartner_ID,
								DatePromised,
								MDDOrder.DOCSTATUS_Drafted });
			if(order_id > 0)
				dd_order_id_cache.put(key,order_id);
		}
		return order_id;
	}
	
	private MBPartner getBPartner(int C_BPartner_ID)
	{
		MBPartner partner = partner_cache.get(C_BPartner_ID);
		if ( partner == null)
		{	
			 partner = MBPartner.get(getCtx(), C_BPartner_ID);
			 partner_cache.put(C_BPartner_ID, partner);
		}
		return partner;
	}
	
	/**
	 * Get ScheduledReceipts to cover the ProjectQtyOnhand
	 * @param AD_Client_ID
	 * @param AD_Org_ID
	 * @param M_Warehouse_ID
	 * @param product
	 * @param ProjectQtyOnhand
	 * @param DemandDateStartSchedule
	 * @return Net Requirements:
	 * 			<li>positive qty means entire qty is available or scheduled to receipt
	 * 			<li>negative qty means qty net required
	 * @throws SQLException 
	 */
	private BigDecimal getNetRequirements(int AD_Client_ID, int AD_Org_ID, 
											int M_Warehouse_ID, MProduct product,
											Timestamp DemandDateStartSchedule) throws SQLException
	{
		BigDecimal QtyNetReqs = QtyProjectOnHand.subtract(QtyGrossReqs);
		System.out.println("onhand"+QtyProjectOnHand+" "+QtyGrossReqs+" "+product.getName());
		System.out.println("net"+QtyNetReqs);
		
		final String whereClause =
			// Planning Dimension
			"AD_Client_ID=? AND AD_Org_ID=?"
			+" AND M_Product_ID=? AND M_Warehouse_ID=?"
			// Scheduled Receipts & Planned Orders
		  	+" AND TypeMRP=? AND DocStatus IN (?,?,?)"
		  	// NonZero Qty
		  	+" AND Qty<>0"
		  	// Only available
			+" AND "+MPPMRP.COLUMNNAME_IsAvailable+"=?";
		ArrayList<Object> parameters= new ArrayList<Object>();
		parameters.add(AD_Client_ID);
		parameters.add(AD_Org_ID);
		parameters.add(product.get_ID());
		parameters.add(M_Warehouse_ID);
		parameters.add(MPPMRP.TYPEMRP_Supply);
		parameters.add(MPPMRP.DOCSTATUS_Completed);
		parameters.add(MPPMRP.DOCSTATUS_InProgress);
		parameters.add(MPPMRP.DOCSTATUS_Drafted);
		parameters.add(true);
		  
		Collection<MPPMRP> mrps = new Query(getCtx(), MPPMRP.Table_Name, whereClause, get_TrxName())
										.setParameters(parameters)
										.setOrderBy(MPPMRP.COLUMNNAME_DateStartSchedule)
										.list();
		for (MPPMRP mrp : mrps)
		{
			
			if (mrp.isReleased())
			{
				QtyScheduledReceipts = QtyScheduledReceipts.add(mrp.getQty());
			}
			
			if(DemandDateStartSchedule != null)
			{
				// MRP-030 De-Expedite Action Notice
				// Indicates that a schedule supply order is due before it is needed and should be delayed,
				// or demand rescheduled to an earlier date.
				// aka: Push Out
				if(mrp.isReleased()
						&& QtyNetReqs.negate().signum() > 0
						&& mrp.getDateStartSchedule() != null 
						&& mrp.getDateStartSchedule().compareTo(DemandDateStartSchedule) < 0)
				{
					String comment = Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DateStartSchedule)
									 + ":" + mrp.getDateStartSchedule()
									 + " " + Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
									 + ":" + DemandDateStartSchedule;
					createMRPNote("MRP-030",  mrp, product, comment);
				}
				
				// MRP-040 Expedite Action Notice
				// Indicates that a scheduled supply order is due after is needed and should be rescheduled to
				// an earlier date or demand rescheduled to a later date.
				// aka: Pull In 
				if(mrp.isReleased()
						&& QtyNetReqs.negate().signum() > 0
						&& mrp.getDateStartSchedule() != null 
						&& mrp.getDateStartSchedule().compareTo(DemandDateStartSchedule) > 0)
				{
					String comment = Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DateStartSchedule)
									 + ":" + mrp.getDateStartSchedule()
									 + " " + Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
									 + ":" + DemandDateStartSchedule;
					createMRPNote("MRP-040",  mrp, product, comment);
				}
				
				// MRP-060 Release Due For Action Notice in time
				// Indicate that a supply order should be released. if it is a draft order, it must also be approved.
				// if(date release > today && date release + after floating)
				if (!mrp.isReleased()
						&& QtyNetReqs.negate().signum() > 0
						&& mrp.getDateStartSchedule() != null 
						&& mrp.getDatePromised().compareTo(getToday()) >= 0)
				{
					String comment =  Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
					 					+ ":" + mrp.getDatePromised();
					createMRPNote("MRP-060",  mrp, product, comment);
				}
				
				// MRP-070 Release Past Due For  Action Notice overdue
				// Indicates that a supply order was not released when it was due, and should be either released 
				// or expedited now, or the demand rescheduled for a later date.
				// if (date release < today && date erelese + before floating)
				if (!mrp.isReleased()
						&& QtyNetReqs.negate().signum() > 0
						&& mrp.getDateStartSchedule() != null 
						&& mrp.getDatePromised().compareTo(getToday()) < 0)
				{
					String comment =  Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
					 					+ ":" + mrp.getDatePromised();
					createMRPNote("MRP-070",  mrp, product, comment);
				}
				
				
				//MRP-110 Past Due  Action Notice
				//Indicates that a schedule supply order receipt is past due.		
				if(mrp.isReleased()
						&& mrp.getDateStartSchedule() != null 
						&& mrp.getDatePromised().compareTo(getToday()) < 0)
				{
					String comment =  Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
									 + ":" + mrp.getDatePromised();
					createMRPNote("MRP-110",  mrp, product, comment);
				}
				
				mrp.setIsAvailable(false);
				mrp.saveEx();
				
				QtyNetReqs = QtyNetReqs.add(mrp.getQty());
				
				if (QtyNetReqs.signum() >= 0)
				{
					return QtyNetReqs;
				}
			}	
			else
			{
				//MRP-050 Cancel Action Notice
				//Indicate that a scheduled supply order is no longer needed and should be deleted.
				if(mrp.isReleased()
						&& QtyScheduledReceipts.signum() > 0)
				{
					String comment = Msg.translate(getCtx(), MPPMRP.COLUMNNAME_DatePromised)
					 				+ ":" + mrp.getDatePromised();
					createMRPNote("MRP-050",  mrp, product, comment);
				}
				
				mrp.setIsAvailable(false);
				mrp.saveEx();	
				
				QtyNetReqs = QtyNetReqs.add(mrp.getQty());
			}
		}

		return QtyNetReqs;
	}
	
	protected int getDocType(String docBaseType, int AD_Org_ID)
	{
		MDocType[] docs = MDocType.getOfDocBaseType(getCtx(), docBaseType);

		if (docs == null || docs.length == 0) 
		{
			String reference = Msg.getMsg(getCtx(), "SequenceDocNotFound");
			String textMsg = "Not found default document type for docbasetype "+ docBaseType;
			MNote note = new MNote(getCtx(), MMessage.getAD_Message_ID (getCtx(), "SequenceDocNotFound"),
									getPlanner_ID(), MPPMRP.Table_ID, 0,
									reference,
									textMsg,
									get_TrxName());
			note.saveEx();
			throw new AdempiereException(textMsg);
		} 
		else
		{
			for(MDocType doc:docs)
			{
				if(doc.getAD_Org_ID()==AD_Org_ID)
				{
					return doc.getC_DocType_ID();
				}
			}
			log.info("Doc Type for "+docBaseType+": "+ docs[0].getC_DocType_ID());
			return docs[0].getC_DocType_ID();
		}
	}
	
	/**
	 * get BOMType
	 * @return
	 */
	private String getBOMType()
	{	
		if(m_product_planning == null || m_product_planning.getPP_Product_BOM_ID() == 0 )
			return null;
		
		String BOMType = DB.getSQLValueString(get_TrxName(), "SELECT BOMType FROM PP_Product_BOM WHERE PP_Product_BOM_ID = ?" , m_product_planning.getPP_Product_BOM_ID());
		return BOMType;
	}

}
