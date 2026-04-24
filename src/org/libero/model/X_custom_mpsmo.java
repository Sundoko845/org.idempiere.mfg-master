/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2012 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
/** Generated Model - DO NOT CHANGE */
package org.libero.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;

import org.compiere.model.I_Persistent;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.POInfo;
import org.compiere.util.Env;

/** Generated Model for custom_mpsmo
 *  @author iDempiere (generated) 
 *  @version Release 10 - $Id$ */
@org.adempiere.base.Model(table="custom_mpsmo")
public class X_custom_mpsmo extends PO implements I_custom_mpsmo, I_Persistent 
{

	/**
	 *
	 */
	private static final long serialVersionUID = 20240329L;

    /** Standard Constructor */
    public X_custom_mpsmo (Properties ctx, int custom_mpsmo_ID, String trxName)
    {
      super (ctx, custom_mpsmo_ID, trxName);
      /** if (custom_mpsmo_ID == 0)
        {
			setcustom_mpsmo_ID (0);
        } */
    }

    /** Standard Constructor */
    public X_custom_mpsmo (Properties ctx, int custom_mpsmo_ID, String trxName, String ... virtualColumns)
    {
      super (ctx, custom_mpsmo_ID, trxName, virtualColumns);
      /** if (custom_mpsmo_ID == 0)
        {
			setcustom_mpsmo_ID (0);
        } */
    }

    /** Load Constructor */
    public X_custom_mpsmo (Properties ctx, ResultSet rs, String trxName)
    {
      super (ctx, rs, trxName);
    }

    /** AccessLevel
      * @return 3 - Client - Org 
      */
    protected int get_AccessLevel()
    {
      return accessLevel.intValue();
    }

    /** Load Meta Data */
    protected POInfo initPO (Properties ctx)
    {
      POInfo poi = POInfo.getPOInfo (ctx, Table_ID, get_TrxName());
      return poi;
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder ("X_custom_mpsmo[")
        .append(get_ID()).append("]");
      return sb.toString();
    }

	public org.compiere.model.I_C_Period getC_Period() throws RuntimeException
	{
		return (org.compiere.model.I_C_Period)MTable.get(getCtx(), org.compiere.model.I_C_Period.Table_ID)
			.getPO(getC_Period_ID(), get_TrxName());
	}

	/** Set Period.
		@param C_Period_ID Period of the Calendar
	*/
	public void setC_Period_ID (int C_Period_ID)
	{
		if (C_Period_ID < 1)
			set_Value (COLUMNNAME_C_Period_ID, null);
		else
			set_Value (COLUMNNAME_C_Period_ID, Integer.valueOf(C_Period_ID));
	}

	/** Get Period.
		@return Period of the Calendar
	  */
	public int getC_Period_ID()
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_C_Period_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	public I_custom_mps getcustom_mps() throws RuntimeException
	{
		return (I_custom_mps)MTable.get(getCtx(), I_custom_mps.Table_ID)
			.getPO(getcustom_mps_ID(), get_TrxName());
	}

	/** Set custom_mps.
		@param custom_mps_ID custom_mps
	*/
	public void setcustom_mps_ID (int custom_mps_ID)
	{
		if (custom_mps_ID < 1)
			set_ValueNoCheck (COLUMNNAME_custom_mps_ID, null);
		else
			set_ValueNoCheck (COLUMNNAME_custom_mps_ID, Integer.valueOf(custom_mps_ID));
	}

	/** Get custom_mps.
		@return custom_mps	  */
	public int getcustom_mps_ID()
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_custom_mps_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set custom_mpsmo.
		@param custom_mpsmo_ID custom_mpsmo
	*/
	public void setcustom_mpsmo_ID (int custom_mpsmo_ID)
	{
		if (custom_mpsmo_ID < 1)
			set_ValueNoCheck (COLUMNNAME_custom_mpsmo_ID, null);
		else
			set_ValueNoCheck (COLUMNNAME_custom_mpsmo_ID, Integer.valueOf(custom_mpsmo_ID));
	}

	/** Get custom_mpsmo.
		@return custom_mpsmo	  */
	public int getcustom_mpsmo_ID()
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_custom_mpsmo_ID);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}

	/** Set custom_mpsmo_UU.
		@param custom_mpsmo_UU custom_mpsmo_UU
	*/
	public void setcustom_mpsmo_UU (String custom_mpsmo_UU)
	{
		set_ValueNoCheck (COLUMNNAME_custom_mpsmo_UU, custom_mpsmo_UU);
	}

	/** Get custom_mpsmo_UU.
		@return custom_mpsmo_UU	  */
	public String getcustom_mpsmo_UU()
	{
		return (String)get_Value(COLUMNNAME_custom_mpsmo_UU);
	}

	/** Set Date Start.
		@param DateStart Date Start for this Order
	*/
	public void setDateStart (Timestamp DateStart)
	{
		set_Value (COLUMNNAME_DateStart, DateStart);
	}

	/** Get Date Start.
		@return Date Start for this Order
	  */
	public Timestamp getDateStart()
	{
		return (Timestamp)get_Value(COLUMNNAME_DateStart);
	}

	/** Set Quantity Plan.
		@param QtyPlan Planned Quantity
	*/
	public void setQtyPlan (BigDecimal QtyPlan)
	{
		set_Value (COLUMNNAME_QtyPlan, QtyPlan);
	}

	/** Get Quantity Plan.
		@return Planned Quantity
	  */
	public BigDecimal getQtyPlan()
	{
		BigDecimal bd = (BigDecimal)get_Value(COLUMNNAME_QtyPlan);
		if (bd == null)
			 return Env.ZERO;
		return bd;
	}

	/** Set Week.
		@param week Week
	*/
	public void setweek (int week)
	{
		set_Value (COLUMNNAME_week, Integer.valueOf(week));
	}

	/** Get Week.
		@return Week	  */
	public int getweek()
	{
		Integer ii = (Integer)get_Value(COLUMNNAME_week);
		if (ii == null)
			 return 0;
		return ii.intValue();
	}
}