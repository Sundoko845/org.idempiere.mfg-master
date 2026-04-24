package org.libero.factory;

import org.adempiere.webui.factory.IFormFactory;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;

public class LiberoFormFactory implements IFormFactory {

	@Override
	public ADForm newFormInstance(String formName) {
		if (formName.startsWith("org.libero.form")) 
		{
			Object form = null;
			Class<?> clasz =  null;
			ClassLoader loader = getClass().getClassLoader();
			try {
				clasz = loader.loadClass(formName);
			} catch (Exception e) {
				// TODO: handle exception
			}
			
			if(clasz != null ) 
			{
				try {
					form = clasz.newInstance();
				} catch (Exception e) {
					// TODO: handle exception
				}
			}
			if(form !=null) 
			{
				if(form instanceof ADForm) 
				{
					return (ADForm) form;
				}else if(form instanceof IFormController) 
				{
					IFormController controller = (IFormController) form;
					ADForm adForm = controller.getForm();
					adForm.setICustomForm(controller);
					return adForm;
				}
			}
		}
		return null;
	}

}
