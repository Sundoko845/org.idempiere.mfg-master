package org.libero.model;

import java.sql.ResultSet;
import java.util.Properties;

public class MCustomMpsMaterial extends X_custom_mpsmaterial {

	private static final long serialVersionUID = 6167277705185418414L;

	public MCustomMpsMaterial(Properties ctx, int custom_mpsmaterial_ID, String trxName, String... virtualColumns) {
		super(ctx, custom_mpsmaterial_ID, trxName, virtualColumns);
		// TODO Auto-generated constructor stub
	}

	public MCustomMpsMaterial(Properties ctx, int custom_mpsmaterial_ID, String trxName) {
		super(ctx, custom_mpsmaterial_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MCustomMpsMaterial(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}
	
	

}
