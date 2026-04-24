package org.libero.model;

import java.math.BigDecimal;

public class TampungDataBom {

	private int productparent;
	private BigDecimal qtyrequired;
	private BigDecimal qtyparentorder;
	private int lowlwvel;
	
	public int getProductparent() {
		return productparent;
	}
	public void setProductparent(int productparent) {
		this.productparent = productparent;
	}
	public BigDecimal getQtyrequired() {
		return qtyrequired;
	}
	public void setQtyrequired(BigDecimal qtyrequired) {
		this.qtyrequired = qtyrequired;
	}
	public int getLowlwvel() {
		return lowlwvel;
	}
	public void setLowlwvel(int lowlwvel) {
		this.lowlwvel = lowlwvel;
	}
	public BigDecimal getQtyparentorder() {
		return qtyparentorder;
	}
	public void setQtyparentorder(BigDecimal qtyparentorder) {
		this.qtyparentorder = qtyparentorder;
	}
	
	
	
}
