/**
 * Licensed under the KARMA v.1 Law of Sharing. As others have shared freely to you, so shall you share freely back to us.
 * If you shall try to cheat and find a loophole in this license, then KARMA will exact your share.
 * and your worldly gain shall come to naught and those who share shall gain eventually above you.
 * In compliance with previous GPLv2.0 works of ComPiere USA, eEvolution MEXICO, iDempiere contributors and Mutlimage SLOVAKIA
 */
package org.libero.model;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DocTypeNotFoundException;
import org.adempiere.model.engines.CostDimension;
import org.adempiere.model.engines.CostEngine;
import org.adempiere.model.engines.CostEngineFactory;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MClient;
import org.compiere.model.MCost;
import org.compiere.model.MCostElement;
import org.compiere.model.MDocType;
import org.compiere.model.MForecastLine;
import org.compiere.model.MLocator;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MProject;
import org.compiere.model.MResource;
import org.compiere.model.MStorageOnHand;
import org.compiere.model.MStorageReservation;
import org.compiere.model.MTable;
import org.compiere.model.MUOM;
import org.compiere.model.MUOMConversion;
import org.compiere.model.MWarehouse;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.POResultSet;
import org.compiere.model.Query;
import org.compiere.print.ReportEngine;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.wf.MWFNode;
import org.compiere.wf.MWFNodeNext;
import org.compiere.wf.MWorkflow;
import org.eevolution.model.I_PP_Order;
import org.eevolution.model.MPPProductBOM;
import org.eevolution.model.MPPProductBOMLine;
import org.eevolution.model.MPPProductPlanning;
import org.eevolution.model.X_PP_Order;
import org.libero.exceptions.BOMExpiredException;
import org.libero.exceptions.RoutingExpiredException; 
import org.libero.tables.I_PP_Order_BOMLine;
import org.libero.tables.I_PP_Order_Node; 
import org.libero.tables.X_PP_Order_Node_Asset;
import org.libero.tables.X_PP_Order_Node_Product;
import org.libero.model.MRequisition;
/**
 *  PP Order Model.
 *
 *  @author Victor Perez www.e-evolution.com     
 *  @author Teo Sarca, www.arhipac.ro
 */
public class MPPOrder extends X_PP_Order implements DocAction,DocOptions
{
	private static final long serialVersionUID = 1L;
	private static CLogger log = CLogger.getCLogger(MPPOrder.class);
	/**
	 * get Manufacturing Order based in Sales Order ID
	 * @param ctx Context
	 * @param C_OrderLine_ID Sales Order Line
	 * @param trxName Transaction 
	 * @return Manufacturing Order
	 */
	public static MPPOrder forC_OrderLine_ID(Properties ctx, int C_OrderLine_ID, String trxName)
	{
		MOrderLine line = new MOrderLine(ctx,  C_OrderLine_ID, trxName);	
		return new Query(ctx, MPPOrder.Table_Name, COLUMNNAME_C_OrderLine_ID+"=? AND "+ COLUMNNAME_M_Product_ID+"=?", trxName)
								.setParameters(new Object[]{C_OrderLine_ID,line.getM_Product_ID()})
								.firstOnly();
	}
	public static MPPOrder forM_ForecastLine_ID(Properties ctx, int C_OrderLine_ID, String trxName)
	{
		MForecastLine line = new MForecastLine(ctx,  C_OrderLine_ID, trxName);	
		return new Query(ctx, MPPOrder.Table_Name, "M_ForecastLine_ID=? AND "+ COLUMNNAME_M_Product_ID+"=?", trxName)
								.setParameters(new Object[]{C_OrderLine_ID,line.getM_Product_ID()})
								.firstOnly();
	}
	
	/**
	 * Set QtyBatchSize and QtyBatchs using Workflow and QtyEntered
	 * @param ctx context
	 * @param order MO
	 * @param override if false, will set QtyBatchSize even if is already set (QtyBatchSize!=0)
	 */
	public static void updateQtyBatchs(Properties ctx, I_PP_Order order, boolean override)
	{
		BigDecimal qtyBatchSize = order.getQtyBatchSize();
		if (qtyBatchSize.signum() == 0 || override)
		{
			int AD_Workflow_ID = order.getAD_Workflow_ID();
			// No workflow entered, or is just a new record:
			if (AD_Workflow_ID <= 0)
				return;
	
			MWorkflow wf = MWorkflow.get(ctx, AD_Workflow_ID);
			qtyBatchSize = wf.getQtyBatchSize().setScale(0, RoundingMode.UP); 
			order.setQtyBatchSize(qtyBatchSize);
		}
		
		BigDecimal QtyBatchs;
		if (qtyBatchSize.signum() == 0)
			QtyBatchs = Env.ONE;
		else
			QtyBatchs = order.getQtyOrdered().divide(qtyBatchSize , 0, BigDecimal.ROUND_UP); 
		System.out.println(QtyBatchs);
		order.setQtyBatchs(QtyBatchs);
	}
	
	/**
	 * get if Component is Available
	 * @param MPPOrdrt Manufacturing order
	 * @param ArrayList Issues
	 * @param minGuaranteeDate Guarantee Date
	 * @return true when the qty available is enough
	 */
	public static boolean isQtyAvailable(MPPOrder order ,ArrayList[][] issue, Timestamp minGuaranteeDate)
	{
		boolean isCompleteQtyDeliver = false;
		for(int i = 0; i < issue.length; i++ )
		{
			KeyNamePair key = (KeyNamePair) issue[i][0].get(0);
			boolean isSelected = key.getName().equals("Y"); 
			if (key == null || !isSelected)
			{
				continue;
			}
			
			String value = (String)issue[i][0].get(2);
			KeyNamePair productkey = (KeyNamePair) issue[i][0].get(3);			
			int M_Product_ID = productkey.getKey();
			BigDecimal qtyToDeliver = (BigDecimal)issue[i][0].get(4);	
			BigDecimal qtyScrapComponent = (BigDecimal) issue[i][0].get(5);	
		
			MProduct product = MProduct.get(order.getCtx(), M_Product_ID);
			if (product != null && product.isStocked()) 
			{
				int M_AttributeSetInstance_ID = 0;
				if (value == null && isSelected)
				{
					M_AttributeSetInstance_ID = (Integer)key.getKey();
				}
				else if (value != null && isSelected) 
				{
					int PP_Order_BOMLine_ID =  (Integer)key.getKey();
					if(PP_Order_BOMLine_ID > 0)
					{
						MPPOrderBOMLine orderBOMLine  = new MPPOrderBOMLine(order.getCtx(), PP_Order_BOMLine_ID, order.get_TrxName());
						//Validate if AttributeSet generate instance
						M_AttributeSetInstance_ID = orderBOMLine.getM_AttributeSetInstance_ID();
					}
				}
				
				MStorageOnHand[] storages = MPPOrder.getStorages(order.getCtx(),
						M_Product_ID,
						order.getM_Warehouse_ID(),
						 M_AttributeSetInstance_ID,
						minGuaranteeDate, order.get_TrxName());
				
				if (M_AttributeSetInstance_ID == 0)
				{					
					BigDecimal toIssue = qtyToDeliver.add(qtyScrapComponent);
					for (MStorageOnHand storage : storages) 
					{
						//	TODO Selection of ASI
						if (storage.getQtyOnHand().signum() == 0)
							continue;
						BigDecimal issueActual = toIssue.min(storage.getQtyOnHand());
						toIssue = toIssue.subtract(issueActual);
						if (toIssue.signum() <= 0)
							break;
					}
				}
				else
				{
					BigDecimal qtydelivered = qtyToDeliver;
					qtydelivered.setScale(4, BigDecimal.ROUND_HALF_UP);
					qtydelivered = Env.ZERO;
				}
		
				BigDecimal onHand = Env.ZERO;
				for (MStorageOnHand storage : storages)
				{
					onHand = onHand.add(storage.getQtyOnHand());
				}
		
				isCompleteQtyDeliver = onHand.compareTo(qtyToDeliver.add(qtyScrapComponent)) >= 0;
				if (!isCompleteQtyDeliver)
					break;
				
			}
		} // for each line
	
		return isCompleteQtyDeliver;
	}
	
	public static MStorageOnHand[] getStorages(
			Properties ctx,
			int M_Product_ID,
			int M_Warehouse_ID,
			int M_ASI_ID,
			Timestamp minGuaranteeDate, String trxName)
	{
		MProduct product = MProduct.get(ctx, M_Product_ID);
		if (product != null && product.isStocked())
		{
			//Validate if AttributeSet of product generated instance
			if(product.getM_AttributeSetInstance_ID() == 0)
			{	
				String MMPolicy = product.getMMPolicy();
				return MStorageOnHand.getWarehouse(ctx,
						M_Warehouse_ID,
						M_Product_ID,
						M_ASI_ID ,
						minGuaranteeDate,
						MClient.MMPOLICY_FiFo.equals(MMPolicy), // FiFo
						true, // positiveOnly
						0, // M_Locator_ID
						trxName
				);
			}
			else
			{
				//TODO: vpj-cd Create logic to get storage that matched with attribute set that not create instances
				String MMPolicy = product.getMMPolicy();
				return MStorageOnHand.getWarehouse(ctx,
						M_Warehouse_ID,
						M_Product_ID,
						0 ,
						minGuaranteeDate,
						MClient.MMPOLICY_FiFo.equals(MMPolicy), // FiFo
						true, // positiveOnly
						0, // M_Locator_ID
						trxName
				);
			}

		}
		else
		{
			return new MStorageOnHand[0];
		}
	}


	/**************************************************************************
	 *  Default Constructor
	 *  @param ctx context
	 *  @param  PP_Order_ID    order to load, (0 create new order)
	 */
	public MPPOrder(Properties ctx, int PP_Order_ID, String trxName)
	{
		super(ctx, PP_Order_ID, trxName);
		//  New
		if (PP_Order_ID == 0)
		{
			setDefault();
		}
	} //	PP_Order

	/**************************************************************************
	 *  Project Constructor
	 *  @param  project Project to create Order from
	 * 	@param	DocSubTypeSO if SO DocType Target (default DocSubTypeSO_OnCredit)
	 */
	public MPPOrder(MProject project, int PP_Product_BOM_ID, int AD_Workflow_ID)
	{
		this(project.getCtx(), 0, project.get_TrxName());
		setAD_Client_ID(project.getAD_Client_ID());
		setAD_Org_ID(project.getAD_Org_ID());
		setC_Campaign_ID(project.getC_Campaign_ID());
		setC_Project_ID(project.getC_Project_ID());
		setDescription(project.getName());
		setLine(10);
		setPriorityRule(MPPOrder.PRIORITYRULE_Medium);
		if (project.getDateContract() == null)
			throw new IllegalStateException("Date Contract is mandatory for Manufacturing Order.");
		if (project.getDateFinish() == null)
			throw new IllegalStateException("Date Finish is mandatory for Manufacturing Order.");
		
		Timestamp ts = project.getDateContract();
		Timestamp df= project.getDateContract();
		
		if (ts != null) setDateOrdered(ts);
		if (ts != null) this.setDateStartSchedule(ts);
		ts = project.getDateFinish();
		if (df != null) setDatePromised(df);
		setM_Warehouse_ID(project.getM_Warehouse_ID());
		setPP_Product_BOM_ID(PP_Product_BOM_ID);
		setAD_Workflow_ID(AD_Workflow_ID);
		setQtyEntered(Env.ONE);
		setQtyOrdered(Env.ONE);
		MPPProductBOM bom = new MPPProductBOM(project.getCtx(), PP_Product_BOM_ID, project.get_TrxName());
		MProduct product = MProduct.get(project.getCtx(), bom.getM_Product_ID());
		setC_UOM_ID(product.getC_UOM_ID());
		
		setM_Product_ID(bom.getM_Product_ID());
		
		String where = MResource.COLUMNNAME_IsManufacturingResource   +" = 'Y' AND "+ 
					   MResource.COLUMNNAME_ManufacturingResourceType +" = '" + MResource.MANUFACTURINGRESOURCETYPE_Plant + "' AND " +
					   MResource.COLUMNNAME_M_Warehouse_ID + " = " + project.getM_Warehouse_ID();
		MResource resoruce = (MResource) MTable.get(project.getCtx(), MResource.Table_ID).getPO( where , project.get_TrxName());
		if (resoruce == null)
			throw new IllegalStateException("Resource is mandatory.");
		setS_Resource_ID(resoruce.getS_Resource_ID());
	} //	MOrder

	/**
	 *  Load Constructor
	 *  @param ctx context
	 *  @param rs result set record
	 */
	public MPPOrder(Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	} //	MOrder

	/**
	 * @return Open Qty (Ordered - Delivered - Scrap)
	 */
	public BigDecimal getQtyOpen()
	{
		return getQtyOrdered().subtract(getQtyDelivered()).subtract(getQtyScrap());
	}

	/**
	 * Get BOM Lines of PP Order
	 * @param requery
	 * @return Order BOM Lines
	 */
	public MPPOrderBOMLine[] getLines(boolean requery)
	{
		if (m_lines != null && !requery)
		{
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}
		String whereClause = MPPOrderBOMLine.COLUMNNAME_PP_Order_ID+"=?";
		List<MPPOrderBOMLine> list = new Query(getCtx(), MPPOrderBOMLine.Table_Name, whereClause, get_TrxName())
										.setParameters(new Object[]{getPP_Order_ID()})
										.setOrderBy(MPPOrderBOMLine.COLUMNNAME_Line)
										.list();
		m_lines = list.toArray(new MPPOrderBOMLine[list.size()]);
		return m_lines;
	}
	private MPPOrderBOMLine[] m_lines = null;

	/**
	 * Get Order BOM Lines
	 * @return Order BOM Lines
	 */
	public MPPOrderBOMLine[] getLines()
	{
		return getLines(true);
	}
	
	public void setC_DocTypeTarget_ID(String docBaseType)
	{
		if(getC_DocTypeTarget_ID() > 0)
			return;
					
		MDocType[] doc = MDocType.getOfDocBaseType(getCtx(), docBaseType);	
		if(doc == null)
		{
			throw new DocTypeNotFoundException(docBaseType, "");
		}
		else
		{	
			setC_DocTypeTarget_ID(doc[0].get_ID());
		}
	}

	@Override
	public void setProcessed(boolean processed)
	{
		super.setProcessed(processed);
		
		// Update DB:
		if (get_ID() <= 0)
			return;
		final String sql = "UPDATE PP_Order SET Processed=? WHERE PP_Order_ID=?";
		DB.executeUpdateEx(sql, new Object[]{processed, get_ID()}, get_TrxName());
	} //	setProcessed

	@Override
	protected boolean beforeSave(boolean newRecord)
	{
		if (getAD_Client_ID() == 0)
		{
			m_processMsg = "AD_Client_ID = 0";
			return false;
		}
		if (getAD_Org_ID() == 0)
		{
			int context_AD_Org_ID = Env.getAD_Org_ID(getCtx());
			if (context_AD_Org_ID == 0) {
				m_processMsg = "AD_Org_ID = 0";
				return false;
			}
			setAD_Org_ID(context_AD_Org_ID);
			log.warning("beforeSave - Changed Org to Context=" + context_AD_Org_ID);
		}
		if (getM_Warehouse_ID() == 0)
		{
			int ii = Env.getContextAsInt(getCtx(), "#M_Warehouse_ID");
			if (ii != 0)
			{
				setM_Warehouse_ID(ii);
			}
		}
		// If UOM not filled, get it from Product
		if (getC_UOM_ID() <= 0 && getM_Product_ID() > 0)
		{
			setC_UOM_ID(getM_Product().getC_UOM_ID());
		}
		// If DateFinishSchedule is not filled, use DatePromised
		if (getDateFinishSchedule() == null)
		{
			setDateFinishSchedule(getDatePromised());
		}
		
		// Order Stock
//		if( is_ValueChanged(MPPOrder.COLUMNNAME_QtyDelivered)
//				|| is_ValueChanged(MPPOrder.COLUMNNAME_QtyOrdered))
//		{	
//			orderStock();
//		}

		
		updateQtyBatchs(getCtx(), this, false);

		return true;
	}

	@Override
	protected boolean afterSave(boolean newRecord, boolean success)
	{
		if (!success)
		{
			return false;
		}
		
		if(MPPOrder.DOCACTION_Close.equals(getDocAction())
				|| MPPOrder.DOCACTION_Void.equals(getDocAction()))
		{
			return true;
		}
		
		if( is_ValueChanged(MPPOrder.COLUMNNAME_QtyEntered) && !isDelivered())
		{	
				deleteWorkflowAndBOM();
				explosion();
		}
		if( is_ValueChanged(MPPOrder.COLUMNNAME_QtyEntered) && isDelivered())
		{
			throw new AdempiereException("Cannot Change Quantity, Only for Draft or In-Progess Status"); // 
		}
		
		if (!newRecord)
		{
			return success;
		}
		
		explosion();
		return true;
	} //	beforeSave

	@Override
	protected boolean beforeDelete()
	{
		// OrderBOMLine
		if (getDocStatus().equals(DOCSTATUS_Drafted) || getDocStatus().equals(DOCSTATUS_InProgress))
		{
			String whereClause = "PP_Order_ID=? AND AD_Client_ID=?";
			Object[] params = new Object[]{get_ID(), getAD_Client_ID()};
			//
			deletePO(MPPOrderCost.Table_Name, whereClause, params);
			deleteWorkflowAndBOM();
		}
		
		// Un-Order Stock
		setQtyOrdered(Env.ZERO);
		orderStock();

		return true;
	} //	beforeDelete
	
	private void deleteWorkflowAndBOM()
	{
		// New record, nothing to do
		if (get_ID() <= 0)
		{
			return;
		}
		//
		String whereClause = "PP_Order_ID=? AND AD_Client_ID=?";
		Object[] params = new Object[]{get_ID(), getAD_Client_ID()};
		// Reset workflow start node
		DB.executeUpdateEx("UPDATE PP_Order_Workflow SET PP_Order_Node_ID=NULL WHERE "+whereClause, params, get_TrxName());
		// Delete workflow:
		deletePO(X_PP_Order_Node_Asset.Table_Name, whereClause, params);
		deletePO(X_PP_Order_Node_Product.Table_Name, whereClause, params);
		deletePO(MPPOrderNodeNext.Table_Name, whereClause, params);
		deletePO(MPPOrderNode.Table_Name, whereClause, params);
		deletePO(MPPOrderWorkflow.Table_Name, whereClause, params);
		// Delete BOM:
		deletePO(MPPOrderBOMLine.Table_Name, whereClause, params);
		deletePO(MPPOrderBOM.Table_Name, whereClause, params);
		
	}

	public boolean processIt(String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine(this, getDocStatus());
		return engine.processIt(processAction, getDocAction());
	} //	processIt

	/**	Process Message 			*/
	private String m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean m_justPrepared = false;

	public boolean unlockIt()
	{
		log.info(toString());
		setProcessing(false);
		return true;
	} //	unlockIt

	public boolean invalidateIt()
	{
		log.info(toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	} //	invalidateIt

	public String prepareIt()
	{
		log.info(toString());
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	Lines
		MPPOrderBOMLine[] lines = getLines(true);
		if (lines.length == 0)
		{
			m_processMsg = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}

		//	Cannot change Std to anything else if different warehouses
		if (getC_DocType_ID() != 0)
		{
			for (int i = 0; i < lines.length; i++)
			{
				if (lines[i].getM_Warehouse_ID() != getM_Warehouse_ID())
				{
					log.warning("different Warehouse " + lines[i]);
					m_processMsg = "@CannotChangeDocType@";
					return DocAction.STATUS_Invalid;
				}
			}
		}

		//	New or in Progress/Invalid
		if (DOCSTATUS_Drafted.equals(getDocStatus())
				|| DOCSTATUS_InProgress.equals(getDocStatus())
				|| DOCSTATUS_Invalid.equals(getDocStatus())
				|| getC_DocType_ID() == 0)
		{
			setC_DocType_ID(getC_DocTypeTarget_ID());
		}

		String docBaseType = MDocType.get(getCtx(), getC_DocType_ID()).getDocBaseType();
		if (MDocType.DOCBASETYPE_QualityOrder.equals(docBaseType))
		{
			; // nothing
		}
		// ManufacturingOrder, MaintenanceOrder
		else
		{
			reserveStock(lines);
			//orderStock();
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		resetCostsLLForLLC0();
		int maxLowLevel = MPPMRP.getMaxLowLevel(getCtx(), get_TrxName());
		// Cost Roll-up for all levels
		
		if(getM_AttributeSetInstance_ID()>0) {
			for (int lowLevel = maxLowLevel; lowLevel >= 0; lowLevel--)
			{
				for (MProduct product : getProducts(lowLevel))
				{
					
					MPPProductBOM bom = MPPProductBOM.get(getCtx(), getPP_Product_BOM_ID());
				
					//rollupfifo(product, bom);
				} // for each Products 
			}
		}else {
			for (int lowLevel = maxLowLevel; lowLevel >= 0; lowLevel--)
			{
				for (MProduct product : getProducts(lowLevel))
				{
					
					MPPProductBOM bom = MPPProductBOM.get(getCtx(), getPP_Product_BOM_ID());
				
					rollup(product, bom);
				} // for each Products 
			}
		}
	
		for (MProduct product : getProductsResource())
		{ 
		

			MWorkflow workflow = new MWorkflow(getCtx(), getAD_Workflow_ID(), get_TrxName());
			rollupupdate(product, workflow);
			
			
		}

		m_justPrepared = true;
		
		return DocAction.STATUS_InProgress;
	} //	prepareIt

	private void orderStock()
	{
		MProduct product = getM_Product();
		if (!product.isStocked())
		{
			return;
		}
		BigDecimal target = getQtyOrdered();
		BigDecimal difference = target.subtract(getQtyReserved()).subtract(getQtyDelivered());
		if (difference.signum() == 0)
		{
			return ;
		}
		BigDecimal ordered = difference;
		
		int M_Locator_ID = getM_Locator_ID(ordered,product);
		// Necessary to clear order quantities when called from closeIt - 4Layers
		if (DOCACTION_Close.equals(getDocAction()))
		{
			if (!MStorageOnHand.add(getCtx(), getM_Warehouse_ID(), M_Locator_ID,
					getM_Product_ID(), getM_AttributeSetInstance_ID(), ordered, get_TrxName()))
			{
				throw new AdempiereException();
			}
		}
		else
		{
			//	Update Storage
			if (!MStorageOnHand.add(getCtx(), getM_Warehouse_ID(), M_Locator_ID,
					getM_Product_ID(), getM_AttributeSetInstance_ID(), ordered, get_TrxName()))
			{
				throw new AdempiereException();
			}
		}

		setQtyReserved(getQtyReserved().add(difference));
	}

	/**
	 * Reserve Inventory.
	 * @param lines order lines (ordered by M_Product_ID for deadlock prevention)
	 * @return true if (un) reserved
	 */
	private void reserveStock(MPPOrderBOMLine[] lines)
	{
		//	Always check and (un) Reserve Inventory		
		for (MPPOrderBOMLine line : lines)
		{
			line.reserveStock();
			line.saveEx(get_TrxName());
		}
	} //	reserveStock
	
	

	public boolean approveIt()
	{
		log.info("approveIt - " + toString());
		MDocType doc = MDocType.get(getCtx(), getC_DocType_ID());
		if (doc.getDocBaseType().equals(MDocType.DOCBASETYPE_QualityOrder))
		{
			String whereClause = COLUMNNAME_PP_Product_BOM_ID+"=? AND "+COLUMNNAME_AD_Workflow_ID+"=?";
			MQMSpecification qms = new Query(getCtx(), MQMSpecification.Table_Name, whereClause, get_TrxName())
										.setParameters(new Object[]{getPP_Product_BOM_ID(), getAD_Workflow_ID()})
										.firstOnly();
			return qms != null ? qms.isValid(getM_AttributeSetInstance_ID()) : true;
		}
		else
		{
			setIsApproved(true);
		}

		return true;
	} //	approveIt
	
	

	public boolean rejectIt()
	{
		log.info("rejectIt - " + toString());
		setIsApproved(false);
		return true;
	} //	rejectIt

	public String completeIt()
	{
		//	Just prepare
		if (DOCACTION_Prepare.equals(getDocAction()))
		{
			setProcessed(false);
			return DocAction.STATUS_InProgress;
		}

		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}
		
		//	Implicit Approval
		if (!isApproved())
		{
			approveIt();
		}
		
		createStandardCosts();
		
		createStandardFifo();
				
		//Create the Activity Control
		autoReportActivities();

		//setProcessed(true);
		setDocAction(DOCACTION_Close);

		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
	
		return DocAction.STATUS_Completed;
	} //	completeIt

	/**
	 * Check if the Quantity from all BOM Lines is available (QtyOnHand >= QtyRequired)
	 * @return true if entire Qty is available for this Order 
	 */
	public boolean isAvailable()
	{
		String whereClause = "QtyOnHand >= QtyRequired AND PP_Order_ID=?";
		boolean available = new Query(getCtx(), "RV_PP_Order_Storage", whereClause, get_TrxName())
										.setParameters(new Object[]{get_ID()})
										.match();
		return available;
	}

	public boolean voidIt()
	{
		log.info(toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;
		
		if(isDelivered())
		{
			throw new AdempiereException("Cannot void this document because exist transactions"); //  
		}
		
		for(MPPOrderBOMLine line : getLines())
		{
			BigDecimal old = line.getQtyRequired();
			if (old.signum() != 0)
			{
				line.addDescription(Msg.parseTranslation(getCtx(), "@Voided@ @QtyRequired@ : (" + old + ")"));
				line.setQtyRequired(Env.ZERO);
				line.saveEx(get_TrxName());
			}
		}
		
		getMPPOrderWorkflow().voidActivities();
		
		BigDecimal old = getQtyOrdered();
		if (old.signum() != 0)
		{	
			addDescription(Msg.parseTranslation(getCtx(),"@Voided@ @QtyOrdered@ : (" + old + ")"));
			setQtyOrdered(Env.ZERO);
			setQtyEntered(Env.ZERO);
			saveEx(get_TrxName());
		}	
		
		orderStock(); // Clear Ordered Quantities
		reserveStock(getLines()); //	Clear Reservations
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;
		
		//setProcessed(true);
		setDocAction(DOCACTION_None);
		return true;
	} //	voidIt

	public boolean closeIt()
	{
		log.info(toString());
		// Before Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_CLOSE);
		if (m_processMsg != null)
			return false;
				
		if(MPPOrder.DOCSTATUS_Closed.equals(getDocStatus()))
			return true;
		
		if(!MPPOrder.DOCSTATUS_Completed.equals(getDocStatus()))
		{
			String DocStatus = completeIt();
			setDocStatus(DocStatus);
			setDocAction(MPPOrder.ACTION_None);
		}
		
		if(!isDelivered())
		{
			throw new AdempiereException("Cannot close this document because do not exist transactions"); // 
		}
			
		createVariances();
		
		for(MPPOrderBOMLine line : getLines())
		{
			BigDecimal old = line.getQtyRequired();
			if (old.compareTo(line.getQtyDelivered()) != 0)
			{	
				line.setQtyRequired(line.getQtyDelivered());
				line.addDescription(Msg.parseTranslation(getCtx(), "@closed@ @QtyRequired@ (" + old + ")"));
				line.saveEx(get_TrxName());
			}	
		}
		
		//Close all the activity do not reported
		MPPOrderWorkflow m_order_wf = getMPPOrderWorkflow(); 
		m_order_wf.closeActivities(m_order_wf.getLastNode(getAD_Client_ID()), getUpdated(),false);
		
		BigDecimal old = getQtyOrdered();
		if (old.signum() != 0)
		{	
			addDescription(Msg.parseTranslation(getCtx(),"@closed@ @QtyOrdered@ : (" + old + ")"));
			setQtyOrdered(getQtyDelivered());
			saveEx(get_TrxName());
		}	
	
		orderStock(); // Clear Ordered Quantities
		reserveStock(getLines()); //	Clear Reservations
		
		setDocStatus(DOCSTATUS_Closed);
		//setProcessed(true);
		setDocAction(DOCACTION_None);
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_CLOSE);
		if (m_processMsg != null)
			return false;
		return true;
	} //	closeIt

	public boolean reverseCorrectIt()
	{
		log.info("reverseCorrectIt - " + toString());
		return voidIt();
	} //	reverseCorrectionIt

	public boolean reverseAccrualIt()
	{
		log.info("reverseAccrualIt - " + toString());
		return false;
	} //	reverseAccrualIt

	public boolean reActivateIt()
	{
		// After reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		MPPOrderCost ppordercost = new MPPOrderCost(getCtx(), getPP_Order_ID(), get_TrxName());
		if(ppordercost != null) {
			DB.executeUpdate("delete from PP_Order_Cost where pp_order_id = "+getPP_Order_ID(), get_TrxName());
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REACTIVATE);
		if (m_processMsg != null)
			return false;
		if(isDelivered())
			throw new AdempiereException("Cannot re activate this document because exist transactions"); // 
		
		setDocAction(DOCACTION_Complete);
		setProcessed(false);
		return true;
	} //	reActivateIt

	public int getDoc_User_ID()
	{
		return getPlanner_ID();
	} //	getDoc_User_ID

	public BigDecimal getApprovalAmt()
	{
		return Env.ZERO;
	} //	getApprovalAmt

	public int getC_Currency_ID()
	{
		return 0;
	}

	public String getProcessMsg()
	{
		return m_processMsg;
	}

	public String getSummary()
	{
		return "" + getDocumentNo() + "/" + getDatePromised();
	}

	public File createPDF()
	{
		try {
			File temp = File.createTempFile(get_TableName() + get_ID() + "_", ".pdf");
			return createPDF(temp);
		}
		catch (Exception e) {
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	} //	getPDF

	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF(File file)
	{
		ReportEngine re = ReportEngine.get(getCtx(), ReportEngine.MANUFACTURING_ORDER, getPP_Order_ID());
		if (re == null)
			return null;
		return re.getPDF(file);
	} //	createPDF

	/**
	 * 	Get Document Info
	 *	@return document info (untranslated)
	 */
	public String getDocumentInfo()
	{
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		return dt.getName() + " " + getDocumentNo();
	} //	getDocumentInfo
	
	private void deletePO(String tableName, String whereClause, Object[] params)
	{
		POResultSet<PO> rs = new Query(getCtx(), tableName, whereClause, get_TrxName())
									.setParameters(params)
									.scroll();
		try
		{
			while(rs.hasNext())
			{
				rs.next().deleteEx(true);
			}
		}
		finally
		{
			rs.close();
		}
	}
	
	/**
	 * 	Set Qty Entered/Ordered.
	 * 	Use this Method if the Line UOM is the Product UOM 
	 *	@param Qty QtyOrdered/Entered
	 */
	public void setQty (BigDecimal Qty)
	{
		super.setQtyEntered (Qty);
		super.setQtyOrdered (getQtyEntered());
	}	//	setQty
	
	/**
	 * 	Set Qty Entered - enforce entered UOM 
	 *	@param QtyEntered
	 */
	public void setQtyEntered (BigDecimal QtyEntered)
	{
		if (QtyEntered != null && getC_UOM_ID() != 0)
		{
			int precision = MUOM.getPrecision(getCtx(), getC_UOM_ID());
			QtyEntered = QtyEntered.setScale(precision, BigDecimal.ROUND_HALF_UP);
		}
		super.setQtyEntered (QtyEntered);
	}	//	setQtyEntered

	/**
	 * 	Set Qty Ordered - enforce Product UOM 
	 *	@param QtyOrdered
	 */
	public void setQtyOrdered (BigDecimal QtyOrdered)
	{
		if (QtyOrdered != null)
		{
			int precision = getM_Product().getUOMPrecision();
			QtyOrdered = QtyOrdered.setScale(precision, BigDecimal.ROUND_HALF_UP);
		}
		super.setQtyOrdered(QtyOrdered);
	}	//	setQtyOrdered
	
//	@Override
	public MProduct getM_Product()
	{
		return MProduct.get (getCtx(), getM_Product_ID());
	}	//	getProduct
	
	public MPPOrderBOM getMPPOrderBOM()
	{
		final String whereClause = MPPOrderBOM.COLUMNNAME_PP_Order_ID+"=?";
		return new Query(getCtx(), MPPOrderBOM.Table_Name, whereClause, get_TrxName())
				.setParameters(new Object[]{getPP_Order_ID()})
				.firstOnly();
	}
	
	private MPPOrderWorkflow m_PP_Order_Workflow = null;
	public static int count_MR;
	public MPPOrderWorkflow getMPPOrderWorkflow()
	{
		if (m_PP_Order_Workflow != null)
		{
			return m_PP_Order_Workflow;
		}
		final String whereClause = MPPOrderWorkflow.COLUMNNAME_PP_Order_ID+"=?";
		m_PP_Order_Workflow = new Query(getCtx(), MPPOrderWorkflow.Table_Name, whereClause, get_TrxName())
				.setParameters(new Object[]{getPP_Order_ID()})
				.firstOnly();
		return m_PP_Order_Workflow;
	}
	
	/**
	 * Create PP_Order_BOM from PP_Product_BOM.
	 * Create PP_Order_Workflow from AD_Workflow.
	 */
	private void explosion()
	{
		// Create BOM Head
		final MPPProductBOM PP_Product_BOM = MPPProductBOM.get(getCtx(), getPP_Product_BOM_ID());
		//iterate Product BOM components as more Parent tab records
		
		// Product from Order should be same as product from BOM - teo_sarca [ 2817870 ] 
		if (getM_Product_ID() != PP_Product_BOM.getM_Product_ID())
		{
			throw new AdempiereException("@NotMatch@ @PP_Product_BOM_ID@ , @M_Product_ID@");
		}
		// Product BOM Configuration should be verified - teo_sarca [ 2817870 ]
		final MProduct product = MProduct.get(getCtx(), PP_Product_BOM.getM_Product_ID());
		if (!product.isVerified())
		{
			throw new AdempiereException("Product BOM Configuration not verified. Please verify the product first - "+product.getValue()); // 
		}
		if (PP_Product_BOM.isValidFromTo(getDateStartSchedule()))
		{
			MPPOrderBOM PP_Order_BOM = new MPPOrderBOM(PP_Product_BOM, getPP_Order_ID(), get_TrxName());
			PP_Order_BOM.setAD_Org_ID(getAD_Org_ID());
			PP_Order_BOM.saveEx(get_TrxName());

			expandBOM(PP_Product_BOM, PP_Order_BOM, getQtyOrdered(),getQtyBatchs(),new BigDecimal(get_Value("QtyPlan").toString()));
		} // end if From / To parent
		else
		{
			throw new BOMExpiredException(PP_Product_BOM, getDateStartSchedule());
		}

		// Create Workflow (Routing & Process)
		final MWorkflow AD_Workflow = MWorkflow.get(getCtx(), getAD_Workflow_ID());
		// Workflow should be validated first - teo_sarca [ 2817870 ]
		if (!AD_Workflow.isValid())
		{
			throw new AdempiereException("Routing is not valid. Please validate it first - "+AD_Workflow.getValue()); // 
		}
		if (AD_Workflow.isValidFromTo(getDateStartSchedule()))
		{
			MPPOrderWorkflow PP_Order_Workflow = new MPPOrderWorkflow(AD_Workflow, get_ID(), get_TrxName());
			PP_Order_Workflow.setAD_Org_ID(getAD_Org_ID());
			PP_Order_Workflow.saveEx(get_TrxName());
			for (MWFNode AD_WF_Node : AD_Workflow.getNodes(false, getAD_Client_ID()))
			{
				if (AD_WF_Node.isValidFromTo(getDateStartSchedule()))
				{
					MPPOrderNode PP_Order_Node = new MPPOrderNode(AD_WF_Node, PP_Order_Workflow,
															getQtyOrdered(),
															get_TrxName());
					PP_Order_Node.setAD_Org_ID(getAD_Org_ID());
					PP_Order_Node.set_CustomColumn("jmltenagakerja", Integer.parseInt(AD_WF_Node.get_Value("jmltenagakerja").toString()));
					PP_Order_Node.saveEx(get_TrxName());
					
					for (MWFNodeNext AD_WF_NodeNext : AD_WF_Node.getTransitions(getAD_Client_ID()))
					{
						MPPOrderNodeNext nodenext = new MPPOrderNodeNext(AD_WF_NodeNext, PP_Order_Node);
						nodenext.setAD_Org_ID(getAD_Org_ID());
						nodenext.saveEx(get_TrxName());
					}// for NodeNext
					
					for (MPPWFNodeProduct wfnp : MPPWFNodeProduct.forAD_WF_Node_ID(getCtx(), AD_WF_Node.get_ID()))
					{
						MPPOrderNodeProduct nodeOrderProduct = new MPPOrderNodeProduct(wfnp, PP_Order_Node);
						nodeOrderProduct.setAD_Org_ID(getAD_Org_ID());
						nodeOrderProduct.saveEx(get_TrxName());
					}
					
					for (MPPWFNodeAsset wfna : MPPWFNodeAsset.forAD_WF_Node_ID(getCtx(), AD_WF_Node.get_ID()))
					{
						MPPOrderNodeAsset nodeorderasset = new MPPOrderNodeAsset(wfna, PP_Order_Node);
						nodeorderasset.setAD_Org_ID(getAD_Org_ID());
						nodeorderasset.saveEx(get_TrxName());
					}					
				}// for node 

			}
			// Update transitions nexts and set first node
			PP_Order_Workflow.getNodes(true); // requery
			for (MPPOrderNode orderNode : PP_Order_Workflow.getNodes(false, getAD_Client_ID()))
			{
				// set workflow start node
				if (PP_Order_Workflow.getAD_WF_Node_ID() == orderNode.getAD_WF_Node_ID())
				{
					PP_Order_Workflow.setPP_Order_Node_ID(orderNode.getPP_Order_Node_ID());
				}
				// set node next
				for (MPPOrderNodeNext next : orderNode.getTransitions(getAD_Client_ID()))
				{
					next.setPP_Order_Next_ID();
					next.saveEx(get_TrxName());
				}
			}
			PP_Order_Workflow.saveEx(get_TrxName());
		} // workflow valid from/to
		else
		{
			throw new RoutingExpiredException(AD_Workflow, getDateStartSchedule());
		}
	}
	/**
	 * Expand BOM and make requisition if any component is purchased.
	 * @param PP_Product_BOM
	 * @param PP_Order_BOM
	 */
	private void expandBOM(final MPPProductBOM PP_Product_BOM, MPPOrderBOM PP_Order_BOM, BigDecimal parentQty, BigDecimal parentqtybatch, BigDecimal qtyparentorder) {
		MProduct productparent = new MProduct(getCtx(), PP_Product_BOM.getM_Product_ID(), get_TrxName());
		for (MPPProductBOMLine PP_Product_BOMline : PP_Product_BOM.getLines(true))
		{				
			MProduct product = (MProduct) PP_Product_BOMline.getM_Product();
			if (PP_Product_BOMline.isValidFromTo(getDateStartSchedule()))
			{
				//creating new children for PPOrderBOM
				MPPOrderBOMLine obl = new MPPOrderBOMLine(PP_Product_BOMline,
						getPP_Order_ID(), PP_Order_BOM.get_ID(),
						getM_Warehouse_ID(),
						get_TrxName());
				obl.setAD_Org_ID(getAD_Org_ID());
				obl.setM_Warehouse_ID(getM_Warehouse_ID());
				obl.setM_Locator_ID(getM_Locator_ID());
				obl.setQtyPlusScrap(parentQty,new BigDecimal(PP_Product_BOMline.get_Value("QtyBatchSize").toString()),parentqtybatch,productparent,qtyparentorder);
				obl.saveEx(get_TrxName()); 
		 /*	DO NOT DO. LOW LEVEL requires each PP_Order to be handled separaete LEVEL to expand.
				//iterate BOM children -- relegate to multiple PPOrder parent product tabs
				MPPProductBOM bom = MPPProductBOM.getDefault(product, get_TrxName());
				if (bom!=null) {
					MPPOrderBOM moBOM = new MPPOrderBOM(bom, getPP_Order_ID(), get_TrxName());
					moBOM.setAD_Org_ID(getAD_Org_ID());
					moBOM.saveEx(get_TrxName());
					expandBOM(bom, moBOM, obl.getQtyRequired());
				}			*/	
				//createRequisitionIFpurchased(product, obl.getQtyRequired());// PShepetko - Issue #16
				
			} // end if valid From / To    
			else
			{
				log.fine("BOM Line skiped - "+PP_Product_BOMline);
			}
		} // end Create Order BOM
	}
	/**
	 * If product is purchased
	 * Passed to Create Requisition Model class
	 *  there QtyReserved.Availability is checked.
	 * @param product
	 */
	private void createRequisitionIFpurchased(MProduct product, BigDecimal qtyRequired) {
 		if (product.isPurchased()){
			log.finer("CLASS PPOrder.explosion product is purchased, check stock/creating requisition");
			
			MPPMRP mrp = new Query(Env.getCtx(),MPPMRP.Table_Name,MPPMRP.COLUMNNAME_PP_Order_ID+"=?",get_TrxName())
			.setParameters(getPP_Order_ID())
			.first();

			MRequisition req = new MRequisition(Env.getCtx(),0,get_TrxName());		
			//TODO getBPartner should be vendor not Order's
			req.create(mrp.get_ID(),qtyRequired, product.getM_Product_ID(), getC_OrderLine().getC_BPartner_ID(), getAD_Org_ID(), getPlanner_ID(), getDatePromised(), 
					getDescription()+"!!!", getM_Warehouse_ID(), getC_DocType_ID());
			//update PP_Order
			
			//counter to send back to CalculateMaterialPlan count_MR
			count_MR ++;
		}
	}
	
	/**
	 * Create Receipt Finish Good
	 * @param order
	 * @param movementDate
	 * @param qtyDelivered
	 * @param qtyToDeliver
	 * @param qtyScrap
	 * @param qtyReject
	 * @param M_Locator_ID
	 * @param M_AttributeSetInstance_ID
	 * @param IsCloseDocument
	 * @param trxName
	 */
	static public void createReceipt(MPPOrder order,
			Timestamp movementDate,
			BigDecimal qtyDelivered,
			BigDecimal qtyToDeliver, 
			BigDecimal qtyScrap,
			BigDecimal qtyReject,
			int M_Locator_ID,
			int M_AttributeSetInstance_ID)
	{
		
		if (qtyToDeliver.signum() != 0 || qtyScrap.signum() != 0 || qtyReject.signum() != 0)
		{
			MPPCostCollector.createCollector(
					order,															//MPPOrder
					order.getM_Product_ID(),										//M_Product_ID
					M_Locator_ID,													//M_Locator_ID
					M_AttributeSetInstance_ID,										//M_AttributeSetInstance_ID
					order.getS_Resource_ID(),										//S_Resource_ID
					0, 																//PP_Order_BOMLine_ID
					0,																//PP_Order_Node_ID
					MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), 	//C_DocType_ID
					MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt,				//Production "+"
					movementDate,													//MovementDate
					qtyToDeliver, qtyScrap, qtyReject,								//qty,scrap,reject
					0,Env.ZERO,Env.ZERO,Env.ZERO,Env.ZERO);															//durationSetup,duration
		}

		order.setDateDelivered(movementDate);
		if (order.getDateStart() == null)
		{
			order.setDateStart(movementDate);
		}

		BigDecimal DQ = qtyDelivered;
		BigDecimal SQ = qtyScrap;
		BigDecimal OQ = qtyToDeliver;
		if (DQ.add(SQ).compareTo(OQ) >= 0)
		{
			order.setDateFinish(movementDate);
		}
		order.saveEx(order.get_TrxName());
	}
	
	/**
	 * Create Issue
	 * @param PP_OrderBOMLine_ID
	 * @param movementdate
	 * @param qty
	 * @param qtyScrap
	 * @param qtyReject
	 * @param storages
	 * @param force Issue
	 */
	public static void createIssue(MPPOrder order, int PP_OrderBOMLine_ID,
			Timestamp movementdate,
			BigDecimal qty, BigDecimal qtyScrap, BigDecimal qtyReject,
			MStorageOnHand[] storages, boolean forceIssue)
	{
		if (qty.signum() == 0)
			return;
		MPPOrderBOMLine PP_orderbomLine = new MPPOrderBOMLine(order.getCtx(), PP_OrderBOMLine_ID, order.get_TrxName());
		BigDecimal toIssue = qty.add(qtyScrap);
		for (MStorageOnHand storage : storages)
		{
			//	TODO Selection of AS

			if (storage.getQtyOnHand().signum() == 0)
				continue;

			BigDecimal qtyIssue = toIssue.min(storage.getQtyOnHand());
			//log.fine("ToIssue: " + issue);			
			//create record for negative and positive transaction
			if (qtyIssue.signum() != 0 || qtyScrap.signum() != 0 || qtyReject.signum() != 0)
			{
				String CostCollectorType = MPPCostCollector.COSTCOLLECTORTYPE_ComponentIssue;
				// Method Variance
				if (PP_orderbomLine.getQtyBatch().signum() == 0
						&& PP_orderbomLine.getQtyBOM().signum() == 0)
				{
					CostCollectorType = MPPCostCollector.COSTCOLLECTORTYPE_MethodChangeVariance;
				}
				else if (PP_orderbomLine.isComponentType(MPPOrderBOMLine.COMPONENTTYPE_Co_Product))
				{
					CostCollectorType = MPPCostCollector.COSTCOLLECTORTYPE_MixVariance;
				}
				//
				MPPCostCollector.createCollector (
						order, 															//MPPOrder
						PP_orderbomLine.getM_Product_ID(),								//M_Product_ID
						storage.getM_Locator_ID(),										//M_Locator_ID
						storage.getM_AttributeSetInstance_ID(),							//M_AttributeSetInstance_ID
						order.getS_Resource_ID(),										//S_Resource_ID
						PP_OrderBOMLine_ID,												//PP_Order_BOMLine_ID
						0,																//PP_Order_Node_ID
						MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), 	//C_DocType_ID,
						CostCollectorType, 												//Production "-"
						movementdate,													//MovementDate
						qtyIssue, qtyScrap, qtyReject,									//qty,scrap,reject
						0,Env.ZERO,Env.ZERO,Env.ZERO,Env.ZERO															//durationSetup,duration
				);

			}			
			toIssue = toIssue.subtract(qtyIssue);
			if (toIssue.signum() == 0)
				break;  
		}
		if(forceIssue && toIssue.signum() != 0)
		{
			MPPCostCollector.createCollector (
					order, 																	//MPPOrder
					PP_orderbomLine.getM_Product_ID(),										//M_Product_ID
					PP_orderbomLine.getM_Locator_ID(),										//M_Locator_ID
					PP_orderbomLine.getM_AttributeSetInstance_ID(),							//M_AttributeSetInstance_ID
					order.getS_Resource_ID(),												//S_Resource_ID
					PP_OrderBOMLine_ID,														//PP_Order_BOMLine_ID
					0,																		//PP_Order_Node_ID
					MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), 	//C_DocType_ID,
					MPPCostCollector.COSTCOLLECTORTYPE_ComponentIssue, 						//Production "-"
					movementdate,															//MovementDate
					toIssue, Env.ZERO, Env.ZERO,											//qty,scrap,reject
					0,Env.ZERO,Env.ZERO,Env.ZERO,Env.ZERO																//durationSetup,duration
					);
					return;
		}

		//
		if (toIssue.signum() != 0)
		{
			// should not happen because we validate Qty On Hand on start of this process
			throw new AdempiereException("Should not happen toIssue="+toIssue);
		}
	}
		
	public static boolean isQtyAvailable(MPPOrder order, I_PP_Order_BOMLine line)
	{
		MProduct product = MProduct.get(order.getCtx(), line.getM_Product_ID());
		if (product == null || !product.isStocked())
		{
			return true;
		}
		
		BigDecimal qtyToDeliver = line.getQtyRequired();
		BigDecimal qtyScrap = line.getQtyScrap();
		BigDecimal qtyRequired = qtyToDeliver.add(qtyScrap);
		BigDecimal qtyAvailable = MStorageOnHand.getQtyOnHand(line.getM_Product_ID(),order.getM_Warehouse_ID(),
												 line.getM_AttributeSetInstance_ID(),
												order.get_TrxName());
		return qtyAvailable.compareTo(qtyRequired) >= 0;
	}
	
	/**
	 * @return Default Locator for current Warehouse
	 */
	public int getM_Locator_ID()
	{
		MWarehouse wh = MWarehouse.get(getCtx(), getM_Warehouse_ID());
		return wh.getDefaultLocator().getM_Locator_ID();
	}
	
	/**
	 * @param qty
	 * @return Storage locator for current product/asi/warehouse and qty
	 * @see MStorage#getM_Locator_ID(int, int, int, BigDecimal, String)
	 */
	private int getM_Locator_ID(BigDecimal qty, MProduct product)
	{
		int M_Locator_ID = 0;
		int M_ASI_ID = getM_AttributeSetInstance_ID();
		// Get existing Locator
		if (M_ASI_ID != 0)
		{
			M_Locator_ID = MStorageOnHand.getM_Locator_ID(getM_Warehouse_ID(), getM_Product_ID(), M_ASI_ID, qty, get_TrxName());
		}
		// Get Default
		if (M_Locator_ID == 0)
		{
			if(product.getM_Locator_ID()>0) {
				M_Locator_ID = product.getM_Locator_ID();
			}else {
				M_Locator_ID = getM_Locator_ID();
			}
		}
		return M_Locator_ID;
	}
	
	/**
	 * @return true if work was delivered for this MO (i.e. Stock Issue, Stock Receipt, Activity Control Report)
	 */
	public boolean isDelivered()
	{
		if(getQtyDelivered().signum() > 0 || getQtyScrap().signum() > 0 ||  getQtyReject().signum() > 0)
		{
			return true;
		}
		
		for (MPPOrderBOMLine line : getLines())
		{
			if(line.getQtyDelivered().signum() > 0)
			{
				return true;
			}
		}
		
		for(MPPOrderNode node : this.getMPPOrderWorkflow().getNodes(true, getAD_Client_ID()))
		{
			if(node.getQtyDelivered().signum() > 0)
			{
				return true;
			}
//			if(node.getDurationReal() > 0)
//			{
//				return true;
//			}
		}
		return false;		
	}
	
	/**
	 * Set default value and reset values whe copy record
	 */
	public void setDefault()
	{
		setLine(10);
		setPriorityRule(PRIORITYRULE_Medium);
		setDescription("");
		setQtyDelivered(Env.ZERO);
		setQtyReject(Env.ZERO);
		setQtyScrap(Env.ZERO);                                                        
		setIsSelected(false);
		setIsSOTrx(false);
		setIsApproved(false);
		setIsPrinted(false);
		setProcessed(false);
		setProcessing(false);
		setPosted(false);
		setC_DocTypeTarget_ID(MDocType.DOCBASETYPE_ManufacturingOrder);
		setC_DocType_ID(getC_DocTypeTarget_ID());
		setDocStatus(DOCSTATUS_Drafted);
		setDocAction(DOCACTION_Prepare);	
		setC_OrderLine_ID(MPPMRP.C_OrderLine_ID);//red1 harmless DNA for other MRPs from this MO
	}
	
	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else
			setDescription(desc + " | " + description);
	}	//	addDescription
	
	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer("MPPOrder[").append(get_ID())
								.append("-").append(getDocumentNo())
								.append(",IsSOTrx=").append(isSOTrx())
								.append(",C_DocType_ID=").append(getC_DocType_ID())
								.append("]");
		return sb.toString();
	}
	
	/*
	 * Auto report the first Activity and Sub contracting  if are Milestone Activity
	 */
	public void autoReportActivities()
	{
		for(MPPOrderNode activity : getMPPOrderWorkflow().getNodes())
		{
		
			if(activity.isMilestone())
			{
				if(activity.isSubcontracting() || activity.get_ID() == getMPPOrderWorkflow().getPP_Order_Node_ID())
				{	
				MPPCostCollector cc = MPPCostCollector.createCollector(
						this, 
						getM_Product_ID(), 
						getM_Locator_ID(), 
						getM_AttributeSetInstance_ID(), 
						getS_Resource_ID(), 
						0, 
						activity.getPP_Order_Node_ID(),
						MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector),
						MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl,
						getUpdated(),
						activity.getQtyToDeliver(),
						Env.ZERO, 
						Env.ZERO, 
						0, 
						Env.ZERO,
						Env.ZERO,Env.ZERO,Env.ZERO);
				}
			}
		}	
	}

	/**
	 * Save standard costs records into PP_Order_Cost.
	 * This will be useful for calculating standard costs variances
	 */
	private final void createStandardCosts()
	{
		MAcctSchema as = MClient.get(getCtx(), getAD_Client_ID()).getAcctSchema();
		log.info("Cost_Group_ID" + as.getM_CostType_ID());

		final TreeSet<Integer> productsAdded = new TreeSet<Integer>();
		
		//
		// Create Standard Costs for Order Header (resulting product)
		{
			final MProduct product = getM_Product();
			productsAdded.add(product.getM_Product_ID());
			//
			final CostDimension d = new CostDimension(product, as, as.getM_CostType_ID(),
												getAD_Org_ID(), getM_AttributeSetInstance_ID(),
												CostDimension.ANY);
			Collection<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
			for (MCost cost : costs)
			{
				MPPOrderCost PP_Order_Cost = new MPPOrderCost(cost, get_ID(), get_TrxName());
				PP_Order_Cost.saveEx(get_TrxName());
			}
		}
		//
		// Create Standard Costs for Order BOM Line
		for (MPPOrderBOMLine line : getLines())
		{
			final MProduct product = line.getM_Product();
			//
			// Check if we already added this product
			if (productsAdded.contains(product.getM_Product_ID()))
			{
				continue;
			}
			productsAdded.add(product.getM_Product_ID());
			//
			CostDimension d = new CostDimension(line.getM_Product(), as, as.getM_CostType_ID(),
												line.getAD_Org_ID(), line.getM_AttributeSetInstance_ID(),
												CostDimension.ANY);
			Collection<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
			for (MCost cost : costs)
			{
				MPPOrderCost PP_Order_Cost = new MPPOrderCost(cost, get_ID(), get_TrxName());
				PP_Order_Cost.saveEx(get_TrxName());
			}
		}
		//
		// Create Standard Costs from Activity Resources
		for (MPPOrderNode node : getMPPOrderWorkflow().getNodes(true))
		{
			final int S_Resource_ID = node.getS_Resource_ID();
			if (S_Resource_ID <= 0)
				continue;
			//
			final MProduct resourceProduct = MProduct.forS_Resource_ID(getCtx(), S_Resource_ID, null);
			//
			// Check if we already added this product
			if (productsAdded.contains(resourceProduct.getM_Product_ID()))
			{
				continue;
			}
			productsAdded.add(resourceProduct.getM_Product_ID());
			//
			CostDimension d = new CostDimension(resourceProduct, as, as.getM_CostType_ID(),
					node.getAD_Org_ID(),
					0, // ASI
					CostDimension.ANY);
			Collection<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
			for (MCost cost : costs)
			{
				MPPOrderCost orderCost = new MPPOrderCost(cost, getPP_Order_ID(), get_TrxName());
				orderCost.saveEx(get_TrxName());
			}
		}
	}
	
	private final void createStandardFifo()
	{
		MAcctSchema as = MClient.get(getCtx(), getAD_Client_ID()).getAcctSchema();
		log.info("Cost_Group_ID" + as.getM_CostType_ID());

		final TreeSet<Integer> productsAdded = new TreeSet<Integer>();
		
		//
		// Create Standard Costs for Order Header (resulting product)
		{
			final MProduct product = getM_Product();
			productsAdded.add(product.getM_Product_ID());
			//
			final CostDimension d = new CostDimension(product, as, as.getM_CostType_ID(),
												getAD_Org_ID(), getM_AttributeSetInstance_ID(),
												CostDimension.ANY);
			Collection<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
			for (MCost cost : costs)
			{
				MPPOrderCost PP_Order_Cost = new MPPOrderCost(cost, get_ID(), get_TrxName());
				PP_Order_Cost.saveEx(get_TrxName());
			}
		}
		//
		// Create Standard Costs for Order BOM Line
		for (MPPOrderBOMLine line : getLines())
		{
			final MProduct product = line.getM_Product();
			//
			// Check if we already added this product
			if (productsAdded.contains(product.getM_Product_ID()))
			{
				continue;
			}
			productsAdded.add(product.getM_Product_ID());
			//
            if(DB.getSQLValue(null, "select m_attributesetinstance_id from m_cost where m_product_id = ? and coalesce(m_attributesetinstance_id,0)>0", product.getM_Product_ID())>0) {
			CostDimension d = new CostDimension(line.getM_Product(), as, as.getM_CostType_ID(),
												line.getAD_Org_ID(), DB.getSQLValue(null, "select m_attributesetinstance_id from m_cost where m_product_id = ? and coalesce(m_attributesetinstance_id,0)>0 ", product.getM_Product_ID()),
												CostDimension.ANY);
			
			Collection<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
			for (MCost cost : costs)
			{
				MPPOrderCost PP_Order_Cost = new MPPOrderCost(cost, get_ID(), get_TrxName());
				PP_Order_Cost.saveEx(get_TrxName());
			}
            }
		}
		//
		// Create Standard Costs from Activity Resources
//		for (MPPOrderNode node : getMPPOrderWorkflow().getNodes(true))
//		{
//			final int S_Resource_ID = node.getS_Resource_ID();
//			if (S_Resource_ID <= 0)
//				continue;
//			//
//			final MProduct resourceProduct = MProduct.forS_Resource_ID(getCtx(), S_Resource_ID, null);
//			//
//			// Check if we already added this product
//			if (productsAdded.contains(resourceProduct.getM_Product_ID()))
//			{
//				continue;
//			}
//			productsAdded.add(resourceProduct.getM_Product_ID());
//			//
//			CostDimension d = new CostDimension(resourceProduct, as, as.getM_CostType_ID(),
//					node.getAD_Org_ID(),
//					0, // ASI
//					CostDimension.ANY);
//			Collection<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
//			for (MCost cost : costs)
//			{
//				MPPOrderCost orderCost = new MPPOrderCost(cost, getPP_Order_ID(), get_TrxName());
//				orderCost.saveEx(get_TrxName());
//			}
//		}
	}
	
	public void createVariances()
	{
		for (MPPOrderBOMLine line : getLines(true))
		{
			createUsageVariance(line);
		}
		m_lines = null; // needs to be required
		//
		MPPOrderWorkflow orderWorkflow = getMPPOrderWorkflow();
		if (orderWorkflow != null)
		{
			for (MPPOrderNode node : orderWorkflow.getNodes(true))
			{
				createUsageVariance(node);
				createUsageVarianceCost(node);
			}
		}
		//orderWorkflow.m_nodes = null;  // TODO: reset nodes cache
	}
	
	private void createUsageVariance(I_PP_Order_BOMLine bomLine)
	{
		MPPOrder order = this;
		Timestamp movementDate = order.getUpdated();
		MPPOrderBOMLine line = (MPPOrderBOMLine)bomLine;

		// If QtyBatch and QtyBOM is zero, than this is a method variance
		// (a product that "was not" in BOM was used)
		if (line.getQtyBatch().signum() == 0 && line.getQtyBOM().signum() == 0)
		{
			return;
		}
		
		final BigDecimal qtyUsageVariancePrev = line.getQtyVariance();	// Previous booked usage variance
		final BigDecimal qtyOpen = line.getQtyOpen();
		// Current usage variance = QtyOpen - Previous Usage Variance 
		final BigDecimal qtyUsageVariance = qtyOpen.subtract(qtyUsageVariancePrev);
		
		//
		if (qtyUsageVariance.signum() == 0 || getQtyReject().compareTo(BigDecimal.ZERO) == 0 )
		{
			return;
		}
		// Get Locator
		int M_Locator_ID = line.getM_Locator_ID();
		if (M_Locator_ID <= 0)
		{
			MLocator locator = MLocator.getDefault(MWarehouse.get(order.getCtx(), order.getM_Warehouse_ID()));
			if (locator != null)
			{
				M_Locator_ID = locator.getM_Locator_ID();
			}
		}
		//
		MPPCostCollector.createCollector(
				order,
				line.getM_Product_ID(),
				M_Locator_ID,
				line.getM_AttributeSetInstance_ID(),
				order.getS_Resource_ID(),
				line.getPP_Order_BOMLine_ID(),
				0, //PP_Order_Node_ID,
				MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), // C_DocType_ID,
				MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance,
				movementDate,
				qtyUsageVariance, // Qty
				Env.ZERO, // scrap,
				Env.ZERO, // reject,
				0, //durationSetup,
				Env.ZERO, // duration
				Env.ZERO,Env.ZERO,Env.ZERO
		);
	}
	
	private BigDecimal costvarian = BigDecimal.ZERO;
	
	public void createUsageVariance(I_PP_Order_Node orderNode)
	{
		MPPOrder order = this;
		final Timestamp movementDate = order.getUpdated();
		final MPPOrderNode node = (MPPOrderNode)orderNode;
		//
		final BigDecimal setupTimeReal = BigDecimal.valueOf(node.getSetupTimeReal());
		final BigDecimal durationReal = BigDecimal.valueOf(node.getDurationReal());
		if (setupTimeReal.signum() == 0 && durationReal.signum() == 0)
		{
			// nothing reported on this activity => it's not a variance, this will be auto-reported on close
			return;
		}
		//
		final BigDecimal setupTimeVariancePrev = node.getSetupTimeUsageVariance();
		final BigDecimal durationVariancePrev = node.getDurationUsageVariance();
		final BigDecimal setupTimeRequired = BigDecimal.valueOf(node.getSetupTimeRequired());
		final BigDecimal durationRequired = BigDecimal.valueOf(node.getDurationRequired());
		final BigDecimal qtyOpen = node.getQtyToDeliver().subtract(new BigDecimal(node.get_ValueAsString("qtyreserved")));
		//
		final BigDecimal setupTimeVariance = setupTimeRequired.subtract(setupTimeReal).subtract(setupTimeVariancePrev);
		//final BigDecimal durationVariance = durationRequired.subtract(durationReal).subtract(durationVariancePrev);
		final BigDecimal durationVariance = new BigDecimal(node.getDuration()).multiply(qtyOpen);
		//
		if (qtyOpen.signum() == 0 && setupTimeVariance.signum() == 0 && durationVariance.signum() == 0 && new BigDecimal(node.get_ValueAsString("qtyreserved")).signum() == 0 )
		{
			return;
		}
//			
		

		
		if(new BigDecimal(node.get_ValueAsString("qtyreserved")).compareTo(BigDecimal.ZERO) == 0) {
		MPPCostCollector.createCollector(
				order,
				order.getM_Product_ID(),
				order.getM_Locator_ID(),
				order.getM_AttributeSetInstance_ID(),
				node.getS_Resource_ID(),
				0, //PP_Order_BOMLine_ID
				node.getPP_Order_Node_ID(),
				MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), // C_DocType_ID
				MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance,
				movementDate,
				qtyOpen, // Qty
				Env.ZERO, // scrap,
				Env.ZERO, // reject,
				setupTimeVariance.intValueExact(), //durationSetup,
				durationVariance, // duration
				Env.ZERO,Env.ZERO,new BigDecimal(node.get_ValueAsInt("jmltenagakerja"))
				
		);
		} else {
			MPPCostCollector cc = MPPCostCollector.createCollector(
					order, 
					getM_Product_ID(), 
					getM_Locator_ID(), 
					getM_AttributeSetInstance_ID(), 
					node.getS_Resource_ID(), 
					0, 
					node.getPP_Order_Node_ID(),
					MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector),
					MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl,
					order.getUpdated(),
					node.getQtyRequired().subtract(node.getQtyDelivered()),
					Env.ZERO, 
					Env.ZERO, 
					setupTimeVariance.intValueExact(), 
					new BigDecimal(node.getDuration()).multiply(node.getQtyRequired().subtract(node.getQtyDelivered())),
					Env.ZERO,Env.ZERO,Env.ZERO);						
				}
		}
	
	
	public void createUsageVarianceCost(I_PP_Order_Node orderNode)
	{
		MPPOrder order = this;
		final Timestamp movementDate = order.getUpdated();
		final MPPOrderNode node = (MPPOrderNode)orderNode;
		//
		final BigDecimal setupTimeReal = BigDecimal.valueOf(node.getSetupTimeReal());
		final BigDecimal durationReal = BigDecimal.valueOf(node.getDurationReal());
//		if (setupTimeReal.signum() == 0 && durationReal.signum() == 0)
//		{
//			// nothing reported on this activity => it's not a variance, this will be auto-reported on close
//			return;
//		}
		//
		final BigDecimal setupTimeVariancePrev = node.getSetupTimeUsageVariance();
		final BigDecimal durationVariancePrev = node.getDurationUsageVariance();
		final BigDecimal setupTimeRequired = BigDecimal.valueOf(node.getSetupTimeRequired());
		final BigDecimal durationRequired = BigDecimal.valueOf(node.getDurationRequired());
		final BigDecimal qtyOpen = node.getQtyToDeliver().subtract(new BigDecimal(node.get_Value("QtyReserved").toString()));
		//
		final BigDecimal setupTimeVariance = setupTimeRequired.subtract(setupTimeReal).subtract(setupTimeVariancePrev);
		//final BigDecimal durationVariance = durationRequired.subtract(durationReal).subtract(durationVariancePrev);
		
		final BigDecimal durationVariance = new BigDecimal(node.getDuration()).multiply(qtyOpen);
		
		//
//		if (qtyOpen.signum() == 0 && setupTimeVariance.signum() == 0 && durationVariance.signum() == 0)
//		{
//			return;
//		}
//		
//		if(node.getPP_Order_Workflow().getCost().compareTo(node.getPP_Order_Workflow().getAD_Workflow().getCost())==0) {
//			return;
//		}else {
			costvarian = node.getPP_Order_Workflow().getCost().subtract(DB.getSQLValueBD(null, "select sum(currentcostprice) from m_cost where M_CostElement_ID != 1000000 and m_product_id = " +  node.getPP_Order_Workflow().getPP_Order().getM_Product_ID()));
	
			
			if(costvarian.compareTo(BigDecimal.ZERO)>0) {
				MPPCostCollector.createCollector(
						order,
						order.getM_Product_ID(),
						order.getM_Locator_ID(),
						order.getM_AttributeSetInstance_ID(),
						node.getS_Resource_ID(),
						0, //PP_Order_BOMLine_ID
						node.getPP_Order_Node_ID(),
						MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), // C_DocType_ID
						MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance,
						movementDate,
						node.getQtyRequired().subtract(node.getQtyDelivered()), // Qty
						Env.ZERO, // scrap,
						Env.ZERO, // reject,
						setupTimeVariance.intValueExact(), //durationSetup,
						new BigDecimal(node.getDuration()).multiply(node.getQtyRequired().subtract(node.getQtyDelivered())), // duration
						costvarian,Env.ZERO,new BigDecimal(node.get_ValueAsInt("jmltenagakerja"))
				);
			}
	
		//
//		MPPCostCollector.createCollector(
//				order,
//				order.getM_Product_ID(),
//				order.getM_Locator_ID(),
//				order.getM_AttributeSetInstance_ID(),
//				node.getS_Resource_ID(),
//				0, //PP_Order_BOMLine_ID
//				node.getPP_Order_Node_ID(),
//				MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), // C_DocType_ID
//				MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance,
//				movementDate,
//				qtyOpen, // Qty
//				Env.ZERO, // scrap,
//				Env.ZERO, // reject,
//				setupTimeVariance.intValueExact(), //durationSetup,
//				durationVariance, // duration
//				costvarian
//		);
	}
	
	/**
	 * Get Quantity to Deliver
	 * @return Quantity to Deliver
	 */
	public BigDecimal getQtyToDeliver()
	{
		return getQtyOrdered().subtract(getQtyDelivered());
	}
	
	/**
	 * Create Auto Receipt and Issue based on Quantity
	 * @param qtyShipment
	 */
	public void updateMakeToKit(BigDecimal qtyShipment)
	{
		MPPOrderBOM obom = (MPPOrderBOM)getMPPOrderBOM();
		getLines(true);
		// Auto receipt and issue for kit
		if (MPPOrderBOM.BOMTYPE_Make_To_Kit.equals(obom.getBOMType()) && MPPOrderBOM.BOMUSE_Manufacturing.equals(obom.getBOMUse()))
		{				
			Timestamp today = new Timestamp(System.currentTimeMillis());
			ArrayList[][] issue = new ArrayList[m_lines.length][1];
			
			for (int i = 0; i < getLines().length ; i++)
			{
				MPPOrderBOMLine line =  m_lines[i];
				
				KeyNamePair id = null;
				
				if(MPPOrderBOMLine.ISSUEMETHOD_Backflush.equals(line.getIssueMethod()))
				{
					id = new KeyNamePair(line.get_ID(),"Y");
				}
				else
					id = new KeyNamePair(line.get_ID(),"N");
					
					ArrayList<Object> data = new ArrayList<Object>();
					BigDecimal qtyToDeliver = qtyShipment.multiply(line.getQtyMultiplier());
					data.add(id); 				  		//0 - MPPOrderBOMLine ID
					data.add(line.isCritical());  		//1 - Critical
					MProduct product = (MProduct) line.getM_Product();
					data.add(product.getValue()); 		//2 - Value
					KeyNamePair productKey = new KeyNamePair(product.get_ID(),product.getName());
					data.add(productKey); 				//3 - KeyNamePair Product
					data.add(qtyToDeliver); 	//4 - QtyToDeliver			
					data.add(Env.ZERO); 				//5 - QtyScrapComponent
					issue[i][0] = data;	
				
			}
			
			boolean forceIssue = false;
			MOrderLine oline = (MOrderLine)getC_OrderLine();				
			if(MOrder.DELIVERYRULE_CompleteLine.equals(oline.getParent().getDeliveryRule()) ||
			   MOrder.DELIVERYRULE_CompleteOrder.equals(oline.getParent().getDeliveryRule()))
			{	
				boolean isCompleteQtyDeliver = MPPOrder.isQtyAvailable(this, issue ,today);	
				if (!isCompleteQtyDeliver)
				{
						throw new AdempiereException("@NoQtyAvailable@");
				}
			}
			else if(MOrder.DELIVERYRULE_Availability.equals(oline.getParent().getDeliveryRule()) ||
					MOrder.DELIVERYRULE_AfterPayment.equals(oline.getParent().getDeliveryRule()) ||
					MOrder.DELIVERYRULE_Manual.equals(oline.getParent().getDeliveryRule()))
			{
				throw new AdempiereException("@ActionNotSupported@");
			}
			else if(MOrder.DELIVERYRULE_Force.equals(oline.getParent().getDeliveryRule()))
			{
				forceIssue = true;
			}
			
			
			for(int i = 0; i < issue.length; i++ )
			{
				int M_AttributeSetInstance_ID = 0;
				KeyNamePair key = (KeyNamePair) issue[i][0].get(0);
				Boolean isCritical = (Boolean) issue[i][0].get(1);
				String value = (String)issue[i][0].get(2);
				KeyNamePair productkey = (KeyNamePair) issue[i][0].get(3);			
				int M_Product_ID = productkey.getKey();
				MProduct product = MProduct.get(getCtx(),  M_Product_ID);
				BigDecimal qtyToDeliver = (BigDecimal)issue[i][0].get(4);	
				BigDecimal qtyScrapComponent = (BigDecimal) issue[i][0].get(5);	
				
				int PP_Order_BOMLine_ID =  (Integer)key.getKey();
				if(PP_Order_BOMLine_ID > 0)
				{
					MPPOrderBOMLine  orderBOMLine = new MPPOrderBOMLine(getCtx(), PP_Order_BOMLine_ID, get_TrxName());
					//Validate if AttributeSet generate instance
					M_AttributeSetInstance_ID = orderBOMLine.getM_AttributeSetInstance_ID();
				}
				
				MStorageOnHand[] storages = MPPOrder.getStorages(getCtx(),
						M_Product_ID,
						getM_Warehouse_ID(),
						M_AttributeSetInstance_ID
						, today, get_TrxName());
				
				MPPOrder.createIssue(
						this, 
						key.getKey(), 
						today, qtyToDeliver,
						qtyScrapComponent, 
						Env.ZERO, 
						storages, forceIssue);
			}	
			MPPOrder.createReceipt(
					this, 
					today , 
					getQtyDelivered(), 
					qtyShipment, 
					getQtyScrap(), 
					getQtyReject(), 
					getM_Locator_ID(), 
					getM_AttributeSetInstance_ID());
			//setQtyDelivered(getQtyOpen());
			//return DOCSTATUS_Closed;
		}
	}
	
	protected void rollup(MProduct product, MPPProductBOM bom)
	{
		for (MCostElement element : getCostElements())
		{		
			
			for (MCost cost : getCosts(product, element.get_ID()))
			{        
				System.out.println("Calculate Lower Cost for: "+ bom);
				BigDecimal price = getCurrentCostPriceLL(bom, element);     
				log.info(element.getName() + " Cost Low Level:" + price);
				cost.setCurrentCostPriceLL(price);
				updateCoProductCosts(bom, cost);
				cost.saveEx();
			} // for each Costs 
		} // for Elements	
	}
	
//	protected void rollupfifo(MProduct product, MPPProductBOM bom)
//	{
//		for (MCostElement element : getCostElements())
//		{		
//			
//			for (MCost cost : getCosts(product, element.get_ID()))
//			{        
//				System.out.println("Calculate Lower Cost for FIFO: "+ bom);
//				BigDecimal price = getCurrentCostPriceLLFifo(bom, element);     
//				log.info(element.getName() + " Cost Low Level FIFO:" + price);
//				cost.setCurrentCostPrice(price);
//				updateCoProductCostsfifo(bom, cost);
//				cost.saveEx();
//			} // for each Costs 
//		} // for Elements	
//	}
	
	private Collection<MCost> getCosts(MProduct product, int M_CostElement_ID)
	{
		System.out.println("costelemet"+M_CostElement_ID);
		MAcctSchema as = MAcctSchema.get(getCtx(),1000000 );
		CostDimension d = null;
		if(getM_AttributeSetInstance_ID()>0) {
			d = new CostDimension(product, as, 1000000, 0, getM_AttributeSetInstance_ID(), M_CostElement_ID);
		}else {
			d = new CostDimension(product, as, 1000000, getAD_Org_ID(), 0, M_CostElement_ID);	
		     
		}
		System.out.println("asfafa"+d);
		return d.toQuery(MCost.class, get_TrxName()).list();
	}
	
	private List<Collection<MCost>> getCostsComponen(MProduct product, int M_CostElement_ID)
	{
		List<Collection<MCost>> list = new ArrayList<>();
		MAcctSchema as = MAcctSchema.get(getCtx(),1000000 );
  		CostDimension d = null;
		final String whereClause = "  m_product_id= "+product.getM_Product_ID()+" and qtyonhand>0";
		Collection<MStorageOnHand> storage = new Query(getCtx(), MStorageOnHand.Table_Name, whereClause, get_TrxName())											
									.list();
		for (MStorageOnHand mStorageOnHand : storage) {
			d = new CostDimension(product, as, 1000000, 0, mStorageOnHand.getM_AttributeSetInstance_ID(), M_CostElement_ID);
			list.add(d.toQuery(MCost.class, get_TrxName()).list());
		}
		System.out.println("asfafafifo"+d);
		return list;
	}


	/**
	 * Update costs for co-products on BOM Lines from given BOM
	 * @param bom product's BOM
	 * @param element cost element
	 * @param baseCost base product cost (BOM Cost)
	 */
	private void updateCoProductCosts(MPPProductBOM bom, MCost baseCost)
	{
		// Skip if not BOM found
		if (bom == null)
			return;
		
		BigDecimal costPriceTotal = Env.ZERO;
		for (MPPProductBOMLine bomline : bom.getLines())
		{
			if (!bomline.isCoProduct())
			{
				continue;
			}
			final BigDecimal costPrice = baseCost.getCurrentCostPriceLL().multiply(bomline.getCostAllocationPerc(true));
			//
			// Get/Create Cost
			MCost cost = MCost.get(baseCost.getCtx(), baseCost.getAD_Client_ID(), baseCost.getAD_Org_ID(),
									bomline.getM_Product_ID(),
									baseCost.getM_CostType_ID(), baseCost.getC_AcctSchema_ID(),
									baseCost.getM_CostElement_ID(),
									0, // ASI
									baseCost.get_TrxName());
			if (cost == null)
			{
				cost = new MCost (baseCost.getCtx(), 0, baseCost.get_TrxName());
				//cost.setAD_Client_ID(baseCost.getAD_Client_ID());
				cost.setAD_Org_ID(baseCost.getAD_Org_ID());
				cost.setM_Product_ID(bomline.getM_Product_ID());
				cost.setM_CostType_ID(baseCost.getM_CostType_ID());
				cost.setC_AcctSchema_ID(baseCost.getC_AcctSchema_ID());
				cost.setM_CostElement_ID(baseCost.getM_CostElement_ID());
				cost.setM_AttributeSetInstance_ID(0);
			}
			cost.setCurrentCostPriceLL(costPrice);
			cost.saveEx();
			costPriceTotal = costPriceTotal.add(costPrice);
		}
		// Update Base Cost:
		if(costPriceTotal.signum() != 0)
		{
			baseCost.setCurrentCostPriceLL(costPriceTotal);
		}
	}
	
	private void updateCoProductCostsfifo(MPPProductBOM bom, MCost baseCost)
	{
		// Skip if not BOM found
		if (bom == null)
			return;
		
		BigDecimal costPriceTotal = Env.ZERO;
		for (MPPProductBOMLine bomline : bom.getLines())
		{
			if (!bomline.isCoProduct())
			{
				continue;
			}
			final BigDecimal costPrice = baseCost.getCurrentCostPrice().multiply(bomline.getCostAllocationPerc(true));
			System.out.println("Cost Price");
			//
			// Get/Create Cost
			MCost cost = MCost.get(baseCost.getCtx(), baseCost.getAD_Client_ID(), baseCost.getAD_Org_ID(),
									bomline.getM_Product_ID(),
									baseCost.getM_CostType_ID(), baseCost.getC_AcctSchema_ID(),
									baseCost.getM_CostElement_ID(),
									0, // ASI
									baseCost.get_TrxName());
			if (cost == null)
			{
				cost = new MCost (baseCost.getCtx(), 0, baseCost.get_TrxName());
				//cost.setAD_Client_ID(baseCost.getAD_Client_ID());
				cost.setAD_Org_ID(baseCost.getAD_Org_ID());
				cost.setM_Product_ID(bomline.getM_Product_ID());
				cost.setM_CostType_ID(baseCost.getM_CostType_ID());
				cost.setC_AcctSchema_ID(baseCost.getC_AcctSchema_ID());
				cost.setM_CostElement_ID(baseCost.getM_CostElement_ID());
				cost.setM_AttributeSetInstance_ID(0);
			}
			cost.setCurrentCostPrice(costPrice);
			cost.saveEx();
			costPriceTotal = costPriceTotal.add(costPrice);
		}
		// Update Base Cost:
		if(costPriceTotal.signum() != 0)
		{
			baseCost.setCurrentCostPrice(costPriceTotal);
		}
	}

	/**
	 * Get the sum Current Cost Price Level Low for this Cost Element
	 * @param bom MPPProductBOM
	 * @param element MCostElement
	 * @return Cost Price Lower Level
	 */
	private BigDecimal getCurrentCostPriceLLFifo(MPPProductBOM bom, MCostElement element)
	{
		log.info("Element: "+ element);
		System.out.println("bom"+bom);
		BigDecimal costPriceLL = Env.ZERO;
		if(bom == null)
			return costPriceLL;
		
		int conv =DB.getSQLValue(get_TrxName(), "select count(*) from C_UOM_Conversion where m_product_id = "+bom.getM_Product_ID());
		
		for (MPPProductBOMLine bomline : bom.getLines())
			{
			MProduct component = MProduct.get(getCtx(), bomline.getM_Product_ID());
		for(Collection<MCost> collection : getCostsComponen(component, element.get_ID())) {
			for (MCost cost : collection) {
				BigDecimal qty = bomline.getQty(true);							
				if(conv>0) {
					int idconv =DB.getSQLValue(get_TrxName(), "select  C_UOM_Conversion_ID from C_UOM_Conversion where m_product_id = "+bom.getM_Product_ID());
					MUOMConversion conversion = new MUOMConversion(Env.getCtx(),idconv, get_TrxName());
					
					if(bomline.getQtyBOM().compareTo( conversion.getDivideRate())==0) {
						BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
						System.out.println("afagagagagag"+ costPrice +" "+qty );
						BigDecimal componentCost = costPrice.multiply(qty);
						costPriceLL = costPrice;
						log.info("CostElement: "+element.getName()
								+ ", Component: "+component.getValue()
								+ ", CostPrice: "+costPrice
								+ ", Qty: " + qty
								+ ", Cost: " + componentCost
								+ " => Total Cost Element: " +  costPriceLL);
					}else {
						BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
						System.out.println("afagagagagag"+ costPrice +" "+qty );
						BigDecimal componentCost = costPrice.multiply(qty);
						costPriceLL = costPrice;
						log.info("CostElement: "+element.getName()
								+ ", Component: "+component.getValue()
								+ ", CostPrice: "+costPrice
								+ ", Qty: " + qty
								+ ", Cost: " + componentCost
								+ " => Total Cost Element: " +  costPriceLL);
					}
				}
				else {
					BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
					System.out.println("afagagagagag"+ costPrice +" "+qty );
					BigDecimal componentCost = costPrice.multiply(qty);
					costPriceLL = costPriceLL.add(componentCost);
					log.info("CostElement: "+element.getName()
							+ ", Component: "+component.getValue()
							+ ", CostPrice: "+costPrice
							+ ", Qty: " + qty
							+ ", Cost: " + componentCost
							+ " => Total Cost Element: " +  costPriceLL);
				}
				// ByProducts
				if (bomline.isByProduct())
				{
					cost.setCurrentCostPriceLL(Env.ZERO);
				}
			}
		    } 
			}
		System.out.println("dsddd"+costPriceLL);

//		for (MPPProductBOMLine bomline : bom.getLines())
//		{
//			// Skip co-product
//			if (bomline.isCoProduct())
//			{
//				continue;
//			}
//			//
//			MProduct component = MProduct.get(getCtx(), bomline.getM_Product_ID());
//			// get the rate for this resource     
//			for (MCost cost : getCostsComponen(component, element.get_ID()))
//			{                 
//				BigDecimal qty = bomline.getQty(true);
//				
//				
//				if(conv>0) {
//					int idconv =DB.getSQLValue(get_TrxName(), "select  C_UOM_Conversion_ID from C_UOM_Conversion where m_product_id = "+bom.getM_Product_ID());
//					MUOMConversion conversion = new MUOMConversion(Env.getCtx(),idconv, get_TrxName());
//					
//					if(bomline.getQtyBOM().compareTo( conversion.getDivideRate())==0) {
//						BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
//						System.out.println("afagagagagag"+ costPrice +" "+qty );
//						BigDecimal componentCost = costPrice.multiply(qty);
//						costPriceLL = costPrice;
//						log.info("CostElement: "+element.getName()
//								+ ", Component: "+component.getValue()
//								+ ", CostPrice: "+costPrice
//								+ ", Qty: " + qty
//								+ ", Cost: " + componentCost
//								+ " => Total Cost Element: " +  costPriceLL);
//					}else {
//						BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
//						System.out.println("afagagagagag"+ costPrice +" "+qty );
//						BigDecimal componentCost = costPrice.multiply(qty);
//						costPriceLL = costPrice;
//						log.info("CostElement: "+element.getName()
//								+ ", Component: "+component.getValue()
//								+ ", CostPrice: "+costPrice
//								+ ", Qty: " + qty
//								+ ", Cost: " + componentCost
//								+ " => Total Cost Element: " +  costPriceLL);
//					}
//				}
//				else {
//					BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
//					System.out.println("afagagagagag"+ costPrice +" "+qty );
//					BigDecimal componentCost = costPrice.multiply(qty);
//					costPriceLL = costPriceLL.add(componentCost);
//					log.info("CostElement: "+element.getName()
//							+ ", Component: "+component.getValue()
//							+ ", CostPrice: "+costPrice
//							+ ", Qty: " + qty
//							+ ", Cost: " + componentCost
//							+ " => Total Cost Element: " +  costPriceLL);
//				}
//				// ByProducts
//				if (bomline.isByProduct())
//				{
//					cost.setCurrentCostPriceLL(Env.ZERO);
//				}
//				
//				
//			} // for each cost
		 // for each BOM line
		return costPriceLL;     
	}
	
	private BigDecimal getCurrentCostPriceLL(MPPProductBOM bom, MCostElement element)
	{
		log.info("Element: "+ element);
		System.out.println("bom"+bom);
		BigDecimal costPriceLL = Env.ZERO;
		if(bom == null)
			return costPriceLL;
		
		int conv =DB.getSQLValue(get_TrxName(), "select count(*) from C_UOM_Conversion where m_product_id = "+bom.getM_Product_ID());

		for (MPPProductBOMLine bomline : bom.getLines())
		{
			// Skip co-product
			if (bomline.isCoProduct())
			{
				continue;
			}
			//
			MProduct component = MProduct.get(getCtx(), bomline.getM_Product_ID());
			// get the rate for this resource     
			for (MCost cost : getCosts(component, element.get_ID()))
			{                 
				BigDecimal qty = bomline.getQty(true);
				
				
				if(conv>0) {
					int idconv =DB.getSQLValue(get_TrxName(), "select  C_UOM_Conversion_ID from C_UOM_Conversion where m_product_id = "+bom.getM_Product_ID());
					MUOMConversion conversion = new MUOMConversion(Env.getCtx(),idconv, get_TrxName());
					
					if(bomline.getQtyBOM().compareTo( conversion.getDivideRate())==0) {
						BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
						System.out.println("afagagagagag"+ costPrice +" "+qty );
						BigDecimal componentCost = costPrice.multiply(qty);
						costPriceLL = costPrice;
						log.info("CostElement: "+element.getName()
								+ ", Component: "+component.getValue()
								+ ", CostPrice: "+costPrice
								+ ", Qty: " + qty
								+ ", Cost: " + componentCost
								+ " => Total Cost Element: " +  costPriceLL);
					}else {
						BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
						System.out.println("afagagagagag"+ costPrice +" "+qty );
						BigDecimal componentCost = costPrice.multiply(qty);
						costPriceLL = costPrice;
						log.info("CostElement: "+element.getName()
								+ ", Component: "+component.getValue()
								+ ", CostPrice: "+costPrice
								+ ", Qty: " + qty
								+ ", Cost: " + componentCost
								+ " => Total Cost Element: " +  costPriceLL);
					}
				}
				else {
					BigDecimal costPrice = cost.getCurrentCostPrice().add(cost.getCurrentCostPriceLL());
					System.out.println("afagagagagag"+ costPrice +" "+qty );
					BigDecimal componentCost = costPrice.multiply(qty);
					costPriceLL = costPriceLL.add(componentCost);
					log.info("CostElement: "+element.getName()
							+ ", Component: "+component.getValue()
							+ ", CostPrice: "+costPrice
							+ ", Qty: " + qty
							+ ", Cost: " + componentCost
							+ " => Total Cost Element: " +  costPriceLL);
				}
				// ByProducts
				if (bomline.isByProduct())
				{
					cost.setCurrentCostPriceLL(Env.ZERO);
				}
				
				
			} // for each cost
		} // for each BOM line
		return costPriceLL;     
	}

//	private Collection<MCost> getCosts(MProduct product, int M_CostElement_ID)
//	{
//		
//		MAcctSchema as = MAcctSchema.get(getCtx(),1000000 );
//		int hasil = 0;
//		CostDimension d = null;
//		 if(DB.getSQLValue(null, "select m_attributesetinstance_id from m_cost where coalesce(m_attributesetinstance_id,0) > 0 and m_product_id = ?",product.getM_Product_ID())>0) {
//			 hasil = DB.getSQLValue(null, "select m_attributesetinstance_id from m_cost where coalesce(m_attributesetinstance_id,0) > 0 and m_product_id = ?",product.getM_Product_ID());
//		 }else {
//			hasil = 0;
//		 }
//			
////		if(M_CostElement_ID == 1000000) {
////			 d = new CostDimension(product, as, 1000000, getAD_Org_ID(), 0, M_CostElement_ID);
////		}else {
//			 d = new CostDimension(product, as, 1000000, getAD_Org_ID(), hasil, M_CostElement_ID);
//		//}
//		return d.toQuery(MCost.class, get_TrxName()).list();
//	}

	private Collection<MProduct> getProducts(int lowLevel)
	{
		List<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer("AD_Client_ID=?")
						.append(" AND ").append(MProduct.COLUMNNAME_LowLevel).append("=?");
		params.add(getAD_Client_ID());
		params.add(lowLevel);
		
		whereClause.append(" AND ").append(MProduct.COLUMNNAME_IsBOM).append("=?");
		params.add(true);
		whereClause.append(" AND ").append(MProduct.COLUMNNAME_M_Product_ID).append("=?");
		params.add(getM_Product_ID());
		
		return new Query(getCtx(),MProduct.Table_Name, whereClause.toString(), get_TrxName())
		.setParameters(params)
		.list();
	}
	
	/**
	 * Reset LowLevel Costs for products with LowLevel=0 (items) 
	 */
	private void resetCostsLLForLLC0()
	{
		List<Object> params = new ArrayList<Object>();
		StringBuffer productWhereClause = new StringBuffer();
		productWhereClause.append("AD_Client_ID=? AND "+MProduct.COLUMNNAME_LowLevel+"=? AND "+MProduct.COLUMNNAME_M_Product_ID+"=?" );
		params.add(getAD_Client_ID());
		params.add(0);
		params.add(getM_Product_ID());		
		final String sql = "UPDATE M_Cost c SET "+MCost.COLUMNNAME_CurrentCostPriceLL+"=0"
		+" WHERE EXISTS (SELECT 1 FROM M_Product p WHERE p.M_Product_ID=c.M_Product_ID"
						+" AND "+productWhereClause+")";
		int no = DB.executeUpdateEx(sql, params.toArray(), get_TrxName());
		log.info("Updated #"+no);
	}
	
	private Collection<MCostElement> m_costElements = null; 
	private Collection<MCostElement> getCostElements()
	{
		
		if (m_costElements == null)
		{
			if(getM_AttributeSetInstance_ID()>0) {
				m_costElements = MCostElement.getByCostingMethod(getCtx(),  "F");	
			}else {
			m_costElements = MCostElement.getByCostingMethod(getCtx(),  "S");
			}
		}
		return m_costElements;
	}
	
	private Collection<MProduct> getProductsResource()
	{
		List<Object> params = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer("AD_Client_ID=?");
		params.add(getAD_Client_ID());

		whereClause.append(" AND (").append(MProduct.COLUMNNAME_ProductType).append("=?");
		params.add(MProduct.PRODUCTTYPE_Item);
		
		whereClause.append(" OR ").append(MProduct.COLUMNNAME_ProductType).append("=?");
		params.add(MProduct.PRODUCTTYPE_Resource);

		whereClause.append(") AND ").append(MProduct.COLUMNNAME_IsBOM).append("=?");
		params.add(true);
		
		whereClause.append(" AND ").append(MProduct.COLUMNNAME_M_Product_ID).append("=?");
		params.add(getM_Product_ID());
			

		Collection<MProduct> products = new Query(getCtx(),MProduct.Table_Name, whereClause.toString(), get_TrxName())
											.setOrderBy(MProduct.COLUMNNAME_LowLevel)
											.setParameters(params)
											.list();    
		return products;
	}

	private RoutingService m_routingService = null;
	
//	public void rollup(MProduct product, MWorkflow workflow)
//	{
//	
//		m_routingService = RoutingServiceFactory.get().getRoutingService(getAD_Client_ID());
//		MWFNode[] nodes = workflow.getNodes(false, getAD_Client_ID());
//		for (MWFNode node : nodes)
//		{
//			DB.executeUpdate("update PP_Order_Node set cost = "+node.getCost()+" where ad_wf_node_id = "+ node.getAD_WF_Node_ID(), null);
//		}
//		                           
//	}
	
	public void rollupupdate(MProduct product, MWorkflow workflow)
	{
		log.info("Workflow: "+workflow);
		m_routingService = RoutingServiceFactory.get().getRoutingService(getAD_Client_ID());
		workflow.setCost(Env.ZERO);
		double Yield = 1;
		int QueuingTime = 0;
		int SetupTime = 0;
		int Duration = 0;
		int WaitingTime = 0;
		int jmltenagakerja = 0;
		int MovingTime = 0;
		int WorkingTime = 0;
		MAcctSchema m_as = MAcctSchema.get(product.getCtx(),1000000 );
		MWFNode[] nodes = workflow.getNodes(false, getAD_Client_ID());
		for (MWFNode node : nodes)
		{
			node.setCost(Env.ZERO);
			if (node.getYield() != 0)
			{	
				Yield = Yield * ((double)node.getYield() / 100);
			}
			// We use node.getDuration() instead of m_routingService.estimateWorkingTime(node) because
			// this will be the minimum duration of this node. So even if the node have defined units/cycle
			// we consider entire duration of the node.
			long nodeDuration = node.getDuration();
			
			QueuingTime += node.getQueuingTime();
			SetupTime += node.getSetupTime();
			Duration += nodeDuration;
			WaitingTime += node.getWaitingTime();
			MovingTime += node.getMovingTime();
			WorkingTime += node.getWorkingTime();
			jmltenagakerja += node.get_ValueAsInt("jmltenagakerja");
		}
		workflow.setCost(Env.ZERO);
		workflow.setYield((int)(Yield * 100));
		workflow.setQueuingTime(QueuingTime);
		workflow.setSetupTime(SetupTime);
		workflow.setDuration(Duration);
		workflow.setWaitingTime(WaitingTime);
		workflow.setMovingTime(MovingTime);
		workflow.setWorkingTime(WorkingTime);
		workflow.set_CustomColumn("jmltenagakerja", jmltenagakerja);

		for (MCostElement element : MCostElement.getByCostingMethod(getCtx(), m_as.getCostingMethod()))
		{
			if (!CostEngine.isActivityControlElement(element))
			{
				continue;
			}
			final CostDimension d = new CostDimension(product, m_as, m_as.getM_CostType_ID(), workflow.getAD_Org_ID(), 0, element.get_ID());
			// MPPCostCollector costcollector = new MPPCostCollector(getCtx(),1000052 , get_TrxName()); 
			final List<MCost> costs = d.toQuery(MCost.class, get_TrxName()).list();
			for (MCost cost : costs)
			{
				final int precision = MAcctSchema.get(Env.getCtx(), cost.getC_AcctSchema_ID()).getCostingPrecision();
				BigDecimal segmentCost = Env.ZERO;
				for (MWFNode node : nodes)
				{
					final CostEngine costEngine = CostEngineFactory.getCostEngine(node.getAD_Client_ID());
					final BigDecimal rate = costEngine.getResourceActualCostRate(null, node.getS_Resource_ID(), d, get_TrxName());
					final BigDecimal baseValue = m_routingService.getResourceBaseValue(node.getS_Resource_ID(), node);
					BigDecimal nodeCost = (baseValue.multiply(rate)).multiply(new BigDecimal(node.get_ValueAsInt("jmltenagakerja")) );
					if (nodeCost.scale() > precision)
					{
						nodeCost = nodeCost.setScale(precision, RoundingMode.HALF_UP);
					}
					segmentCost = segmentCost.add(nodeCost);
					log.info("Element : "+element+", Node="+node
							+", BaseValue="+baseValue+", rate="+rate
							+", nodeCost="+nodeCost+" => Cost="+segmentCost);
					// Update AD_WF_Node.Cost:
					node.setCost(node.getCost().add(nodeCost));
				}
				//
				System.out.println("segmentcost"+segmentCost);
				cost.setCurrentCostPrice(segmentCost);
				cost.saveEx();
				// Update Workflow cost
				workflow.setCost(workflow.getCost().add(segmentCost));
			} // MCost
		} // Cost Elements
		//
		// Save Workflow & Nodes
		for (MWFNode node : nodes)
		{
			node.saveEx();
		}
		workflow.saveEx();
		if(getM_AttributeSetInstance_ID()>0) {
//			BigDecimal crrentcost = DB.getSQLValueBD(get_TrxName(),"select currentcostprice from m_cost where m_product_id = "+getM_Product_ID()+" "
//					+ "and m_attributesetinstance_id = "+getM_AttributeSetInstance_ID() );
			DB.executeUpdate("update m_cost set currentcostprice = "+workflow.getCost()+
					" where m_product_id = "+getM_Product_ID()+" and m_attributesetinstance_id = "+getM_AttributeSetInstance_ID(), get_TrxName());
		}
		log.info("Product: "+product.getName()+" WFCost: " + workflow.getCost());                                
	}

	@Override
	public int customizeValidActions(String docStatus, Object processing, String orderType, String isSOTrx,
			int AD_Table_ID, String[] docAction, String[] options, int index) {
		for(int i=0;i<options.length;i++){
			options[i]=null;
		}
		
		index = 0;
		
		if(docStatus.equals(DocAction.STATUS_Drafted)){
			options[index++] = DocAction.ACTION_Prepare;
			options[index++] = DocAction.ACTION_Void;
			
		}else if(docStatus.equals(DocAction.STATUS_Completed)){
			options[index++] = DocAction.ACTION_Void;
			options[index++] = DocAction.ACTION_ReActivate;
			
		}else if(docStatus.equals(DocAction.STATUS_Invalid)){
			options[index++] = DocAction.ACTION_Void;
			options[index++] = DocAction.ACTION_Complete;
			
		}else if(docStatus.equals(DocAction.STATUS_InProgress)){
			options[index++] = DocAction.ACTION_Complete;
			options[index++] = DocAction.ACTION_Void;
		}
		
		return index;
	}

	
} // MPPOrder
