package org.jd.core.v1.model.classfile.constant;

import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.Visitor;

import java.util.List;

import static org.apache.bcel.Const.CONSTANT_Methodref;

public class ConstantMethodref extends ConstantCP {
		private List<String> listOfParameterSignatures;
	private String returnedSignature;

	public ConstantMethodref(int classIndex, int nameAndTypeIndex) {
		super(CONSTANT_Methodref, classIndex, nameAndTypeIndex);
	}

	public ConstantMethodref(int classIndex, int nameAndTypeIndex, List<String> listOfParameterSignatures,
			String returnedSignature) {
		super(CONSTANT_Methodref, classIndex, nameAndTypeIndex);
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
		return this.listOfParameterSignatures == null ? 0 : this.listOfParameterSignatures.size();
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

	@Override
	public void accept(Visitor v) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ (listOfParameterSignatures == null ? 0 : listOfParameterSignatures.hashCode());
		return prime * result + (returnedSignature == null ? 0 : returnedSignature.hashCode());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj) || getClass() != obj.getClass()) {
			return false;
		}
		ConstantMethodref other = (ConstantMethodref) obj;
		if (listOfParameterSignatures == null) {
			if (other.listOfParameterSignatures != null) {
				return false;
			}
		} else if (!listOfParameterSignatures.equals(other.listOfParameterSignatures)) {
			return false;
		}
		if (returnedSignature == null) {
			if (other.returnedSignature != null) {
				return false;
			}
		} else if (!returnedSignature.equals(other.returnedSignature)) {
			return false;
		}
		return true;
	}
}
