/**
 * Licensed under the KARMA v.1 Law of Sharing. As others have shared freely to you, so shall you share freely back to us.
 * If you shall try to cheat and find a loophole in this license, then KARMA will exact your share.
 * and your worldly gain shall come to naught and those who share shall gain eventually above you.
 * In compliance with previous GPLv2.0 works of ComPiere USA, eEvolution MEXICO, iDempiere contributors and Mutlimage SLOVAKIA
 */
package org.idempiere.component;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.adempiere.base.event.LoginEventData;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.I_M_Forecast;
import org.compiere.model.I_M_ForecastLine;
import org.compiere.model.I_M_InOut;
import org.compiere.model.I_M_Inventory;
import org.compiere.model.I_M_InventoryLine;
import org.compiere.model.I_M_Movement;
import org.compiere.model.I_M_Product;
import org.compiere.model.I_M_Requisition;
import org.compiere.model.I_M_RequisitionLine;
import org.compiere.model.I_S_Resource;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MCost;
import org.compiere.model.MCostElement;
import org.compiere.model.MCostQueue;
import org.compiere.model.MDocType;
import org.compiere.model.MForecastLine;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInventory;
import org.compiere.model.MInventoryLine;
import org.compiere.model.MLocator;
import org.compiere.model.MLot;
import org.compiere.model.MLotCtl;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
//import org.compiere.model.MMovement;
//import org.compiere.model.MMovementLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MQuery;
import org.compiere.model.MRMALine;
import org.compiere.model.MRequisition;
import org.compiere.model.MRequisitionLine;
import org.compiere.model.MResource;
import org.compiere.model.MTable;
import org.compiere.model.MUOMConversion;
import org.compiere.model.MWarehouse;
import org.compiere.model.PO;
import org.compiere.model.PrintInfo;
import org.compiere.model.Query;
import org.compiere.model.X_M_Forecast;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportCtl;
import org.compiere.print.ReportEngine;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.eevolution.model.MDDOrder;
import org.eevolution.model.MDDOrderLine;
import org.eevolution.model.MPPProductBOM;
import org.eevolution.model.X_PP_Product_Planning;
import org.libero.model.MPPCostCollector;
import org.libero.model.MPPMRP;
import org.libero.model.MPPOrder;
import org.libero.model.MPPOrderBOM;
import org.libero.model.MPPOrderBOMLine;
import org.libero.model.MPPOrderWorkflow;
import org.libero.model.TampungDataBom;
import org.libero.model.TampungDataDeleteMRP;
import org.libero.tables.I_DD_Order;
import org.libero.tables.I_DD_OrderLine;
import org.libero.tables.I_PP_Order;
import org.libero.tables.I_PP_Order_BOMLine;
import org.osgi.service.event.Event;

/**
 *
 * @author hengsin (new Event ModelValidator regime)
 * @author Victor Perez, Teo Sarca,  
 * @contributor red1@red1.org (refactoring to new OSGi framework)
 *
 */
public class MFG_Validator extends AbstractEventHandler {
	private static CLogger log = CLogger.getCLogger(MFG_Validator.class);
	private String trxName = "";
	private PO po = null;
	@Override
	protected void initialize() {
		registerEvent(IEventTopics.AFTER_LOGIN);
		registerTableEvent(IEventTopics.PO_BEFORE_NEW, I_M_Movement.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_C_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_C_OrderLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_M_Requisition.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_M_RequisitionLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_M_Forecast.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_M_ForecastLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_DD_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_DD_OrderLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_PP_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_PP_Order_BOMLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_C_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_C_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, MPPOrder.Table_Name);
		//registerTableEvent(IEventTopics.PO_AFTER_NEW, I_m_forecastlinedetail.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, I_M_Product.Table_Name); 
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_M_Product.Table_Name); 
		registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, I_S_Resource.Table_Name); 
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_S_Resource.Table_Name); 
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_C_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_C_OrderLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_M_Requisition.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_M_RequisitionLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_M_Forecast.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_M_ForecastLine.Table_Name);
		//registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_m_forecastlinedetail.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_DD_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_DD_OrderLine.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_PP_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, I_PP_Order_BOMLine.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_C_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_C_OrderLine.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_M_Requisition.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_M_RequisitionLine.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_M_Forecast.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_M_ForecastLine.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_DD_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_DD_OrderLine.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_PP_Order.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_PP_Order_BOMLine.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_M_Forecast.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_DELETE, I_M_ForecastLine.Table_Name); 
		registerTableEvent(IEventTopics.DOC_BEFORE_PREPARE, I_M_Forecast.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, I_M_ForecastLine.Table_Name);
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_M_Movement.Table_Name);
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_M_InOut.Table_Name);
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_C_Order.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REACTIVATE, I_DD_Order.Table_Name);
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, MPPOrder.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, MPPOrder.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_PREPARE, MPPOrder.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, I_M_Inventory.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_NEW, I_M_InventoryLine.Table_Name);
		log.info("MFG MODEL VALIDATOR IS NOW INITIALIZED");
	}

	@Override
	protected void doHandleEvent(Event event) {
		String type = event.getTopic();
		DocAction doc = null;
		boolean isDelete = false;
		boolean isReleased = false;
		boolean isVoided = false;
		boolean isChange = false;
		
		if (type.equals(IEventTopics.AFTER_LOGIN)) {
			LoginEventData eventData = getEventData(event);
			log.fine(" topic="+event.getTopic()+" AD_Client_ID="+eventData.getAD_Client_ID()
					+" AD_Org_ID="+eventData.getAD_Org_ID()+" AD_Role_ID="+eventData.getAD_Role_ID()
					+" AD_User_ID="+eventData.getAD_User_ID());
			System.out.println(" topic="+event.getTopic()+" AD_Client_ID="+eventData.getAD_Client_ID()
			+" AD_Org_ID="+eventData.getAD_Org_ID()+" AD_Role_ID="+eventData.getAD_Role_ID()
			+" AD_User_ID="+eventData.getAD_User_ID());
		}
		else 
		{
			setPo(getPO(event));
			setTrxName(po.get_TrxName());
			log.info(" topic="+event.getTopic()+" po="+po);
			isChange = (IEventTopics.PO_AFTER_NEW == type || (IEventTopics.PO_AFTER_CHANGE == type && MPPMRP.isChanged(po)));
			isDelete = (IEventTopics.PO_BEFORE_DELETE == type);
			isReleased = false;
			isVoided = false;
			
			if (po instanceof DocAction)
			{
				doc = (DocAction)po;
			}
				else if (po instanceof MOrderLine)
			{
				doc = ((MOrderLine)po).getParent();
			}
			
			if (doc != null)
			{
				String docStatus = doc.getDocStatus();
				isReleased = DocAction.STATUS_InProgress.equals(docStatus)
							|| DocAction.STATUS_Completed.equals(docStatus);
				isVoided = DocAction.STATUS_Voided.equals(docStatus);
			}
		
			// Can we change M_Product.C_UOM_ID ?
			if (po instanceof MProduct && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged(MProduct.COLUMNNAME_C_UOM_ID)
					&& MPPMRP.hasProductRecords((MProduct)po))
			{
				throw new AdempiereException("@SaveUomError@");
			}
			
			if(po instanceof MProduct && IEventTopics.PO_AFTER_NEW == type) {
				MProduct product = (MProduct)po;
				if(product.isBOM() || product.getProductType().equals("R")) {
				MAcctSchema as = MAcctSchema.get(po.getCtx(),1000000 );
				final String whereClause = "costelementtype != 'M' and ad_client_id = "+po.getAD_Client_ID();
				Collection<MCostElement> costelement = new Query(po.getCtx(), MCostElement.Table_Name, whereClause, trxName)											
											.list();
				for (MCostElement mCostElement : costelement) {
					 MCost cost  = new MCost(product, 0, as,0, mCostElement.getM_CostElement_ID());
					 cost.save();
				}
				}
				
			}
			
			
			if (po instanceof MProduct && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("Costlabor")
					)
			{
				MProduct product = (MProduct)po;
				updatecost(product, new BigDecimal(product.get_Value("Costlabor").toString()) , "Labor");
			}
			
			if (po instanceof MProduct && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("CostBurden")
					)
			{
				MProduct product = (MProduct)po;
				updatecost(product, new BigDecimal(product.get_Value("CostBurden").toString()) , "Burden");
			}
			
			if (po instanceof MProduct && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("Costoverhead")
					)
			{
				MProduct product = (MProduct)po;
				updatecost(product, new BigDecimal(product.get_Value("Costoverhead").toString()) , "Overhead");
			}
			
			if (po instanceof MProduct && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("Costoutside")
					)
			{
				MProduct product = (MProduct)po;
				updatecost(product, new BigDecimal(product.get_Value("Costoutside").toString()) , "Outside Processing");
			}
			
			if(po instanceof MResource && IEventTopics.PO_AFTER_NEW == type) {
				MResource mResource = (MResource)po;
				MProduct mProduct = MProduct.forS_Resource_ID(mResource.getCtx(), mResource.getS_Resource_ID(), mResource.get_TrxName());
				MProduct product = new MProduct(mResource.getCtx(), mProduct.getM_Product_ID(), mResource.get_TrxName());			
			    product.setC_UOM_ID(mResource.get_ValueAsInt("C_UOM_ID"));
				product.save();
				updatecost(product, new BigDecimal(mResource.get_Value("Costlabor").toString()) , "Labor");
				updatecost(product, new BigDecimal(mResource.get_Value("CostBurden").toString()) , "Burden");
				updatecost(product, new BigDecimal(mResource.get_Value("Costoverhead").toString()) , "Overhead");
				updatecost(product, new BigDecimal(mResource.get_Value("Costoutside").toString()) , "Outside Processing");
				
			}
			
			if(po instanceof MResource && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("C_UOM_ID")) {
				MResource mResource = (MResource)po;
				MProduct mProduct = MProduct.forS_Resource_ID(mResource.getCtx(), mResource.getS_Resource_ID(), mResource.get_TrxName());
				MProduct product = new MProduct(mResource.getCtx(), mProduct.getM_Product_ID(), mResource.get_TrxName());			
			    product.setC_UOM_ID(mResource.get_ValueAsInt("C_UOM_ID"));
				product.save();
				
			}
			
			if (po instanceof MResource && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("Costlabor")
					)
			{
				MResource mResource = (MResource)po;
				MProduct mProduct = MProduct.forS_Resource_ID(mResource.getCtx(), mResource.getS_Resource_ID(), mResource.get_TrxName());
				MProduct product = new MProduct(mResource.getCtx(), mProduct.getM_Product_ID(), mResource.get_TrxName());			
				updatecost(product, new BigDecimal(mResource.get_Value("Costlabor").toString()) , "Labor");
			}
			
			if (po instanceof MResource && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("CostBurden")
					)
			{
				MResource mResource = (MResource)po;
				MProduct mProduct = MProduct.forS_Resource_ID(mResource.getCtx(), mResource.getS_Resource_ID(), mResource.get_TrxName());
				MProduct product = new MProduct(mResource.getCtx(), mProduct.getM_Product_ID(), mResource.get_TrxName());	
				updatecost(product, new BigDecimal(mResource.get_Value("CostBurden").toString()) , "Burden");
			}
			
			if (po instanceof MResource && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("Costoverhead")
					)
			{
				MResource mResource = (MResource)po;
				MProduct mProduct = MProduct.forS_Resource_ID(mResource.getCtx(), mResource.getS_Resource_ID(), mResource.get_TrxName());
				MProduct product = new MProduct(mResource.getCtx(), mProduct.getM_Product_ID(), mResource.get_TrxName());	
				updatecost(product, new BigDecimal(mResource.get_Value("Costoverhead").toString()) , "Overhead");
			}
			
			if (po instanceof MResource && IEventTopics.PO_BEFORE_CHANGE == type
					&& po.is_ValueChanged("Costoutside")
					)
			{
				MResource mResource = (MResource)po;
				MProduct mProduct = MProduct.forS_Resource_ID(mResource.getCtx(), mResource.getS_Resource_ID(), mResource.get_TrxName());
				MProduct product = new MProduct(mResource.getCtx(), mProduct.getM_Product_ID(), mResource.get_TrxName());
				updatecost(product, new BigDecimal(mResource.get_Value("Costoutside").toString()) , "Outside Processing");
			}
			
			if(po instanceof MInventoryLine && IEventTopics.PO_AFTER_NEW == type ) {
				MInventoryLine  inventoryLine = (MInventoryLine)po;
				if(inventoryLine.getM_Product().getM_AttributeSet_ID()>0 && inventoryLine.getM_Inventory().getReversal_ID()==0) {
				GenerateCodeAttribute(inventoryLine, po);
				}
			}
			
			
			
			if(po instanceof MInventory && IEventTopics.DOC_BEFORE_COMPLETE == type ) {
				MInventory  inventory = (MInventory)po;
				 if(!inventory.getDocStatus().equals("CO")) {
					     cost(inventory, po);
						costQueue(inventory, po);
					 }
			}
			
			if(po instanceof MPPOrder && (IEventTopics.DOC_BEFORE_COMPLETE == type || IEventTopics.DOC_BEFORE_PREPARE  == type )) {
				MPPOrder mppOrder = (MPPOrder)po;
				if(mppOrder.getM_Product().getM_AttributeSet_ID()>0) {
					if(mppOrder.getM_AttributeSetInstance_ID() == 0) {
						throw new AdempiereException("No Attribute");
					}
				}
			}
			
			
			
			
		 
			if (isDelete || isVoided || !po.isActive())
			{
				logEvent(event, po, type);//log.fine("MPPMRP.deleteMRP(po)");
				MPPMRP.deleteMRP(po);
			}
			else if (po instanceof MOrder)
			{
				MOrder order = (MOrder)po;
			// Create/Update a planning supply when isPurchase Order
			// or when you change DatePromised or DocStatus and is Purchase Order
				if (isChange && !order.isSOTrx())
				{
					logEvent(event, po, type);//log.fine("isChange && !order.isSOTrx() .. MPPMRP.C_Order(order)");
					MPPMRP.C_Order(order);
				}
			// Update MRP when you change the status order to complete or in process for a sales order
			// or you change DatePromised
				else if (type == IEventTopics.PO_AFTER_CHANGE && order.isSOTrx())
				{
					if (isReleased || MPPMRP.isChanged(order)) 
					{	
						logEvent(event, po, type);//log.fine("isReleased || MPPMRP.isChanged(order) .. MPPMRP.C_Order(order)");
						MPPMRP.C_Order(order);
					}
				}
			}
		// 
			else if (po instanceof MOrderLine && isChange)
			{
				MOrderLine ol = (MOrderLine)po;
				MOrder order = ol.getParent();
			// Create/Update a planning supply when isPurchase Order or you change relevant fields
				if (!order.isSOTrx())
				{
					
					logEvent(event, po, type);//log.fine("!order.isSOTrx() .. MPPMRP.C_OrderLine(ol)");
					MPPMRP.C_OrderLine(ol);
				}
			// Update MRP when Sales Order have document status in process or complete and 
			// you change relevant fields
				else if(order.isSOTrx() && isReleased)
				{		
					logEvent(event, po, type);//log.fine("order.isSOTrx() && isReleased .. MPPMRP.C_OrderLine(ol)");
					//MPPMRP.C_OrderLine(ol);
				}
			}
		//
			else if (po instanceof MRequisition && isChange)
			{
				MRequisition r = (MRequisition)po;
				logEvent(event, po, type);//log.fine(" .. MPPMRP.M_Requisition(r)");
				log.warning(event.getTopic());
				MPPMRP.M_Requisition(r);
			}
		//
			else if (po instanceof MRequisitionLine && isChange)
			{
				MRequisitionLine rl = (MRequisitionLine)po;
				logEvent(event, po, type);//log.fine(" .. MPPMRP.M_Requisition(rl)");
				MPPMRP.M_RequisitionLine(rl);
			}
		//
			else if (po instanceof X_M_Forecast && isChange)
			{
				X_M_Forecast fl = (X_M_Forecast)po;
				logEvent(event, po, type);//log.fine(" .. MPPMRP.M_Forecast(fl)");
				//MPPMRP.M_Forecast(fl);
			}
		//
			else if (po instanceof MForecastLine && isChange)
			{
				MForecastLine fl = (MForecastLine)po;
				logEvent(event, po, type);//log.fine(" .. MPPMRP.M_ForecastLine(fl)");
				MPPMRP.M_ForecastLine(fl);
				//MPPMRP.createMOFromForecast( fl, fl.getQty());
			}
			
//			else if (po instanceof MForecastLineDetail && isChange)
//			{
//				MForecastLineDetail fl = (MForecastLineDetail)po;
//				System.out.println("yoyo");
//				MPPMRP.createMOFromForecast( fl, fl.getQty());
//			}
		
//			else if (po instanceof MDDOrder  && isChange)
//			{
//				MDDOrder order = (MDDOrder)po;
//				logEvent(event, po, type);//log.fine(" .. MPPMRP.DD_Order(order)");
//				MPPMRP.DD_Order(order);
//			}
		
		//
//			else if (po instanceof MDDOrderLine && isChange)
//			{
//				MDDOrderLine ol = (MDDOrderLine)po;
//				logEvent(event, po, type);//log.fine(" .. MPPMRP.DD_OrderLine(ol)");
//				MPPMRP.DD_OrderLine(ol);
//			}
		//
			else if (po instanceof MPPOrder && isChange)
			{
				MPPOrder order = (MPPOrder)po;
				logEvent(event, po, type);//log.fine(" .. MPPMRP.PP_Order(order)");
				MPPMRP.PP_Order(order);
			    
			}
		//
			else if (po instanceof MPPOrderBOMLine && isChange)
			{
				MPPOrderBOMLine obl = (MPPOrderBOMLine)po;
				logEvent(event, po, type);//log.fine(" .. MPPMRP.PP_Order_BOMLine(obl)");
				MPPMRP.PP_Order_BOMLine(obl);
				
			}	
			
		//PO: TYPE_AFTER_NEW
			if (event.getTopic().equals(IEventTopics.PO_AFTER_NEW)) {
				po = getPO(event);
				log.info(" topic="+event.getTopic()+" po="+po); 
		
		//MProduct: TYPE_BEFORE_CHANGE
			} else if (event.getTopic().equals(IEventTopics.PO_BEFORE_CHANGE)) { 
			 po = getPO(event);
			log.info(" topic="+event.getTopic()+" po="+po);
			if (po.get_TableName().equals(I_M_Product.Table_Name)) {
 				String msg = "TODO";
 				logEvent(event, po, type);//log.fine("EVENT MANAGER // Product: PO_BEFORE_CHANGE >> MFG TODO 1 = '"+msg+"'");
			}
		}
		if (po instanceof MInOut && type == IEventTopics.DOC_AFTER_COMPLETE)
			{
				logEvent(event, po, type);//
				MInOut inout = (MInOut)po;
				if(inout.isSOTrx())
				{
					for (MInOutLine outline : inout.getLines())
					{										
						updateMPPOrder(outline);				
					}
				}
			//Purchase Receipt
				else
				{	
					for (MInOutLine line : inout.getLines())
					{
						
						final String whereClause = "C_OrderLine_ID=? AND PP_Cost_Collector_ID IS NOT NULL";
						Collection<MOrderLine> olines = new Query(po.getCtx(), MOrderLine.Table_Name, whereClause, trxName)
													.setParameters(new Object[]{line.getC_OrderLine_ID()})
													.list();
						for (MOrderLine oline : olines)
						{
							if(oline.getQtyOrdered().compareTo(oline.getQtyDelivered()) >= 0)
							{	
								MPPCostCollector cc = new MPPCostCollector(po.getCtx(), oline.getPP_Cost_Collector_ID(), trxName);
								String docStatus = cc.completeIt();
								cc.setDocStatus(docStatus);
								cc.setDocAction(MPPCostCollector.DOCACTION_Close);
								cc.saveEx(trxName);
								return;
							}
						}	
					}
				}	
			}
		//
		// Update Distribution Order Line
			else if (po instanceof MMovement && type == IEventTopics.DOC_AFTER_REVERSECORRECT)
			{
				logEvent(event, po, type);//
				MMovement move = (MMovement)po;
				updateDDOrderQtyInOutBoundReverse(move);
				
//				for (MMovementLine line : move.getLines(false))
//				{
//					if(line.getDD_OrderLine_ID() > 0)
//					{
//						MDDOrderLine oline= new MDDOrderLine(line.getCtx(),line.getDD_OrderLine_ID(), po.get_TrxName());
//						MLocator locator_to = MLocator.get(line.getCtx(), line.getM_LocatorTo_ID());
//						MWarehouse warehouse =  MWarehouse.get(line.getCtx(), locator_to.getM_Warehouse_ID()); 
//						if(warehouse.isInTransit())
//						{
//							oline.setQtyInTransit(oline.getQtyInTransit().add(line.getMovementQty()));
//							oline.setConfirmedQty(Env.ZERO);
//						}
//						else
//						{
//							oline.setQtyInTransit(oline.getQtyInTransit().subtract(line.getMovementQty()));
//							oline.setQtyDelivered(oline.getQtyDelivered().add(line.getMovementQty()));
//						}   
//						oline.saveEx(trxName);				   
//					}
//				}			
//				if(move.getDD_Order_ID() > 0)
//				{	
//					MDDOrder order = new MDDOrder(move.getCtx(), move.getDD_Order_ID(), move.get_TrxName());
//					order.setIsInTransit(isInTransit(order));
//					order.reserveStock(order.getLines(true, null));
//					order.saveEx(trxName);
//					}
			}else if (po instanceof MMovement && type == IEventTopics.DOC_AFTER_COMPLETE) { 
		    	  logEvent(event, po, type);//
					MMovement move = (MMovement)po;
		    	    updateDDOrderQtyInOutBound(move);  
		      }else if(po instanceof MDDOrder && type == IEventTopics.DOC_BEFORE_REACTIVATE) {
					logEvent(event, po, type);//
					MDDOrder ddorder = (MDDOrder)po;
					checkActiveInOutBound(ddorder);
//					if(order.isSOTrx()) {
//						print(order);
//					}
					
				} else if(po instanceof MPPOrder && type == IEventTopics.DOC_AFTER_COMPLETE) {
					logEvent(event, po, type);//
					MPPOrder morder = (MPPOrder)po;
					
				}
			}
	}
	
	private void updatecost(MProduct product, BigDecimal costprice, String costelementname) {
	
		final String whereClause = "costelementtype != 'M' and ad_client_id = "+po.getAD_Client_ID();
		Collection<MCostElement> costelement = new Query(po.getCtx(), MCostElement.Table_Name, whereClause, trxName)											
									.list();
		for (MCostElement mCostElement : costelement) {		
			
			 if(mCostElement.getName().equals(costelementname)) {
				 DB.executeUpdate("update m_cost set currentcostprice = "+costprice+" where m_product_id = "+product.getM_Product_ID()+" and m_costelement_id = "+mCostElement.getM_CostElement_ID(), trxName);
				 
			 }
		}
	}
	
	

	
//	private void print(MOrder order)
//	{
//		String formatName = "PP Sales Order";
//		String tableName = "RV_PP_SalesOrder";
//			// Get Format & Data
//			int format_id= MPrintFormat.getPrintFormat_ID(formatName, MTable.getTable_ID(tableName), order.getAD_Client_ID());
//			MPrintFormat format = MPrintFormat.get(order.getCtx(), format_id, true);
//			if (format == null)
//			{
//				log.warning("@NotFound@ @AD_PrintFormat_ID@"+format_id);
//			}
//			// query
//			MQuery query = new MQuery(tableName);
//			query.addRestriction("C_Order_ID", MQuery.EQUAL, Integer.valueOf(order.getC_Order_ID()));
//			//query.addRestriction("AD_PInstance_ID", MQuery.EQUAL, AD_PInstance_ID);
//			// Engine
//			PrintInfo info = new PrintInfo(X_RV_PP_Product_BOMLine_Table_Name, 
//					X_RV_PP_Product_BOMLine_Table_ID, order.getC_Order_ID());
//			ReportEngine re = new ReportEngine(order.getCtx(), format, query, info);
//			ReportCtl.preview(re);
//			re.print(); // prints only original	
//	}
	
	/**
	 * Define if a Distribution Order is in transit 
	 * @param order
	 * @return true or false
	 */
	private boolean isInTransit(MDDOrder order)
	{
		for (MDDOrderLine line : order.getLines(true, null))
		{
			if(line.getQtyInTransit().signum() != 0)
			{
				return true;
			}
		}
		return false;
	}
	
	private void updateMPPOrder(MInOutLine outline)
	{
		MPPOrder order = null;
		BigDecimal qtyShipment = Env.ZERO;
		MInOut inout =  outline.getParent();
		String movementType = inout.getMovementType();
		int C_OrderLine_ID = 0;
		if(MInOut.MOVEMENTTYPE_CustomerShipment.equals(movementType))
		{
		   C_OrderLine_ID = outline.getC_OrderLine_ID();
		   qtyShipment = outline.getMovementQty();
		}
		else if (MInOut.MOVEMENTTYPE_CustomerReturns.equals(movementType)) 
		{
				MRMALine rmaline = new MRMALine(outline.getCtx(),outline.getM_RMALine_ID(), null); 
				MInOutLine line = (MInOutLine) rmaline.getM_InOutLine();
				C_OrderLine_ID = line.getC_OrderLine_ID();
				qtyShipment = outline.getMovementQty().negate();
		}
		
		final String whereClause = " C_OrderLine_ID = ? "
				+ " AND DocStatus IN  (?,?)"
				+ " AND EXISTS (SELECT 1 FROM  PP_Order_BOM "
				+ " WHERE PP_Order_BOM.PP_Order_ID=PP_Order.PP_Order_ID AND PP_Order_BOM.BOMType =? )"; 
	
		order = new Query(outline.getCtx(), I_PP_Order.Table_Name, whereClause, outline.get_TrxName())
			 .setParameters(new Object[]{C_OrderLine_ID,
				 					 MPPOrder.DOCSTATUS_InProgress,
				 					 MPPOrder.DOCSTATUS_Completed,
				 					 MPPOrderBOM.BOMTYPE_Make_To_Kit
				 					}).firstOnly();
		if (order == null)
		{	
			return;
		}
		
		if(MPPOrder.DOCSTATUS_InProgress.equals(order.getDocStatus()))
		{
			order.completeIt();
			order.setDocStatus(MPPOrder.ACTION_Complete);
			order.setDocAction(MPPOrder.DOCACTION_Close);
			order.saveEx(trxName);			
		}
		if (MPPOrder.DOCSTATUS_Completed.equals(order.getDocStatus()))
		{	
			String description = order.getDescription() !=  null ?  order.getDescription() : ""
				+ Msg.translate(inout.getCtx(), MInOut.COLUMNNAME_M_InOut_ID) 
				+ " : " 
				+ Msg.translate(inout.getCtx(), MInOut.COLUMNNAME_DocumentNo);
			order.setDescription(description);
			order.updateMakeToKit(qtyShipment);
			order.saveEx(trxName);
		}
		
		if(order.getQtyToDeliver().compareTo(Env.ZERO)==0)
		{
			order.closeIt();
			order.setDocStatus(MPPOrder.DOCACTION_Close);
			order.setDocAction(MPPOrder.DOCACTION_None);
			order.saveEx(trxName);
		}
		return;
	}
	//red1 factored log message handling
	private void logEvent (Event event, PO po, String msg) {
		log.info("LiberoMFG >> ModelValidator // "+event.getTopic()+" po="+po+" MESSAGE ="+msg);		
	}

	private void setPo(PO eventPO) {
		 po = eventPO;
	}

	private void setTrxName(String get_TrxName) {
		trxName = get_TrxName;		
	}
	
	private static String updateDDOrderQtyInOutBound(MMovement move) {
		boolean isOutbound = false;
		boolean isInbound = false;
		
		if (move.get_ValueAsBoolean("IsOutbound")) {
			isOutbound=true;
		}
		else if (move.get_ValueAsBoolean("IsInbound")) {
			isInbound=true;
		}
		if (isOutbound || isInbound) {			
			MMovementLine[] moveLines = move.getLines(false);
			for (MMovementLine moveLine : moveLines) {
				int DD_OrderLine_ID = moveLine.get_ValueAsInt("DD_OrderLine_ID");
				if (DD_OrderLine_ID <= 0) {
					return "Bound move line : "+moveLine.get_Value("Line")+" have no referenced DD_OrderLine_ID";
				}
				MDDOrderLine ddLine = new MDDOrderLine(move.getCtx(), DD_OrderLine_ID, move.get_TrxName());
				if (isOutbound && moveLine.getMovementQty().compareTo(BigDecimal.ZERO)>0 ) {
					String jml = ddLine.get_ValueAsString("qtyoutbound");
					BigDecimal qtyDDOutBound = new BigDecimal(jml);
					qtyDDOutBound = qtyDDOutBound.add(moveLine.getMovementQty());
					ddLine.set_ValueOfColumn("qtyoutbound", qtyDDOutBound);
				}
				else if (isInbound && moveLine.getMovementQty().compareTo(BigDecimal.ZERO)>0) {
					String jml = ddLine.get_ValueAsString("qtyinbound");
					BigDecimal qtyDDInBound =  new BigDecimal(jml);
					qtyDDInBound = qtyDDInBound.add(moveLine.getMovementQty());
					ddLine.set_ValueOfColumn("qtyinbound",  qtyDDInBound);
				}
				ddLine.saveEx(move.get_TrxName());

			}
		}
		
		return "";
	}
	
	private static String updateDDOrderQtyInOutBoundReverse(MMovement move) {
		boolean isOutbound = false;
		boolean isInbound = false;
		
		if (move.get_ValueAsBoolean("IsOutbound")) {
			isOutbound=true;
		}
		else if (move.get_ValueAsBoolean("IsInbound")) {
			isInbound=true;
		}
		if (isOutbound || isInbound) {			
			MMovementLine[] moveLines = move.getLines(false);
			for (MMovementLine moveLine : moveLines) {
				int DD_OrderLine_ID = moveLine.get_ValueAsInt("DD_OrderLine_ID");
				if (DD_OrderLine_ID <= 0) {
					return "Bound move line : "+moveLine.get_Value("Line")+" have no referenced DD_OrderLine_ID";
				}
				MDDOrderLine ddLine = new MDDOrderLine(move.getCtx(), DD_OrderLine_ID, move.get_TrxName());
				if (isOutbound && moveLine.getMovementQty().compareTo(BigDecimal.ZERO)>0 ) {
					String jml = ddLine.get_ValueAsString("QtyOutbound");
					BigDecimal qtyDDOutBound = new BigDecimal(jml);
					qtyDDOutBound = qtyDDOutBound.add(moveLine.getMovementQty().negate());
					//hsloutbound = DB.getSQLValueBD(null, "select sum(movementqty) from m_movementline where dd_orderline_id = ?", DD_OrderLine_ID);
					ddLine.set_ValueOfColumn("QtyOutbound", qtyDDOutBound);
				}
				else if (isInbound && moveLine.getMovementQty().compareTo(BigDecimal.ZERO)>0) {
					String jml = ddLine.get_ValueAsString("qtyinbound");
					BigDecimal qtyDDInBound = new BigDecimal(jml);
			
				    
					qtyDDInBound = qtyDDInBound.add(moveLine.getMovementQty().negate());
					ddLine.set_ValueOfColumn("QtyInbound", qtyDDInBound);
					//}
				}
				ddLine.saveEx(move.get_TrxName());

			}
		}
		
		return "";
	}
	
	private static String checkActiveInOutBound(MDDOrder ddOrder){
		
		String sqlWhere = "DD_Order_ID="+ddOrder.getDD_Order_ID()+" AND DocStatus IN ('CO','CL') AND AD_Client_ID="+ddOrder.getAD_Client_ID();
		boolean match = new Query(ddOrder.getCtx(), MMovement.Table_Name, sqlWhere, ddOrder.get_TrxName())
						.match();
		if (match)
			throw new AdempiereException("Active OutBound Or InBound Exist");
		//	return "Active OutBound Or InBound Exist";
		
		return "";
	}
	
	private static BigDecimal costqty = BigDecimal.ZERO;
	private static BigDecimal costqtyQueue = BigDecimal.ZERO;
	
	
	
	
	public static String cost (MInventory inventory,PO po){
		MAcctSchema acctSchema = new MAcctSchema(po.getCtx(),1000000 , po.get_TrxName());
		//MProduction production2 = (MProduction) po;
		for (MInventoryLine inventoryLine : inventory.getLines(false)) {
			//DB.executeUpdate("delete from M_CostQueue where m_product_id = " + orderLine.getM_Product_ID(), get_TrxName());
			try {
				if(inventoryLine.getM_AttributeSetInstance_ID()>0)
					costqty = DB.getSQLValueBD(null, "select coalesce(currentcostprice,0) from M_CostQueue where m_product_id = ? and m_attributesetinstance_id = " + inventoryLine.getM_AttributeSetInstance_ID(), inventoryLine.getM_Product_ID());
					if(costqty==null) {
						  MProduct product = new MProduct(po.getCtx(), inventoryLine.getM_Product_ID(), po.get_TrxName());
						  MCost cost = new MCost(product, inventoryLine.getM_AttributeSetInstance_ID(), acctSchema, 0, 1000002);
						  cost.setCurrentCostPrice(DB.getSQLValueBD(null, "select costprice from m_inventoryline where m_inventoryline_id = ?", inventoryLine.getM_InventoryLine_ID()));
						  if(cost.save()) {
							  costqty = BigDecimal.ZERO;
						  }
					}	
			} catch (Exception e) {
				// TODO: handle exception
			}
			
		}	
		  
		  return "";
	}
	
	public static String costQueue (MInventory inventory,PO po){
		MAcctSchema acctSchema = new MAcctSchema(po.getCtx(),1000000 , po.get_TrxName());
		//MProduction production2 = (MProduction) po;
		for (MInventoryLine inventoryLine : inventory.getLines(false)) {
			//DB.executeUpdate("delete from M_CostQueue where m_product_id = " + orderLine.getM_Product_ID(), get_TrxName());
			try {
				if(inventoryLine.getM_AttributeSetInstance_ID()>0)
					costqtyQueue = DB.getSQLValueBD(null, "select coalesce(currentcostprice,0) from M_CostQueue where m_product_id = ? and m_attributesetinstance_id = " + inventoryLine.getM_AttributeSetInstance_ID(), inventoryLine.getM_Product_ID());
					if(costqtyQueue == null) {
					      MProduct product = new MProduct(po.getCtx(), inventoryLine.getM_Product_ID(), po.get_TrxName());
						  MCostQueue costQueue = new MCostQueue(product, inventoryLine.getM_AttributeSetInstance_ID(), acctSchema, 0, 1000002, po.get_TrxName());
						  costQueue.setCurrentCostPrice(DB.getSQLValueBD(null, "select costprice from m_inventoryline where m_inventoryline_id = ?", inventoryLine.getM_InventoryLine_ID()));
						  if(costQueue.save()) {
							  costqtyQueue = BigDecimal.ZERO;
						  }
					}
			} catch (Exception e) {
				// TODO: handle exception
			}
			
		}	
		  
		  return "";
	}
	

	
	private void GenerateCodeAttribute(MInventoryLine line, PO po) {
		MLot lot = new MLot(po.getCtx(), 0, po.get_TrxName());
		MLotCtl lotCtl = new MLotCtl(po.getCtx(), DB.getSQLValue(po.get_TrxName(), "select M_LotCtl_ID from M_AttributeSet ma left join m_product mp on mp.M_AttributeSet_ID = ma.M_AttributeSet_ID where mp.m_product_id = "+line.getM_Product_ID()), po.get_TrxName());
		lot.setM_Product_ID(line.getM_Product_ID());
		lot.setM_LotCtl_ID(lotCtl.getM_LotCtl_ID());
		lot.setName(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
		if(lot.save()) {
			MAttributeSetInstance attributeSetInstance = new MAttributeSetInstance(po.getCtx(), 0, po.get_TrxName());
			attributeSetInstance.setM_AttributeSet_ID(line.getM_Product().getM_AttributeSet_ID());
			attributeSetInstance.setM_Lot_ID(lot.getM_Lot_ID());
			attributeSetInstance.setAD_Org_ID(line.getAD_Org_ID());
			attributeSetInstance.setDescription(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
			attributeSetInstance.setLot(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
			if(attributeSetInstance.save(po.get_TrxName())) {
				
				lotCtl.setCurrentNext(lotCtl.getCurrentNext()+lotCtl.getIncrementNo());
				lotCtl.save();
				
				MInventoryLine Line = new MInventoryLine(po.getCtx(), line.getM_InventoryLine_ID(), po.get_TrxName());
				Line.setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
				Line.saveEx();
			}
		}
		
	
}
	

}
