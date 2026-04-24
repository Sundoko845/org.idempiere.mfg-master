package org.libero.model;

import java.sql.ResultSet;
import java.util.Properties;

public class MCustomMps extends X_custom_mps {
	
	private static final long serialVersionUID = 975077305060920088L;

	public MCustomMps(Properties ctx, int custom_mps_ID, String trxName) {
		super(ctx, custom_mps_ID, trxName);
		// TODO Auto-generated constructor stub
	}
	
	

	public MCustomMps(Properties ctx, int custom_mps_ID, String trxName, String... virtualColumns) {
		super(ctx, custom_mps_ID, trxName, virtualColumns);
		// TODO Auto-generated constructor stub
	}



	public MCustomMps(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

	
   @Override
protected boolean beforeSave(boolean newRecord) {
	System.out.println("tes");
	return super.beforeSave(newRecord);
}


	
	
	

}
