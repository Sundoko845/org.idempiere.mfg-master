/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.libero.form;


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.Callback;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Combobox;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Tab;
import org.adempiere.webui.component.Tabbox;
import org.adempiere.webui.component.Tabs;
import org.adempiere.webui.component.Textbox;
import org.adempiere.webui.component.WListbox;
import org.adempiere.webui.editor.WDateEditor;
import org.adempiere.webui.editor.WLocatorEditor;
import org.adempiere.webui.editor.WNumberEditor;
import org.adempiere.webui.editor.WPAttributeEditor;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.CustomForm;
import org.adempiere.webui.panel.IFormController;
import org.adempiere.webui.session.SessionManager;
import org.adempiere.webui.window.FDialog;
import org.compiere.minigrid.IDColumn;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridField;
import org.compiere.model.GridFieldVO;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MColumn;
import org.compiere.model.MCost;
import org.compiere.model.MCostElement;
import org.compiere.model.MCostQueue;
import org.compiere.model.MDocType;
import org.compiere.model.MLocator;
import org.compiere.model.MLocatorLookup;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MLot;
import org.compiere.model.MLotCtl;
import org.compiere.model.MProduct;
import org.compiere.model.MProductCategoryAcct;
import org.compiere.model.MTab;
import org.compiere.model.MWindow;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Language;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;
import org.libero.model.MPPCostCollector;
import org.libero.model.MPPOrder;
import org.libero.model.MPPOrderBOMLine;
import org.libero.model.MPPOrderNode;
import org.libero.model.MPPOrderWorkflow;
import org.libero.tables.I_PP_Order_Node;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.North;
import org.zkoss.zul.South;
import org.zkoss.zul.Html;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Row;
import org.zkoss.zul.Space;
import org.zkoss.zul.Tabpanel;
import org.zkoss.zul.Tabpanels;

/**
 *  @author Cristina Ghita, www.arhipac.ro
 *  @author Adi Takacs, www.arhipac.ro
 *  @author victor.perez@e-evolution.com, www.e-evolution.com
 */

public class WOrderReceiptIssue extends OrderReceiptIssue  implements IFormController, EventListener,  
ValueChangeListener,Serializable,WTableModelListener  
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -3451096834043054791L;
	/**	Window No			*/
	private int m_WindowNo = 0;
	private String m_sql;
	private MPPOrder m_PP_order = null;
	
	private Panel Generate = new Panel();
	private Panel PanelBottom = new Panel();
	private Panel mainPanel = new Panel();
	private Panel northPanel = new Panel();
	private Button Process = new Button();
	
	
	
	private Label attributeLabel = new Label();
	private Label orderedQtyLabel = new Label();
	private Label deliveredQtyLabel = new Label();
	private Label openQtyLabel = new Label();
	private Label orderLabel = new Label();
	private Label toDeliverQtyLabel = new Label();
	private Label movementDateLabel = new Label();
	private Label rejectQtyLabel = new Label();
	private Label resourceLabel = new Label();
	private Label DescriptionLabel = new Label();
	
	
	private CustomForm form = new CustomForm();
	private Borderlayout ReceiptIssueOrder = new Borderlayout();
	private Tabbox TabsReceiptsIssue = new Tabbox();
	private Html info = new Html();
	private Grid fieldGrid = GridFactory.newGridLayout();
	private WPAttributeEditor attribute = null;
	
	private Label warehouseLabel = new Label();
	private Label scrapQtyLabel = new Label();
	private Label productLabel = new Label(Msg.translate(Env.getCtx(),"M_Product_ID"));
	private Label uomLabel = new Label(Msg.translate(Env.getCtx(), "C_UOM_ID"));
	private Label uomorderLabel = new Label(Msg.translate(Env.getCtx(), "Altert UOM"));
	private Label locatorLabel = new Label(Msg.translate(Env.getCtx(), "M_Locator_ID"));
	private Label backflushGroupLabel = new Label(Msg.translate(Env.getCtx(), "BackflushGroup"));
	private Label labelcombo = new Label(Msg.translate(Env.getCtx(), "DeliveryRule"));
	private Label QtyBatchsLabel = new Label();
	private Label QtyBatchSizeLabel = new Label();
	
    private Textbox backflushGroup = new Textbox();
	private Textbox description = new Textbox();
	
	private WNumberEditor orderedQtyField = new WNumberEditor("QtyOrdered", false, false, false, DisplayType.Quantity, "QtyOrdered");
	private WNumberEditor deliveredQtyField = new WNumberEditor("QtyDelivered", false, false, false, DisplayType.Quantity, "QtyDelivered");
	private WNumberEditor openQtyField = new WNumberEditor("QtyOpen", false, false, false, DisplayType.Quantity, "QtyOpen");
	private WNumberEditor toDeliverQty = new WNumberEditor("QtyToDeliver", true, false, true, DisplayType.Quantity, "QtyToDeliver");
	private WNumberEditor rejectQty = new WNumberEditor("Qtyreject", false, false, true, DisplayType.Quantity, "QtyReject");
	private WNumberEditor scrapQtyField = new WNumberEditor("Qtyscrap", false, false, true, DisplayType.Quantity, "Qtyscrap");
	private WNumberEditor qtyBatchsField = new WNumberEditor("QtyBatchs", false, false, false, DisplayType.Quantity, "QtyBatchs");
	private WNumberEditor qtyBatchSizeField = new WNumberEditor("QtyBatchSize", false, false, false, DisplayType.Quantity, "QtyBatchSize");
	
	private WSearchEditor orderField = null;
	private WSearchEditor resourceField = null;
	private WSearchEditor warehouseField = null;
	private WSearchEditor productField = null;
	private WSearchEditor uomField = null;
	private WSearchEditor uomorderField = null;
	
	private WListbox issue = ListboxFactory.newDataTable();
	private WDateEditor movementDateField = new WDateEditor("MovementDate", true, false, true,  "MovementDate");	
	
	private WLocatorEditor locatorField = null;
	
	private Combobox pickcombo = new Combobox();
	

	/**
	 *	Initialize Panel
	 *  @param WindowNo window
	 *  @param frame frame
	 */
	
	public WOrderReceiptIssue() 
	{
		Env.setContext(Env.getCtx(), form.getWindowNo(), "IsSOTrx", "Y");
		try 
		{
			//	UI
			fillPicks();
			jbInit();
			//
			dynInit();
			pickcombo.addEventListener(Events.ON_CHANGE, this);

		} 
		catch (Exception e) 
		{
			throw new AdempiereException(e);
		}
	} //	init

	/**
	 *	Fill Picks
	 *		Column_ID from C_Order
	 *	This is only run as part of the windows initialization process
	 *  @throws Exception if Lookups cannot be initialized
	 */
	private void fillPicks() throws Exception 
	{

		Properties ctx = Env.getCtx();
		Language language = Language.getLoginLanguage(); // Base Language
		MLookup orderLookup = MLookupFactory.get(ctx, m_WindowNo,
											MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_PP_Order_ID),
											DisplayType.Search, language, "PP_Order_ID", 0, false,
											"PP_Order.DocStatus = '" + MPPOrder.DOCACTION_Complete + "'");

		orderField = new WSearchEditor(MPPOrder.COLUMNNAME_PP_Order_ID, false, false, true, orderLookup);
		orderField.addValueChangeListener(this);

		MLookup resourceLookup = MLookupFactory.get(ctx, m_WindowNo, 0,
											   MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_S_Resource_ID),
											   DisplayType.TableDir);
		resourceField = new WSearchEditor(MPPOrder.COLUMNNAME_S_Resource_ID, false, false, false, resourceLookup);

		MLookup warehouseLookup = MLookupFactory.get(ctx, m_WindowNo, 0,
												MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_M_Warehouse_ID),
												DisplayType.TableDir);
		warehouseField = new WSearchEditor(MPPOrder.COLUMNNAME_M_Warehouse_ID, false, false, false, warehouseLookup);

		MLookup productLookup = MLookupFactory.get(ctx, m_WindowNo, 0,
											  	   MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_M_Product_ID),
											  	   DisplayType.TableDir);
		productField = new WSearchEditor(MPPOrder.COLUMNNAME_M_Product_ID, false, false, false, productLookup);

		MLookup uomLookup = MLookupFactory.get(ctx, m_WindowNo, 0,
											   MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_C_UOM_ID),
											   DisplayType.TableDir);
		uomField = new WSearchEditor(MPPOrder.COLUMNNAME_C_UOM_ID, false, false, false, uomLookup);

		MLookup uomOrderLookup = MLookupFactory.get(ctx, m_WindowNo, 0,
													MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_C_UOM_ID),
													DisplayType.TableDir);
		uomorderField = new WSearchEditor(MPPOrder.COLUMNNAME_C_UOM_ID, false, false, false, uomOrderLookup);

		MLocatorLookup locatorL = new MLocatorLookup(ctx, m_WindowNo);
		locatorField = new WLocatorEditor(MLocator.COLUMNNAME_M_Locator_ID, true, false, true, locatorL, m_WindowNo);

		
		//  Tab, Window
		int m_Window = MWindow.getWindow_ID("Manufacturing Order");
		GridFieldVO vo = GridFieldVO.createStdField(ctx, m_WindowNo, 0,m_Window, MTab.getTab_ID(m_Window, "Manufacturing Order"), 
													false, false, false);
		vo.AD_Column_ID = MColumn.getColumn_ID(MPPOrder.Table_Name, MPPOrder.COLUMNNAME_M_AttributeSetInstance_ID);
		vo.ColumnName = MPPOrder.COLUMNNAME_M_AttributeSetInstance_ID;
		vo.displayType = DisplayType.PAttribute;  

		GridField field = new GridField(vo);
		// M_AttributeSetInstance_ID
		attribute = new WPAttributeEditor(field.getGridTab(),field);
		attribute.setValue(0);
		// 4Layers - Further init
		scrapQtyField.setValue(Env.ZERO);
		rejectQty.setValue(Env.ZERO);
		// 4Layers - end
		pickcombo.appendItem(Msg.translate(Env.getCtx(),"IsBackflush"), 1);
		pickcombo.appendItem(Msg.translate(Env.getCtx(),"OnlyIssue"),2);
		pickcombo.appendItem(Msg.translate(Env.getCtx(),"OnlyReceipt"),3);
		pickcombo.addEventListener(Events.ON_CHANGE, this);
		Process.addActionListener(this);
		toDeliverQty.addValueChangeListener(this);
		scrapQtyField.addValueChangeListener(this);
	} //	fillPicks
	
	/**
	 *  Static Init.
	 *  Places static visual elements into the window.
	 *  This is only run as part of the windows initialization process
	 *  <pre>
	 *  mainPanel
	 *      northPanel
	 *      centerPanel
	 *          xMatched
	 *          xPanel
	 *          xMathedTo
	 *      southPanel
	 *  </pre>
	 *  @throws Exception
	 */
	private void jbInit() throws Exception
	{
		Center center = new Center();
		South south = new South();
		North north = new North();
		form.appendChild(mainPanel);	 
		mainPanel.appendChild(TabsReceiptsIssue);
		mainPanel.setStyle("width: 100%; height: 100%; padding: 0; margin: 0");		
		ReceiptIssueOrder.setWidth("100%");
		ReceiptIssueOrder.setHeight("99%");
		ReceiptIssueOrder.appendChild(north);
		description.setWidth("100%");
		north.appendChild(northPanel);
		northPanel.appendChild(fieldGrid);
		orderLabel.setText(Msg.translate(Env.getCtx(), "PP_Order_ID"));
		Rows tmpRows = fieldGrid.newRows();
		
		// 1st
		Row tmpRow = tmpRows.newRow();
		
		tmpRow.appendChild(orderLabel.rightAlign());
		tmpRow.appendChild(orderField.getComponent());		
		resourceLabel.setText(Msg.translate(Env.getCtx(), "S_Resource_ID"));
		tmpRow.appendChild(resourceLabel.rightAlign());
		tmpRow.appendChild(resourceField.getComponent());		
		warehouseLabel.setText(Msg.translate(Env.getCtx(), "M_Warehouse_ID"));
		tmpRow.appendChild(warehouseLabel.rightAlign());
		tmpRow.appendChild(warehouseField.getComponent());
		
		// Product 2nd
		tmpRow = tmpRows.newRow();
		
		tmpRow.appendChild(productLabel.rightAlign());
		tmpRow.appendChild(productField.getComponent());		
		tmpRow.appendChild(uomLabel.rightAlign());
		tmpRow.appendChild(uomField.getComponent());	
		tmpRow.appendChild(uomorderLabel.rightAlign());
		tmpRow.appendChild(uomorderField.getComponent());
		
		tmpRow = tmpRows.newRow();
		
		orderedQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyOrdered"));
		tmpRow.appendChild(orderedQtyLabel.rightAlign());
		tmpRow.appendChild(orderedQtyField.getComponent());
		deliveredQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyDelivered"));
		tmpRow.appendChild(deliveredQtyLabel.rightAlign());
		tmpRow.appendChild(deliveredQtyField.getComponent());	
		openQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyOpen"));
		tmpRow.appendChild(openQtyLabel.rightAlign());
		tmpRow.appendChild(openQtyField.getComponent());
		//3rd
		tmpRow = tmpRows.newRow();
		
		tmpRow.appendChild(productLabel.rightAlign());
		tmpRow.appendChild(productField.getComponent());		
		tmpRow.appendChild(uomLabel.rightAlign());
		tmpRow.appendChild(uomField.getComponent());	
		tmpRow.appendChild(uomorderLabel.rightAlign());
		tmpRow.appendChild(uomorderField.getComponent());
		//4th
		tmpRow = tmpRows.newRow();
		
		QtyBatchsLabel.setText(Msg.translate(Env.getCtx(), "QtyBatchs"));
		tmpRow.appendChild(QtyBatchsLabel.rightAlign());
		tmpRow.appendChild(qtyBatchsField.getComponent());
		QtyBatchSizeLabel.setText(Msg.translate(Env.getCtx(), "QtyBatchSize"));
		tmpRow.appendChild(QtyBatchSizeLabel.rightAlign());
		tmpRow.appendChild(qtyBatchSizeField.getComponent());	
		openQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyOpen"));
		tmpRow.appendChild(openQtyLabel.rightAlign());
		tmpRow.appendChild(openQtyField.getComponent());
		//5th
		tmpRow = tmpRows.newRow();
		
		tmpRow.appendChild(labelcombo.rightAlign());
		tmpRow.appendChild(pickcombo);
		tmpRow.appendChild(backflushGroupLabel.rightAlign());
		tmpRow.appendChild(backflushGroup);
		tmpRow.appendChild(new Space());
		tmpRow.appendChild(new Space());
		//6th
		tmpRow = tmpRows.newRow();
		
		toDeliverQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyToDeliver"));
		tmpRow.appendChild(toDeliverQtyLabel.rightAlign());
		tmpRow.appendChild(toDeliverQty.getComponent());
		scrapQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyScrap"));
		tmpRow.appendChild(scrapQtyLabel.rightAlign());
		tmpRow.appendChild(scrapQtyField.getComponent());
		rejectQtyLabel.setText(Msg.translate(Env.getCtx(), "QtyReject"));
		tmpRow.appendChild(rejectQtyLabel.rightAlign());
		tmpRow.appendChild(rejectQty.getComponent());
		//7th
		tmpRow = tmpRows.newRow();
		
		movementDateLabel.setText(Msg.translate(Env.getCtx(), "MovementDate"));
		tmpRow.appendChild(movementDateLabel.rightAlign());
		tmpRow.appendChild(movementDateField.getComponent());
		locatorLabel.setText(Msg.translate(Env.getCtx(), "M_Locator_ID"));
		tmpRow.appendChild(locatorLabel.rightAlign());
		tmpRow.appendChild(locatorField.getComponent());
		attributeLabel.setText(Msg.translate(Env.getCtx(), "M_AttributeSetInstance_ID"));
		tmpRow.appendChild(attributeLabel.rightAlign());
		tmpRow.appendChild(attribute.getComponent());
		ReceiptIssueOrder.appendChild(center);
		center.appendChild(issue);
		ReceiptIssueOrder.appendChild(south);						
		south.appendChild(PanelBottom);	
		
		tmpRow = tmpRows.newRow();
		
		DescriptionLabel.setText(Msg.translate(Env.getCtx(), "Description"));
		tmpRow.appendChild(DescriptionLabel.rightAlign());
		tmpRow.appendChild(description);
		
		Process.setLabel(Msg.translate(Env.getCtx(), "OK"));
		PanelBottom.appendChild(Process);
		PanelBottom.setWidth("100%");
		PanelBottom.setStyle("text-align:center");
		
		Tabs tabs = new Tabs(); 
		Tab tab1 =new Tab();
		Tab tab2 =new Tab();
		tab1.setLabel(Msg.translate(Env.getCtx(), "IsShipConfirm"));
		tab2.setLabel(Msg.translate(Env.getCtx(), "Generate"));
		tabs.appendChild(tab1);
		tabs.appendChild(tab2);
	
		TabsReceiptsIssue.appendChild(tabs);
		Tabpanels tabps = new Tabpanels();
		Tabpanel tabp1 = new Tabpanel();
		Tabpanel tabp2 = new Tabpanel();
		TabsReceiptsIssue.appendChild(tabps);
		TabsReceiptsIssue.setWidth("100%");
		TabsReceiptsIssue.setHeight("100%");
		tabps.appendChild(tabp1);
		tabps.appendChild(tabp2);
		tabp1.appendChild(ReceiptIssueOrder);
		tabp1.setWidth("100%");
		tabp1.setHeight("100%");
		tabp2.appendChild(Generate);
		tabp2.setWidth("100%");
		tabp2.setHeight("100%");
		Generate.appendChild(info);
		Generate.setVisible(true);
		info.setVisible(true);
		TabsReceiptsIssue.addEventListener(Events.ON_CHANGE, this);
	} //  jbInit    

	/**
	 *  Dynamic Init.
	 *  Table Layout, Visual, Listener
	 *  This is only run as part of the windows initialization process
	 */
	public void dynInit()
	{
		disableToDeliver();
		prepareTable(issue);
		issue.autoSize();
		issue.getModel().addTableModelListener(this);
		issue.setRowCount(0);	
	} //  dynInit
	
	public void prepareTable(IMiniTable miniTable)
	{
		configureMiniTable(miniTable);
	}
	
	/**
	 * Called when events occur in the window
	 */
	
	public void onEvent(Event e) throws Exception 
	{
		if (e.getName().equals(Events.ON_CANCEL))
		{
			dispose();
			return;
		}

		if (e.getTarget().equals(Process))
		{
			if (getMovementDate() == null)
			{
					Messagebox.show( Msg.getMsg(Env.getCtx(), "NoDate"), "Info",Messagebox.OK, Messagebox.INFORMATION);
				return;
			}

			if ((isOnlyReceipt() || isBackflush()) && getM_Locator_ID() <= 0) 
			{
				Messagebox.show(Msg.getMsg(Env.getCtx(), "NoLocator"),"Info", Messagebox.OK, Messagebox.INFORMATION);
				return;
			}
		//  Switch Tabs
			TabsReceiptsIssue.setSelectedIndex(1);
			
			generateSummaryTable();

			int result = -1;			
			
			result = Messagebox.show(Msg.getMsg(Env.getCtx(), "Update"),"",Messagebox.OK|Messagebox.CANCEL,Messagebox.QUESTION);
			 			
			if ( result == 1)
			{				
				final boolean isCloseDocument = (Messagebox.show(Msg.parseTranslation(Env.getCtx(),"@IsCloseDocument@ : &&&&"+  getPP_Order().getDocumentNo()),"",Messagebox.OK|Messagebox.CANCEL,Messagebox.QUESTION) == Messagebox.OK);

				if (cmd_process(isCloseDocument, issue))
				{
					dispose();
					return;
				}
				//Clients.showBusy(TabsReceiptsIssue, null);
			}
			TabsReceiptsIssue.setSelectedIndex(0);
		}	

		if (e.getTarget().equals(pickcombo))
		{
			if (isOnlyReceipt())
			{
				enableToDeliver();
				locatorLabel.setVisible(true);
				locatorField.setVisible(true);
				attribute.setVisible(true);
				attributeLabel.setVisible(true);
				issue.setVisible(false);
			}
			else if (isOnlyIssue())
			{
				disableToDeliver();
				locatorLabel.setVisible(false);
				locatorField.setVisible(false);
				attribute.setVisible(false);
				attributeLabel.setVisible(false);
				issue.setVisible(true);
				executeQuery();
			}
			else if (isBackflush())
			{
				enableToDeliver();
				locatorLabel.setVisible(true);
				locatorField.setVisible(true);
				attribute.setVisible(true);
				attributeLabel.setVisible(true);
				issue.setVisible(true);
				executeQuery();
			}
			setToDeliverQty(getOpenQty()); //reset toDeliverQty to openQty
		}
	}
	
	public void enableToDeliver()
	{
		setToDeliver(true);
	}

	public void disableToDeliver()
	{
		setToDeliver(false);
	}
	
	private void setToDeliver(Boolean state)
	{
		toDeliverQty.getComponent().setEnabled(state); 
		scrapQtyLabel.setVisible(state);
		scrapQtyField.setVisible(state);
		rejectQtyLabel.setVisible(state);
		rejectQty.setVisible(state);
	}

	/**
	 *  Queries for and fills the table in the lower half of the screen
	 *  This is only run if isBackflush() or isOnlyIssue
	 */
	public void executeQuery()
	{
		m_sql = m_sql + " ORDER BY obl."+MPPOrderBOMLine.COLUMNNAME_Line;
		//  reset table
		issue.clearTable();
		executeQuery(issue);
		issue.repaint();
	} //  executeQuery

	

	
	
	public void valueChange(ValueChangeEvent e)
	{
		String name = e.getPropertyName();
		Object value = e.getNewValue();
		
		if (value == null)
			return;

		//  PP_Order_ID
		if (name.equals("PP_Order_ID"))
		{
			
			orderField.setValue(value);

			MPPOrder pp_order = getPP_Order();
			setPP_Order_ID(pp_order.getPP_Order_ID());
			if (pp_order != null)
			{
				setS_Resource_ID(pp_order.getS_Resource_ID());
				setM_Warehouse_ID(pp_order.getM_Warehouse_ID());
				setDeliveredQty(pp_order.getQtyDelivered());
				setOrderedQty(pp_order.getQtyOrdered());
				//m_PP_order.getQtyOrdered().subtract(m_PP_order.getQtyDelivered());
				setQtyBatchs(pp_order.getQtyBatchs());
				setQtyBatchSize(pp_order.getQtyBatchSize());
				setOpenQty(pp_order.getQtyOrdered().subtract(pp_order.getQtyDelivered()));
				setToDeliverQty(getOpenQty());
				setM_Product_ID(pp_order.getM_Product_ID());
				MProduct m_product = MProduct.get(Env.getCtx(), pp_order.getM_Product_ID());
				setC_UOM_ID(m_product.getC_UOM_ID());
				setOrder_UOM_ID(pp_order.getC_UOM_ID());
				//Default ASI defined from the Parent BOM Order
				//setM_AttributeSetInstance_ID(pp_order.getM_Product().getM_AttributeSetInstance_ID());
				setM_AttributeSetInstance_ID(pp_order.getM_AttributeSetInstance_ID());
				String docBaseType = MDocType.get(pp_order.getCtx(), pp_order.getC_DocType_ID()).getDocBaseType();
				if(docBaseType.equals(MDocType.DOCBASETYPE_MaintenanceOrder)) {
					pickcombo.setSelectedIndex(1); 
				}else {
					pickcombo.setSelectedIndex(0); 
				}
				 //default to first entry - isBackflush
				Event ev = new Event(Events.ON_CHANGE,pickcombo);
				try {
					onEvent(ev);
				} catch (Exception e1) {					
					throw new AdempiereException(e1);
				}
			}
		} //  PP_Order_ID
		
		if (name.equals(toDeliverQty.getColumnName()) || name.equals(scrapQtyField.getColumnName()))
		{
			if (getPP_Order_ID() > 0 && isBackflush())
			{
				executeQuery();
			}
		}
	}
	
	
	public void showMessage(String message, boolean error)
	{
		try
		{
			if(!error)
				Messagebox.show(message, "Info",Messagebox.OK, Messagebox.INFORMATION);
			else
				Messagebox.show(message,"",Messagebox.OK,Messagebox.ERROR);
		}
		catch(Exception e)
		{
			
		}
	}

	

	/**
	 *  Generate Summary of Issue/Receipt.
	 */
	private void generateSummaryTable() 
	{
		info.setContent(generateSummaryTable(issue, productField.getDisplay(), 
				uomField.getDisplay(), 
				attribute.getDisplay(), 
				toDeliverQty.getDisplay(), 
				deliveredQtyField.getDisplay(),
				scrapQtyField.getDisplay(),
				isBackflush(), isOnlyIssue(), isOnlyReceipt()		
		));
		
	} //  generateInvoices_complete


	/**
	 * Determines whether the Delivery Rule is set to 'OnlyReciept'
	 * @return	
	 */
	protected boolean isOnlyReceipt() 
	{
		super.setIsOnlyReceipt(pickcombo.getText().equals("OnlyReceipt"));
		return super.isOnlyReceipt();
	}
	
	/**
	 * Determines whether the Delivery Rule is set to 'OnlyIssue'
	 * @return	
	 */
	protected boolean isOnlyIssue() 
	{
		super.setIsOnlyIssue(pickcombo.getText().equals("OnlyIssue"));
		return super.isOnlyIssue();
	}

	/**
	 * Determines whether the Delivery Rule is set to 'isBackflush'
	 * @return	
	 */
	protected boolean isBackflush()
	{
		super.setIsBackflush(pickcombo.getText().equals("IsBackflush"));
		return super.isBackflush();
	}

	protected Timestamp getMovementDate()
	{
		return (Timestamp) movementDateField.getValue();
	}

	
	protected BigDecimal getOrderedQty()
	{
		BigDecimal bd = (BigDecimal) orderedQtyField.getValue();
		return bd != null ? bd : Env.ZERO;
	}

	protected void setOrderedQty(BigDecimal qty)
	{
		this.orderedQtyField.setValue(qty);
	}

	protected BigDecimal getDeliveredQty()
	{
		BigDecimal bd = (BigDecimal) deliveredQtyField.getValue();
		return bd != null ? bd : Env.ZERO;
	}
	
	protected void setDeliveredQty(BigDecimal qty)
	{
		deliveredQtyField.setValue(qty);
	}

	protected BigDecimal getToDeliverQty()
	{
		BigDecimal bd = (BigDecimal) toDeliverQty.getValue();
		return bd != null ? bd : Env.ZERO;
	}
	
	protected void setToDeliverQty(BigDecimal qty)
	{
		toDeliverQty.setValue(qty);
	}

	protected BigDecimal getScrapQty()
	{
		BigDecimal bd = (BigDecimal) scrapQtyField.getValue();
		return bd != null ? bd : Env.ZERO;
	}

	protected BigDecimal getRejectQty() 
	{
		BigDecimal bd = (BigDecimal) rejectQty.getValue();
		return bd != null ? bd : Env.ZERO;
	}

	protected BigDecimal getOpenQty()
	{
		BigDecimal bd = (BigDecimal) openQtyField.getValue();
		return bd != null ? bd : Env.ZERO;
	}
	protected void setOpenQty(BigDecimal qty)
	{
		openQtyField.setValue(qty);
	}
	
	protected BigDecimal getQtyBatchs()
	{
		BigDecimal bd = (BigDecimal) qtyBatchsField.getValue();
		return bd != null ? bd : Env.ZERO;
	}
	protected void setQtyBatchs(BigDecimal qty)
	{
		qtyBatchsField.setValue(qty);
	}
	
	protected BigDecimal getQtyBatchSize()
	{
		BigDecimal bd = (BigDecimal) qtyBatchSizeField.getValue();
		return bd != null ? bd : Env.ZERO;
	}
	
	protected void setQtyBatchSize(BigDecimal qty)
	{
		qtyBatchSizeField.setValue(qty);
	}

	protected int getM_AttributeSetInstance_ID()
	{
		Integer ii = (Integer) attribute.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setM_AttributeSetInstance_ID(int M_AttributeSetInstance_ID)
	{
		attribute.setValue(M_AttributeSetInstance_ID);
	}

	protected int getM_Locator_ID()
	{
		Integer ii = (Integer) locatorField.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setM_Locator_ID(int M_Locator_ID)
	{
		locatorField.setValue(M_Locator_ID);
	}

	protected int getPP_Order_ID()
	{
		Integer ii = (Integer) orderField.getValue();
		return ii != null ? ii.intValue() : 0;
	}	
	
	protected MPPOrder getPP_Order()
	{
		int id = getPP_Order_ID();
		if (id <= 0)
		{
			m_PP_order = null;
			return null;
		}
		if (m_PP_order == null || m_PP_order.get_ID() != id)
		{
			
			m_PP_order = new MPPOrder(Env.getCtx(), id, null);
		}
		return m_PP_order;
	}

	protected int getS_Resource_ID()
	{
		Integer ii = (Integer) resourceField.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setS_Resource_ID(int S_Resource_ID)
	{
		resourceField.setValue(S_Resource_ID);
	}
	
	protected int getM_Warehouse_ID()
	{
		Integer ii = (Integer) warehouseField.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setM_Warehouse_ID(int M_Warehouse_ID)
	{
		warehouseField.setValue(M_Warehouse_ID);
	}
	
	protected int getM_Product_ID()
	{
		Integer ii = (Integer) productField.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setM_Product_ID(int M_Product_ID)
	{
		productField.setValue(M_Product_ID);
		Env.setContext(Env.getCtx(), m_WindowNo, "M_Product_ID", M_Product_ID);
	}
	
	protected int getC_UOM_ID()
	{
		Integer ii = (Integer) uomField.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setC_UOM_ID(int C_UOM_ID)
	{
		uomField.setValue(C_UOM_ID);
	}
	
	protected int getOrder_UOM_ID()
	{
		Integer ii = (Integer) uomorderField.getValue();
		return ii != null ? ii.intValue() : 0;
	}
	
	protected void setOrder_UOM_ID(int C_UOM_ID)
	{
		uomorderField.setValue(C_UOM_ID);
	}
	
	

	public void dispose()
	{
		SessionManager.getAppDesktop().closeActiveWindow();
	}	//	dispose

	
	public ADForm getForm() 
	{
		return form;
	}

	
	public void tableChanged(WTableModelEvent event) 
	{
		//nothing
	}
	
	private BigDecimal getValueBigDecimal(IMiniTable issue, int row, int col) {
		BigDecimal bd = (BigDecimal) issue.getValueAt(row, col);
		return bd == null ? Env.ZERO : bd;
	}
	
	BigDecimal costfifo = BigDecimal.ZERO;
	BigDecimal costfifoprdmaster = BigDecimal.ZERO;
	public boolean cmd_process(final boolean isCloseDocument, final IMiniTable issue)
	{

		if (isOnlyReceipt() || isBackflush())
		{
			if (getM_Locator_ID() <= 0)
			{
				//JOptionPane.showMessageDialog(null, Msg.getMsg(Env.getCtx(),"NoLocator"), "Info", JOptionPane.INFORMATION_MESSAGE);
				showMessage( Msg.getMsg(Env.getCtx(),"NoLocator"), false);
			}
		}
		if (getPP_Order() == null || getMovementDate() == null)
		{
			return false;
		}		
		if(description.getValue().equals("")) {
			showMessage( Msg.getMsg(Env.getCtx(),"NoDescription"), false);
			return false;
		}
		
//		if(getM_AttributeSetInstance_ID()>0) {
//			System.out.println(getM_Product_ID() +"-"+ getM_AttributeSetInstance_ID() );
//			//if(DB.getSQLValueBD(null, "select coalesce(cumulatedamt,0) from m_cost where m_costelement_id = 1000002 and m_product_id = " +getM_Product_ID()+" and m_attributesetinstance_id = "+ getM_AttributeSetInstance_ID()).compareTo(BigDecimal.ZERO)>0 || DB.getSQLValueBD(null, "select  coalesce(cumulatedamt,0) from m_cost where m_costelement_id = 1000002 and m_product_id = " +getM_Product_ID()+" and m_attributesetinstance_id = "+ getM_AttributeSetInstance_ID()).compareTo(BigDecimal.ZERO)<0 ) {
////				MAttributeSetInstance attributeSetInstance = new MAttributeSetInstance(getPP_Order().getCtx(), 0, getPP_Order().get_TrxName());
////				attributeSetInstance.setM_AttributeSet_ID(getPP_Order().getM_Product().getM_AttributeSet_ID());
////				attributeSetInstance.setM_Lot_ID(getPP_Order().getM_AttributeSetInstance().getM_Lot_ID());
////				attributeSetInstance.setAD_Org_ID(getPP_Order().getAD_Org_ID());
////				attributeSetInstance.setDescription(getPP_Order().getM_AttributeSetInstance().getDescription());
////				attributeSetInstance.setLot(getPP_Order().getLot());
////				if(attributeSetInstance.save()) {
////					setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
////					MPPOrder mppOrder = new MPPOrder(getPP_Order().getCtx(), getPP_Order_ID(), getPP_Order().get_TrxName());
////					mppOrder.setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
//			MAcctSchema acctSchema = new MAcctSchema(getPP_Order().getCtx(),1000000 , getPP_Order().get_TrxName());	
//			MCostElement
//			        MCost costprd = new MCost(getPP_Order().getM_Product(), getM_AttributeSetInstance_ID(),acctSchema, 0, i)
//					if(mppOrder.save()) {
//						costPPorder(mppOrder);
//						costQueuePPorder(mppOrder);
//					}
//			//	}	
//		//	}
//		}
		
		
		
		for (int i = 0; i < issue.getRowCount(); i++) {
			
			IDColumn id = (IDColumn) issue.getValueAt(i, 0);
			KeyNamePair key = new KeyNamePair(id.getRecord_ID(),
					id.isSelected() ? "Y" : "N");
			
			boolean isSelected = key.getName().equals("Y");
			if(isSelected) {
				
				if(getValueBigDecimal(issue, i, 10).compareTo(getValueBigDecimal(issue, i, 8))<0 && getValueBigDecimal(issue, i, 8).compareTo(BigDecimal.ZERO)>0) {
					showMessage( Msg.getMsg(Env.getCtx(),issue.getValueAt(i, 2)+" Qty Onhand "+getValueBigDecimal(issue, i, 10) ), false);
					return false;
				}else {
				
				if(getM_AttributeSetInstance_ID()>0) {
					
					KeyNamePair productkey = (KeyNamePair) issue.getValueAt(i, 3);
					KeyNamePair productasi = (KeyNamePair) issue.getValueAt(i, 5);
					MAcctSchema acctSchema = new MAcctSchema(getPP_Order().getCtx(),1000000 , getPP_Order().get_TrxName());
					MProductCategoryAcct mpc = MProductCategoryAcct.get(getPP_Order().getM_Product().getM_Product_Category_ID(), acctSchema.getC_AcctSchema_ID());
					MCostElement costelemnt = MCostElement.getMaterialCostElement(getPP_Order().getCtx(), mpc.getCostingMethod());
					
					costfifoprdmaster = (DB.getSQLValueBD(null, "select currentcostprice from m_cost where m_costelement_id = "+costelemnt.getM_CostElement_ID()+" and m_product_id = " +getM_Product_ID()+" and m_attributesetinstance_id = "+ getM_AttributeSetInstance_ID()));
					
					//int attribute =  Integer.parseInt(issue.getValueAt(i, 5).toString()) ;
					costfifo = (getValueBigDecimal(issue, i, 8).multiply(DB.getSQLValueBD(null, "select currentcostprice from m_cost where m_costelement_id = "+costelemnt.getM_CostElement_ID()+" and m_product_id = " +productkey.getKey()+" and m_attributesetinstance_id = "+ productasi.getKey()))).divide(getToDeliverQty(), RoundingMode.HALF_EVEN);
					DB.executeUpdate("update m_cost set currentcostprice = "+costfifo.add(costfifoprdmaster)+" where  m_costelement_id = "+costelemnt.getM_CostElement_ID()+" and m_product_id = " +getM_Product_ID()+" and m_attributesetinstance_id = "+ getM_AttributeSetInstance_ID(), null);
					
					//System.out.println("ssd"+productkey.getKey()+" "+productasi.getKey()+" "+getValueBigDecimal(issue, i, 8).multiply(DB.getSQLValueBD(null, "select currentcostprice from m_cost where m_costelement_id = 1000002 and m_product_id = " +productkey.getKey()+" and m_attributesetinstance_id = "+ productasi.getKey())) );
				}
				
				}
			}

		}
		
		try
		{
			Trx.run(new TrxRunnable() {
				public void run(String trxName)
				{
					MPPOrder order = new MPPOrder(Env.getCtx(), getPP_Order_ID(), trxName);
					if (isBackflush() || isOnlyIssue()) 
					{
						createIssue(order, issue);
					}
					if (isOnlyReceipt() || isBackflush()) 
					{
						MPPOrder.createReceipt(order,
								getMovementDate(),
								getDeliveredQty(),
								getToDeliverQty(), 
								getScrapQty(),
								getRejectQty(),
								getM_Locator_ID(),
								getM_AttributeSetInstance_ID()
						);
						if (isCloseDocument && getToDeliverQty().compareTo(getOrderedQty().subtract(getDeliveredQty())) == 0)
						{
							order.setDateFinish(getMovementDate());
							order.closeIt();
							order.saveEx();
						}else {
							MPPOrderWorkflow orderWorkflow = order.getMPPOrderWorkflow();
							if (orderWorkflow != null)
							{
								for (MPPOrderNode node : orderWorkflow.getNodes(true))
								{
									autoReportActivities(node, order, getToDeliverQty());
									createUsageVarianceCost(node, order, getToDeliverQty());
									//createUsageVariance(node,order);
									//order.createUsageVarianceCost(node);
								}
							}
						}
					}
				}});
		}
		catch (Exception e)
		{
			e.printStackTrace();
			showMessage(e.getLocalizedMessage(), true);
			return false;
		}
		finally
		{
			m_PP_order = null;
		}

		return true;
	}
//	
//	public void costPPorder (MPPOrder pporder){
//		MAcctSchema acctSchema = new MAcctSchema(pporder.getCtx(),1000000 , pporder.get_TrxName());	
//				if(pporder.getM_AttributeSetInstance_ID()>0) {					
//						  MProduct product = new MProduct(pporder.getCtx(), pporder.getM_Product_ID(), pporder.get_TrxName());
//						  MCost cost = new MCost(product, pporder.getM_AttributeSetInstance_ID(), acctSchema, 0, 1000002);
//						  cost.setCurrentCostPrice(DB.getSQLValueBD(pporder.get_TrxName(), "select sum(currentcostprice) from m_cost where m_costelement_id != 1000002 and m_product_id =  "+product.getM_Product_ID()));
//						  cost.save();
//				}	 
//		  
//	}
//	
//	public  void costQueuePPorder (MPPOrder mppOrder){
//		MAcctSchema acctSchema = new MAcctSchema(mppOrder.getCtx(),1000000 , mppOrder.get_TrxName());
//		
//				if(mppOrder.getM_AttributeSetInstance_ID()>0) {
//					
//					      MProduct product = new MProduct(mppOrder.getCtx(), mppOrder.getM_Product_ID(), mppOrder.get_TrxName());
//						  MCostQueue costQueue = new MCostQueue(product, mppOrder.getM_AttributeSetInstance_ID(), acctSchema, 0, 1000002, mppOrder.get_TrxName());
//						  costQueue.setCurrentCostPrice(BigDecimal.ZERO);
//						  costQueue.save();
//							
//					
//				}
//			
//			
//		  
//		
//	}
	
//	private void GenerateCodeAttributepporder(MPPOrder mppOrder) {
//		MLot lot = new MLot(mppOrder.getCtx(), 0, mppOrder.get_TrxName());
//		MLotCtl lotCtl = new MLotCtl(mppOrder.getCtx(), DB.getSQLValue(mppOrder.get_TrxName(), "select M_LotCtl_ID from M_AttributeSet ma left join m_product mp on mp.M_AttributeSet_ID = ma.M_AttributeSet_ID where mp.m_product_id = "+mppOrder.getM_Product_ID()), mppOrder.get_TrxName());
//		lot.setM_Product_ID(mppOrder.getM_Product_ID());
//		lot.setM_LotCtl_ID(lotCtl.getM_LotCtl_ID());
//		lot.setName(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
//		if(lot.save()) {
//			MAttributeSetInstance attributeSetInstance = new MAttributeSetInstance(mppOrder.getCtx(), 0, mppOrder.get_TrxName());
//			attributeSetInstance.setM_AttributeSet_ID(mppOrder.getM_Product().getM_AttributeSet_ID());
//			attributeSetInstance.setM_Lot_ID(lot.getM_Lot_ID());
//			attributeSetInstance.setAD_Org_ID(mppOrder.getAD_Org_ID());
//			attributeSetInstance.setDescription(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
//			attributeSetInstance.setLot(lotCtl.getPrefix()+""+lotCtl.getCurrentNext());
//			if(attributeSetInstance.save(mppOrder.get_TrxName())) {
//				
//				lotCtl.setCurrentNext(lotCtl.getCurrentNext()+lotCtl.getIncrementNo());
//				lotCtl.save();
//				
//				MPPOrder Line = new MPPOrder(mppOrder.getCtx(), mppOrder.getPP_Order_ID(), mppOrder.get_TrxName());
//				Line.setM_AttributeSetInstance_ID(attributeSetInstance.getM_AttributeSetInstance_ID());
//				if(Line.save()) {
//					costPPorder(Line);
//					costQueuePPorder(Line);
//				}
//			}
//		}
//		
//	
//}
	
//	public void createUsageVariance(I_PP_Order_Node orderNode,MPPOrder order)
//	{
//		final Timestamp movementDate = order.getUpdated();
//		final MPPOrderNode node = (MPPOrderNode)orderNode;
//		//
//		final BigDecimal setupTimeReal = BigDecimal.valueOf(node.getSetupTimeReal());
//		final BigDecimal durationReal = BigDecimal.valueOf(node.getDurationReal());
//		
//		
//		if (setupTimeReal.signum() == 0 && durationReal.signum() == 0)
//		{
//			// nothing reported on this activity => it's not a variance, this will be auto-reported on close
//			return;
//		}
//		//
//		final BigDecimal setupTimeVariancePrev = node.getSetupTimeUsageVariance();
//		final BigDecimal durationVariancePrev = node.getDurationUsageVariance();
//		final BigDecimal setupTimeRequired = BigDecimal.valueOf(node.getSetupTimeRequired());
//		final BigDecimal durationRequired = BigDecimal.valueOf(node.getDurationRequired());
//		final BigDecimal qtyOpen = node.getQtyToDeliver();
//		//
//		final BigDecimal setupTimeVariance = setupTimeRequired.subtract(setupTimeReal).subtract(setupTimeVariancePrev);
//		final BigDecimal durationVariance = durationRequired.subtract(durationReal).subtract(durationVariancePrev);
//		//
//		if (qtyOpen.signum() == 0 && setupTimeVariance.signum() == 0 && durationVariance.signum() == 0)
//		{
//			return;
//		}				
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
//				Env.ZERO,Env.ZERO
//		);
//	}
	
	public void autoReportActivities(I_PP_Order_Node orderNode,MPPOrder order,BigDecimal Qty)
	{
		final MPPOrderNode node = (MPPOrderNode)orderNode;
		
		final BigDecimal setupTimeReal = BigDecimal.valueOf(node.getSetupTimeReal());
		final BigDecimal durationReal = BigDecimal.valueOf(node.getDurationReal());
		
		final BigDecimal setupTimeVariancePrev = node.getSetupTimeUsageVariance();
		final BigDecimal durationVariancePrev = node.getDurationUsageVariance();
		final BigDecimal setupTimeRequired = BigDecimal.valueOf(node.getSetupTimeRequired());
		final BigDecimal durationRequired = BigDecimal.valueOf(node.getDurationRequired());
		
		final BigDecimal setupTimeVariance = setupTimeRequired.subtract(setupTimeReal).subtract(setupTimeVariancePrev);
		final BigDecimal durationVariance = new BigDecimal(node.getDuration()).multiply(Qty);
		
	//	BigDecimal costvarian = node.getPP_Order_Workflow().getCost().subtract(DB.getSQLValueBD(null, "select sum(currentcostprice) from m_cost where M_CostElement_ID != 1000000 and m_product_id = " +  node.getPP_Order_Workflow().getPP_Order().getM_Product_ID()));
			
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
						Qty,
						Env.ZERO, 
						Env.ZERO, 
						setupTimeVariance.intValueExact(), 
						durationVariance,
						Env.ZERO,node.getQtyRequired().subtract(Qty.add(node.getQtyDelivered())),Env.ZERO);						
	};
	
	public void createUsageVarianceCost(I_PP_Order_Node orderNode,MPPOrder order,BigDecimal Qty)
	{
		
		
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
		final BigDecimal qtyOpen = node.getQtyToDeliver().subtract(new BigDecimal(node.get_ValueAsString("qtyreserved")));
		//
		final BigDecimal setupTimeVariance = setupTimeRequired.subtract(setupTimeReal).subtract(setupTimeVariancePrev);
		final BigDecimal durationVariance = durationRequired.subtract(durationReal).subtract(durationVariancePrev);
		//
//		if (qtyOpen.signum() == 0 && setupTimeVariance.signum() == 0 && durationVariance.signum() == 0)
//		{
//			return;
//		}
//		
//		if(node.getPP_Order_Workflow().getCost().compareTo(node.getPP_Order_Workflow().getAD_Workflow().getCost())==0) {
//			return;
//		}else {
			BigDecimal costvarian = node.getPP_Order_Workflow().getCost().subtract(DB.getSQLValueBD(null, "select sum(currentcostprice) from m_cost where M_CostElement_ID != 1000000 and m_product_id = " +  node.getPP_Order_Workflow().getPP_Order().getM_Product_ID()));
		
			
			if(costvarian.compareTo(BigDecimal.ZERO)>0) {
				MPPCostCollector.createCollector(
						order,
						getM_Product_ID(), 
						getM_Locator_ID(), 
						getM_AttributeSetInstance_ID(), 
						node.getS_Resource_ID(),
						0, //PP_Order_BOMLine_ID
						node.getPP_Order_Node_ID(),
						MDocType.getDocType(MDocType.DOCBASETYPE_ManufacturingCostCollector), // C_DocType_ID
						MPPCostCollector.COSTCOLLECTORTYPE_UsegeVariance,
						order.getUpdated(),
						Qty, // Qty
						Env.ZERO, // scrap,
						Env.ZERO, // reject,
						setupTimeVariance.intValueExact(), //durationSetup,
						durationVariance, // duration
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


}
