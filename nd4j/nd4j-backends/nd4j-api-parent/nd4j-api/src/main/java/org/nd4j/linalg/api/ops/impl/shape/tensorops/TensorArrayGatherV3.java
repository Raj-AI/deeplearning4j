package org.nd4j.linalg.api.ops.impl.shape.tensorops;

import lombok.val;
import onnx.OnnxProto3;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.list.compat.TensorList;

import java.util.List;
import java.util.Map;

public class TensorArrayGatherV3 extends BaseTensorOp {

   @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op name found for " + opName());
    }

    @Override
    public String tensorflowName() {
        return "TensorArrayGatherV3";
    }


    @Override
    public String toString() {
        return opName();
    }

    @Override
    public String opName() {
        return "tensorarraygatherv3";
    }

    @Override
    public TensorList execute(SameDiff sameDiff) {
       val list = getList(sameDiff);

       val array = list.stack();

       val name = this.getOwnName();

        sameDiff.putArrayForVarName(name, array);

        return list;
    }

    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Op.Type opType() {
        return Op.Type.CUSTOM;
    }
}
