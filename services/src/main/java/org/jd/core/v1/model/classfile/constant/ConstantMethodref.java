package org.jd.core.v1.model.classfile.constant;

import java.util.List;

public class ConstantMethodref extends ConstantMemberRef {
	
	private List<String> listOfParameterSignatures;
	private String returnedSignature;

	public ConstantMethodref(int classIndex, int nameAndTypeIndex) {
		super(classIndex, nameAndTypeIndex);
	}

	public ConstantMethodref(int classIndex, int nameAndTypeIndex, List<String> listOfParameterSignatures,
			String returnedSignature) {
		super(classIndex, nameAndTypeIndex);
		this.listOfParameterSignatures = listOfParameterSignatures;
		this.returnedSignature = returnedSignature;
	}

	public List<String> getListOfParameterSignatures() {
		return listOfParameterSignatures;
	}

	public void setParameterSignatures(List<String> listOfParameterSignatures) {
		this.listOfParameterSignatures = listOfParameterSignatures;
	}

	public int getNbrOfParameters() {
		return (this.listOfParameterSignatures == null) ? 0 : this.listOfParameterSignatures.size();
	}

	public String getReturnedSignature() {
		return returnedSignature;
	}

	public void setReturnedSignature(String returnedSignature) {
		this.returnedSignature = returnedSignature;
	}

	public boolean returnAResult() {
		return this.returnedSignature != null && !"V".equals(this.returnedSignature);
	}
}
