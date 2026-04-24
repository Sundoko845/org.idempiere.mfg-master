package org.libero.model;

import java.sql.ResultSet;
import java.util.Properties;

public class MCustomMpsMo extends X_custom_mpsmo {

	private static final long serialVersionUID = -5726218807304971207L;

	public MCustomMpsMo(Properties ctx, int custom_mpsmo_ID, String trxName, String... virtualColumns) {
		super(ctx, custom_mpsmo_ID, trxName, virtualColumns);
		// TODO Auto-generated constructor stub
	}

	public MCustomMpsMo(Properties ctx, int custom_mpsmo_ID, String trxName) {
		super(ctx, custom_mpsmo_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MCustomMpsMo(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}
	
	

}
